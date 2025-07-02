package grafioschtrader.reports;

import java.time.DayOfWeek;
import java.time.LocalDate;
import java.time.ZoneId;
import java.time.temporal.ChronoUnit;
import java.util.Comparator;
import java.util.Date;
import java.util.GregorianCalendar;
import java.util.HashSet;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutionException;
import java.util.stream.Collectors;

import org.apache.commons.collections4.map.PassiveExpiringMap;
import org.springframework.beans.BeanUtils;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Component;

import grafiosch.BaseConstants;
import grafiosch.entities.User;
import grafiosch.exceptions.DataViolationException;
import grafioschtrader.entities.Portfolio;
import grafioschtrader.entities.TradingDaysPlus;
import grafioschtrader.reportviews.performance.FirstAndMissingTradingDays;
import grafioschtrader.reportviews.performance.IPeriodHolding;
import grafioschtrader.reportviews.performance.PerformancePeriod;
import grafioschtrader.reportviews.performance.PeriodHoldingAndDiff;
import grafioschtrader.reportviews.performance.WeekYear;
import grafioschtrader.repository.HoldSecurityaccountSecurityJpaRepository;
import grafioschtrader.repository.PortfolioJpaRepository;
import grafioschtrader.repository.TradingDaysPlusJpaRepository;

/**
 * Service component responsible for generating period performance reports for portfolios and tenants.
 * 
 * <p>
 * This class provides comprehensive performance analysis by calculating trading day availability, missing quote
 * detection, and period-based performance metrics. It supports both tenant-level (across all portfolios) and individual
 * portfolio performance reporting.
 * </p>
 * 
 * <p>
 * <strong>Key Features:</strong>
 * </p>
 * <ul>
 * <li>Trading day validation and missing quote detection</li>
 * <li>Period performance calculation with weekly or yearly aggregation</li>
 * <li>Holiday and non-trading day handling</li>
 * <li>Multi-currency support with automatic conversion</li>
 * <li>Caching of trading day metadata for improved performance</li>
 * </ul>
 * 
 * * <p>
 * Missing quote days are particularly important for performance calculations as they represent gaps in the
 * price history of held securities. These days are identified by analyzing the quote availability for all
 * securities held across portfolios within a tenant or specific portfolio.
 * </p>
 * 
 * <p>
 * <strong>Performance Optimization:</strong>
 * </p>
 * <p>
 * The class employs a passive expiring cache with a 2-minute TTL to store FirstAndMissingTradingDays objects, reducing
 * database queries for frequently accessed trading day information.
 * </p>
 * 
 */
@Component
public class PerformanceReport {
  private static final String DATE_FROM_FIELD_MSG = "date.from";
  private static final String DATE_TO_FIELD_MSG = "date.to";
  private static final String PERIOD_SPLIT = "period.split";

  @Autowired
  private HoldSecurityaccountSecurityJpaRepository holdSecurityaccountSecurityRepository;

  @Autowired
  private TradingDaysPlusJpaRepository tradingDaysPlusJpaRepository;

  @Autowired
  private PortfolioJpaRepository portfolioJpaRepository;

  /**
   * Cache for trading day metadata with 2-minute expiration to improve performance. Maps portfolio/tenant keys to their
   * corresponding trading day information.
   */
  PassiveExpiringMap<PortfolioOrTenantKey, FirstAndMissingTradingDays> firstAndMissingTradingDaysMap = new PassiveExpiringMap<>(
      120_000);

  /**
   * Retrieves trading day metadata for the current user's tenant.
   * 
   * <p>
   * This convenience method extracts the tenant ID from the current security context
   * and delegates to the parameterized version. It's commonly used in web controllers
   * where the tenant context is implicit.
   * </p>
   * 
   * @return trading day metadata for the current tenant
   */
  public FirstAndMissingTradingDays getFirstAndMissingTradingDaysByTenant()
      throws InterruptedException, ExecutionException {
    final User user = (User) SecurityContextHolder.getContext().getAuthentication().getDetails();
    return getFirstAndMissingTradingDaysByTenant(user.getIdTenant());
  }

