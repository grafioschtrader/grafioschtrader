package grafioschtrader.service;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

import java.time.*;
import java.util.*;

import org.junit.jupiter.api.*;
import org.springframework.context.MessageSource;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.SimpleTransactionStatus;

import grafiosch.entities.*;
import grafiosch.repository.*;
import grafiosch.service.*;
import grafiosch.types.*;
import grafioschtrader.config.FeatureConfig;
import grafioschtrader.entities.AlgoMessageAlert;
import grafioschtrader.entities.Tenant;
import grafioschtrader.repository.*;
import grafioschtrader.types.MailSendForwardDefault;

/** Exercises the transport orchestrator with distinct tenant/user identities and recoverable channel failures. */
class AlgoAlarmDeliveryServiceTest {
  final AlgoMessageAlertJpaRepository alarms = mock(AlgoMessageAlertJpaRepository.class);
  final TenantJpaRepository tenants = mock(TenantJpaRepository.class);
  final AlgoStrategyJpaRepository strategies = mock(AlgoStrategyJpaRepository.class);
  final UserJpaRepository users = mock(UserJpaRepository.class);
  final TenantAccessJpaRepository access = mock(TenantAccessJpaRepository.class);
  final MailSettingForwardJpaRepository settings = mock(MailSettingForwardJpaRepository.class);
  final MailEntityJpaRepository links = mock(MailEntityJpaRepository.class);
  final SendMailInternalExternalService mail = mock(SendMailInternalExternalService.class);
  final MailExternalService smtp = mock(MailExternalService.class);
  final MessageSource messages = mock(MessageSource.class);
  final FeatureConfig features = new FeatureConfig();
  final PlatformTransactionManager manager = mock(PlatformTransactionManager.class);
  AlgoAlarmDeliveryService service;
  AlgoMessageAlert alarm;
  User owner;
  final Instant origin = Instant.parse("2026-09-07T12:00:00Z");

  @BeforeEach
  void setup() {
    MailSendForwardDefault.initialize();
    features.setAlgo(true);
    features.setAlert(true);
    when(manager.getTransaction(any())).thenAnswer(_ -> new SimpleTransactionStatus());
    service = makeService(0);
    Tenant tenant = new Tenant();
    tenant.setIdTenant(42);
    tenant.setCreateIdUser(7);
    when(tenants.findById(42)).thenReturn(Optional.of(tenant));
    when(tenants.existsById(42)).thenReturn(true);
    when(strategies.existsById(3)).thenReturn(true);
    owner = new User();
    owner.setIdUser(7);
    owner.setUsername("owner@example.test");
    owner.setLocaleStr("de");
    owner.setEnabled(true);
    when(users.findByIdTenantAndHomeTenantReadOnlyFalse(42)).thenReturn(List.of(owner));
    when(mail.sendInternalMail(eq(0), eq(7), anyString(), anyString())).thenReturn(99);
    when(messages.getMessage(anyString(), any(), any(Locale.class))).thenReturn("Alarm");
    when(mail.renderExternalMessage(anyInt(), anyString(), any())).thenAnswer(i -> i.getArgument(1));
    alarm = new AlgoMessageAlert();
    alarm.setIdAlgoMessageAlert(1);
    alarm.setIdTenant(42);
    alarm.setIdAlgoStrategy(3);
    alarm.setIdSecurityCurrency(4);
    alarm.setAlertDay(LocalDate.of(2026, 9, 7));
    alarm.setAlarmDetails("{}");
    alarm.setContextName("Strategy");
    alarm.setSecurityName("Instrument");
    when(alarms.lockDelivery(1)).thenReturn(Optional.of(alarm));
    when(alarms.dueDeliveries(any(), any())).thenReturn(List.of(1));
  }

  AlgoAlarmDeliveryService makeService(long minutes) {
    var result = new AlgoAlarmDeliveryService(alarms, tenants, strategies, users, access, settings, links, mail, smtp,
        messages, features, manager);
    ReflectionTestUtils.setField(result, "clock", Clock.fixed(origin.plusSeconds(minutes * 60), ZoneOffset.UTC));
    return result;
  }

  @Test
  void deliversToOwnerUserRatherThanTenantId() {
    service.deliverPending();
    assertThat(alarm.getRecipientUserId()).isEqualTo(7);
    assertThat(alarm.getInternalMessageId()).isEqualTo(99);
    assertThat(alarm.getDeliveryStatus()).isEqualTo("DELIVERED");
    verify(mail).sendInternalMail(eq(0), eq(7), anyString(), anyString());
    verifyNoInteractions(smtp);
  }

