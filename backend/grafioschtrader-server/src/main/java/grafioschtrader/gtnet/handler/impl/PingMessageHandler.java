package grafioschtrader.gtnet.handler.impl;

import java.util.Date;

import org.springframework.stereotype.Component;

import grafioschtrader.entities.GTNetMessage;
import grafioschtrader.gtnet.GTNetMessageCodeType;
import grafioschtrader.gtnet.MessageCategory;
import grafioschtrader.gtnet.SendReceivedType;
import grafioschtrader.gtnet.handler.AbstractGTNetMessageHandler;
import grafioschtrader.gtnet.handler.GTNetMessageContext;
import grafioschtrader.gtnet.handler.HandlerResult;
import grafioschtrader.gtnet.m2m.model.MessageEnvelope;

/**
 * Handler for GT_NET_PING messages.
 *
 * Ping is a lightweight health check that always returns an immediate response. It does not require authentication and
 * is not persisted.
 */
@Component
public class PingMessageHandler extends AbstractGTNetMessageHandler {

  @Override
  public GTNetMessageCodeType getSupportedMessageCode() {
    return GTNetMessageCodeType.GT_NET_PING;
  }

  @Override
  public MessageCategory getCategory() {
    return MessageCategory.REQUEST;
  }

  @Override
  public HandlerResult handle(GTNetMessageContext context) {
    // Ping is special: no persistence, immediate response
    GTNetMessage responseMsg = new GTNetMessage(null, new Date(), SendReceivedType.ANSWER.getValue(), null,
        GTNetMessageCodeType.GT_NET_PING.getValue(), null, null);

    MessageEnvelope response = new MessageEnvelope(context.getMyGTNet(), responseMsg);
    return new HandlerResult.ImmediateResponse(response);
  }
}
