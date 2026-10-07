package grafioschtrader.repository;

import java.time.LocalDate;
import java.util.List;

import grafioschtrader.entities.HoldDailyTotal;

/**
 * Read access to the daily total value for consumers. Each result carries the {@code recalcFromDate} of the tenant, so
 * that a caller knows from which day on the rows may be outdated or missing.
 *
 * <p>
 * The methods do not check ownership: the caller passes the tenant of the request, and a portfolio of another tenant
 * simply yields no rows because both ids are part of the condition.
 * </p>
 */
public interface HoldDailyTotalJpaRepositoryCustom {

  /**
   * The rows of a tenant or of one of its portfolios between two days.
   *
   * @param idTenant    the tenant
   * @param idPortfolio the portfolio, or null for the total of the tenant
   * @param fromDate    the first day, inclusive
   * @param toDate      the last day, inclusive
   * @return the rows in ascending order of the day, with the marker of the tenant
   */
  HoldDailyTotalSeries getSeries(Integer idTenant, Integer idPortfolio, LocalDate fromDate, LocalDate toDate);

  /**
   * The last row of a tenant or of one of its portfolios on or before a day. A fee model wanting the value before a
   * trade passes the day before the trade date.
   *
   * @param idTenant    the tenant
   * @param idPortfolio the portfolio, or null for the total of the tenant
   * @param date        the latest day that may be returned
   * @return the row, or a null row when none exists, with the marker of the tenant
   */
  HoldDailyTotalValue getLastOnOrBefore(Integer idTenant, Integer idPortfolio, LocalDate date);

  /**
   * Rows of a tenant or portfolio together with the marker of the tenant.
   *
   * @param rows           the rows in ascending order of the day
   * @param recalcFromDate the first day whose rows may be outdated or missing; null when the tenant has never been
   *                       computed, so that every row may be missing
   */
  record HoldDailyTotalSeries(List<HoldDailyTotal> rows, LocalDate recalcFromDate) {
  }

  /**
   * One row of a tenant or portfolio together with the marker of the tenant.
   *
   * @param row            the row, null when none exists
   * @param recalcFromDate the first day whose rows may be outdated or missing; null when the tenant has never been
   *                       computed
   */
  record HoldDailyTotalValue(HoldDailyTotal row, LocalDate recalcFromDate) {

    /**
     * @return the total value of the row, null when there is no row; never 0 for a missing row
     */
    public Double totalBalanceMC() {
      return row == null ? null : row.getTotalBalanceMC();
    }
  }
}
