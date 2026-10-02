package grafioschtrader.service;

import java.math.BigDecimal;
import java.time.DayOfWeek;
import java.time.LocalDate;
import java.time.YearMonth;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.function.BiPredicate;
import java.util.stream.Collectors;

import org.springframework.context.MessageSource;
import org.springframework.stereotype.Service;

import com.ezylang.evalex.Expression;
import com.ezylang.evalex.config.ExpressionConfiguration;

import grafiosch.common.DataHelper;
import grafiosch.exceptions.DataViolationException;
import grafioschtrader.common.DataBusinessHelper;
import grafioschtrader.entities.Cashaccount;
import grafioschtrader.entities.Historyquote;
import grafioschtrader.entities.Security;
import grafioschtrader.entities.Transaction;
import grafioschtrader.exceptions.TransactionLimitExceededException;
import grafioschtrader.repository.CashaccountJpaRepository;
import grafioschtrader.repository.TransactionJpaRepository;
import grafioschtrader.service.AlgoReplayInputs.CashStandingOrder;
import grafioschtrader.service.AlgoReplayInputs.SecurityStandingOrder;
import grafioschtrader.service.StandingOrderExecutionService.SecurityOrderAmounts;
import grafioschtrader.types.AlgoEventType;
import grafioschtrader.types.PeriodDayPosition;
import grafioschtrader.types.RepeatUnit;
import grafioschtrader.types.TransactionType;
import grafioschtrader.types.WeekendAdjustType;

/**
 * Executes the frozen standing orders of one historical replay. A cash standing order is booked on its weekend-adjusted
 * date. A security standing order is additionally moved onto a session of its instrument's exchange, as the daily
 * execution does, and is booked at that day's close with the costs the standing order itself defines.
 */
@Service
public class AlgoReplayStandingOrderService {
  /**
   * One configuration for every expression. {@code new Expression(text)} builds the default configuration anew on each
   * call, including an instance of every built-in function whose parameters are read by reflection, which a replay
   * evaluating these formulas thousands of times paid for every time. The configuration is never modified here.
   */
  private static final ExpressionConfiguration EVALEX = ExpressionConfiguration.defaultConfiguration();

  private static final String RATIONALE_FAILED = "REPLAY_STANDING_ORDER_FAILED";
  private static final String RATIONALE_SECURITY_FAILED = "REPLAY_SECURITY_STANDING_ORDER_FAILED";
  private static final int MAX_OCCURRENCES = 100_000;

  /** Reason of an occurrence for which no session of the exchange was found within the adjustment steps. */
  static final String NO_TRADING_DAY = "no trading day within "
      + StandingOrderExecutionService.MAX_TRADING_DAY_ADJUSTMENT_ITERATIONS + " days";

  /** Reason of an occurrence whose instrument has reached its end of life or a trading stop on the session found. */
  static final String NOT_TRADABLE = "not tradable";

  private final CashaccountJpaRepository cashaccounts;
  private final TransactionJpaRepository transactions;
  private final GlobalparametersService globalparameters;
  private final MessageSource messageSource;

  public AlgoReplayStandingOrderService(CashaccountJpaRepository cashaccounts, TransactionJpaRepository transactions,
      GlobalparametersService globalparameters, MessageSource messageSource) {
    this.cashaccounts = cashaccounts;
    this.transactions = transactions;
    this.globalparameters = globalparameters;
    this.messageSource = messageSource;
  }