  /**
   * Retrieves trading day metadata for a specific tenant with caching and concurrent data loading.
   * 
   * <p>
   * Aggregates trading day information across all portfolios within the tenant, including the
   * earliest trading day, missing quote days, and holiday information. Uses CompletableFuture
   * for concurrent data loading from multiple sources.
   * </p>
   * 
   * @param idTenant the tenant identifier
   * @return comprehensive trading day metadata for the tenant
   */
  private FirstAndMissingTradingDays getFirstAndMissingTradingDaysByTenant(Integer idTenant)
      throws InterruptedException, ExecutionException {

    PortfolioOrTenantKey portfolioOrTenantKey = new PortfolioOrTenantKey(idTenant, PortfolioTentant.Tenant);
    FirstAndMissingTradingDays firstAndMissingTradingDays = firstAndMissingTradingDaysMap.get(portfolioOrTenantKey);
    if (firstAndMissingTradingDays == null) {
      final CompletableFuture<LocalDate> firstEverTradingDayCF = CompletableFuture
          .supplyAsync(() -> holdSecurityaccountSecurityRepository.findByIdTenantMinFromHoldDate(idTenant));
      final CompletableFuture<Set<Date>> missingQuoteDaysCF = CompletableFuture
          .supplyAsync(() -> holdSecurityaccountSecurityRepository.getMissingsQuoteDaysByTenant(idTenant));
      final CompletableFuture<Set<Date>> combinedHolidayOfHoldingsCF = CompletableFuture
          .supplyAsync(() -> holdSecurityaccountSecurityRepository.getCombinedHolidayOfHoldingsByTenant(idTenant));
      return getFirstAndMissingTradingDays(portfolioOrTenantKey, firstEverTradingDayCF, missingQuoteDaysCF,
          combinedHolidayOfHoldingsCF);
    } else {
      return firstAndMissingTradingDays;
    }
  }

  
  /**
   * Retrieves trading day metadata for a specific portfolio with security validation.
   * 
   * <p>
   * Provides detailed trading day analysis for an individual portfolio. Enforces tenant-based
   * access control to ensure users can only access portfolios within their tenant scope.
   * </p>
   * 
   * @param idPortfolio the portfolio identifier
   * @return trading day metadata specific to the portfolio
   * @throws SecurityException if the portfolio doesn't belong to the current user's tenant
   */
  public FirstAndMissingTradingDays getFirstAndMissingTradingDaysByPortfolio(Integer idPortfolio)
      throws InterruptedException, ExecutionException {
    final User user = (User) SecurityContextHolder.getContext().getAuthentication().getDetails();
    Portfolio portfolio = portfolioJpaRepository.findByIdTenantAndIdPortfolio(user.getIdTenant(), idPortfolio);
    if (portfolio != null) {
      PortfolioOrTenantKey portfolioOrTenantKey = new PortfolioOrTenantKey(idPortfolio, PortfolioTentant.Portfolio);
      FirstAndMissingTradingDays firstAndMissingTradingDays = firstAndMissingTradingDaysMap.get(portfolioOrTenantKey);
      if (firstAndMissingTradingDays == null) {
        final CompletableFuture<LocalDate> firstEverTradingDayCF = CompletableFuture
            .supplyAsync(() -> holdSecurityaccountSecurityRepository.findByIdPortfolioMinFromHoldDate(idPortfolio));
        final CompletableFuture<Set<Date>> missingQuoteDaysCF = CompletableFuture
            .supplyAsync(() -> holdSecurityaccountSecurityRepository.getMissingsQuoteDaysByPortfolio(idPortfolio));
        final CompletableFuture<Set<Date>> combinedHolidayOfHoldingsCF = CompletableFuture.supplyAsync(
            () -> holdSecurityaccountSecurityRepository.getCombinedHolidayOfHoldingsByPortfolio(idPortfolio));
        return getFirstAndMissingTradingDays(portfolioOrTenantKey, firstEverTradingDayCF, missingQuoteDaysCF,
            combinedHolidayOfHoldingsCF);
      } else {
        return firstAndMissingTradingDays;
      }
    } else {
      throw new SecurityException(BaseConstants.CLIENT_SECURITY_BREACH);
    }
  }

