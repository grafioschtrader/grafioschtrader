package grafiosch.gtnet.handler.impl;

import java.util.Date;

import org.springframework.stereotype.Component;

import grafiosch.entities.GTNetMessage;
import grafiosch.gtnet.GNetCoreMessageCode;
import grafiosch.gtnet.GTNetMessageCode;
import grafiosch.gtnet.MessageCategory;
import grafiosch.gtnet.SendReceivedType;
import grafiosch.gtnet.handler.AbstractGTNetMessageHandler;
import grafiosch.gtnet.handler.GTNetMessageContext;
import grafiosch.gtnet.handler.HandlerResult;
import grafiosch.gtnet.m2m.model.MessageEnvelope;

/**
 * Handler for GT_NET_PING messages.
 *
 * Ping is a lightweight health check that always returns an immediate response. It does not require authentication and
 * is not persisted.
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
    GTNetMessage responseMsg = new GTNetMessage(null, new Date(), SendReceivedType.ANSWER.getValue(), null,
        GNetCoreMessageCode.GT_NET_PING.getValue(), null, null);

    MessageEnvelope response = new MessageEnvelope(context.getMyGTNet(), responseMsg);
    return new HandlerResult.ImmediateResponse<>(response);
  }
}
