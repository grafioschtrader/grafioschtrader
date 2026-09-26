package grafioschtrader.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.util.List;
import java.util.Map;
import java.util.Optional;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;

import grafiosch.entities.User;
import grafiosch.exceptions.DataViolationException;
import grafioschtrader.entities.AlgoAssetclass;
import grafioschtrader.entities.AlgoSecurity;
import grafioschtrader.entities.AlgoStrategy;
import grafioschtrader.entities.AlgoTop;
import grafioschtrader.entities.Tenant;
import grafioschtrader.repository.AlgoAssetclassJpaRepository;
import grafioschtrader.repository.AlgoMessageAlertJpaRepository;
import grafioschtrader.repository.AlgoRecommendationJpaRepository;
import grafioschtrader.repository.AlgoSecurityJpaRepository;
import grafioschtrader.repository.AlgoStrategyJpaRepository;
import grafioschtrader.repository.AlgoTopJpaRepository;
import grafioschtrader.repository.TenantJpaRepository;
import grafioschtrader.types.TenantKindType;

/** Monitoring authorization, hierarchy ownership and notification-only preference behavior without a database. */
class AlgoMonitoringServiceTest {
  private final TenantJpaRepository tenants = mock(TenantJpaRepository.class);
  private final AlgoTopJpaRepository tops = mock(AlgoTopJpaRepository.class);
  private final AlgoAssetclassJpaRepository buckets = mock(AlgoAssetclassJpaRepository.class);
  private final AlgoSecurityJpaRepository members = mock(AlgoSecurityJpaRepository.class);
  private final AlgoStrategyJpaRepository strategies = mock(AlgoStrategyJpaRepository.class);
  private final AlgoMessageAlertJpaRepository alarms = mock(AlgoMessageAlertJpaRepository.class);
  private final AlgoRecommendationJpaRepository recommendations = mock(AlgoRecommendationJpaRepository.class);
  private final AlgoAlertScopeLifecycle lifecycle = mock(AlgoAlertScopeLifecycle.class);
  private final AlgoMonitoringService service = new AlgoMonitoringService(tenants, tops, buckets, members, strategies,
      alarms, recommendations, lifecycle);
  private final Tenant tenant = new Tenant();
  private final AlgoTop top = new AlgoTop();
  private final AlgoStrategy strategy = new AlgoStrategy();
  private final User user = new User();

  @BeforeEach
  void setup() {
    tenant.setIdTenant(1);
    tenant.setTenantKindType(TenantKindType.MAIN);
    tenant.setIdAlgoTop(10);
    top.setIdTenant(1);
    top.setIdAlgoAssetclassSecurity(10);
    strategy.setIdTenant(1);
    strategy.setIdAlgoRuleStrategy(20);
    strategy.setIdAlgoAssetclassSecurity(10);
    when(tenants.findById(1)).thenReturn(Optional.of(tenant));
    when(tenants.lockMonitoringTenant(1)).thenReturn(Optional.of(tenant));
    when(tops.findByIdTenantAndIdAlgoAssetclassSecurity(1, 10)).thenReturn(top);
    when(strategies.findById(20)).thenReturn(Optional.of(strategy));
    when(strategies.findByIdTenant(1)).thenReturn(List.of(strategy));
    when(lifecycle.snapshot(1)).thenReturn(Map.of());
    user.setIdTenant(1);
    var auth = new UsernamePasswordAuthenticationToken("monitor", "", List.of());
    auth.setDetails(user);
    SecurityContextHolder.getContext().setAuthentication(auth);
  }

  @AfterEach
  void clearContext() {
    SecurityContextHolder.clearContext();
  }

  @Test
  void defaultsOnButRequiresMatchingAssignment() {
    assertThat(strategy.isAlertEnabled()).isTrue();
    assertThat(service.permitsAlert(1, 20)).isTrue();
    tenant.setIdAlgoTop(null);
    assertThat(service.permitsAlert(1, 20)).isFalse();
    tenant.setIdAlgoTop(11);
    assertThat(service.permitsAlert(1, 20)).isFalse();
    tenant.setIdAlgoTop(10);
    strategy.setAlertEnabled(false);
    assertThat(service.permitsAlert(1, 20)).isFalse();
    assertThat(service.permitsAlert(2, 20)).isFalse();
  }