  /**
   * One occurrence of a security standing order on the timeline of a replay.
   *
   * @param order       the frozen standing order
   * @param unavailable null when the occurrence is to be executed, otherwise why it cannot be; such an occurrence only
   *                    writes a row to the trail
   */
  record ScheduledSecurityOrder(SecurityStandingOrder order, String unavailable) {
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
        scheduled = nextDate(order.repeatUnit(), order.repeatInterval(), order.monthOfExecution(),
            order.periodDayPosition(), order.dayOfExecution(), scheduled);
        if (++occurrences > MAX_OCCURRENCES) {
          throw new IllegalArgumentException("Standing order recurrence exceeds the replay safety limit");
        }
      }
    }
    result.values().forEach(list -> list.sort(Comparator.comparing(CashStandingOrder::id)));
    return result;
  }

  /**
   * Builds the effective-date schedule of the security standing orders. Each occurrence is weekend-adjusted and then
   * moved in the direction of its weekend adjustment until its exchange holds a session, at most
   * {@link StandingOrderExecutionService#MAX_TRADING_DAY_ADJUSTMENT_ITERATIONS} steps - the rule of the daily execution.
   * An occurrence that finds no session stays on its weekend-adjusted date and is marked unavailable, and so is one
   * whose instrument is not tradable on the session found; that one is not moved further, because a later session
   * would not make an expired or stopped instrument tradable again.
   *
   * @param inputs   the frozen inputs of the run
   * @param opening  the opening date, on or before which no occurrence is executed
   * @param end      the end date of the run, after which no occurrence is executed
   * @param session  whether the exchange of the order's instrument holds a session on a date
   * @param tradable whether the order's instrument may be traded on a date
   * @return the occurrences by effective date, each list ordered by standing order id
   */
  Map<LocalDate, List<ScheduledSecurityOrder>> scheduleSecurities(AlgoReplayInputs.Snapshot inputs, LocalDate opening,
      LocalDate end, BiPredicate<SecurityStandingOrder, LocalDate> session,
      BiPredicate<SecurityStandingOrder, LocalDate> tradable) {
    Map<LocalDate, List<ScheduledSecurityOrder>> result = new LinkedHashMap<>();
    for (SecurityStandingOrder order : inputs.securityStandingOrdersOrEmpty()) {
      LocalDate scheduled = order.validFrom();
      int occurrences = 0;
      while (scheduled != null && !scheduled.isAfter(order.validTo()) && !scheduled.isAfter(end)) {
        LocalDate weekendAdjusted = adjustForWeekend(scheduled, order.weekendAdjust());
        LocalDate effective = toSession(order, weekendAdjusted, session);
        String unavailable = null;
        if (effective == null) {
          effective = weekendAdjusted;
          unavailable = NO_TRADING_DAY;
        } else if (!tradable.test(order, effective)) {
          unavailable = NOT_TRADABLE;
        }
        if (effective.isAfter(opening) && !effective.isAfter(end)) {
          result.computeIfAbsent(effective, _ -> new ArrayList<>()).add(new ScheduledSecurityOrder(order, unavailable));
        }
        scheduled = nextDate(order.repeatUnit(), order.repeatInterval(), order.monthOfExecution(),
            order.periodDayPosition(), order.dayOfExecution(), scheduled);
        if (++occurrences > MAX_OCCURRENCES) {
          throw new IllegalArgumentException("Standing order recurrence exceeds the replay safety limit");
        }
      }
    }
    result.values().forEach(list -> list.sort(Comparator.comparing(scheduled -> scheduled.order().id())));
    return result;
  }

  /**
   * @return the first session found by stepping from {@code date} in the direction of the order's weekend adjustment,
   *         or null when none lies within the allowed steps
   */
  private static LocalDate toSession(SecurityStandingOrder order, LocalDate date,
      BiPredicate<SecurityStandingOrder, LocalDate> session) {
    LocalDate candidate = date;
    for (int i = 0; i < StandingOrderExecutionService.MAX_TRADING_DAY_ADJUSTMENT_ITERATIONS; i++) {
      if (session.test(order, candidate)) {
        return candidate;
      }
      candidate = order.weekendAdjust() == WeekendAdjustType.BEFORE ? candidate.minusDays(1) : candidate.plusDays(1);
      candidate = adjustForWeekend(candidate, order.weekendAdjust());
    }
    return null;
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

  /**
   * Executes the security standing orders whose effective date is the supplied timeline date. A failure is written to
   * the trail and the run continues; only the transaction limit ends it, as for cash standing orders. The fills are not
   * external flows - cash is exchanged for the instrument - and they carry no strategy, so a strategy managing the same
   * instrument sees their units as unassigned.
   *
   * @param state  the running replay
   * @param date   the timeline date
   * @param orders the occurrences due on that date
   */
  void executeSecurities(AlgoReplayState state, LocalDate date, List<ScheduledSecurityOrder> orders) {
    for (ScheduledSecurityOrder scheduled : orders) {
      SecurityStandingOrder order = scheduled.order();
      Security security = state.securities.get(order.idSecurity());
      if (scheduled.unavailable() != null) {
        writeSecurityFailure(state, date, order, security, scheduled.unavailable());
        continue;
      }
      try {
        transactions.throwWhenTransactionLimitReached(state.idTenant(), 1);
        Cashaccount account = cashaccounts.findByIdSecuritycashAccountAndIdTenant(order.idCashaccount(),
            state.idTenant());
        if (account == null || security == null) {
          throw new IllegalArgumentException(account == null ? "Cash account is no longer available"
              : "Security is no longer available");
        }
        Transaction saved = transactions.saveOnlyAttributes(securityTransaction(state, order, security, account, date),
            null, Set.of());
        state.roundTrips.add(null, security.getId(), saved.getTransactionType(), saved.getUnits(),
            saved.getQuotation(), saved.getTransactionCost(), saved.getTaxCost(), saved.getAssetInvestmentValue1(),
            saved.getTransactionDate());
        state.write(AlgoEventType.SECURITY_STANDING_ORDER, date, null, security.getId(), saved.getUnits(),
            saved.getQuotation(), saved.getCashaccountAmount(), account.getCurrency(),
            saved.getTransactionType().name(), securityDetails(order, security));
      } catch (TransactionLimitExceededException failure) {
        throw failure;
      } catch (Exception failure) {
        writeSecurityFailure(state, date, order, security, describe(failure, order, state.locale));
      }
    }
  }

  private Transaction securityTransaction(AlgoReplayState state, SecurityStandingOrder order, Security security,
      Cashaccount account, LocalDate date) {
    Double quotation = closeWithinPastTolerance(state.market, order.idSecurity(), date, order.quoteToleranceDays());
    if (quotation == null) {
      throw new IllegalArgumentException("No price is available on or before " + date);
    }
    Double rate = null;
    if (order.idCurrencypair() != null) {
      rate = closeWithinPastTolerance(state.market, order.idCurrencypair(), date, order.quoteToleranceDays());
      if (rate == null) {
        throw new IllegalArgumentException("No exchange rate is available on or before " + date);
      }
    }
    SecurityOrderAmounts amounts = StandingOrderExecutionService.securityOrderAmounts(order.units(),
        order.investAmount(), order.amountIncludesCosts(), order.fractionalUnits(), order.taxCost(),
        order.taxCostFormula(), order.transactionCost(), order.transactionCostFormula(), order.transactionType(),
        quotation, rate, order.securityCurrency(), account.getCurrency());
    Transaction transaction = new Transaction(order.idSecurityaccount(), account, security,
        amounts.cashaccountAmount(), amounts.units(), quotation, order.transactionType(),
        amounts.taxCost() > 0 ? amounts.taxCost() : null,
        amounts.transactionCost() > 0 ? amounts.transactionCost() : null, null, date.atStartOfDay(), rate,
        order.idCurrencypair(), null, null);
    transaction.setIdTenant(state.idTenant());
    transaction.setIdStandingOrder(order.id());
    transaction.setSimulationOpening(false);
    transaction.setNote(order.note());
    return transaction;
  }

  private static void writeSecurityFailure(AlgoReplayState state, LocalDate date, SecurityStandingOrder order,
      Security security, String reason) {
    state.write(AlgoEventType.UNAVAILABLE, date, null, order.idSecurity(), null, null, null,
        order.cashaccountCurrency(), RATIONALE_SECURITY_FAILED, securityDetails(order, security) + ": " + reason);
  }

  /**
   * Turns the refusal of an occurrence into readable text in the language of the run. The write path keeps its reasons
   * in the violations of a {@link DataViolationException}, and the shared amount calculation raises a message key.
   */
  private String describe(Exception failure, SecurityStandingOrder order, Locale locale) {
    if (failure instanceof DataViolationException violation) {
      return violation.getDataViolation().stream()
          .map(v -> messageSource.getMessage(v.getMessageKey(), v.getData(), v.getMessageKey(), locale))
          .collect(Collectors.joining(", "));
    }
    if (StandingOrderExecutionService.ZERO_UNITS_KEY.equals(failure.getMessage())) {
      return messageSource.getMessage(StandingOrderExecutionService.ZERO_UNITS_KEY, new Object[] { order.id() },
          StandingOrderExecutionService.ZERO_UNITS_KEY, locale);
    }
    return message(failure);
  }

  private static String securityDetails(SecurityStandingOrder order, Security security) {
    return "#" + order.id() + " · " + (security == null ? String.valueOf(order.idSecurity()) : security.getName());
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

  /**
   * The close of an instrument on the given day or on one of the {@code |tolerance|} days before it. Unlike the daily
   * execution the replay never looks ahead: a simulation standing order cannot prefer a future quote, and the market
   * data of a replay cannot return one.
   *
   * @return the latest close within the window, or null when the window holds none
   */
  static Double closeWithinPastTolerance(AlgoReplayMarketData market, Integer id, LocalDate date,
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

  private static LocalDate nextDate(RepeatUnit repeatUnit, short repeatInterval, Byte monthOfExecution,
      PeriodDayPosition periodDayPosition, Byte dayOfExecution, LocalDate date) {
    return switch (repeatUnit) {
    case DAYS -> date.plusDays(repeatInterval);
    case MONTHS -> resolveDay(date.plusMonths(repeatInterval), periodDayPosition, dayOfExecution);
    case YEARS -> {
      LocalDate next = date.plusYears(repeatInterval);
      if (monthOfExecution != null) {
        next = next.withMonth(monthOfExecution);
      }
      yield resolveDay(next, periodDayPosition, dayOfExecution);
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
