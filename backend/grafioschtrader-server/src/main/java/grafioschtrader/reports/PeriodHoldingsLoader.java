package grafioschtrader.reports;

import java.time.LocalDate;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import grafioschtrader.entities.HoldDailyTotal;
import grafioschtrader.entities.HoldDailyTotalState;
import grafioschtrader.reportviews.performance.IPeriodHolding;
import grafioschtrader.repository.HoldDailyTotalJpaRepository;
import grafioschtrader.repository.HoldDailyTotalStateJpaRepository;
import grafioschtrader.repository.HoldSecurityaccountSecurityJpaRepository;

//@formatter:off
/**
 * Loads the daily holdings of a tenant or portfolio for the period performance report, reading the days the task
 * {@code HOLD_DAILY_TOTAL_UPDATE} has already stored from {@code hold_daily_total} and only the remaining days through
 * the period holdings query.
 *
 * <p>
 * The stored rows are copies of that query, so a stored day and a computed day are the same. Reading them is what
 * makes the report fast: over twenty years the query took 7 s for a tenant, the stored rows 0.1 s. A day is taken from
 * the table only when it is known to be current, that is
 * </p>
 * <ul>
 * <li>on or after the first stored day of the scope. The task starts at the zero base, the live query also returns the
 * cash-only days before it;</li>
 * <li>before the marker {@code recalc_from_date} of the tenant, which bookings lower at once;</li>
 * <li>before the earliest day whose price was created or changed since the last run of the task. Such a price, for
 * example a back-filled gap, only lowers the marker on the next run, and until then the stored rows of that day and
 * the following ones are outdated or missing.</li>
 * </ul>
 *
 * <p>
 * A tenant the task has never computed has no marker; all its days come from the query. The periodic prices of
 * {@code historyquote_period} carry no timestamp, so a change of one is noticed by neither the task nor this loader.
 * </p>
 *
 * <p>
 * Everything is read in one read-only transaction, so the marker and the rows belong to the same snapshot even when the
 * task rewrites the rows at the same moment.
 * </p>
 */
//@formatter:on
@Component
public class PeriodHoldingsLoader {

  /**
   * Shorter ranges are computed live in full. The check for changed prices is one pass over the price table, about
   * 0.75 s, while the query costs about 1.5 ms per calendar day for a client with twenty years and some 50 securities.
   * The stored rows therefore pay off from about a year on; the card of the last sessions, a few days, stays live.
   */
  static final long MIN_STORED_RANGE_DAYS = 366;

  private final HoldSecurityaccountSecurityJpaRepository holdSecurityaccountSecurityJpaRepository;
  private final HoldDailyTotalJpaRepository holdDailyTotalJpaRepository;
  private final HoldDailyTotalStateJpaRepository holdDailyTotalStateJpaRepository;

  public PeriodHoldingsLoader(HoldSecurityaccountSecurityJpaRepository holdSecurityaccountSecurityJpaRepository,
      HoldDailyTotalJpaRepository holdDailyTotalJpaRepository,
      HoldDailyTotalStateJpaRepository holdDailyTotalStateJpaRepository) {
    this.holdSecurityaccountSecurityJpaRepository = holdSecurityaccountSecurityJpaRepository;
    this.holdDailyTotalJpaRepository = holdDailyTotalJpaRepository;
    this.holdDailyTotalStateJpaRepository = holdDailyTotalStateJpaRepository;
  }