  /**
   * Orchestrates concurrent loading and combination of trading day metadata from multiple sources.
   * 
   * <p>
   * Combines data from holdings history, global holidays, security-specific holidays, missing quote data,
   * and trading calendar information. Results are automatically cached for future use.
   * </p>
   * 
   * @param portfolioOrTenantKey cache key for portfolio or tenant-level data
   * @param firstEverTradingDayCF CompletableFuture providing earliest trading day
   * @param missingQuoteDaysCF CompletableFuture providing days with missing quotes
   * @param combinedHolidayOfHoldingsCF CompletableFuture providing holidays affecting holdings
   * @return comprehensive trading day metadata
   * @throws InterruptedException if concurrent operations are interrupted
   * @throws ExecutionException if data retrieval operations fail
   */
  private FirstAndMissingTradingDays getFirstAndMissingTradingDays(PortfolioOrTenantKey portfolioOrTenantKey,
      final CompletableFuture<LocalDate> firstEverTradingDayCF, final CompletableFuture<Set<Date>> missingQuoteDaysCF,
      final CompletableFuture<Set<Date>> combinedHolidayOfHoldingsCF) throws InterruptedException, ExecutionException {
    int actYear = LocalDate.now().getYear();
    LocalDate fromDate = LocalDate.of(actYear - 1, 1, 1);
    LocalDate toDate = LocalDate.of(actYear - 1, 12, 31);
    final CompletableFuture<Set<Date>> globalHolidaysCF = CompletableFuture
        .supplyAsync(() -> tradingDaysPlusJpaRepository.getGlobalHolidays());
    final CompletableFuture<List<TradingDaysPlus>> tradingDaysOfLastYearCF = CompletableFuture.supplyAsync(
        () -> tradingDaysPlusJpaRepository.findByTradingDateBetweenOrderByTradingDateDesc(fromDate, toDate));
    FirstAndMissingTradingDays firstAndMissingTradingDays = combineFirstAndMissingTradingDays(
        firstEverTradingDayCF.get(), globalHolidaysCF.get(), missingQuoteDaysCF.get(),
        combinedHolidayOfHoldingsCF.get(), tradingDaysOfLastYearCF.get(), actYear - 1);
    firstAndMissingTradingDaysMap.put(portfolioOrTenantKey, firstAndMissingTradingDays);
    return firstAndMissingTradingDays;
  }

  /**
   * Combines raw trading day data from multiple sources into unified metadata.
   * 
   * <p>
   * Merges global holidays, security-specific holidays, and missing quote data. Calculates
   * latest valid trading days using sophisticated algorithms that account for missing data
   * and holiday schedules.
   * </p>
   * 
   * @param firstEverTradingDay earliest trading day in holdings history
   * @param globalHolidays universal holidays affecting all markets
   * @param missingQuoteDays days where historical price quotes are unavailable
   * @param combinedHolidayOfHoldings holidays specific to currently held securities
   * @param tradingDaysOfLastYearReverse trading days from previous year in descending order
   * @param lastYear reference year for calculations
   * @return comprehensive trading day metadata
   */
  private FirstAndMissingTradingDays combineFirstAndMissingTradingDays(LocalDate firstEverTradingDay,
      Set<Date> globalHolidays, Set<Date> missingQuoteDays, Set<Date> combinedHolidayOfHoldings,
      List<TradingDaysPlus> tradingDaysOfLastYearReverse, int lastYear) {
    Date fromDate = new GregorianCalendar(lastYear - 1, 11, 31).getTime();
    Date toDate = new GregorianCalendar(lastYear + 1, 0, 1).getTime();
    combinedHolidayOfHoldings.addAll(globalHolidays);
    Set<Date> combinedMissingQuoteDaysAndHolidays = new HashSet<>(missingQuoteDays);
    combinedMissingQuoteDaysAndHolidays.addAll(combinedHolidayOfHoldings);
    List<LocalDate> lastYearMissingDays = combinedMissingQuoteDaysAndHolidays.stream()
        .filter(missingDate -> missingDate.after(fromDate) && missingDate.before(toDate))
        .map(date -> ((java.sql.Date) date).toLocalDate()).sorted(Comparator.reverseOrder())
        .collect(Collectors.toList());
    Optional<TradingDaysPlus> lastTradingDayOfLastYearOpt = tradingDaysOfLastYearReverse.stream()
        .filter(tradingDaysPlus -> !lastYearMissingDays.contains(tradingDaysPlus.getTradingDate())).findFirst();
    LocalDate latestTradingDay = getLatestTradingDayBeforeDate(combinedMissingQuoteDaysAndHolidays, LocalDate.now(),
        firstEverTradingDay);
    LocalDate secondLatestTradingDay = getLatestTradingDayBeforeDate(combinedMissingQuoteDaysAndHolidays,
        latestTradingDay, firstEverTradingDay);
    LocalDate secondEverTradingDay = getFirstTradingDayAfterDate(combinedMissingQuoteDaysAndHolidays,
        firstEverTradingDay, latestTradingDay);

    LocalDate leatestPossibleTradingDay = getLatestTradingDayBeforeDate(combinedHolidayOfHoldings, LocalDate.now(),
        firstEverTradingDay);

    return new FirstAndMissingTradingDays(firstEverTradingDay, secondEverTradingDay,
        lastTradingDayOfLastYearOpt.isEmpty() ? null : lastTradingDayOfLastYearOpt.get().getTradingDate(),
        leatestPossibleTradingDay, latestTradingDay, secondLatestTradingDay, combinedHolidayOfHoldings,
        missingQuoteDays);
  }

