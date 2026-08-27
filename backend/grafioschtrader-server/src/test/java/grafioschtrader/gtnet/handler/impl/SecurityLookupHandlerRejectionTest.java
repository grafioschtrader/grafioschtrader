package grafioschtrader.gtnet.handler.impl;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import java.time.LocalDateTime;
import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.test.util.ReflectionTestUtils;

import grafiosch.entities.GTNet;
import grafiosch.entities.GTNetEntity;
import grafiosch.entities.GTNetMessage;
import grafiosch.gtnet.AcceptRequestTypes;
import grafiosch.gtnet.GTNetServerStateTypes;
import grafiosch.gtnet.handler.GTNetMessageContext;
import grafiosch.gtnet.handler.HandlerResult;
import grafiosch.gtnet.m2m.model.MessageEnvelope;
import grafiosch.repository.GTNetMessageJpaRepository;
import grafioschtrader.gtnet.GTNetExchangeKindType;
import grafioschtrader.gtnet.GTNetMessageCodeType;
import grafioschtrader.gtnet.model.msg.SecurityLookupMsg;
import tools.jackson.databind.ObjectMapper;

/**
 * A refused lookup used to leave the handler as a transport error, so GT_NET_SECURITY_LOOKUP_REJECTED_S was never
 * produced and the requester could not tell a refusal apart from a lookup that found nothing.
 */
class SecurityLookupHandlerRejectionTest {

  private final GTNetMessageJpaRepository messageRepository = mock(GTNetMessageJpaRepository.class);
  private final ObjectMapper objectMapper = new ObjectMapper();
  private SecurityLookupHandler handler;

  @BeforeEach
  void setUp() {
    handler = new SecurityLookupHandler();
    ReflectionTestUtils.setField(handler, "gtNetMessageJpaRepository", messageRepository);
    ReflectionTestUtils.setField(handler, "objectMapper", objectMapper);
    AtomicInteger ids = new AtomicInteger(10);
    when(messageRepository.saveMsg(any())).thenAnswer(invocation -> {
      GTNetMessage message = invocation.getArgument(0);
      message.setIdGtNetMessage(ids.incrementAndGet());
      return message;
    });
  }

  @Test
  void answersARefusedLookupWithTheRejectionCode() throws Exception {
    var result = handler.handle(context(AcceptRequestTypes.AC_CLOSED));

    MessageEnvelope envelope = ((HandlerResult.ImmediateResponse<GTNetMessage, MessageEnvelope>) result).response();
    assertThat(envelope.messageCode).isEqualTo(GTNetMessageCodeType.GT_NET_SECURITY_LOOKUP_REJECTED_S.getValue());
    assertThat(envelope.message).contains("not accepting security metadata requests");
  }

  @Test
  void answersARefusedLookupWhenNoMetadataEntityIsRegistered() throws Exception {
    var result = handler.handle(context(null));

    MessageEnvelope envelope = ((HandlerResult.ImmediateResponse<GTNetMessage, MessageEnvelope>) result).response();
    assertThat(envelope.messageCode).isEqualTo(GTNetMessageCodeType.GT_NET_SECURITY_LOOKUP_REJECTED_S.getValue());
  }

  /**
   * @param acceptRequest accept mode of the security-metadata entity, or null to register no such entity at all
   */
  private GTNetMessageContext context(AcceptRequestTypes acceptRequest) {
    GTNet myGTNet = new GTNet();
    myGTNet.setIdGtNet(1);
    myGTNet.setDomainRemoteName("https://local");
    if (acceptRequest != null) {
      GTNetEntity metadataEntity = new GTNetEntity();
      metadataEntity.setEntityKindValue(GTNetExchangeKindType.SECURITY_METADATA.getValue());
      metadataEntity.setAcceptRequest(acceptRequest);
      metadataEntity.setServerState(GTNetServerStateTypes.SS_OPEN);
      myGTNet.getGtNetEntities().add(metadataEntity);
    }
    GTNet remote = new GTNet();
    remote.setIdGtNet(2);
    remote.setDomainRemoteName("https://remote");
    MessageEnvelope request = new MessageEnvelope();
    request.messageCode = GTNetMessageCodeType.GT_NET_SECURITY_LOOKUP_SEL_C.getValue();
    request.timestamp = LocalDateTime.now();
    request.sourceDomain = remote.getDomainRemoteName();
    SecurityLookupMsg lookup = new SecurityLookupMsg();
    lookup.isin = "CH001";
    lookup.currency = "CHF";
    request.payload = objectMapper.valueToTree(lookup);
    return new GTNetMessageContext(myGTNet, remote, request, List.of(), objectMapper);
  }
}
