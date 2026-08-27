package grafiosch.gtnet;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.time.LocalDate;

import org.junit.jupiter.api.Test;

import grafiosch.entities.GTNet;
import grafiosch.entities.GTNetConfig;
import grafiosch.gtnet.handler.GTNetMessageHandlerRegistry;
import grafiosch.repository.GTNetConfigJpaRepository;

/**
 * Guards which messages {@link GTNetRequestBudgetService} charges against the daily request budget and how it reacts to
 * the outcome of the charging statement. The rollover itself lives in SQL and is covered by the two-peer suite.
 */
class GTNetRequestBudgetServiceTest {

  private static final byte DATA_REQUEST = GNetCoreMessageCode.GT_NET_DATA_REQUEST_SEL_RR_C.getValue();
  private static final Integer ID_GT_NET = 7;

  private final GTNetConfigJpaRepository configRepository = mock(GTNetConfigJpaRepository.class);
  private final GTNetMessageHandlerRegistry handlerRegistry = mock(GTNetMessageHandlerRegistry.class);
  private final GTNetRequestBudgetService budgetService = new GTNetRequestBudgetService(configRepository,
      handlerRegistry);

  GTNetRequestBudgetServiceTest() {
    when(handlerRegistry.getCategory(any(Byte.class))).thenReturn(MessageCategory.REQUEST);
  }

  @Test
  void servesARequestThatStillFitsInTheBudget() {
    charging(1);
    assertThat(budgetService.chargeIncoming(remote(), local(100), DATA_REQUEST)).isTrue();
  }

  @Test
  void refusesARequestOnceTheBudgetIsUsedUp() {
    charging(0);
    assertThat(budgetService.chargeIncoming(remote(), local(100), DATA_REQUEST)).isFalse();
  }

  @Test
  void publishesTheChargedCountSoARuleCanReadIt() {
    charging(1);
    when(configRepository.findChargedIncomingCount(eq(ID_GT_NET), any(LocalDate.class))).thenReturn(42);

    GTNet remote = remote();
    budgetService.chargeIncoming(remote, local(100), DATA_REQUEST);

    assertThat(remote.getGtNetConfig().getDailyRequestLimitCount()).isEqualTo(42);
    assertThat(remote.getGtNetConfig().getDailyRequestLimitDate()).isEqualTo(LocalDate.now(java.time.ZoneOffset.UTC));
  }

  @Test
  void treatsAMissingLimitAsUnlimited() {
    charging(1);
    budgetService.chargeIncoming(remote(), local(null), DATA_REQUEST);
    verify(configRepository).chargeIncomingRequest(eq(ID_GT_NET), any(LocalDate.class), eq(Integer.MAX_VALUE));
  }

  @Test
  void neverChargesPingHandshakeOrTokenRefresh() {
    for (GNetCoreMessageCode exempt : new GNetCoreMessageCode[] { GNetCoreMessageCode.GT_NET_PING,
        GNetCoreMessageCode.GT_NET_FIRST_HANDSHAKE_SEL_RR_S, GNetCoreMessageCode.GT_NET_TOKEN_REFRESH_SEL_RR_C }) {
      assertThat(budgetService.chargeIncoming(remote(), local(1), exempt.getValue())).isTrue();
    }
    verify(configRepository, never()).chargeIncomingRequest(any(), any(), anyInt());
  }

  @Test
  void neverChargesResponsesOrAnnouncements() {
    when(handlerRegistry.getCategory(any(Byte.class))).thenReturn(MessageCategory.ANNOUNCEMENT);
    assertThat(budgetService.chargeIncoming(remote(), local(1), DATA_REQUEST)).isTrue();

    when(handlerRegistry.getCategory(any(Byte.class))).thenReturn(MessageCategory.RESPONSE);
    assertThat(budgetService.chargeIncoming(remote(), local(1), DATA_REQUEST)).isTrue();

    verify(configRepository, never()).chargeIncomingRequest(any(), any(), anyInt());
  }

  @Test
  void letsAPeerWithoutAConfigurationRowPass() {
    assertThat(budgetService.chargeIncoming(null, local(1), DATA_REQUEST)).isTrue();
    assertThat(budgetService.chargeIncoming(new GTNet(), local(1), DATA_REQUEST)).isTrue();
    verify(configRepository, never()).chargeIncomingRequest(any(), any(), anyInt());
  }

  @Test
  void measuresAnOutgoingRequestAgainstTheLimitThePeerPublished() {
    when(configRepository.chargeOutgoingRequest(eq(ID_GT_NET), any(LocalDate.class), anyInt())).thenReturn(1);

    GTNet target = remote();
    target.setDailyRequestLimit(250);
    assertThat(budgetService.chargeOutgoing(target, DATA_REQUEST)).isTrue();
    verify(configRepository).chargeOutgoingRequest(eq(ID_GT_NET), any(LocalDate.class), eq(250));
  }

  @Test
  void suppressesAnOutgoingRequestOnceThePeersBudgetIsUsedUp() {
    when(configRepository.chargeOutgoingRequest(eq(ID_GT_NET), any(LocalDate.class), anyInt())).thenReturn(0);

    GTNet target = remote();
    target.setDailyRequestLimit(250);
    assertThat(budgetService.chargeOutgoing(target, DATA_REQUEST)).isFalse();
  }

  private void charging(int affectedRows) {
    when(configRepository.chargeIncomingRequest(eq(ID_GT_NET), any(LocalDate.class), anyInt()))
        .thenReturn(affectedRows);
  }

  private GTNet remote() {
    GTNetConfig config = new GTNetConfig();
    config.setIdGtNet(ID_GT_NET);
    GTNet remote = new GTNet();
    remote.setIdGtNet(ID_GT_NET);
    remote.setDomainRemoteName("peer.example.org");
    remote.setGtNetConfig(config);
    return remote;
  }

  private GTNet local(Integer dailyRequestLimit) {
    GTNet myGTNet = new GTNet();
    myGTNet.setDailyRequestLimit(dailyRequestLimit);
    return myGTNet;
  }
}
