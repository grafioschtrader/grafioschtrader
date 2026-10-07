package grafioschtrader.service;

import java.time.LocalDate;
import java.util.Map;
import java.util.TreeMap;

import grafioschtrader.entities.HoldDailyTotal;
import grafioschtrader.repository.HoldDailyTotalJpaRepository;

/**
 * The daily total value of one tenant or portfolio, loaded once, for a caller that needs the value before many
 * different dates, such as the fee model comparison walking years of trades.
 *
 * <p>
 * A fee rule graded by the total assets uses the value of the last computed day <em>before</em> the trade date, because
 * the close of the trade date itself already contains the trade. A missing value is returned as null and never as 0.
 * </p>
 */
public final class HoldDailyTotalHistory {

  /** Lower bound of the loaded series; earlier than any trading day the application knows. */
  private static final LocalDate EARLIEST_DATE = LocalDate.of(1900, 1, 1);

  private final TreeMap<LocalDate, Double> totals = new TreeMap<>();

  private HoldDailyTotalHistory() {
  }

  /**
   * Loads the series of a tenant or portfolio up to a day.
   *
   * @param repository  the repository of the daily total value
   * @param idTenant    the tenant
   * @param idPortfolio the portfolio, or null for the total of the tenant
   * @param toDate      the last day that will be asked for
   * @return the loaded series
   */
  public static HoldDailyTotalHistory load(HoldDailyTotalJpaRepository repository, Integer idTenant,
      Integer idPortfolio, LocalDate toDate) {
    HoldDailyTotalHistory history = new HoldDailyTotalHistory();
    for (HoldDailyTotal row : repository.getSeries(idTenant, idPortfolio, EARLIEST_DATE, toDate).rows()) {
      history.totals.put(row.getHoldDate(), row.getTotalBalanceMC());
    }
    return history;
  }

  /**
   * The total value of the last computed day before a date.
   *
   * @param date the trade date
   * @return the total value, null when no day before it has been computed
   */
  public Double totalBefore(LocalDate date) {
    if (date == null) {
      return null;
    }
    Map.Entry<LocalDate, Double> entry = totals.lowerEntry(date);
    return entry == null ? null : entry.getValue();
  }
}
