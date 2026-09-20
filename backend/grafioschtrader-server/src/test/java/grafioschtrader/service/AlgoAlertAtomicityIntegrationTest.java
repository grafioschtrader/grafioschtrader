package grafioschtrader.service;

import static org.assertj.core.api.Assertions.*;

import java.time.LocalDate;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;

import grafioschtrader.algo.strategy.model.AlgoStrategyImplementationType;
import grafioschtrader.entities.*;
import grafioschtrader.rest.GTIntegrationTestContext;
import grafioschtrader.types.TenantKindType;
import jakarta.persistence.EntityManager;
import jakarta.persistence.PersistenceContext;

/** Runs the actual evaluator, crossing state and signal upsert against the dedicated test database. */
@GTIntegrationTestContext
class AlgoAlertAtomicityIntegrationTest {
  @PersistenceContext
  EntityManager em;
  @Autowired
  PlatformTransactionManager manager;
  @Autowired
  AlgoAlarmEvaluationService evaluator;

  @Test
  void signalAndCrossingRollbackTogetherAndDuplicateDoesNotPoisonTransaction() {
    TransactionTemplate tx = new TransactionTemplate(manager);
    AlgoAlertScope scope = tx.execute(_ -> {
      Tenant tenant = new Tenant("Alert atomicity", "CHF", 0, TenantKindType.MAIN, false);
      em.persist(tenant);
      Watchlist watchlist = new Watchlist(tenant.getId(), "Alert atomicity");
      em.persist(watchlist);
      AlgoTop top = new AlgoTop();
      top.setIdTenant(tenant.getId());
      top.setName("Alert atomicity");
      top.setPercentage(100f);
      top.setIdWatchlist(watchlist.getId());
      em.persist(top);
      AlgoStrategy strategy = new AlgoStrategy();
      strategy.setIdTenant(tenant.getId());
      strategy.setIdAlgoAssetclassSecurity(top.getId());
      strategy.setAlgoStrategyImplementations(AlgoStrategyImplementationType.AS_OBSERVED_SECURITY_ABSOLUTE_PRICE);
      strategy.setStrategyConfig("{\"upperValue\":100}");
      em.persist(strategy);
      Security security = em.createQuery("SELECT s FROM Security s ORDER BY s.idSecuritycurrency", Security.class)
          .setMaxResults(1).getSingleResult();
      em.detach(security); // Quotes below are test observations, never changes to a shared instrument.
      return new AlgoAlertScope(tenant.getId(), strategy, security, "Alert atomicity", true);
    });
    try {
      tx.executeWithoutResult(_ -> evaluate(scope, 90));
      assertThatThrownBy(() -> tx.executeWithoutResult(_ -> {
        evaluate(scope, 110);
        throw new IllegalStateException("Simulated failure after recording");
      })).isInstanceOf(IllegalStateException.class);
      assertThat(tx.<Long>execute(_ -> count(scope))).isZero();
      tx.executeWithoutResult(_ -> evaluate(scope, 110));
      assertThat(tx.<Long>execute(_ -> count(scope))).isEqualTo(1L);
      tx.executeWithoutResult(_ -> evaluate(scope, 90));
      tx.executeWithoutResult(_ -> evaluate(scope, 110));
      assertThat(tx.<Long>execute(_ -> count(scope))).isEqualTo(1L);
      assertThat(tx.<String>execute(_ -> em
          .createQuery("SELECT a.deliveryStatus FROM AlgoMessageAlert a WHERE a.idTenant = :tenant", String.class)
          .setParameter("tenant", scope.idTenant()).getSingleResult())).isEqualTo("PENDING");
    } finally {
      tx.executeWithoutResult(_ -> {
        for (String entity : new String[] { "AlgoMessageAlert", "AlgoAlertState", "AlgoStrategy", "AlgoTop",
            "Watchlist", "Tenant" })
          em.createQuery("DELETE FROM " + entity + " a WHERE a.idTenant = :tenant")
              .setParameter("tenant", scope.idTenant()).executeUpdate();
      });
    }
  }

  private long count(AlgoAlertScope scope) {
    return em.createQuery("SELECT COUNT(a) FROM AlgoMessageAlert a WHERE a.idTenant = :tenant", Long.class)
        .setParameter("tenant", scope.idTenant()).getSingleResult();
  }

  private void evaluate(AlgoAlertScope scope, double price) {
    scope.security().setSLast(price);
    try {
      evaluator.evaluateOne(scope, scope.security(), scope.strategy().getAlgoStrategyImplementations(),
          LocalDate.now());
    } catch (Exception e) {
      throw new IllegalStateException(e);
    }
  }
}
