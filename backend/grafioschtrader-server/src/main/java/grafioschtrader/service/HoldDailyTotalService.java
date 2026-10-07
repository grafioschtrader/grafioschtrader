package grafioschtrader.service;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Set;
import java.util.function.Supplier;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import grafioschtrader.entities.HoldDailyTotal;
import grafioschtrader.entities.HoldDailyTotalState;
import grafioschtrader.entities.Portfolio;
import grafioschtrader.entities.Tenant;
import grafioschtrader.entities.TradingDaysPlus;
import grafioschtrader.reportviews.performance.IPeriodHolding;
import grafioschtrader.repository.HoldDailyTotalJpaRepository;
import grafioschtrader.repository.HoldDailyTotalStateJpaRepository;
import grafioschtrader.repository.HoldSecurityaccountSecurityJpaRepository;
import grafioschtrader.repository.PortfolioJpaRepository;
import grafioschtrader.repository.TenantJpaRepository;
import grafioschtrader.repository.TradingDaysPlusJpaRepository;
import grafioschtrader.types.TenantKindType;

/**
 * Brings the daily total value of a tenant and of each of its portfolios in {@code hold_daily_total} up to date.
 *
 * <p>
 * The rows are copies of the period holdings queries of the performance report, so they match that report by
 * construction. Per tenant, in one transaction:
 * </p>
 * <ol>
 * <li>The marker {@code hold_daily_total_state.recalc_from_date} is lowered to the earliest day on which a price the
 * tenant depends on was created or changed since the previous run. This catches back-filled gaps, corrected and
 * reloaded histories and the gap filler.</li>
 * <li>When the marker lies after the latest trading day, nothing is left to do.</li>
 * <li>The rows from the marker up to the latest trading day are deleted on both levels and computed again, with the
 * zero base row added exactly like the performance report adds it.</li>
 * <li>The new marker is the earliest day of that range with a missing quote that is still within the retry window, so
 * that a price arriving late is picked up on the next run, or else the day after the range. A holiday of the holdings
 * is no missing quote day and is never retried. A day outside the window stays a gap until its price arrives, which the
 * scan of step 1 then notices.</li>
 * </ol>
 *
 * <p>
 * A tenant without a marker has never been computed: its rows are deleted and its history is computed from the zero
 * base day, the last trading day before its first security position, or else from that first hold day. This covers new
 * tenants, tenants restored from an export and tenants whose hold tables were rebuilt.
 * </p>
 */
@Service
public class HoldDailyTotalService {

  /**
   * Overlap of two quote scans. A price written by a transaction that was still open when a scan started carries a
   * timestamp before the scan without being visible to it; looking back this far finds it on the next run.
   */
  private static final int QUOTE_SCAN_OVERLAP_MINUTES = 10;

  private static final Logger log = LoggerFactory.getLogger(HoldDailyTotalService.class);

  /** Days back from today within which a missing quote day is computed again on every run. */
  @Value("${gt.hold.daily.total.retry.days:10}")
  private int retryDays;

  @Autowired
  private HoldDailyTotalJpaRepository holdDailyTotalJpaRepository;

  @Autowired
  private HoldDailyTotalStateJpaRepository holdDailyTotalStateJpaRepository;

  @Autowired
  private HoldSecurityaccountSecurityJpaRepository holdSecurityaccountSecurityJpaRepository;

  @Autowired
  private PortfolioJpaRepository portfolioJpaRepository;

  @Autowired
  private TenantJpaRepository tenantJpaRepository;

  @Autowired
  private TradingDaysPlusJpaRepository tradingDaysPlusJpaRepository;

  /**
   * The tenants a run without a given tenant covers: every main tenant. Simulation copies are excluded.
   *
   * @return the ids of the main tenants
   */
  public List<Integer> getIdTenantsToUpdate() {
    return tenantJpaRepository.findByTenantKindType(TenantKindType.MAIN.getValue()).stream().map(Tenant::getIdTenant)
        .toList();
  }

