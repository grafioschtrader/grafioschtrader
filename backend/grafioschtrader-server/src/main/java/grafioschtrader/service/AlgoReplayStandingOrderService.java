package grafioschtrader.service;

import java.math.BigDecimal;
import java.time.DayOfWeek;
import java.time.LocalDate;
import java.time.YearMonth;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

import org.springframework.stereotype.Service;

import com.ezylang.evalex.Expression;
import com.ezylang.evalex.config.ExpressionConfiguration;

import grafiosch.common.DataHelper;
import grafioschtrader.common.DataBusinessHelper;
import grafioschtrader.entities.Cashaccount;
import grafioschtrader.entities.Historyquote;
import grafioschtrader.entities.Transaction;
import grafioschtrader.exceptions.TransactionLimitExceededException;
import grafioschtrader.repository.CashaccountJpaRepository;
import grafioschtrader.repository.TransactionJpaRepository;
import grafioschtrader.service.AlgoReplayInputs.CashStandingOrder;
import grafioschtrader.types.AlgoEventType;
import grafioschtrader.types.PeriodDayPosition;
import grafioschtrader.types.TransactionType;
import grafioschtrader.types.WeekendAdjustType;

/** Executes the frozen cash-account standing orders of one historical replay. */
@Service
public class AlgoReplayStandingOrderService {
  /**
   * One configuration for every expression. {@code new Expression(text)} builds the default configuration anew on each
   * call, including an instance of every built-in function whose parameters are read by reflection, which a replay
   * evaluating these formulas thousands of times paid for every time. The configuration is never modified here.
   */
  private static final ExpressionConfiguration EVALEX = ExpressionConfiguration.defaultConfiguration();

  private static final String RATIONALE_FAILED = "REPLAY_STANDING_ORDER_FAILED";
  private static final int MAX_OCCURRENCES = 100_000;

  private final CashaccountJpaRepository cashaccounts;
  private final TransactionJpaRepository transactions;
  private final GlobalparametersService globalparameters;

  public AlgoReplayStandingOrderService(CashaccountJpaRepository cashaccounts, TransactionJpaRepository transactions,
      GlobalparametersService globalparameters) {
    this.cashaccounts = cashaccounts;
    this.transactions = transactions;
    this.globalparameters = globalparameters;
  }

  /** Builds the complete effective-date schedule before the run starts walking its timeline. */
  Map<LocalDate, List<CashStandingOrder>> schedule(AlgoReplayInputs.Snapshot inputs, LocalDate opening, LocalDate end) {
    Map<LocalDate, List<CashStandingOrder>> result = new LinkedHashMap<>();
    List<CashStandingOrder> orders = inputs.cashStandingOrders() == null ? List.of() : inputs.cashStandingOrders();
    for (CashStandingOrder order : orders) {
      LocalDate scheduled = order.validFrom();
      int occurrences = 0;
      while (scheduled != null && !scheduled.isAfter(order.validTo()) && !scheduled.isAfter(end)) {
        LocalDate effective = adjustForWeekend(scheduled, order.weekendAdjust());
        if (effective.isAfter(opening) && !effective.isAfter(end)) {
          result.computeIfAbsent(effective, _ -> new ArrayList<>()).add(order);
        }
        scheduled = nextDate(order, scheduled);
        if (++occurrences > MAX_OCCURRENCES) {
          throw new IllegalArgumentException("Standing order recurrence exceeds the replay safety limit");
        }
      }
    }
    result.values().forEach(list -> list.sort(Comparator.comparing(CashStandingOrder::id)));
    return result;
  }

  /** Executes every occurrence whose adjusted effective date is the supplied timeline date. */
  void execute(AlgoReplayState state, LocalDate date, List<CashStandingOrder> orders) {
    for (CashStandingOrder order : orders) {
      try {
        transactions.throwWhenTransactionLimitReached(state.idTenant(), 1);
        Cashaccount account = cashaccounts.findByIdSecuritycashAccountAndIdTenant(order.idCashaccount(),
            state.idTenant());
        if (account == null) {
          throw new IllegalArgumentException("Cash account is no longer available");
        }
        Transaction saved = transactions.saveOnlyAttributes(transaction(state, order, account, date), null, Set.of());
        if (saved.getTransactionType() == TransactionType.DEPOSIT
            || saved.getTransactionType() == TransactionType.WITHDRAWAL) {
          state.addExternalCashFlow(date, account.getCurrency(), saved.getCashaccountAmount());
        }
        state.write(AlgoEventType.CASH_STANDING_ORDER, date, null, null, null, null, saved.getCashaccountAmount(),
            account.getCurrency(), saved.getTransactionType().name(), details(order, account));
      } catch (TransactionLimitExceededException failure) {
        throw failure;
      } catch (Exception failure) {
        state.write(AlgoEventType.UNAVAILABLE, date, null, null, null, null, null, order.cashaccountCurrency(),
            RATIONALE_FAILED, details(order, null) + ": " + message(failure));
      }
    }
  }

