package grafioschtrader.service;

import java.time.LocalDate;
import java.time.YearMonth;
import java.time.temporal.ChronoUnit;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TreeMap;
import java.util.TreeSet;
import java.util.function.Function;

import org.springframework.stereotype.Service;

import grafiosch.common.DataHelper;
import grafioschtrader.entities.Cashaccount;
import grafioschtrader.entities.Transaction;
import grafioschtrader.exceptions.TransactionLimitExceededException;
import grafioschtrader.repository.HoldCashaccountBalanceJpaRepository;
import grafioschtrader.repository.TransactionJpaRepository;
import grafioschtrader.types.AlgoEventType;
import grafioschtrader.types.TransactionType;

/**
 * Charges interest on the overdrawn cash accounts of a historical replay. An account whose frozen borrowing rate is above
 * zero accrues interest every calendar day on its negative balance at the end of that day, by the ACT/360 convention of
 * a current account overdraft. The accrued interest is charged at the end of every calendar month and on the end date of
 * the run, as an account interest booking with a negative amount; from the following day on it is itself overdrawn and
 * bears interest, which is the monthly capitalisation of a bank statement.
 *
 * <p>
 * Interest is an expense, not an external flow: it lowers the equity and the return of the run.
 * </p>
 */
@Service
public class AlgoReplayOverdraftInterest {

  /** Convention key stated by a run that charges overdraft interest. */
  public static final String CONVENTION = "REPLAY_OVERDRAFT_INTEREST_ACT360";
  static final String RATIONALE = "REPLAY_OVERDRAFT_INTEREST";
  static final String FAILED = "REPLAY_OVERDRAFT_INTEREST_FAILED";
  static final String NOTE = "[overdraft interest]";
  private static final double DAYS_PER_YEAR = 360.0;

  private final HoldCashaccountBalanceJpaRepository cashHoldings;
  private final TransactionJpaRepository transactions;
  private final AlgoReplayBooking booking;

  public AlgoReplayOverdraftInterest(HoldCashaccountBalanceJpaRepository cashHoldings,
      TransactionJpaRepository transactions, AlgoReplayBooking booking) {
    this.cashHoldings = cashHoldings;
    this.transactions = transactions;
    this.booking = booking;
  }

  /**
   * @param state        the running replay, whose frozen inputs name the borrowing rates
   * @param cashaccounts the cash accounts of the environment
   * @return the interest state of this run
   */
  Session open(AlgoReplayState state, List<Cashaccount> cashaccounts) {
    return new Session(state, cashaccounts, key -> cashHoldings.getBalanceBeforeDate(key.idCashaccount(),
        key.beforeDate()));
  }

  /**
   * The days interest is charged on: the last calendar day of every month after the opening date, and the end date.
   *
   * @param opening the opening date of the run, whose month end is charged only when it lies after it
   * @param end     the end date of the run
   * @return the charge days in order
   */
  static Set<LocalDate> chargeDates(LocalDate opening, LocalDate end) {
    Set<LocalDate> dates = new TreeSet<>();
    for (YearMonth month = YearMonth.from(opening); !month.atDay(1).isAfter(end); month = month.plusMonths(1)) {
      LocalDate monthEnd = month.atEndOfMonth();
      if (monthEnd.isAfter(opening) && !monthEnd.isAfter(end)) {
        dates.add(monthEnd);
      }
    }
    dates.add(end);
    return dates;
  }

  /**
   * Interest of one calendar day on a balance.
   *
   * @param balance the balance at the end of the day, null for an account without any booking
   * @param rate    the annual rate in percent
   * @return the interest of that day, zero for a balance that is not negative
   */
  static double dailyInterest(Double balance, double rate) {
    return balance == null || balance >= 0 ? 0 : -balance * rate / 100 / DAYS_PER_YEAR;
  }

  record BalanceKey(Integer idCashaccount, LocalDate beforeDate) {
  }