  /**
   * Brings one tenant up to date. The marker stays locked until the transaction ends, so a booking lowering it
   * meanwhile waits and then lowers the marker written here instead of being overwritten by it.
   *
   * @param idTenant the tenant
   * @param today    the current day in UTC; the rows reach up to the latest trading day on or before it
   * @return the number of days computed on the tenant level, 0 when there was nothing to do
   */
  @Transactional
  public int updateTenant(Integer idTenant, LocalDate today) {
    LocalDateTime scanTime = holdDailyTotalStateJpaRepository.getDatabaseNow().minusMinutes(QUOTE_SCAN_OVERLAP_MINUTES);
    HoldDailyTotalState state = holdDailyTotalStateJpaRepository.lockByIdTenant(idTenant).orElse(null);
    if (state == null) {
      state = createState(idTenant);
      if (state == null) {
        return 0;
      }
    } else if (state.getQuoteCheckedTime() != null) {
      LocalDate changedQuoteDate = holdDailyTotalJpaRepository.getEarliestChangedQuoteDateByTenant(idTenant,
          state.getQuoteCheckedTime());
      if (changedQuoteDate != null && changedQuoteDate.isBefore(state.getRecalcFromDate())) {
        state.setRecalcFromDate(changedQuoteDate);
      }
    }
    state.setQuoteCheckedTime(scanTime);

    LocalDate fromDate = state.getRecalcFromDate();
    TradingDaysPlus latestTradingDay = tradingDaysPlusJpaRepository
        .findTopByTradingDateLessThanOrderByTradingDateDesc(today.plusDays(1));
    if (latestTradingDay == null || fromDate.isAfter(latestTradingDay.getTradingDate())) {
      return 0;
    }
    LocalDate toDate = latestTradingDay.getTradingDate();
    int days = recompute(idTenant, fromDate, toDate);
    state.setRecalcFromDate(getNextRecalcFromDate(idTenant, fromDate, toDate, today));
    return days;
  }

  /**
   * Starts a tenant that has never been computed: removes whatever rows it still has and inserts its marker at its
   * first day. The inserted row is locked by this transaction like the one {@code lockByIdTenant} would have returned.
   *
   * @param idTenant the tenant
   * @return the new marker, or null when the tenant has not held any security yet
   */
  private HoldDailyTotalState createState(Integer idTenant) {
    LocalDate startDate = holdSecurityaccountSecurityJpaRepository.getZeroBaseDateByTenant(idTenant);
    if (startDate == null) {
      startDate = holdSecurityaccountSecurityJpaRepository.findByIdTenantMinFromHoldDate(idTenant);
    }
    if (startDate == null) {
      return null;
    }
    holdDailyTotalJpaRepository.removeByIdTenant(idTenant);
    return holdDailyTotalStateJpaRepository.saveAndFlush(new HoldDailyTotalState(idTenant, startDate));
  }

  /**
   * Replaces the rows of a tenant from one day on, on the tenant level and for every portfolio. The range is processed
   * in calendar years, which bounds the size of each query of a long history; every day is computed on its own by the
   * queries, so the result does not depend on the chunking.
   *
   * @param idTenant the tenant
   * @param fromDate the first day to compute, inclusive
   * @param toDate   the last day to compute, inclusive
   * @return the number of rows written on the tenant level
   */
  private int recompute(Integer idTenant, LocalDate fromDate, LocalDate toDate) {
    long started = System.currentTimeMillis();
    holdDailyTotalJpaRepository.removeByIdTenantFromDate(idTenant, fromDate);
    List<Portfolio> portfolios = portfolioJpaRepository.findByIdTenantOrderByName(idTenant);
    LocalDate zeroBaseTenant = holdSecurityaccountSecurityJpaRepository.getZeroBaseDateByTenant(idTenant);
    List<LocalDate> zeroBasePortfolios = portfolios.stream()
        .map(p -> holdSecurityaccountSecurityJpaRepository.getZeroBaseDateByPortfolio(p.getIdPortfolio())).toList();
    int tenantRows = 0;
    for (LocalDate chunkFrom = fromDate; !chunkFrom.isAfter(toDate); chunkFrom = chunkFrom.plusYears(1)
        .withDayOfYear(1)) {
      LocalDate chunkTo = chunkFrom.withDayOfYear(chunkFrom.lengthOfYear());
      if (chunkTo.isAfter(toDate)) {
        chunkTo = toDate;
      }
      final LocalDate cFrom = chunkFrom;
      final LocalDate cTo = chunkTo;
      List<IPeriodHolding> tenantHoldings = withZeroBase(
          holdSecurityaccountSecurityJpaRepository.getPeriodHoldingsByTenant(idTenant, cFrom, cTo), zeroBaseTenant,
          cFrom, cTo,
          () -> holdSecurityaccountSecurityJpaRepository.getPeriodHoldingZeroBaseByTenant(idTenant, zeroBaseTenant));
      tenantRows += save(idTenant, null, tenantHoldings);
      for (int i = 0; i < portfolios.size(); i++) {
        Integer idPortfolio = portfolios.get(i).getIdPortfolio();
        LocalDate zeroBase = zeroBasePortfolios.get(i);
        save(idTenant, idPortfolio, withZeroBase(
            holdSecurityaccountSecurityJpaRepository.getPeriodHoldingsByPortfolio(idPortfolio, cFrom, cTo), zeroBase,
            cFrom, cTo,
            () -> holdSecurityaccountSecurityJpaRepository.getPeriodHoldingZeroBaseByPortfolio(idPortfolio, zeroBase)));
      }
    }
    log.debug("hold_daily_total of tenant {} from {} to {}: {} days in {} ms", idTenant, fromDate, toDate, tenantRows,
        System.currentTimeMillis() - started);
    return tenantRows;
  }

