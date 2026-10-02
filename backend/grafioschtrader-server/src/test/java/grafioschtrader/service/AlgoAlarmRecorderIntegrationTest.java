package grafioschtrader.service;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.LocalDate;
import java.util.List;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.transaction.annotation.Transactional;

import grafioschtrader.algo.strategy.model.AlgoStrategyImplementationType;
import grafioschtrader.entities.AlgoMessageAlert;
import grafioschtrader.entities.AlgoStrategy;
import grafioschtrader.entities.AlgoTop;
import grafioschtrader.entities.Security;
import grafioschtrader.entities.Tenant;
import grafioschtrader.entities.Watchlist;
import grafioschtrader.rest.GTIntegrationTestContext;
import grafioschtrader.types.AlgoSignalKind;
import grafioschtrader.types.TenantKindType;
import jakarta.persistence.EntityManager;
import jakarta.persistence.PersistenceContext;

/**
 * The last step of a rebalancing signal: what {@link AlgoAlarmRecorder} stores for a {@link AlgoSignalKind#REBALANCE_DRIFT}
 * against the test database. The service side, which decides when such a signal is raised, is covered by
 * {@link AlgoRebalancingServiceTest}; here the daily identity of the stored row is proven, which only the unique key of
 * the table enforces.
 */
@GTIntegrationTestContext
class AlgoAlarmRecorderIntegrationTest {

  @PersistenceContext
  EntityManager em;
  @Autowired
  AlgoAlarmRecorder recorder;

  @Test
  @Transactional
  void rebalanceDriftIsOneRowPerDayAndDirection() {
    AlgoAlertScope scope = monitoredRebalancingScope();
    String details = AlgoAlarmDetails.of("trigger", "PERIODIC", "action", "REBALANCE_SELL", "target", 25.0, "actual",
        35.0, "deviation", 10.0);
    LocalDate day = LocalDate.now();

    recorder.record(scope, AlgoSignalKind.REBALANCE_DRIFT, (byte) 1, details, day);
    // The duplicate is absorbed by the upsert and leaves the surrounding transaction usable.
    recorder.record(scope, AlgoSignalKind.REBALANCE_DRIFT, (byte) 1, details, day);
    assertThat(rows(scope)).hasSize(1);

    recorder.record(scope, AlgoSignalKind.REBALANCE_DRIFT, (byte) -1, details, day);
    assertThat(rows(scope)).hasSize(2);
    recorder.record(scope, AlgoSignalKind.REBALANCE_DRIFT, (byte) 1, details, day.plusDays(1));
    assertThat(rows(scope)).hasSize(3);

    assertThat(rows(scope)).allSatisfy(alert -> {
      assertThat(alert.getAlarmType()).isEqualTo(AlgoSignalKind.REBALANCE_DRIFT);
      assertThat(alert.getDeliveryStatus()).isEqualTo("PENDING");
      assertThat(alert.getTrancheKey()).isEqualTo(AlgoMessageAlert.NO_TRANCHE);
    });
    assertThat(rows(scope)).extracting(AlgoMessageAlert::getSignalDirection).containsExactlyInAnyOrder((byte) 1,
        (byte) -1, (byte) 1);

    // A strategy whose alerts are switched off records nothing.
    scope.strategy().setAlertEnabled(false);
    em.flush();
    recorder.record(scope, AlgoSignalKind.REBALANCE_DRIFT, (byte) 1, details, day.plusDays(2));
    assertThat(rows(scope)).hasSize(3);
  }

  /**
   * A main tenant whose rebalancing strategy sits on the AlgoTop assigned to portfolio monitoring, which is what
   * {@link AlgoMonitoringService#permitsAlert(Integer, Integer)} requires before a signal is stored.
   */
  private AlgoAlertScope monitoredRebalancingScope() {
    Tenant tenant = new Tenant("Rebalancing recorder", "CHF", 0, TenantKindType.MAIN, false);
    em.persist(tenant);
    Watchlist watchlist = new Watchlist(tenant.getId(), "Rebalancing recorder");
    em.persist(watchlist);
    AlgoTop top = new AlgoTop();
    top.setIdTenant(tenant.getId());
    top.setName("Rebalancing recorder");
    top.setPercentage(100f);
    top.setIdWatchlist(watchlist.getId());
    em.persist(top);
    tenant.setIdAlgoTop(top.getId());
    AlgoStrategy strategy = new AlgoStrategy();
    strategy.setIdTenant(tenant.getId());
    strategy.setIdAlgoAssetclassSecurity(top.getId());
    strategy.setAlgoStrategyImplementations(AlgoStrategyImplementationType.AS_HOLDING_TOP_REBALANCING);
    em.persist(strategy);
    em.flush();
    Security security = em.createQuery("SELECT s FROM Security s ORDER BY s.idSecuritycurrency", Security.class)
        .setMaxResults(1).getSingleResult();
    return new AlgoAlertScope(tenant.getId(), strategy, security, top.getName(), true);
  }

  private List<AlgoMessageAlert> rows(AlgoAlertScope scope) {
    return em.createQuery("SELECT a FROM AlgoMessageAlert a WHERE a.idTenant = :tenant", AlgoMessageAlert.class)
        .setParameter("tenant", scope.idTenant()).getResultList();
  }
}