  @Test
  void retriesOnlyExternalChannelAfterRestartAndAcrossDays() throws Exception {
    MailSettingForward setting = new MailSettingForward();
    setting.setMessageTargetType(MessageTargetType.INTERNAL_AND_EXTERNAL_MAIL);
    when(settings.findByIdUserAndMessageComType(eq(7), anyByte())).thenReturn(Optional.of(setting));
    doThrow(new IllegalStateException("SMTP unavailable")).doNothing().when(smtp).sendSimpleMessage(anyString(),
        anyString(), anyString());
    service.deliverPending();
    assertThat(alarm.getDeliveryStatus()).isEqualTo("RETRY");
    assertThat(alarm.getNotifiedAt()).isNull();
    assertThat(alarm.getInternalCompletedAt()).isNotNull();
    assertThat(alarm.getExternalCompletedAt()).isNull();
    makeService(2).deliverPending();
    verify(smtp, times(1)).sendSimpleMessage(anyString(), anyString(), anyString());
    makeService(1440).deliverPending();
    assertThat(alarm.getDeliveryStatus()).isEqualTo("DELIVERED");
    verify(mail, times(1)).sendInternalMail(anyInt(), anyInt(), anyString(), anyString());
    verify(smtp, times(2)).sendSimpleMessage(anyString(), anyString(), anyString());
  }

  @Test
  void legacyRowsRequireExplicitRetry() {
    alarm.setDeliveryStatus("REVIEW_REQUIRED");
    service.deliverPending();
    verifyNoInteractions(mail);
    service.retry(42, 1);
    service.deliverPending();
    assertThat(alarm.getDeliveryStatus()).isEqualTo("DELIVERED");
    service.retry(42, 1);
    service.deliverPending();
    verify(mail, times(1)).sendInternalMail(anyInt(), anyInt(), anyString(), anyString());
  }

  @Test
  void advisorRequiresLiveManageGrantAndViewersAreNotRecipients() {
    when(users.findByIdTenantAndHomeTenantReadOnlyFalse(42)).thenReturn(List.of());
    when(users.findById(7)).thenReturn(Optional.of(owner));
    when(access.findByIdUserAndIdTenant(7, 42))
        .thenReturn(Optional.of(new TenantAccess(7, 42, TenantAccessLevel.MANAGE)));
    assertThat(service.recipient(42)).isSameAs(owner);
    when(access.findByIdUserAndIdTenant(7, 42))
        .thenReturn(Optional.of(new TenantAccess(7, 42, TenantAccessLevel.READ)));
    assertThatThrownBy(() -> service.recipient(42)).hasMessageContaining("No authorized");
  }

  @Test
  void liveLeasePreventsAnotherWorkerAndExpiredLeaseRecovers() {
    alarm.setDeliveryStatus("SENDING");
    alarm.setDeliveryLeaseToken("old");
    alarm.setDeliveryLeaseUntil(LocalDateTime.ofInstant(origin.plusSeconds(300), ZoneOffset.UTC));
    service.deliverPending();
    verifyNoInteractions(mail);
    makeService(6).deliverPending();
    assertThat(alarm.getDeliveryStatus()).isEqualTo("DELIVERED");
  }

  @Test
  void exhaustedFailuresStopAndForeignTenantCannotRetry() {
    alarm.setDeliveryAttempts(7);
    when(users.findByIdTenantAndHomeTenantReadOnlyFalse(42)).thenReturn(List.of());
    service.deliverPending();
    assertThat(alarm.getDeliveryStatus()).isEqualTo("FAILED");
    assertThat(alarm.getNextAttemptAt()).isNull();
    assertThatThrownBy(() -> service.retry(43, 1)).isInstanceOf(grafiosch.exceptions.ResourceNotFoundException.class);
  }

  @Test
  void deletingStrategyCancelsAndFeatureSwitchPauses() {
    features.setAlert(false);
    service.deliverPending();
    verifyNoInteractions(mail);
    features.setAlert(true);
    when(strategies.existsById(3)).thenReturn(false);
    service.deliverPending();
    assertThat(alarm.getDeliveryStatus()).isEqualTo("CANCELLED");
    verifyNoInteractions(mail);
  }
}