  /** Per-run mutable state, never shared by the singleton service. */
  final class Session {
    private final AlgoReplayState state;
    private final Map<Integer, Double> rates;
    private final Map<Integer, Cashaccount> accounts = new TreeMap<>();
    private final Map<Integer, Double> accrued = new TreeMap<>();
    private final Function<BalanceKey, Double> balances;
    /** The last day whose interest was accrued; the opening day belongs to the starting state and bears none. */
    private LocalDate previous;

    Session(AlgoReplayState state, List<Cashaccount> cashaccounts, Function<BalanceKey, Double> balances) {
      this.state = state;
      this.rates = state.inputs.borrowingRatesOrEmpty();
      this.balances = balances;
      this.previous = state.run.getOpeningDate();
      cashaccounts.stream().filter(c -> rates.containsKey(c.getId())).forEach(c -> accounts.put(c.getId(), c));
    }

    /** @return the charge days the timeline has to visit, empty when no account bears interest */
    Set<LocalDate> dates() {
      return rates.isEmpty() ? Set.of() : chargeDates(state.run.getOpeningDate(), state.run.getEndDate());
    }

    /**
     * Accrues the interest up to and including the given day, after every booking of that day, and charges it when the
     * day is a charge day. The calendar days since the previously accrued day carry the balance of that day, because
     * nothing is booked in between.
     *
     * @param date a day of the timeline, never earlier than the previous call
     */
    void close(LocalDate date) {
      if (rates.isEmpty() || !date.isAfter(previous)) {
        return;
      }
      long gap = ChronoUnit.DAYS.between(previous, date) - 1;
      for (var account : accounts.values()) {
        double rate = rates.get(account.getId());
        double interest = gap * dailyInterest(balance(account, previous), rate)
            + dailyInterest(balance(account, date), rate);
        accrued.merge(account.getId(), interest, Double::sum);
      }
      previous = date;
      if (date.getDayOfMonth() == date.lengthOfMonth() || date.equals(state.run.getEndDate())) {
        accounts.values().forEach(account -> charge(account, date));
      }
    }

    /** @return the balance at the end of the day, null when the account has no booking up to it */
    private Double balance(Cashaccount account, LocalDate date) {
      return balances.apply(new BalanceKey(account.getId(), date.plusDays(1)));
    }

    private void charge(Cashaccount account, LocalDate date) {
      double amount = DataHelper.round(accrued.getOrDefault(account.getId(), 0.0),
          state.precision(account.getCurrency()));
      if (amount <= 0) {
        return;
      }
      Transaction interest = new Transaction();
      interest.setIdTenant(state.idTenant());
      interest.setCashaccount(account);
      interest.setTransactionType(TransactionType.INTEREST_CASHACCOUNT);
      // Only a FEE is negated by the write path; an interest booking carries its signed cash effect.
      interest.setCashaccountAmount(-amount);
      interest.setTransactionTime(date.atTime(23, 59));
      interest.setAlgoFillId(state.run.getIdSimulationResult() + ":I:" + account.getId() + ":" + date);
      interest.setSimulationOpening(false);
      interest.setNote(NOTE);
      try {
        transactions.throwWhenTransactionLimitReached(state.idTenant(), 1);
        transactions.saveOnlyAttributes(interest, null, Set.of());
      } catch (TransactionLimitExceededException e) {
        throw e;
      } catch (Exception e) {
        throw new IllegalArgumentException(FAILED + ": " + account.getName() + ": " + reason(e), e);
      }
      accrued.put(account.getId(), 0.0);
      state.write(AlgoEventType.OVERDRAFT_INTEREST, date, null, null, null, null, -amount, account.getCurrency(),
          RATIONALE, account.getName());
    }

    private String reason(Exception e) {
      String details = booking == null ? null : booking.detailsOf(e, state.locale);
      return details != null ? details : e.getMessage();
    }
  }
}