  @Test
  void resolvesBucketAndSecurityStrategiesAndStandaloneFollowsPreference() {
    AlgoAssetclass bucket = new AlgoAssetclass();
    bucket.setIdTenant(1);
    bucket.setIdAlgoAssetclassSecurity(11);
    bucket.setIdAlgoAssetclassParent(10);
    when(buckets.findById(11)).thenReturn(Optional.of(bucket));
    strategy.setIdAlgoAssetclassSecurity(11);
    assertThat(service.permitsAlert(1, 20)).isTrue();
    AlgoSecurity member = new AlgoSecurity();
    member.setIdTenant(1);
    member.setIdAlgoSecurityParent(11);
    when(members.findById(12)).thenReturn(Optional.of(member));
    strategy.setIdAlgoAssetclassSecurity(12);
    assertThat(service.permitsAlert(1, 20)).isTrue();
    tenant.setIdAlgoTop(null);
    strategy.setAlertEnabled(false);
    assertThat(service.permitsAlert(1, 20)).isFalse();
    member.setIdAlgoSecurityParent(null);
    // A standalone alert needs no assignment, but its own preference switches it off.
    assertThat(service.permitsAlert(1, 20)).isFalse();
    strategy.setAlertEnabled(true);
    assertThat(service.permitsAlert(1, 20)).isTrue();
    member.setIdTenant(2);
    assertThat(service.permitsAlert(1, 20)).isFalse();
  }

  @Test
  void standalonePreferenceIsEditableWithoutAssignment() {
    AlgoSecurity standalone = new AlgoSecurity();
    standalone.setIdTenant(1);
    when(members.findById(12)).thenReturn(Optional.of(standalone));
    strategy.setIdAlgoAssetclassSecurity(12);
    tenant.setIdAlgoTop(null);
    service.setAlertEnabled(20, false);
    assertThat(strategy.isAlertEnabled()).isFalse();
    assertThat(service.permitsAlert(1, 20)).isFalse();
    verify(alarms).cancelPendingForStrategy(1, 20);
  }

  @Test
  void unassignmentCancelsUnsentAlertsAndResetsScopesButKeepsPreferences() {
    service.assign(null);
    assertThat(tenant.getIdAlgoTop()).isNull();
    assertThat(strategy.isAlertEnabled()).isTrue();
    verify(lifecycle).changed(1, Map.of());
    verify(alarms).cancelPendingForStrategy(1, 20);
    service.assign(10);
    assertThat(tenant.getIdAlgoTop()).isEqualTo(10);
  }

  @Test
  void replacementRequiresAnOwnedHierarchy() {
    assertThatThrownBy(() -> service.assign(99)).isInstanceOf(SecurityException.class);
    assertThat(tenant.getIdAlgoTop()).isEqualTo(10);
    AlgoTop replacement = new AlgoTop();
    replacement.setIdAlgoAssetclassSecurity(30);
    replacement.setIdTenant(1);
    when(tops.findByIdTenantAndIdAlgoAssetclassSecurity(1, 30)).thenReturn(replacement);
    service.assign(30);
    assertThat(tenant.getIdAlgoTop()).isEqualTo(30);
    verify(alarms).cancelPendingForStrategy(1, 20);
    // Only the assigned hierarchy keeps a live plan, so the previous one loses its rows.
    verify(recommendations).deleteAllKindsByIdTenantAndIdAlgoTop(1, 10);
  }

  @Test
  void preferenceChangeDoesNotValidateOrModifyDraftConfigurationOrActivation() {
    strategy.setActivatable(false);
    strategy.setStrategyConfig(null);
    service.setAlertEnabled(20, false);
    assertThat(strategy.isActivatable()).isFalse();
    assertThat(strategy.getStrategyConfig()).isNull();
    assertThat(strategy.isAlertEnabled()).isFalse();
    verify(lifecycle).changed(1, Map.of());
    verify(alarms).cancelPendingForStrategy(1, 20);
  }

  @Test
  void unassignedPreferenceCannotBeEdited() {
    tenant.setIdAlgoTop(null);
    assertThat(service.canEdit(1, 10)).isFalse();
    assertThatThrownBy(() -> service.setAlertEnabled(20, false)).isInstanceOf(DataViolationException.class);
    assertThat(strategy.isAlertEnabled()).isTrue();
  }

  @Test
  void viewersAndSimulationSessionsCannotAssignOrToggle() {
    user.setTenantAccessReadOnly(true);
    assertThat(service.canEdit(1, 10)).isFalse();
    assertThatThrownBy(() -> service.assign(null)).isInstanceOf(SecurityException.class);
    assertThatThrownBy(() -> service.setAlertEnabled(20, false)).isInstanceOf(SecurityException.class);
    user.setTenantAccessReadOnly(false);
    user.setActualIdTenant(1);
    user.setIdTenant(2);
    assertThat(service.canEdit(1, 10)).isFalse();
    assertThatThrownBy(() -> service.assign(null)).isInstanceOf(SecurityException.class);
    assertThatThrownBy(() -> service.setAlertEnabled(20, false)).isInstanceOf(SecurityException.class);
  }

  @Test
  void simulationAssociationDoesNotAuthorizeLiveAlerts() {
    tenant.setTenantKindType(TenantKindType.SIMULATION_COPY);
    assertThat(service.permitsAlert(1, 20)).isFalse();
    assertThatThrownBy(() -> service.assign(null)).isInstanceOf(SecurityException.class);
  }
}
