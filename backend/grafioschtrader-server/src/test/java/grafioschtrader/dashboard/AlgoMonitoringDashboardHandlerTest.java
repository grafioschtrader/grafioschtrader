package grafioschtrader.dashboard;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import java.time.LocalDate;
import java.util.List;
import java.util.Locale;
import java.util.Optional;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import grafiosch.entities.User;
import grafioschtrader.config.FeatureConfig;
import grafioschtrader.dto.AlgoMonitoringSummary;
import grafioschtrader.entities.AlgoRecommendation;
import grafioschtrader.entities.AlgoTop;
import grafioschtrader.entities.Security;
import grafioschtrader.entities.Tenant;
import grafioschtrader.repository.AlgoRecommendationJpaRepository;
import grafioschtrader.repository.AlgoTopJpaRepository;
import grafioschtrader.repository.SecurityJpaRepository;
import grafioschtrader.repository.TenantJpaRepository;
import grafioschtrader.service.AlgoRebalancingService;
import grafioschtrader.types.AlgoRebalancingTrigger;
import grafioschtrader.types.AlgoRecommendationAction;
import tools.jackson.databind.json.JsonMapper;

/** The monitoring card condenses the stored plan of the assigned hierarchy without calculating anything. */
class AlgoMonitoringDashboardHandlerTest {

  private static final int ID_TENANT = 7;
  private static final int ID_TOP = 303;
  private static final LocalDate DAY = LocalDate.of(2026, 9, 20);

  private final TenantJpaRepository tenants = mock(TenantJpaRepository.class);
  private final AlgoTopJpaRepository tops = mock(AlgoTopJpaRepository.class);
  private final AlgoRecommendationJpaRepository recommendations = mock(AlgoRecommendationJpaRepository.class);
  private final SecurityJpaRepository securities = mock(SecurityJpaRepository.class);
  private final AlgoRebalancingService rebalancing = mock(AlgoRebalancingService.class);
  private final AlgoMonitoringDashboardHandler handler = new AlgoMonitoringDashboardHandler(tenants, tops,
      recommendations, securities, rebalancing, mock(FeatureConfig.class), JsonMapper.builder().build());
  private final Tenant tenant = new Tenant();
  private final User user = mock(User.class);
  private final AlgoTop top = new AlgoTop();

  @BeforeEach
  void setup() {
    tenant.setIdTenant(ID_TENANT);
    tenant.setIdAlgoTop(ID_TOP);
    top.setIdAlgoAssetclassSecurity(ID_TOP);
    top.setIdTenant(ID_TENANT);
    top.setName("Hugo Rebalance");
    when(user.getActualIdTenant()).thenReturn(ID_TENANT);
    when(user.createAndGetJavaLocale()).thenReturn(Locale.ENGLISH);
    when(tenants.findById(ID_TENANT)).thenReturn(Optional.of(tenant));
    when(tops.findByIdTenantAndIdAlgoAssetclassSecurity(ID_TENANT, ID_TOP)).thenReturn(top);
    when(rebalancing.nextCheckpointDate(top, DAY)).thenReturn(DAY.plusDays(91));
    when(rebalancing.bucketLabel(any(), any())).thenReturn("Equities");
  }

  @Test
  @DisplayName("Without a monitoring assignment the card only states that")
  void noAssignment() {
    tenant.setIdAlgoTop(null);
    assertThat(handler.summarize(user).reasonKey()).isEqualTo("DASHBOARD_ALGO_NO_MONITORING");
  }

  @Test
  @DisplayName("An assigned hierarchy without a stored plan has not been evaluated yet")
  void notEvaluated() {
    assertThat(handler.summarize(user).reasonKey()).isEqualTo("DASHBOARD_ALGO_NOT_EVALUATED");
  }