  /**
   * Finds the latest valid trading day before a specified date.
   * 
   * <p>
   * Implements backward-scanning algorithm to identify the most recent trading day that
   * excludes weekends, holidays, and days with missing price quotes.
   * </p>
   * 
   * @param missingQuoteDays set of dates to exclude (holidays and missing quotes)
   * @param beforeDate the date before which to search (exclusive)
   * @param firstEverTradingDay lower boundary to prevent infinite scanning
   * @return latest valid trading day before the specified date, or null if none found
   */
  private LocalDate getLatestTradingDayBeforeDate(Set<Date> missingQuoteDays, LocalDate beforeDate,
      LocalDate firstEverTradingDay) {
    LocalDate latestTradingDay = null;
    if (firstEverTradingDay != null) {
      LocalDate stepBackLocalDate = beforeDate;
      do {
        stepBackLocalDate = stepBackLocalDate.minusDays(1);
        Date date = Date.from(stepBackLocalDate.atStartOfDay(ZoneId.systemDefault()).toInstant());
        if (stepBackLocalDate.getDayOfWeek() != DayOfWeek.SATURDAY
            && stepBackLocalDate.getDayOfWeek() != DayOfWeek.SUNDAY && !missingQuoteDays.contains(date)) {
          latestTradingDay = stepBackLocalDate;
        }
      } while (stepBackLocalDate.isAfter(firstEverTradingDay) && latestTradingDay == null);
    }
    return latestTradingDay;
  }

  /**
   * Finds the earliest valid trading day after a specified date.
   * 
   * <p>
   * Implements forward-scanning algorithm to identify the first trading day after a given
   * date that excludes weekends, holidays, and days with missing quotes.
   * </p>
   * 
   * @param missingQuoteDays set of dates to exclude (holidays and missing quotes)
   * @param afterDate the date after which to search (exclusive)
   * @param latestTradingDay upper boundary to prevent scanning beyond available data
   * @return earliest valid trading day after the specified date, or null if none found
   */
  private LocalDate getFirstTradingDayAfterDate(Set<Date> missingQuoteDays, LocalDate afterDate,
      LocalDate latestTradingDay) {
    LocalDate afterTradingDay = null;
    if (latestTradingDay != null) {
      LocalDate stepLocalDate = afterDate;
      do {
        stepLocalDate = stepLocalDate.plusDays(1);
        Date date = Date.from(stepLocalDate.atStartOfDay(ZoneId.systemDefault()).toInstant());
        if (stepLocalDate.getDayOfWeek() != DayOfWeek.SATURDAY && stepLocalDate.getDayOfWeek() != DayOfWeek.SUNDAY
            && !missingQuoteDays.contains(date)) {
          afterTradingDay = stepLocalDate;
        }
      } while (stepLocalDate.isBefore(latestTradingDay) && afterTradingDay == null);
    }
    return afterTradingDay;
  }

  /**
   * Generates a period performance report for all portfolios within the current tenant.
   * 
   * <p>
   * This method aggregates performance data across all portfolios belonging to the current user's tenant. It provides a
   * tenant-wide view of investment performance over the specified period.
   * </p>
   * 
   * <p>
   * <strong>Performance Metrics Include:</strong>
   * </p>
   * <ul>
   * <li>Total portfolio values in tenant currency</li>
   * <li>Cash balance changes and external transfers</li>
   * <li>Security position changes and market valuations</li>
   * <li>Dividend, interest, and fee aggregations</li>
   * <li>Net gain/loss calculations</li>
   * </ul>
   * 
   * @param dateFrom    the start date of the performance period (inclusive)
   * @param dateTo      the end date of the performance period (inclusive)
   * @param periodSplit whether to aggregate by week or year
   * @return comprehensive performance analysis for the tenant
   * @throws Exception if date validation fails or data retrieval encounters errors
   */
  public PerformancePeriod getPeriodPerformanceByTenant(LocalDate dateFrom, LocalDate dateTo, WeekYear periodSplit)
      throws Exception {
    final User user = (User) SecurityContextHolder.getContext().getAuthentication().getDetails();
    FirstAndMissingTradingDays firstAndMissingTradingDays = this
        .getFirstAndMissingTradingDaysByTenant(user.getIdTenant());
    checkInputParam(firstAndMissingTradingDays, user.getLocaleStr(), dateFrom, dateTo, periodSplit);
    List<IPeriodHolding> periodHoldings = holdSecurityaccountSecurityRepository
        .getPeriodHoldingsByTenant(user.getIdTenant(), dateFrom, dateTo);
    return getPeriodPerformance(firstAndMissingTradingDays, periodHoldings, periodSplit);
  }

