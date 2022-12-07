package grafioschtrader.reports;

import java.lang.reflect.InvocationTargetException;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Optional;
import java.util.stream.Stream;

import org.apache.commons.beanutils.PropertyUtils;
import org.apache.commons.math3.stat.descriptive.SummaryStatistics;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.transaction.annotation.Transactional;

import grafioschtrader.dto.AnnualisedPerformance;
import grafioschtrader.dto.AnnualisedPerformance.AnnualisedYears;
import grafioschtrader.dto.AnnualisedPerformance.LastYears;
import grafioschtrader.dto.StatisticsSummary;
import grafioschtrader.dto.StatisticsSummary.StatsProperty;
import grafioschtrader.entities.Currencypair;
import grafioschtrader.entities.Security;
import grafioschtrader.entities.Securitycurrency;
import grafioschtrader.entities.Tenant;
import grafioschtrader.entities.User;
import grafioschtrader.entities.projection.SecurityYearClose;
import grafioschtrader.reports.ReportHelper.ClosePricesCurrencyClose;
import grafioschtrader.reports.ReportHelper.CurrencyAvailableRequired;
import grafioschtrader.reports.ReportHelper.CurrencyRequired;
import grafioschtrader.repository.CurrencypairJpaRepository;
import grafioschtrader.repository.SecurityJpaRepository;
import grafioschtrader.repository.TenantJpaRepository;
import grafioschtrader.types.SamplingPeriodType;
import grafioschtrader.types.TimePeriodType;

public class InstrumentStatisticsSummary {

  private final static byte[] ANNUALISED_YEARS = { 1, 3, 5, 10 };

  private final SecurityJpaRepository securityJpaRepository;
  private final TenantJpaRepository tenantJpaRepository;
  private final CurrencypairJpaRepository currencypairJpaRepository;

  private String tenantCurrency = null;
  private Securitycurrency<?> securityCurrency;
  private boolean securtyTenantSameCurrency;
  private List<Currencypair> currencypairs = null;
  private String currencyOfSecurity = null;

  public InstrumentStatisticsSummary(SecurityJpaRepository securityJpaRepository,
      TenantJpaRepository tenantJpaRepository, CurrencypairJpaRepository currencypairJpaRepository) {
    this.securityJpaRepository = securityJpaRepository;
    this.tenantJpaRepository = tenantJpaRepository;
    this.currencypairJpaRepository = currencypairJpaRepository;
  }

  @Transactional
  public void prepareSecurityCurrencypairs(Integer idSecuritycurrency) {
    Optional<Currencypair> currencypairOpt = currencypairJpaRepository.findById(idSecuritycurrency);
    if (currencypairOpt.isEmpty()) {
      prepareSecurity(idSecuritycurrency);
    } else {
      securityCurrency = currencypairOpt.get();
      securtyTenantSameCurrency = true;
    }
  }

  private void prepareSecurity(Integer idSecuritycurrency) {
    final User user = (User) SecurityContextHolder.getContext().getAuthentication().getDetails();
    Tenant tenant = tenantJpaRepository.getReferenceById(user.getIdTenant());
    tenantCurrency = tenant.getCurrency();
    securityCurrency = securityJpaRepository
        .findByIdTenantPrivateIsNullOrIdTenantPrivateAndIdSecuritycurrency(idSecuritycurrency, user.getIdTenant());
    currencyOfSecurity = ((Security) securityCurrency).getCurrency();
    securtyTenantSameCurrency = tenantCurrency.equals(currencyOfSecurity);
    if (!securtyTenantSameCurrency) {
      currencypairs = currencypairJpaRepository
          .findByFromCurrencyAndToCurrencyOrToCurrencyAndFromCurrency(currencyOfSecurity, tenantCurrency);
    }
    if (!securtyTenantSameCurrency && currencypairs.isEmpty()) {
      // Required currency pair is missing
      currencypairs = new ArrayList<>();
      currencypairs.add(
          currencypairJpaRepository.createNonExistingCurrencypair(tenant.getCurrency(), currencyOfSecurity, false));
    }
  }