  @Test
  @DisplayName("A plan of holds only is 'nothing to do', with the largest bucket drift and the next checkpoint")
  void nothingToDo() {
    when(recommendations.findByIdTenantAndIdAlgoTopOrderByLevelTypeAscIdNodeAsc(ID_TENANT, ID_TOP))
        .thenReturn(List.of(line("A", 20, null, AlgoRecommendationAction.REBALANCE_HOLD, null, 1.2),
            line("A", 21, null, AlgoRecommendationAction.REBALANCE_HOLD, null, -2.4),
            line("S", 30, 40, AlgoRecommendationAction.REBALANCE_HOLD, null, 0.1)));

    AlgoMonitoringSummary summary = handler.summarize(user);

    assertThat(summary.reasonKey()).isNull();
    assertThat(summary.statusKey()).isEqualTo("DASHBOARD_ALGO_NOTHING_TO_DO");
    assertThat(summary.largestDeviationBucket()).isEqualTo("Equities");
    assertThat(summary.largestDeviationPercentage()).isEqualTo(-2.4);
    assertThat(summary.lastCheckpointDate()).isEqualTo(DAY);
    assertThat(summary.nextCheckpointDate()).isEqualTo(DAY.plusDays(91));
    assertThat(summary.topTrades()).isEmpty();
  }

  @Test
  @DisplayName("A checkpoint lists at most three trades, sales first and the largest first")
  void checkpointWithTrades() {
    List<AlgoRecommendation> plan = List.of(
        line("S", 30, 40, AlgoRecommendationAction.REBALANCE_BUY, 500.0, 1.0),
        line("S", 31, 41, AlgoRecommendationAction.REBALANCE_BUY, 900.0, 1.0),
        line("S", 32, 42, AlgoRecommendationAction.REBALANCE_SELL, 300.0, 1.0),
        line("S", 33, 43, AlgoRecommendationAction.REBALANCE_BUY, 100.0, 1.0));
    plan.forEach(r -> r.setTriggerKind(AlgoRebalancingTrigger.PERIODIC));
    when(recommendations.findByIdTenantAndIdAlgoTopOrderByLevelTypeAscIdNodeAsc(ID_TENANT, ID_TOP)).thenReturn(plan);
    when(securities.findAllById(any())).thenReturn(List.of(security(40, "A"), security(41, "B"), security(42, "C")));

    AlgoMonitoringSummary summary = handler.summarize(user);

    assertThat(summary.statusKey()).isEqualTo("DASHBOARD_ALGO_CHECKPOINT_DUE");
    assertThat(summary.buyCount()).isEqualTo(3);
    assertThat(summary.buyAmount()).isEqualTo(1500.0);
    assertThat(summary.sellCount()).isEqualTo(1);
    assertThat(summary.topTrades()).extracting(AlgoMonitoringSummary.Trade::securityName).containsExactly("C", "B",
        "A");
  }

  @Test
  @DisplayName("Mean reversion proposals of another hierarchy are not counted")
  void meanReversionOfTheMonitoredHierarchyOnly() {
    when(recommendations.findByIdTenantAndIdAlgoTopOrderByLevelTypeAscIdNodeAsc(ID_TENANT, ID_TOP))
        .thenReturn(List.of(line("S", 30, 40, AlgoRecommendationAction.REBALANCE_HOLD, null, 0.0)));
    AlgoRecommendation own = line("S", 30, 40, AlgoRecommendationAction.REBALANCE_BUY, 100.0, null);
    AlgoRecommendation other = line("S", 30, 40, AlgoRecommendationAction.REBALANCE_BUY, 100.0, null);
    other.setIdAlgoTop(237);
    when(recommendations.findByIdTenantAndTriggerKind(ID_TENANT, AlgoRebalancingTrigger.MEAN_REVERSION))
        .thenReturn(List.of(own, other));

    assertThat(handler.summarize(user).meanReversionSignals()).isEqualTo(1);
  }

  private static AlgoRecommendation line(String level, int idNode, Integer idSecurity, AlgoRecommendationAction action,
      Double amount, Double deviation) {
    AlgoRecommendation r = new AlgoRecommendation();
    r.setIdTenant(ID_TENANT);
    r.setIdAlgoTop(ID_TOP);
    r.setLevelType(level);
    r.setIdNode(idNode);
    r.setIdSecuritycurrency(idSecurity);
    r.setRecommendedAction(action);
    r.setRecommendedAmount(amount);
    r.setDeviationPercentage(deviation);
    r.setValuationDate(DAY);
    r.setCheckpointDate(DAY);
    r.setCurrency("CHF");
    r.setTriggerKind(AlgoRebalancingTrigger.NONE);
    return r;
  }

  private static Security security(int id, String name) {
    Security security = new Security();
    security.setIdSecuritycurrency(id);
    security.setName(name);
    return security;
  }
}
