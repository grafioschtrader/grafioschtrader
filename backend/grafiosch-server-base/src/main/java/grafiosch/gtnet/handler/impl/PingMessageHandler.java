package grafiosch.gtnet.handler.impl;

import org.springframework.stereotype.Component;

import grafiosch.entities.GTNetMessage;
import grafiosch.gtnet.GNetCoreMessageCode;
import grafiosch.gtnet.GTNetMessageCode;
import grafiosch.gtnet.GTNetTime;
import grafiosch.gtnet.MessageCategory;
import grafiosch.gtnet.SendReceivedType;
import grafiosch.gtnet.handler.AbstractGTNetMessageHandler;
import grafiosch.gtnet.handler.GTNetMessageContext;
import grafiosch.gtnet.handler.HandlerResult;
import grafiosch.gtnet.m2m.model.MessageEnvelope;

/**
 * Handler for GT_NET_PING messages.
 *
 * Ping is a lightweight health check that always returns an immediate response and is not persisted. It uses normal
 * GTNet token authentication; only the first-handshake request bypasses incoming-token validation.
 */
@Component
public class PingMessageHandler extends AbstractGTNetMessageHandler {

  @Override
  public GTNetMessageCode getSupportedMessageCode() {
    return GNetCoreMessageCode.GT_NET_PING;
  }

  @Override
  public MessageCategory getCategory() {
    return MessageCategory.REQUEST;
  }

  @Override
  public HandlerResult<GTNetMessage, MessageEnvelope> handle(GTNetMessageContext context) {
    // Ping is special: no persistence, immediate response
    GTNetMessage responseMsg = new GTNetMessage(null, GTNetTime.now(), SendReceivedType.ANSWER.getValue(), null,
        GNetCoreMessageCode.GT_NET_PING.getValue(), null, null);

    MessageEnvelope response = new MessageEnvelope(context.getMyGTNet(), responseMsg);
    return new HandlerResult.ImmediateResponse<>(response);
  }
}
