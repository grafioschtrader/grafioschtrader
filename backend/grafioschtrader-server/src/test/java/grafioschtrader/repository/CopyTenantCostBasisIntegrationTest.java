package grafioschtrader.repository;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.Map;
import java.util.stream.Collectors;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.transaction.annotation.Transactional;

import grafiosch.entities.User;
import grafioschtrader.entities.Portfolio;
import grafioschtrader.entities.Securityaccount;
import grafioschtrader.entities.Tenant;
import grafioschtrader.entities.TradingPlatformPlan;
import grafioschtrader.instrument.CostBasisMethods;
import grafioschtrader.rest.GTIntegrationTestContext;
import grafioschtrader.service.CostBasisMethodService;
import grafioschtrader.types.CostBasisMethod;
import grafioschtrader.types.TenantKindType;
import jakarta.persistence.EntityManager;
import jakarta.persistence.PersistenceContext;

/**
 * A copied tenant has to calculate as its source does. The fixture is its own and is rolled back after the test; it
 * never starts the numbered resource suites.
 */
@GTIntegrationTestContext
@Transactional
class CopyTenantCostBasisIntegrationTest {

  private static final String OVERRIDDEN = "Copy basis overridden";
  private static final String INHERITING = "Copy basis inheriting";

  @PersistenceContext
  private EntityManager em;
  @Autowired
  private CopyTenantService copyTenantService;
  @Autowired
  private CostBasisMethodService costBasisMethods;

  @Test
  @DisplayName("The copy takes over the method of the source tenant and the override of a portfolio")
  void copyCalculatesWithTheMethodsOfItsSource() {
    Tenant source = new Tenant("Copy cost basis source", "CHF", 0, TenantKindType.MAIN, false);
    source.setCostBasisMethod(CostBasisMethod.LIFO);
    em.persist(source);
    // The target starts with a method and an override of its own, both must be gone after the copy
    Tenant target = new Tenant("Copy cost basis target", "CHF", 0, TenantKindType.MAIN, false);
    target.setCostBasisMethod(CostBasisMethod.FIFO);
    em.persist(target);
    securityaccountIn(source.getId(), OVERRIDDEN, CostBasisMethod.FIFO);
    securityaccountIn(source.getId(), INHERITING, null);
    securityaccountIn(target.getId(), "Copy basis replaced", CostBasisMethod.AVERAGE_COST);
    em.flush();
    em.clear();
    User sourceUser = new User(source.getId());
    sourceUser.setIdUser(-1001);
    User targetUser = new User(target.getId());
    targetUser.setIdUser(-1002);

    copyTenantService.copyTenant(sourceUser, targetUser);
    em.flush();
    em.clear();

    Map<String, Integer> idByPortfolio = em
        .createQuery("SELECT s FROM Securityaccount s WHERE s.idTenant = ?1", Securityaccount.class)
        .setParameter(1, target.getId()).getResultList().stream()
        .collect(Collectors.toMap(account -> account.getPortfolio().getName(), Securityaccount::getId));
    assertThat(idByPortfolio).containsOnlyKeys(OVERRIDDEN, INHERITING);
    CostBasisMethods methods = costBasisMethods.forTenant(target.getId());
    assertThat(methods.tenantMethod()).isEqualTo(CostBasisMethod.LIFO);
    assertThat(methods.forAccount(idByPortfolio.get(OVERRIDDEN))).isEqualTo(CostBasisMethod.FIFO);
    assertThat(methods.forAccount(idByPortfolio.get(INHERITING))).isEqualTo(CostBasisMethod.LIFO);
  }

  /** A portfolio of the tenant with the given override and one security account, the portfolio name on both. */
  private void securityaccountIn(Integer idTenant, String name, CostBasisMethod override) {
    Portfolio portfolio = new Portfolio(idTenant, name, "CHF");
    portfolio.setCostBasisMethod(override);
    em.persist(portfolio);
    Securityaccount securityaccount = new Securityaccount(name, portfolio);
    securityaccount.setIdTenant(idTenant);
    securityaccount.setTradingPlatformPlan(em.createQuery("SELECT p FROM TradingPlatformPlan p", TradingPlatformPlan.class)
        .setMaxResults(1).getSingleResult());
    em.persist(securityaccount);
  }
}