  /**
   * The daily holdings of a tenant or of one of its portfolios between two days, exactly as the period holdings query
   * returns them for that range.
   *
   * @param idTenant    the tenant; for a portfolio the tenant it belongs to
   * @param idPortfolio the portfolio, or null for the total of the tenant
   * @param dateFrom    the first day, inclusive
   * @param dateTo      the last day, inclusive
   * @return the days in ascending order
   */
  @Transactional(readOnly = true)
  public List<IPeriodHolding> load(Integer idTenant, Integer idPortfolio, LocalDate dateFrom, LocalDate dateTo) {
    if (ChronoUnit.DAYS.between(dateFrom, dateTo) < MIN_STORED_RANGE_DAYS) {
      return live(idTenant, idPortfolio, dateFrom, dateTo);
    }
    LocalDate firstStored = firstStoredDay(idTenant, idPortfolio);
    LocalDate currentBefore = firstStored == null ? null : currentBefore(idTenant);
    if (currentBefore == null || !currentBefore.isAfter(firstStored) || !currentBefore.isAfter(dateFrom)
        || firstStored.isAfter(dateTo)) {
      return live(idTenant, idPortfolio, dateFrom, dateTo);
    }
    List<IPeriodHolding> holdings = new ArrayList<>();
    if (dateFrom.isBefore(firstStored)) {
      holdings.addAll(live(idTenant, idPortfolio, dateFrom, firstStored.minusDays(1)));
    }
    LocalDate storedFrom = dateFrom.isBefore(firstStored) ? firstStored : dateFrom;
    LocalDate storedTo = dateTo.isBefore(currentBefore) ? dateTo : currentBefore.minusDays(1);
    holdings.addAll(stored(idTenant, idPortfolio, storedFrom, storedTo));
    if (!currentBefore.isAfter(dateTo)) {
      holdings.addAll(live(idTenant, idPortfolio, currentBefore, dateTo));
    }
    return holdings;
  }

  /**
   * The first day from which the stored rows may be outdated or missing, or null when the tenant has never been
   * computed.
   */
  private LocalDate currentBefore(Integer idTenant) {
    Optional<HoldDailyTotalState> state = holdDailyTotalStateJpaRepository.findById(idTenant);
    if (state.isEmpty() || state.get().getRecalcFromDate() == null) {
      return null;
    }
    LocalDate currentBefore = state.get().getRecalcFromDate();
    if (state.get().getQuoteCheckedTime() != null) {
      LocalDate changedQuoteDate = holdDailyTotalJpaRepository.getEarliestChangedQuoteDateByTenant(idTenant,
          state.get().getQuoteCheckedTime());
      if (changedQuoteDate != null && changedQuoteDate.isBefore(currentBefore)) {
        currentBefore = changedQuoteDate;
      }
    }
    return currentBefore;
  }

  private LocalDate firstStoredDay(Integer idTenant, Integer idPortfolio) {
    Optional<HoldDailyTotal> first = idPortfolio == null
        ? holdDailyTotalJpaRepository.findFirstByIdTenantAndIdPortfolioIsNullOrderByHoldDate(idTenant)
        : holdDailyTotalJpaRepository.findFirstByIdTenantAndIdPortfolioOrderByHoldDate(idTenant, idPortfolio);
    return first.map(HoldDailyTotal::getHoldDate).orElse(null);
  }

  private List<HoldDailyTotal> stored(Integer idTenant, Integer idPortfolio, LocalDate fromDate, LocalDate toDate) {
    return idPortfolio == null
        ? holdDailyTotalJpaRepository.findByIdTenantAndIdPortfolioIsNullAndHoldDateBetweenOrderByHoldDate(idTenant,
            fromDate, toDate)
        : holdDailyTotalJpaRepository.findByIdTenantAndIdPortfolioAndHoldDateBetweenOrderByHoldDate(idTenant,
            idPortfolio, fromDate, toDate);
  }

  private List<IPeriodHolding> live(Integer idTenant, Integer idPortfolio, LocalDate fromDate, LocalDate toDate) {
    return idPortfolio == null
        ? holdSecurityaccountSecurityJpaRepository.getPeriodHoldingsByTenant(idTenant, fromDate, toDate)
        : holdSecurityaccountSecurityJpaRepository.getPeriodHoldingsByPortfolio(idPortfolio, fromDate, toDate);
  }
}