  private Transaction transaction(AlgoReplayState state, CashStandingOrder order, Cashaccount account, LocalDate date)
      throws Exception {
    Transaction transaction = new Transaction();
    transaction.setIdTenant(state.idTenant());
    transaction.setCashaccount(account);
    transaction.setCashaccountAmount(amount(state, order, account, date));
    transaction.setTransactionType(order.transactionType());
    transaction.setTransactionTime(date.atStartOfDay());
    transaction.setTransactionCost(order.transactionCost());
    transaction.setIdStandingOrder(order.id());
    transaction.setSimulationOpening(false);
    transaction.setNote(order.note());
    return transaction;
  }

  private double amount(AlgoReplayState state, CashStandingOrder order, Cashaccount account, LocalDate date)
      throws Exception {
    Double rate = null;
    if (order.idCurrencypair() != null) {
      rate = closeWithinPastTolerance(state.market, order.idCurrencypair(), date, order.quoteToleranceDays());
      if (rate == null) {
        throw new IllegalArgumentException("No exchange rate is available on or before " + date);
      }
    }
    double amount;
    if (order.formula() != null && !order.formula().isBlank()) {
      Expression expression = new Expression(order.formula(), EVALEX).with("a", BigDecimal.valueOf(order.amount()));
      if (rate != null) {
        expression.with("r", BigDecimal.valueOf(rate));
      }
      amount = expression.evaluate().getNumberValue().doubleValue();
    } else if (rate != null) {
      amount = DataBusinessHelper.divideMultiplyExchangeRate(order.amount(), rate, order.amountCurrency(),
          account.getCurrency());
    } else {
      amount = order.amount();
    }
    return DataHelper.round(amount, globalparameters.getPrecisionForCurrency(account.getCurrency()));
  }

  private static Double closeWithinPastTolerance(AlgoReplayMarketData market, Integer id, LocalDate date,
      byte tolerance) {
    LocalDate oldest = date.minusDays(Math.abs(tolerance));
    List<Historyquote> history = market.history(id, date);
    if (history.isEmpty()) {
      return null;
    }
    Historyquote quote = history.getLast();
    return quote.getDate().isBefore(oldest) ? null : quote.getClose();
  }

  private static LocalDate adjustForWeekend(LocalDate date, WeekendAdjustType adjustment) {
    if (date.getDayOfWeek() == DayOfWeek.SATURDAY) {
      return adjustment == WeekendAdjustType.BEFORE ? date.minusDays(1) : date.plusDays(2);
    }
    if (date.getDayOfWeek() == DayOfWeek.SUNDAY) {
      return adjustment == WeekendAdjustType.BEFORE ? date.minusDays(2) : date.plusDays(1);
    }
    return date;
  }

  private static LocalDate nextDate(CashStandingOrder order, LocalDate date) {
    return switch (order.repeatUnit()) {
    case DAYS -> date.plusDays(order.repeatInterval());
    case MONTHS -> resolveDay(date.plusMonths(order.repeatInterval()), order.periodDayPosition(),
        order.dayOfExecution());
    case YEARS -> {
      LocalDate next = date.plusYears(order.repeatInterval());
      if (order.monthOfExecution() != null) {
        next = next.withMonth(order.monthOfExecution());
      }
      yield resolveDay(next, order.periodDayPosition(), order.dayOfExecution());
    }
    };
  }

  private static LocalDate resolveDay(LocalDate date, PeriodDayPosition position, Byte day) {
    YearMonth month = YearMonth.from(date);
    return switch (position) {
    case FIRST_DAY -> month.atDay(1);
    case LAST_DAY -> month.atEndOfMonth();
    case SPECIFIC_DAY -> month.atDay(Math.min(day, (byte) month.lengthOfMonth()));
    };
  }

  private static String details(CashStandingOrder order, Cashaccount account) {
    String name = account == null ? order.cashaccountName() : account.getName();
    return "#" + order.id() + " · " + name;
  }

  private static String message(Exception failure) {
    return failure.getMessage() == null ? failure.getClass().getSimpleName() : failure.getMessage();
  }
}
