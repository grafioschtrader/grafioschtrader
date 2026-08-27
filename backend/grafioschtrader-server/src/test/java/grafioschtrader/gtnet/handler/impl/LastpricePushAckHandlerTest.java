package grafioschtrader.gtnet.handler.impl;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import java.time.LocalDateTime;
import java.util.List;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.test.util.ReflectionTestUtils;

import grafiosch.entities.GTNet;
import grafiosch.entities.GTNetMessage;
import grafiosch.gtnet.MessageCategory;
import grafiosch.gtnet.SendReceivedType;
import grafiosch.gtnet.handler.GTNetMessageContext;
import grafiosch.gtnet.handler.HandlerResult;
import grafiosch.gtnet.m2m.model.MessageEnvelope;
import grafiosch.repository.GTNetMessageJpaRepository;
import grafioschtrader.gtnet.GTNetMessageCodeType;
import grafioschtrader.gtnet.model.msg.LastpriceExchangeMsg;
import tools.jackson.databind.ObjectMapper;

/**
 * The intraday push acknowledgement (code 63) had no handler bean, so the reply to a push was answered with NO_HANDLER
 * instead of being consumed. These tests pin the handler down to the same contract the historyquote ack already has.
 */
class LastpricePushAckHandlerTest {

  private final GTNetMessageJpaRepository messageRepository = mock(GTNetMessageJpaRepository.class);
  private final ObjectMapper objectMapper = new ObjectMapper();
  private LastpricePushAckHandler handler;

  @BeforeEach
  void setUp() {
    handler = new LastpricePushAckHandler();
    ReflectionTestUtils.setField(handler, "gtNetMessageJpaRepository", messageRepository);
    ReflectionTestUtils.setField(handler, "objectMapper", objectMapper);
    when(messageRepository.saveMsg(any())).thenAnswer(invocation -> invocation.getArgument(0));
  }

  @Test
  void answersForTheIntradayPushAcknowledgementAsAResponse() {
    assertThat(handler.getSupportedMessageCode()).isEqualTo(GTNetMessageCodeType.GT_NET_LASTPRICE_PUSH_ACK_S);
    assertThat(handler.getCategory()).isEqualTo(MessageCategory.RESPONSE);
  }

  @Test
  void storesTheAcknowledgementAndNeedsNoReply() throws Exception {
    LastpriceExchangeMsg ack = LastpriceExchangeMsg.forPushAck(17);

    var result = handler.handle(context(objectMapper.valueToTree(ack)));

    assertThat(result).isInstanceOf(HandlerResult.NoResponseNeeded.class);
    var stored = org.mockito.ArgumentCaptor.forClass(GTNetMessage.class);
    org.mockito.Mockito.verify(messageRepository).saveMsg(stored.capture());
    assertThat(stored.getValue().getMessageCodeValue())
        .isEqualTo(GTNetMessageCodeType.GT_NET_LASTPRICE_PUSH_ACK_S.getValue());
    assertThat(stored.getValue().getSendRecv()).isEqualTo(SendReceivedType.RECEIVED);
  }

  @Test
  void toleratesAnAcknowledgementWithoutPayload() throws Exception {
    assertThat(handler.handle(context(null))).isInstanceOf(HandlerResult.NoResponseNeeded.class);
  }

  private GTNetMessageContext context(tools.jackson.databind.JsonNode payload) {
    GTNet myGTNet = new GTNet();
    myGTNet.setIdGtNet(1);
    GTNet remote = new GTNet();
    remote.setIdGtNet(2);
    remote.setDomainRemoteName("https://remote");
    MessageEnvelope request = new MessageEnvelope();
    request.messageCode = GTNetMessageCodeType.GT_NET_LASTPRICE_PUSH_ACK_S.getValue();
    request.timestamp = LocalDateTime.now();
    request.sourceDomain = remote.getDomainRemoteName();
    request.payload = payload;
    return new GTNetMessageContext(myGTNet, remote, request, List.of(), objectMapper);
  }
}
