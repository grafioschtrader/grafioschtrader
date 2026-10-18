package grafioschtrader.service;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.Mockito.*;

import java.util.List;
import java.util.Optional;

import org.junit.jupiter.api.*;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.transaction.PlatformTransactionManager;

import grafiosch.entities.User;
import grafiosch.service.DailyLimitService;
import grafioschtrader.config.FeatureConfig;
import grafioschtrader.entities.AlgoMessageAlert;
import grafioschtrader.entities.Tenant;
import grafioschtrader.repository.*;
import grafioschtrader.rest.AlgoAlertResource;
import grafioschtrader.rest.AlgoAlertResource.NotificationDto;
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
    when(alarms.findByIdTenantOrderByIdAlgoMessageAlertDesc(42)).thenReturn(List.of());
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
    when(alarms.findByIdTenantOrderByIdAlgoMessageAlertDesc(42)).thenReturn(List.of(alert("Home book")));
    when(alarms.findByIdTenantOrderByIdAlgoMessageAlertDesc(7)).thenReturn(List.of(alert("Request book")));
    var recordingEvaluator = new RecordingEvaluator();
    var recordingDelivery = new RecordingDelivery();
    var homeResource = new AlgoAlertResource(mock(AlgoAlertScopeResolver.class),
        mock(AlgoAlertEvaluationStateJpaRepository.class), alarms, recordingDelivery, recordingEvaluator, tenants,
        limits, features);

    assertThat(homeResource.notifications()).extracting(NotificationDto::contextName).containsExactly("Home book");
    homeResource.evaluateNow();
    assertThat(recordingEvaluator.tenant).isEqualTo(42);
    homeResource.retry(123);
    assertThat(recordingDelivery.tenant).isEqualTo(42);
    assertThat(recordingDelivery.alertId).isEqualTo(123);
  }

  @Test
  void readOnlyAllowsDiagnosticsButRejectsActions() {
    when(user.isTenantAccessReadOnly()).thenReturn(true);
    assertThat(resource.notifications()).isEmpty();
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
    assertThatThrownBy(resource::notifications).isInstanceOf(grafiosch.exceptions.DataViolationException.class);
    currentTenant.setTenantKindType(TenantKindType.MAIN);
    features.setAlert(false);
    assertThatThrownBy(resource::evaluateNow).isInstanceOf(SecurityException.class);
    verifyNoInteractions(evaluator, delivery, limits);
  }

  private static AlgoMessageAlert alert(String contextName) {
    AlgoMessageAlert alert = new AlgoMessageAlert();
    alert.setContextName(contextName);
    return alert;
  }

  /** Records the tenant a manual evaluation was asked to run for. */
  private static final class RecordingEvaluator extends AlgoAlarmEvaluationService {
    private Integer tenant;

    @Override
    public void evaluateAlertsForTenant(Integer idTenant) {
      tenant = idTenant;
    }
  }

  /** Records the tenant and notification a manual retry was asked to send again. */
  private static final class RecordingDelivery extends AlgoAlarmDeliveryService {
    private Integer tenant;
    private Integer alertId;

    private RecordingDelivery() {
      super(null, null, null, null, null, null, null, null, null, null, new FeatureConfig(),
          mock(PlatformTransactionManager.class));
    }

    @Override
    public void retry(Integer tenant, Integer id) {
      this.tenant = tenant;
      this.alertId = id;
    }
  }
}
