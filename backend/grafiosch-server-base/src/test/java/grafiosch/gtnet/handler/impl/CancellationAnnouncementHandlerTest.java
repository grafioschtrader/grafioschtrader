package grafiosch.gtnet.handler.impl;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyByte;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

import org.junit.jupiter.api.Test;
import org.springframework.test.util.ReflectionTestUtils;

import grafiosch.entities.GTNet;
import grafiosch.entities.GTNetEntity;
import grafiosch.entities.GTNetMessage;
import grafiosch.gtnet.AcceptRequestTypes;
import grafiosch.gtnet.GNetCoreMessageCode;
import grafiosch.gtnet.GTNetServerOnlineStatusTypes;
import grafiosch.gtnet.GTNetServerStateTypes;
import grafiosch.gtnet.SendReceivedType;
import grafiosch.gtnet.handler.GTNetMessageContext;
import grafiosch.gtnet.handler.HandlerResult;
import grafiosch.gtnet.m2m.model.MessageEnvelope;
import grafiosch.repository.GTNetJpaRepository;
import grafiosch.repository.GTNetMaintenanceWindowJpaRepository;
import grafiosch.repository.GTNetMessageJpaRepository;
import tools.jackson.databind.ObjectMapper;

class CancellationAnnouncementHandlerTest {

  private GTNetMessageJpaRepository messageRepository = mock(GTNetMessageJpaRepository.class);
  private GTNetJpaRepository gtNetRepository = mock(GTNetJpaRepository.class);
  private GTNetMaintenanceWindowJpaRepository windowRepository = mock(GTNetMaintenanceWindowJpaRepository.class);

  @Test
  void maintenanceCancellationDropsTheWindowsOfTheCorrelatedAnnouncement() throws Exception {
    CancellationAnnouncementHandler handler = handler();
    GTNet remote = remote();
    correlate(GNetCoreMessageCode.GT_NET_MAINTENANCE_ALL_C, 41);

    var result = handler.handle(context(remote, GNetCoreMessageCode.GT_NET_MAINTENANCE_CANCEL_ALL_C, 900));

    assertThat(result).isInstanceOf(HandlerResult.NoResponseNeeded.class);
    // Whether a remote is under maintenance is read from its window rows and nowhere else, so deleting them is the
    // whole of the cancellation. The per-kind server state is deliberately not touched.
    verify(windowRepository).deleteByIdGtNetMessage(41);
    assertThat(remote.getGtNetEntities().getFirst().getServerState()).isEqualTo(GTNetServerStateTypes.SS_OPEN);
  }

  @Test
  void discontinuationCancellationRevivesAPeerThatIsAlreadyOutOfService() throws Exception {
    CancellationAnnouncementHandler handler = handler();
    GTNet remote = remote();
    remote.setCloseStartDate(LocalDate.now().plusDays(30));
    remote.setServerOnline(GTNetServerOnlineStatusTypes.SOS_OUT_OF_SERVICE);
    correlate(GNetCoreMessageCode.GT_NET_OPERATION_DISCONTINUED_ALL_C, 42);

    var result = handler.handle(context(remote, GNetCoreMessageCode.GT_NET_OPERATION_DISCONTINUED_CANCEL_ALL_C, 900));

    assertThat(result).isInstanceOf(HandlerResult.NoResponseNeeded.class);
    assertThat(remote.getCloseStartDate()).isNull();
    // SOS_UNKNOWN rather than SOS_ONLINE: the next status check decides whether the peer is really reachable again.
    assertThat(remote.getServerOnline()).isEqualTo(GTNetServerOnlineStatusTypes.SOS_UNKNOWN);
    verify(windowRepository, never()).deleteByIdGtNetMessage(any());
  }

  @Test
  void discontinuationCancellationDropsAShutdownThatHasNotTakenEffectYet() throws Exception {
    CancellationAnnouncementHandler handler = handler();
    GTNet remote = remote();
    remote.setCloseStartDate(LocalDate.now().plusDays(30));
    remote.setServerOnline(GTNetServerOnlineStatusTypes.SOS_ONLINE);
    correlate(GNetCoreMessageCode.GT_NET_OPERATION_DISCONTINUED_ALL_C, 43);

    handler.handle(context(remote, GNetCoreMessageCode.GT_NET_OPERATION_DISCONTINUED_CANCEL_ALL_C, 900));

    assertThat(remote.getCloseStartDate()).isNull();
    assertThat(remote.getServerOnline()).isEqualTo(GTNetServerOnlineStatusTypes.SOS_ONLINE);
  }

  @Test
  void rejectsCancellationWhoseOriginalCannotBeCorrelated() throws Exception {
    CancellationAnnouncementHandler handler = handler();
    when(messageRepository.findByIdGtNetAndSendRecvAndIdSourceGtNetMessageAndMessageCode(any(), anyByte(), any(),
        anyByte())).thenReturn(Optional.empty());

    var result = handler.handle(context(remote(), GNetCoreMessageCode.GT_NET_MAINTENANCE_CANCEL_ALL_C, 900));

    assertThat(result).isInstanceOf(HandlerResult.ProcessingError.class);
  }

  private void correlate(GNetCoreMessageCode originalCode, int originalId) {
    GTNetMessage original = new GTNetMessage();
    original.setIdGtNetMessage(originalId);
    when(messageRepository.findByIdGtNetAndSendRecvAndIdSourceGtNetMessageAndMessageCode(7,
        SendReceivedType.RECEIVED.getValue(), 900, originalCode.getValue())).thenReturn(Optional.of(original));
    when(messageRepository.saveMsg(any())).thenAnswer(invocation -> invocation.getArgument(0));
    when(messageRepository.save(any())).thenAnswer(invocation -> invocation.getArgument(0));
  }

  private CancellationAnnouncementHandler handler() {
    CancellationAnnouncementHandler handler = new CancellationAnnouncementHandler();
    ReflectionTestUtils.setField(handler, "gtNetMessageJpaRepository", messageRepository);
    ReflectionTestUtils.setField(handler, "gtNetMessageJpaRepositoryFull", messageRepository);
    ReflectionTestUtils.setField(handler, "gtNetJpaRepository", gtNetRepository);
    ReflectionTestUtils.setField(handler, "gtNetMaintenanceWindowJpaRepository", windowRepository);
    return handler;
  }

  private GTNetMessageContext context(GTNet remote, GNetCoreMessageCode code, Integer originalId) {
    MessageEnvelope request = new MessageEnvelope();
    request.messageCode = code.getValue();
    request.idOriginalMessage = originalId;
    request.timestamp = LocalDateTime.now();
    return new GTNetMessageContext(local(), remote, request, List.of(), new ObjectMapper());
  }

  private GTNet local() {
    GTNet local = new GTNet();
    local.setIdGtNet(1);
    local.setDomainRemoteName("https://local");
    return local;
  }

  private GTNet remote() {
    GTNet remote = new GTNet();
    remote.setIdGtNet(7);
    GTNetEntity entity = new GTNetEntity();
    entity.setAcceptRequest(AcceptRequestTypes.AC_OPEN);
    entity.setServerState(GTNetServerStateTypes.SS_OPEN);
    remote.setGtNetEntities(new java.util.ArrayList<>(List.of(entity)));
    return remote;
  }
}
