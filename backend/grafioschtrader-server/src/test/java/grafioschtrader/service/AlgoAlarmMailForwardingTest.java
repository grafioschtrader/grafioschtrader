package grafioschtrader.service;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

import java.util.EnumSet;
import java.util.List;
import java.util.Optional;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;
import org.springframework.context.MessageSource;
import org.springframework.test.util.ReflectionTestUtils;

import grafiosch.BaseConstants;
import grafiosch.dto.MailSendForwardDefaultBase;
import grafiosch.entities.GTNet;
import grafiosch.entities.MailSettingForward;
import grafiosch.entities.User;
import grafiosch.repository.GTNetJpaRepository;
import grafiosch.repository.GlobalparametersJpaRepository;
import grafiosch.repository.MailSettingForwardJpaRepository;
import grafiosch.repository.UserJpaRepository;
import grafiosch.service.MailExternalService;
import grafiosch.service.SendMailInternalExternalService;
import grafiosch.types.MessageTargetType;
import grafioschtrader.types.MailSendForwardDefault;
import grafioschtrader.types.MessageGTComType;

/** Verifies that algo alarms use the registered defaults and the recipient's selected delivery channels. */
@DisplayName("Algo alarm mail forwarding")
class AlgoAlarmMailForwardingTest {

  private final MailSettingForwardJpaRepository settings = mock(MailSettingForwardJpaRepository.class);
  private final MailExternalService externalMail = mock(MailExternalService.class);
  private final SendMailInternalExternalService delivery = spy(new SendMailInternalExternalService());

  @BeforeEach
  void setUp() {
    MailSendForwardDefault.initialize();
    ReflectionTestUtils.setField(delivery, "mailSettingForwardJpaRepository", settings);
    ReflectionTestUtils.setField(delivery, "mailExternalService", externalMail);
    ReflectionTestUtils.setField(delivery, "messagesSource", mock(MessageSource.class));

    UserJpaRepository users = mock(UserJpaRepository.class);
    User user = mock(User.class);
    when(user.getUsername()).thenReturn("alarm@example.test");
    when(user.getLocaleStr()).thenReturn("en");
    when(users.findById(17)).thenReturn(Optional.of(user));
    ReflectionTestUtils.setField(delivery, "userJpaRepository", users);

    GlobalparametersJpaRepository parameters = mock(GlobalparametersJpaRepository.class);
    when(parameters.getGTNetMyEntryID()).thenReturn(1);
    ReflectionTestUtils.setField(delivery, "globalparametersJpaRepository", parameters);
    GTNetJpaRepository peers = mock(GTNetJpaRepository.class);
    GTNet peer = mock(GTNet.class);
    when(peer.getDomainRemoteName()).thenReturn("https://instance.example.test");
    when(peers.findById(1)).thenReturn(Optional.of(peer));
    ReflectionTestUtils.setField(delivery, "gtNetJpaRepository", peers);

    doReturn(99).when(delivery).sendInternalMail(BaseConstants.SYSTEM_ID_USER, 17, "Alarm", "Details");
  }

  @Test
  @DisplayName("Ordinary users can select all three delivery channels without user redirection")
  void exposesAlarmConfigurationForOrdinaryUsers() {
    var defaults = new MailSendForwardDefaultBase(List.of(), false);
    var alarm = defaults.mailSendForwardDefaultMapForUser.get(MessageGTComType.USER_ALGO_ALARM_TRIGGERED);
    assertNotNull(alarm);
    assertEquals(MessageTargetType.INTERNAL_MAIL, alarm.messageTargetDefaultType);
    assertEquals(EnumSet.of(MessageTargetType.INTERNAL_MAIL, MessageTargetType.EXTERNAL_MAIL,
        MessageTargetType.INTERNAL_AND_EXTERNAL_MAIL), alarm.mttPossibleTypeSet);
    assertFalse(alarm.canRedirect);
  }

  @Test
  @DisplayName("An alarm without a forwarding setting is delivered internally")
  void defaultsToInternalDelivery() throws Exception {
    when(settings.findByIdUserAndMessageComType(17, (byte) 6)).thenReturn(Optional.empty());
    assertEquals(99, sendAlarm());
    verify(delivery).sendInternalMail(BaseConstants.SYSTEM_ID_USER, 17, "Alarm", "Details");
    verifyNoInteractions(externalMail);
  }

  @ParameterizedTest
  @EnumSource(value = MessageTargetType.class, names = { "INTERNAL_MAIL", "EXTERNAL_MAIL",
      "INTERNAL_AND_EXTERNAL_MAIL" })
  @DisplayName("An alarm follows the recipient's saved delivery channel")
  void respectsSavedDeliveryChannel(MessageTargetType target) throws Exception {
    MailSettingForward setting = new MailSettingForward();
    setting.setMessageTargetType(target);
    when(settings.findByIdUserAndMessageComType(17, (byte) 6)).thenReturn(Optional.of(setting));

    Integer internalId = sendAlarm();
    boolean internal = target != MessageTargetType.EXTERNAL_MAIL;
    assertEquals(internal ? Integer.valueOf(99) : null, internalId);
    verify(delivery, times(internal ? 1 : 0)).sendInternalMail(BaseConstants.SYSTEM_ID_USER, 17, "Alarm", "Details");
    if (target == MessageTargetType.INTERNAL_MAIL) {
      verifyNoInteractions(externalMail);
    } else {
      verify(externalMail).sendSimpleMessageAsync(eq(new String[] { "alarm@example.test" }), eq("GT: Alarm"),
          contains("Details"));
    }
  }

  private Integer sendAlarm() throws Exception {
    return delivery.sendMailInternAndOrExternal(BaseConstants.SYSTEM_ID_USER, 17, "Alarm", "Details",
        MessageGTComType.USER_ALGO_ALARM_TRIGGERED);
  }
}
