package grafioschtrader.reports;

import java.time.LocalDate;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Optional;

import org.apache.commons.math3.stat.descriptive.DescriptiveStatistics;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.transaction.annotation.Transactional;

import grafioschtrader.dto.AnnualisedSecurityPerformance;
import grafioschtrader.dto.AnnualisedSecurityPerformance.AnnualisedYears;
import grafioschtrader.dto.AnnualisedSecurityPerformance.LastYears;
import grafioschtrader.dto.SecurityStatisticsSummaryResult;
import grafioschtrader.entities.Currencypair;
import grafioschtrader.entities.Security;
import grafioschtrader.entities.Securitycurrency;
import grafioschtrader.entities.Tenant;
import grafioschtrader.entities.User;
import grafioschtrader.entities.projection.SecurityYearClose;
import grafioschtrader.reports.ReportHelper.ClosePricesCurrencyClose;
import grafioschtrader.repository.CurrencypairJpaRepository;
import grafioschtrader.repository.SecurityJpaRepository;
import grafioschtrader.repository.TenantJpaRepository;
import grafioschtrader.types.SamplingPeriodType;

public class SecurityStatisticsSummary {

  private final static byte[] ANNUALISED_YEARS = { 1, 3, 5, 10 };

  private final SecurityJpaRepository securityJpaRepository;
  private final TenantJpaRepository tenantJpaRepository;
  private final CurrencypairJpaRepository currencypairJpaRepository;

  private String tenantCurrency = null;
  private Securitycurrency<?> securityCurrency;
  private boolean securtyTenantSameCurrency;
  private List<Currencypair> currencypairs = null;
  private String currencyOfSecurity = null;

  public SecurityStatisticsSummary(SecurityJpaRepository securityJpaRepository, TenantJpaRepository tenantJpaRepository,
      CurrencypairJpaRepository currencypairJpaRepository) {
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
    Tenant tenant = tenantJpaRepository.getById(user.getIdTenant());
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

  public AnnualisedSecurityPerformance getAnnualisedSecurityPerformance() {
    List<SecurityYearClose> sycList = securtyTenantSameCurrency
        ? securityJpaRepository.getSecurityYearCloseDivSum(securityCurrency.getIdSecuritycurrency())
        : securityJpaRepository.getSecurityYearDivSumCurrencyClose(securityCurrency.getIdSecuritycurrency(),
            currencypairs.get(0).getIdSecuritycurrency());
    AnnualisedSecurityPerformance asp = calculateYearPerformance(sycList, divideOrMultiplyCurrency(tenantCurrency));
    calculateAnnualisedYears(asp);
    return asp;
  }

  private AnnualisedSecurityPerformance calculateYearPerformance(List<SecurityYearClose> sycList, boolean divide) {
    var asp = new AnnualisedSecurityPerformance(currencyOfSecurity, tenantCurrency,
        sycList.get(sycList.size() - 1).getDate(), sycList.get(0).getDate());
    for (int i = 0; i < sycList.size() - 1; i++) {
      SecurityYearClose syc = sycList.get(i);
      double fcur = securtyTenantSameCurrency ? 1.0 : divide ? 1.0 / syc.getCurrencyClose() : syc.getCurrencyClose();
      SecurityYearClose sycBefore = sycList.get(i + 1);
      double fcurBefore = securtyTenantSameCurrency ? 1.0
          : divide ? 1.0 / sycBefore.getCurrencyClose() : sycBefore.getCurrencyClose();
      double performance = ((syc.getSecurityClose() + syc.getYearDiv()) - sycBefore.getSecurityClose())
          / sycBefore.getSecurityClose() * 100;
      double performanceMC = (((syc.getSecurityClose() + syc.getYearDiv()) * fcur)
          - sycBefore.getSecurityClose() * fcurBefore) / (sycBefore.getSecurityClose() * fcurBefore) * 100.0;
      asp.lastYears.add(new LastYears(syc.getDate().getYear(), performance, performanceMC));
    }
    return asp;
  }

  private void calculateAnnualisedYears(AnnualisedSecurityPerformance asp) {
    int i = 0;
    int numberOfYears = 0;
    double performance = 1;
    double performanceMC = 1;
    for (LastYears lastYear : asp.lastYears) {
      if (lastYear.year == LocalDate.now().getYear()) {
        continue;
      }
      numberOfYears++;
      performance *= (1 + lastYear.performanceYear / 100);
      performanceMC *= (1 + lastYear.performanceYearMC / 100);
      if (i < ANNUALISED_YEARS.length && ANNUALISED_YEARS[i] == numberOfYears) {
        addAnnualisedYear(asp, numberOfYears, performance, performanceMC);
        i++;
      }
    }
    if (numberOfYears > ANNUALISED_YEARS[ANNUALISED_YEARS.length - 1]) {
      addAnnualisedYear(asp, numberOfYears, performance, performanceMC);
    }
  }

  private void addAnnualisedYear(AnnualisedSecurityPerformance asp, int numberOfYears, double performance,
      double performanceMC) {
    asp.annualisedYears.add(new AnnualisedYears(numberOfYears, (Math.pow(performance, 1.0 / numberOfYears) - 1.0) * 100,
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

  public SecurityStatisticsSummaryResult getStandardDeviation(JdbcTemplate jdbcTemplate, LocalDate dateFrom,
      LocalDate dateTo, boolean adjustCurrency) {
    SecurityStatisticsSummaryResult sResult = new SecurityStatisticsSummaryResult();
    List<Securitycurrency<?>> securitycurrencyList = Arrays.asList(securityCurrency);
    ClosePricesCurrencyClose closePrices = ReportHelper.loadCloseData(jdbcTemplate, currencypairJpaRepository,
        securitycurrencyList, SamplingPeriodType.DAILY_RETURNS, dateFrom, dateTo, adjustCurrency);
    ReportHelper.adjustCloseToSameCurrency(securitycurrencyList, closePrices);

    DescriptiveStatistics stats = new DescriptiveStatistics();
    double[][] percentageChange = ReportHelper.transformToPercentageChange(closePrices.dateCloseTree, 1);
    for (int i = 0; i < percentageChange.length; i++) {
      stats.addValue(percentageChange[i][0]);
    }
    sResult.dailyStandardDeviation = stats.getStandardDeviation();

    return sResult;
  }

}