  /**
   * Generates a period performance report for a specific portfolio.
   * 
   * <p>
   * This method provides detailed performance analysis for an individual portfolio, including all security positions
   * and cash accounts within that portfolio.
   * </p>
   * 
   * <p>
   * The analysis includes currency conversion to the portfolio's base currency and comprehensive breakdown of
   * performance drivers.
   * </p>
   * 
   * @param idPortfolio the portfolio identifier
   * @param dateFrom    the start date of the performance period (inclusive)
   * @param dateTo      the end date of the performance period (inclusive)
   * @param periodSplit whether to aggregate by week or year
   * @return detailed performance analysis for the portfolio
   * @throws Exception if portfolio validation fails, date validation fails, or data retrieval encounters errors
   */
  public PerformancePeriod getPeriodPerformanceByPortfolio(Integer idPortfolio, LocalDate dateFrom, LocalDate dateTo,
      WeekYear periodSplit) throws Exception {
    final User user = (User) SecurityContextHolder.getContext().getAuthentication().getDetails();
    FirstAndMissingTradingDays firstAndMissingTradingDays = this.getFirstAndMissingTradingDaysByPortfolio(idPortfolio);
    checkInputParam(firstAndMissingTradingDays, user.getLocaleStr(), dateFrom, dateTo, periodSplit);
    List<IPeriodHolding> periodHoldings = holdSecurityaccountSecurityRepository
        .getPeriodHoldingsByPortfolio(idPortfolio, dateFrom, dateTo);
    return getPeriodPerformance(firstAndMissingTradingDays, periodHoldings, periodSplit);
  }

  /**
   * Creates a performance period analysis from holding data and trading day metadata.
   * 
   * <p>
   * This method processes the raw holding data and converts it into a structured performance analysis with period
   * windows, daily changes, and summary statistics.
   * </p>
   * 
   * @param firstAndMissingTradingDays trading day metadata for validation and processing
   * @param periodHoldings             list of daily holding snapshots for the period
   * @param periodSplit                aggregation level (weekly or yearly)
   * @return structured performance analysis
   * @throws Exception if data processing encounters errors
   */
  private PerformancePeriod getPeriodPerformance(FirstAndMissingTradingDays firstAndMissingTradingDays,
      List<IPeriodHolding> periodHoldings, WeekYear periodSplit) throws Exception {
    PeriodHoldingAndDiff firstDayTotals = new PeriodHoldingAndDiff();
    PeriodHoldingAndDiff lastDayTotals = new PeriodHoldingAndDiff();
    if (!periodHoldings.isEmpty()) {
      BeanUtils.copyProperties(periodHoldings.getFirst(), firstDayTotals);
      BeanUtils.copyProperties(periodHoldings.getLast(), lastDayTotals);
    }
    PerformancePeriod periodPerformance = new PerformancePeriod(periodSplit, firstDayTotals, lastDayTotals,
        lastDayTotals.calculateDiff(firstDayTotals));
    if (!periodHoldings.isEmpty()) {
      periodPerformance.createPeriodWindows(firstAndMissingTradingDays, periodHoldings);
    }
    return periodPerformance;
  }