  public AnnualisedPerformance getAnnualisedSecurityPerformance() {
    List<SecurityYearClose> sycList = securtyTenantSameCurrency
        ? securityJpaRepository.getSecurityYearCloseDivSum(securityCurrency.getIdSecuritycurrency())
        : securityJpaRepository.getSecurityYearDivSumCurrencyClose(securityCurrency.getIdSecuritycurrency(),
            currencypairs.get(0).getIdSecuritycurrency());
    AnnualisedPerformance ap = calculateYearPerformance(sycList, divideOrMultiplyCurrency(tenantCurrency));
    calculateAnnualisedYears(ap);
    return ap;
  }

  private AnnualisedPerformance calculateYearPerformance(List<SecurityYearClose> sycList, boolean divide) {
    var ap = new AnnualisedPerformance(currencyOfSecurity, tenantCurrency, sycList.get(sycList.size() - 1).getDate(),
        sycList.get(0).getDate());
    for (int i = 0; i < sycList.size() - 1; i++) {
      SecurityYearClose syc = sycList.get(i);
      double fcur = securtyTenantSameCurrency ? 1.0 : divide ? 1.0 / syc.getCurrencyClose() : syc.getCurrencyClose();
      SecurityYearClose sycBefore = sycList.get(i + 1);
      double fcurBefore = securtyTenantSameCurrency ? 1.0
          : divide ? 1.0 / sycBefore.getCurrencyClose() : sycBefore.getCurrencyClose();
      double performance = ((syc.getSecurityClose() + syc.getYearDiv()) / sycBefore.getSecurityClose() - 1.0) * 100.0;
      double performanceMC = ((syc.getSecurityClose() + syc.getYearDiv()) * fcur
          / (sycBefore.getSecurityClose() * fcurBefore) - 1.0) * 100.0;
      ap.lastYears.add(new LastYears(syc.getDate().getYear(), performance, performanceMC));
    }
    return ap;
  }

  private void calculateAnnualisedYears(AnnualisedPerformance ap) {
    int i = 0;
    int numberOfYears = 0;
    double performance = 1;
    double performanceMC = 1;
    for (LastYears lastYear : ap.lastYears) {
      if (lastYear.year == LocalDate.now().getYear()) {
        continue;
      }
      numberOfYears++;
      performance *= (1 + lastYear.performanceYear / 100);
      performanceMC *= (1 + lastYear.performanceYearMC / 100);
      if (i < ANNUALISED_YEARS.length && ANNUALISED_YEARS[i] == numberOfYears) {
        addAnnualisedYear(ap, numberOfYears, performance, performanceMC);
        i++;
      }
    }
    if (numberOfYears > ANNUALISED_YEARS[ANNUALISED_YEARS.length - 1]) {
      addAnnualisedYear(ap, numberOfYears, performance, performanceMC);
    }
  }

  private void addAnnualisedYear(AnnualisedPerformance ap, int numberOfYears, double performance,
      double performanceMC) {
    ap.annualisedYears.add(new AnnualisedYears(numberOfYears, (Math.pow(performance, 1.0 / numberOfYears) - 1.0) * 100,
        (Math.pow(performanceMC, 1.0 / numberOfYears) - 1.0) * 100));
  }

  private boolean divideOrMultiplyCurrency(String mainCurrency) {
    boolean divide = false;
    if (!securtyTenantSameCurrency) {
      if (currencypairs.get(0).getFromCurrency().equals(mainCurrency)) {
        divide = true;
      }
    }
    return divide;
  }

  public StatisticsSummary getStandardDeviation(JdbcTemplate jdbcTemplate, LocalDate dateFrom, LocalDate dateTo,
      boolean adjustCurrency) throws IllegalAccessException, InvocationTargetException, NoSuchMethodException {

    StatisticsSummary statsSummary = new StatisticsSummary();
    statsSummary.addProperties(TimePeriodType.DAILY, StatisticsSummary.STANDARD_DEVIATION,
        StatisticsSummary.MIN, StatisticsSummary.MAX);
    statsSummary.addProperties(TimePeriodType.MONTHLY, StatisticsSummary.STANDARD_DEVIATION);
    statsSummary.addProperties(TimePeriodType.ANNUAL, StatisticsSummary.STANDARD_DEVIATION);

    for (TimePeriodType key : statsSummary.statsPropertyMap.keySet()) {
      if (key != TimePeriodType.ANNUAL) {
        createStatisticsSummaryByPeriod(jdbcTemplate, dateFrom, dateTo, adjustCurrency, 
            key, statsSummary);
      }
    }
    statsSummary.createAnnualByMonthly();

    return statsSummary;
  }