  //@formatter:off
  /**
   * Adds the zero base row to the holdings of a range when the zero base day lies within it and the regular query did
   * not return that day, as {@code PerformanceReport.prependZeroBaseHolding} does for a report starting on that day.
   * The regular query normally returns cash-only days as well, so this is only a fallback.
   *
   * @param holdings         the holdings of the range as delivered by the period holdings query
   * @param zeroBaseDate     the last trading day before the first security position, may be null
   * @param fromDate         the first day of the range
   * @param toDate           the last day of the range
   * @param zeroBaseSupplier supplies the zero base row, only called when it is needed
   * @return the holdings in ascending order of the day, including the zero base row when it applies
   */
  //@formatter:on
  private List<IPeriodHolding> withZeroBase(List<IPeriodHolding> holdings, LocalDate zeroBaseDate, LocalDate fromDate,
      LocalDate toDate, Supplier<List<IPeriodHolding>> zeroBaseSupplier) {
    if (zeroBaseDate == null || zeroBaseDate.isBefore(fromDate) || zeroBaseDate.isAfter(toDate)
        || holdings.stream().anyMatch(h -> h.getDate().isEqual(zeroBaseDate))) {
      return holdings;
    }
    List<IPeriodHolding> zeroBaseHolding = zeroBaseSupplier.get();
    if (zeroBaseHolding.isEmpty()) {
      return holdings;
    }
    List<IPeriodHolding> combined = new ArrayList<>(holdings.size() + zeroBaseHolding.size());
    combined.addAll(zeroBaseHolding);
    combined.addAll(holdings);
    combined.sort(Comparator.comparing(IPeriodHolding::getDate));
    return combined;
  }

  private int save(Integer idTenant, Integer idPortfolio, List<IPeriodHolding> holdings) {
    holdDailyTotalJpaRepository
        .saveAll(holdings.stream().map(h -> new HoldDailyTotal(idTenant, idPortfolio, h)).toList());
    return holdings.size();
  }

  /**
   * Determines where the next run has to start. That is the earliest missing quote day of the tenant within the
   * computed range that still lies in the retry window, otherwise the day after the range.
   *
   * @param idTenant the tenant
   * @param fromDate the first computed day
   * @param toDate   the last computed day
   * @param today    the current day, the end of the retry window
   * @return the new marker
   */
  private LocalDate getNextRecalcFromDate(Integer idTenant, LocalDate fromDate, LocalDate toDate, LocalDate today) {
    LocalDate retryFromDate = today.minusDays(retryDays);
    Set<LocalDate> missingQuoteDays = holdSecurityaccountSecurityJpaRepository.getMissingsQuoteDaysByTenant(idTenant);
    return missingQuoteDays.stream()
        .filter(d -> !d.isBefore(fromDate) && !d.isAfter(toDate) && !d.isBefore(retryFromDate))
        .min(Comparator.naturalOrder()).orElse(toDate.plusDays(1));
  }
}