  /**
   * Validates input parameters for performance analysis requests.
   * 
   * <p>
   * Performs comprehensive validation including date range validity, trading day validation,
   * data boundary validation, and period split appropriateness. Ensures that:
   * </p>
   * <ul>
   * <li>Start and end dates are valid trading days</li>
   * <li>Date range is within available data bounds</li>
   * <li>End date is after start date</li>
   * <li>Period split is appropriate for the date range</li>
   * </ul>
   * 
   * @param firstAndMissingTradingDays trading day metadata for validation
   * @param localeStr user's locale for error message formatting
   * @param dateFrom requested start date
   * @param dateTo requested end date
   * @param periodSplit requested aggregation level
   * @throws DataViolationException if any validation rule is violated
   */
  private void checkInputParam(FirstAndMissingTradingDays firstAndMissingTradingDays, String localeStr,
      LocalDate dateFrom, LocalDate dateTo, WeekYear periodSplit) {

    checkInputDateNotHolidayOrMissingQuotes(localeStr, dateFrom, DATE_FROM_FIELD_MSG, firstAndMissingTradingDays);
    checkInputDateNotHolidayOrMissingQuotes(localeStr, dateTo, DATE_TO_FIELD_MSG, firstAndMissingTradingDays);
    if (dateFrom.isBefore(firstAndMissingTradingDays.firstEverTradingDay)) {
      throw new DataViolationException(DATE_FROM_FIELD_MSG, "gt.not.valid.trading.day", dateFrom, localeStr);
    }
    if (dateTo.isAfter(firstAndMissingTradingDays.latestTradingDay)) {
      throw new DataViolationException(DATE_TO_FIELD_MSG, "gt.not.valid.trading.day", dateTo, localeStr);
    }
    if (!dateTo.isAfter(dateFrom)) {
      throw new DataViolationException("date.from.to", "gt.not.date.period", null, localeStr);
    }
    long weeks = ChronoUnit.WEEKS.between(dateFrom, dateTo);
    long months = ChronoUnit.MONTHS.between(dateFrom, dateTo);
    if (periodSplit == WeekYear.WM_WEEK && weeks > firstAndMissingTradingDays.maxWeekLimit
        || periodSplit == WeekYear.WM_YEAR && months < firstAndMissingTradingDays.minIncludeMonthLimit) {
      throw new DataViolationException(PERIOD_SPLIT, "gt.not.valid.period.choosed", null, localeStr);
    }
  }

  /**
   * Validates that a specific date is a valid trading day.
   * 
   * <p>
   * A valid trading day must be a weekday (not Saturday or Sunday), not a holiday,
   * and not a day with missing quote data.
   * </p>
   * 
   * @param localeStr user's locale for error message formatting
   * @param localDate the date to validate
   * @param fieldName the field name for error reporting
   * @param firstAndMissingTradingDays trading day metadata containing holidays and missing data
   * @throws DataViolationException if the date is not a valid trading day
   */
  private void checkInputDateNotHolidayOrMissingQuotes(String localeStr, LocalDate localDate, String fieldName,
      FirstAndMissingTradingDays firstAndMissingTradingDays) {
    if (localDate.getDayOfWeek() != DayOfWeek.SATURDAY && localDate.getDayOfWeek() != DayOfWeek.SUNDAY) {
      Date date = Date.from(localDate.atStartOfDay(ZoneId.systemDefault()).toInstant());
      if (!firstAndMissingTradingDays.isMissingQuoteDayOrHoliday(date)) {
        return;
      }
    }
    throw new DataViolationException(fieldName, "gt.not.valid.trading.day", localDate, localeStr);
  }

  /**
   * Internal cache key for distinguishing between portfolio and tenant-level metadata.
   * 
   * <p>
   * This class serves as a compound key for the {@code firstAndMissingTradingDaysMap} cache, allowing separate caching
   * of trading day metadata for portfolios and tenants.
   * </p>
   */
  private static class PortfolioOrTenantKey {
    /** The identifier (portfolio ID or tenant ID). */
    public Integer id;
    /** The type of entity (Portfolio or Tenant). */
    public PortfolioTentant portfolioTenant;

    public PortfolioOrTenantKey(Integer id, PortfolioTentant portfolioTenant) {
      this.id = id;
      this.portfolioTenant = portfolioTenant;
    }

    @Override
    public boolean equals(Object o) {
      if (this == o) {
        return true;
      }
      if (o == null || getClass() != o.getClass()) {
        return false;
      }
      PortfolioOrTenantKey that = (PortfolioOrTenantKey) o;
      return Objects.equals(id, that.id) && portfolioTenant == that.portfolioTenant;
    }

    @Override
    public int hashCode() {
      return Objects.hash(id, portfolioTenant);
    }

  }

  /**
   * Enumeration for distinguishing between portfolio and tenant-level operations.
   * 
   * <p>
   * Used in cache keys and internal processing to ensure correct data scope and prevent cross-contamination between
   * portfolio and tenant-level metadata.
   * </p>
   */
  enum PortfolioTentant {
    /** Indicates portfolio-level scope. */
    Portfolio,
    /** Indicates tenant-level scope. */
    Tenant;
  }
}
