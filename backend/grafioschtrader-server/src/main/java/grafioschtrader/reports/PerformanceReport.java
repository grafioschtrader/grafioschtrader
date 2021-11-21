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

import grafioschtrader.GlobalConstants;
import grafioschtrader.entities.Portfolio;
import grafioschtrader.entities.TradingDaysPlus;
import grafioschtrader.entities.User;
import grafioschtrader.exceptions.DataViolationException;
import grafioschtrader.reportviews.performance.FirstAndMissingTradingDays;
import grafioschtrader.reportviews.performance.IPeriodHolding;
import grafioschtrader.reportviews.performance.PerformancePeriod;
import grafioschtrader.reportviews.performance.PeriodHoldingAndDiff;
import grafioschtrader.reportviews.performance.WeekYear;
import grafioschtrader.repository.HoldSecurityaccountSecurityJpaRepository;
import grafioschtrader.repository.PortfolioJpaRepository;
import grafioschtrader.repository.TradingDaysPlusJpaRepository;

/**
 * Creates the report for period performance.
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

  PassiveExpiringMap<PortfolioOrTenantKey, FirstAndMissingTradingDays> firstAndMissingTradingDaysMap = new PassiveExpiringMap<>(
      120_000);

  public FirstAndMissingTradingDays getFirstAndMissingTradingDaysByTenant()
      throws InterruptedException, ExecutionException {
    final User user = (User) SecurityContextHolder.getContext().getAuthentication().getDetails();
    return getFirstAndMissingTradingDaysByTenant(user.getIdTenant());
  }

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
      throw new SecurityException(GlobalConstants.CLIENT_SECURITY_BREACH);
    }
  }

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
    LocalDate secondEverTradingDay = getLatestTradingDayAfterDate(combinedMissingQuoteDaysAndHolidays,
        firstEverTradingDay, latestTradingDay);

    LocalDate leatestPossibleTradingDay = getLatestTradingDayBeforeDate(combinedHolidayOfHoldings, LocalDate.now(),
        firstEverTradingDay);

    return new FirstAndMissingTradingDays(firstEverTradingDay, secondEverTradingDay,
        lastTradingDayOfLastYearOpt.isEmpty() ? null : lastTradingDayOfLastYearOpt.get().getTradingDate(),
        leatestPossibleTradingDay, latestTradingDay, secondLatestTradingDay, combinedHolidayOfHoldings,
        missingQuoteDays);
  }

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

  private LocalDate getLatestTradingDayAfterDate(Set<Date> missingQuoteDays, LocalDate afterDate,
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

  public PerformancePeriod getPeriodPerformanceByPortfolio(Integer idPortfolio, LocalDate dateFrom, LocalDate dateTo,
      WeekYear periodSplit) throws Exception {
    final User user = (User) SecurityContextHolder.getContext().getAuthentication().getDetails();
    FirstAndMissingTradingDays firstAndMissingTradingDays = this.getFirstAndMissingTradingDaysByPortfolio(idPortfolio);
    checkInputParam(firstAndMissingTradingDays, user.getLocaleStr(), dateFrom, dateTo, periodSplit);
    List<IPeriodHolding> periodHoldings = holdSecurityaccountSecurityRepository
        .getPeriodHoldingsByPortfolio(idPortfolio, dateFrom, dateTo);
    return getPeriodPerformance(firstAndMissingTradingDays, periodHoldings, periodSplit);
  }

  private PerformancePeriod getPeriodPerformance(FirstAndMissingTradingDays firstAndMissingTradingDays,
      List<IPeriodHolding> periodHoldings, WeekYear periodSplit) throws Exception {
    PeriodHoldingAndDiff firstDayTotals = new PeriodHoldingAndDiff();
    PeriodHoldingAndDiff lastDayTotals = new PeriodHoldingAndDiff();
    if (!periodHoldings.isEmpty()) {
      BeanUtils.copyProperties(periodHoldings.get(0), firstDayTotals);
      BeanUtils.copyProperties(periodHoldings.get(periodHoldings.size() - 1), lastDayTotals);
    }
    PerformancePeriod periodPerformance = new PerformancePeriod(periodSplit, firstDayTotals, lastDayTotals,
        lastDayTotals.calculateDiff(firstDayTotals));
    if (!periodHoldings.isEmpty()) {
      periodPerformance.createPeriodWindows(firstAndMissingTradingDays, periodHoldings);
    }
    return periodPerformance;
  }

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

  private static class PortfolioOrTenantKey {
    public Integer id;
    public PortfolioTentant portfolioTenant;

    public PortfolioOrTenantKey(Integer id, PortfolioTentant portfolioTenant) {
      this.id = id;
      this.portfolioTenant = portfolioTenant;
    }

    @Override
    public boolean equals(Object o) {
      if (this == o)
        return true;
      if (o == null || getClass() != o.getClass())
        return false;
      PortfolioOrTenantKey that = (PortfolioOrTenantKey) o;
      return Objects.equals(id, that.id) && portfolioTenant == that.portfolioTenant;
    }

    @Override
    public int hashCode() {
      return Objects.hash(id, portfolioTenant);
    }

  }

  enum PortfolioTentant {
    Portfolio, Tenant;
  }
}
