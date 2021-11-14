package grafioschtrader.reports;

import java.time.LocalDate;
import java.util.List;

import org.springframework.security.core.context.SecurityContextHolder;

import grafioschtrader.dto.AnnualisedSecurityPerformance;
import grafioschtrader.dto.AnnualisedSecurityPerformance.LastYears;
import grafioschtrader.entities.Currencypair;
import grafioschtrader.entities.Security;
import grafioschtrader.entities.Tenant;
import grafioschtrader.entities.User;
import grafioschtrader.entities.projection.SecurityYearClose;
import grafioschtrader.repository.CurrencypairJpaRepository;
import grafioschtrader.repository.SecurityJpaRepository;
import grafioschtrader.repository.TenantJpaRepository;

public class AnnualisedPerformanceReport {

  private final SecurityJpaRepository securityJpaRepository;
  private final TenantJpaRepository tenantJpaRepository;
  private final CurrencypairJpaRepository currencypairJpaRepository;

  public AnnualisedPerformanceReport(SecurityJpaRepository securityJpaRepository,
      TenantJpaRepository tenantJpaRepository, CurrencypairJpaRepository currencypairJpaRepository) {
    super();
    this.securityJpaRepository = securityJpaRepository;
    this.tenantJpaRepository = tenantJpaRepository;
    this.currencypairJpaRepository = currencypairJpaRepository;
  }

  public AnnualisedSecurityPerformance getAnnualisedPerformance(Integer idSecurity) {
    List<Currencypair> currencypairs = null;
    var asp = new AnnualisedSecurityPerformance();
    final User user = (User) SecurityContextHolder.getContext().getAuthentication().getDetails();
    Tenant tenant = tenantJpaRepository.getById(user.getIdTenant());
    Security security = securityJpaRepository
        .findByIdTenantPrivateIsNullOrIdTenantPrivateAndIdSecuritycurrency(idSecurity, user.getIdTenant());
    boolean sameCurrency = tenant.getCurrency().equals(security.getCurrency());
    if (!sameCurrency) {
      currencypairs = currencypairJpaRepository
          .findByFromCurrencyAndToCurrencyOrToCurrencyAndFromCurrency(security.getCurrency(), tenant.getCurrency());
    }

    if (sameCurrency || !currencypairs.isEmpty()) {
      boolean div = false;
      if (!sameCurrency) {
        if (currencypairs.get(0).getFromCurrency().equals(tenant.getCurrency())) {
          div = true;
        }
      }

      List<SecurityYearClose> sycList = sameCurrency
          ? securityJpaRepository.getSecurityYearDivSum(security.getIdSecuritycurrency())
          : securityJpaRepository.getSecurityYearDivSumCurrencyClose(security.getIdSecuritycurrency(),
              currencypairs.get(0).getIdSecuritycurrency());
      for (int i = 0; i < sycList.size() - 1; i++) {

        SecurityYearClose syc = sycList.get(i);
        double fcur = sameCurrency ? 1.0 : div ? 1.0 / syc.getCurrencyClose() : syc.getCurrencyClose();
        SecurityYearClose sycBefore = sycList.get(i + 1);
        double fcurBefore = sameCurrency ? 1.0
            : div ? 1.0 / sycBefore.getCurrencyClose() : sycBefore.getCurrencyClose();
        double performance = ((syc.getSecurityClose() + syc.getYearDiv()) - sycBefore.getSecurityClose())
            / sycBefore.getSecurityClose() * 100;
        double performanceMC = (((syc.getSecurityClose() + syc.getYearDiv()) * fcur)
            - sycBefore.getSecurityClose() * fcurBefore) / (sycBefore.getSecurityClose() * fcurBefore) * 100.0;
        if (syc.getDate().getYear() == LocalDate.now().getYear()) {
          asp.ytd = performance;
          asp.ytdMC = performanceMC;
        } else {
          asp.lastYears.add(new LastYears(syc.getDate().getYear(), performance, performanceMC));
        }

      }
    }

    return asp;
  }

}
