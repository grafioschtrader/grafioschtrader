package grafioschtrader.service;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

import java.util.Optional;

import org.junit.jupiter.api.*;
import org.springframework.data.domain.Page;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;

import grafiosch.entities.User;
import grafiosch.service.DailyLimitService;
import grafioschtrader.config.FeatureConfig;
import grafioschtrader.entities.Tenant;
import grafioschtrader.repository.*;
import grafioschtrader.rest.AlgoAlertResource;
import grafioschtrader.types.TenantKindType;

/**
 * The effective tenant controls diagnostics and manual actions, including managed and read-only sessions.
 *
 * <p>
 * The session modelled here is a switched one: the request runs in tenant 7 while the user's own tenant is 42. Which of
 * the two is examined matters - the alert endpoints are refused by the kind of the tenant the request operates in,
 * while the alerts themselves are always evaluated for the home tenant.
 * </p>
 */
class AlgoAlertAccessTest {
  final User user = mock(User.class);
  /** The home tenant, which alert reads and actions address. */
  final Tenant tenant = new Tenant();
  /** The tenant of the request; a plain main tenant unless a test turns it into a simulation environment. */
  final Tenant currentTenant = new Tenant();
  final TenantJpaRepository tenants = mock(TenantJpaRepository.class);
  final AlgoMessageAlertJpaRepository alarms = mock(AlgoMessageAlertJpaRepository.class);
  final AlgoAlarmDeliveryService delivery = mock(AlgoAlarmDeliveryService.class);
  final AlgoAlarmEvaluationService evaluator = mock(AlgoAlarmEvaluationService.class);
  final DailyLimitService limits = mock(DailyLimitService.class);
  final FeatureConfig features = new FeatureConfig();
  AlgoAlertResource resource;

  @BeforeEach
  void setup() {
    when(user.getIdTenant()).thenReturn(7);
    when(user.getActualIdTenant()).thenReturn(42);
    when(user.getIdUser()).thenReturn(9);
    when(tenants.findById(42)).thenReturn(Optional.of(tenant));
    when(tenants.findById(7)).thenReturn(Optional.of(currentTenant));
    when(alarms.findByIdTenant(eq(42), any())).thenReturn(Page.empty());
    Authentication authentication = mock(Authentication.class);
    when(authentication.getDetails()).thenReturn(user);
    SecurityContextHolder.getContext().setAuthentication(authentication);
    features.setAlgo(true);
    features.setAlert(true);
    resource = new AlgoAlertResource(mock(AlgoAlertScopeResolver.class),
        mock(AlgoAlertEvaluationStateJpaRepository.class), alarms, delivery, evaluator, tenants, limits, features);
  }

  @AfterEach
  void clear() {
    SecurityContextHolder.clearContext();
  }

  @Test
  void managedSessionReadsAndActsOnlyOnEffectiveTenant() {
    assertThatThrownBy(() -> resource.notifications(0, 1000, null)).isInstanceOf(IllegalArgumentException.class);
    resource.notifications(0, 100, null);
    verify(alarms).findByIdTenant(eq(42), argThat(page -> page.getPageSize() == 100));
    resource.evaluateNow();
    resource.retry(123);
    verify(evaluator).evaluateAlertsForTenant(42);
    verify(delivery).retry(42, 123);
    verify(limits, times(2)).check(user, AlgoAlertResource.ACTION_LIMIT, 1);
  }

  @Test
  void readOnlyAllowsDiagnosticsButRejectsActions() {
    when(user.isTenantAccessReadOnly()).thenReturn(true);
    assertThat(resource.statuses()).contains("REVIEW_REQUIRED");
    assertThatThrownBy(resource::evaluateNow).isInstanceOf(SecurityException.class);
    assertThatThrownBy(() -> resource.retry(1)).isInstanceOf(SecurityException.class);
    verifyNoInteractions(evaluator, delivery, limits);
  }

  @Test
  void simulationAndDisabledFeatureCannotRunLiveActions() {
    // The request operates in a simulation environment below the user's own tenant, which is the only shape this can
    // take: a home tenant is never a simulation copy.
    currentTenant.setTenantKindType(TenantKindType.SIMULATION_COPY);
    currentTenant.setIdParentTenant(42);
    assertThatThrownBy(resource::statuses).isInstanceOf(grafiosch.exceptions.DataViolationException.class);
    currentTenant.setTenantKindType(TenantKindType.MAIN);
    features.setAlert(false);
    assertThatThrownBy(resource::evaluateNow).isInstanceOf(SecurityException.class);
    verifyNoInteractions(evaluator, delivery, limits);
  }
}
