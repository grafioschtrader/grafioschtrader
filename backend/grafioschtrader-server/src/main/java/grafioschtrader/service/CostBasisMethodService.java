package grafioschtrader.service;

import java.util.HashMap;
import java.util.Map;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import grafioschtrader.entities.Tenant;
import grafioschtrader.instrument.CostBasisMethods;
import grafioschtrader.repository.PortfolioJpaRepository;
import grafioschtrader.repository.TenantJpaRepository;
import grafioschtrader.types.CostBasisMethod;

/**
 * Resolves the cost basis methods a report run of a tenant calculates by: the method of the tenant and the methods of
 * the security accounts whose portfolio overrides it.
 */
@Service
public class CostBasisMethodService {

  @Autowired
  private TenantJpaRepository tenantJpaRepository;

  @Autowired
  private PortfolioJpaRepository portfolioJpaRepository;

  /**
   * The methods in effect for a tenant. The tenant comes from the argument alone and never from the security context,
   * because the alert evaluation calls this without a request.
   *
   * @param idTenant the tenant whose reports are calculated
   * @return its method and the overrides of its portfolios, average cost for an unknown tenant
   */
  public CostBasisMethods forTenant(Integer idTenant) {
    final CostBasisMethod tenantMethod = tenantJpaRepository.findById(idTenant).map(Tenant::getCostBasisMethod)
        .orElse(CostBasisMethod.AVERAGE_COST);
    final Map<Integer, CostBasisMethod> byIdSecurityaccount = new HashMap<>();
    for (final Object[] row : portfolioJpaRepository.getCostBasisMethodOverrides(idTenant)) {
      byIdSecurityaccount.put(((Number) row[0]).intValue(),
          CostBasisMethod.getCostBasisMethodByValue(((Number) row[1]).byteValue()));
    }
    return new CostBasisMethods(tenantMethod, Map.copyOf(byIdSecurityaccount));
  }
}