  private void createStatisticsSummaryByPeriod(JdbcTemplate jdbcTemplate, LocalDate dateFrom, LocalDate dateTo,
      boolean adjustCurrency, TimePeriodType timePeriodType, StatisticsSummary statsSummary)
      throws IllegalAccessException, InvocationTargetException, NoSuchMethodException {
    List<Securitycurrency<?>> securitycurrencyList = new ArrayList<>(Arrays.asList(securityCurrency));
    CurrencyAvailableRequired currencyAvailableRequired = prepareFakeSecurityWhenNotTenantCurrency(
        securitycurrencyList);

    ClosePricesCurrencyClose closePrices = ReportHelper.loadCloseData(jdbcTemplate, currencypairJpaRepository,
        securitycurrencyList,SamplingPeriodType.getSamplingPeriodTypeByValue(timePeriodType.getValue()), dateFrom, dateTo, adjustCurrency);
    adjustCloseOfDifferentTenantCurrency(securitycurrencyList, currencyAvailableRequired, closePrices);
    int columns = currencyAvailableRequired == null ? 1 : 2;
    double[][] percentageChange = ReportHelper.transformToPercentageChange(closePrices.dateCloseTree, columns);
    SummaryStatistics[] stats = Stream.iterate(0, x -> x + 1).limit(columns).map(i -> new SummaryStatistics())
        .toArray(SummaryStatistics[]::new);
    for (int row = 0; row < percentageChange.length; row++) {
      for (int col = 0; col < columns; col++) {
        stats[col].addValue(percentageChange[row][col]);
      }
    }

    List<StatsProperty> statsProperties = statsSummary.getPropertiesBySamplingPeriodType(timePeriodType);
    for (StatsProperty statsProperty : statsProperties) {
      statsProperty.value = (double) PropertyUtils.getProperty(stats[0], statsProperty.property);
      if (stats.length == 2) {
        statsProperty.valueMC = (double) PropertyUtils.getProperty(stats[1], statsProperty.property);
      }
    }
  }

  private CurrencyAvailableRequired prepareFakeSecurityWhenNotTenantCurrency(
      List<Securitycurrency<?>> securitycurrencyList) {
    if (!securtyTenantSameCurrency) {
      Currencypair cp = currencypairs.get(0);
      Security security = (Security) securityCurrency;
      Security faseSecurity = new Security();
      faseSecurity.setIdSecuritycurrency(security.getIdSecuritycurrency());
      faseSecurity.setCurrency(security.getCurrency());
      securitycurrencyList.add(faseSecurity);
      securitycurrencyList.add(cp);
      return new CurrencyAvailableRequired(securitycurrencyList.size() - 1, cp.getIdSecuritycurrency(),
          cp.getFromCurrency(), cp.getToCurrency());
    }
    return null;
  }

  private void adjustCloseOfDifferentTenantCurrency(List<Securitycurrency<?>> securitycurrencyList,
      CurrencyAvailableRequired currencyAvailableRequired, ClosePricesCurrencyClose closePrices) {
    if (currencyAvailableRequired != null) {
      Security security = (Security) securitycurrencyList.get(0);
      String realCurrency = security.getCurrency();
      security.setCurrency(tenantCurrency);
      var cr = new CurrencyRequired(tenantCurrency);
      cr.carList.add(currencyAvailableRequired);
      closePrices.currencyRequired = cr;
      ReportHelper.adjustCloseToSameCurrency(securitycurrencyList, closePrices);
      security.setCurrency(realCurrency);
    }
  }

}
