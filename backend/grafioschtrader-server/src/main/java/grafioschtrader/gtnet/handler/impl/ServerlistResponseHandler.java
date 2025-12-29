package grafioschtrader.gtnet.handler.impl;

import java.util.Set;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import grafioschtrader.entities.GTNetMessage;
import grafioschtrader.gtnet.GTNetMessageCodeType;
import grafioschtrader.gtnet.handler.AbstractResponseHandler;
import grafioschtrader.gtnet.handler.GTNetMessageContext;

/**
 * Handler for server list response messages (accept and reject).
 *
 * Processes both GT_NET_UPDATE_SERVERLIST_ACCEPT_S and GT_NET_UPDATE_SERVERLIST_REJECTED_S responses. These are simple
 * acknowledgment messages that don't require state changes - they just need to be stored for visibility.
 *
 * When accepted, the response may include a list of known servers in the payload (if the remote has spreadCapability).
 */
@Component
public class ServerlistResponseHandler extends AbstractResponseHandler {

  private static final Logger log = LoggerFactory.getLogger(ServerlistResponseHandler.class);

  @Override
  public GTNetMessageCodeType getSupportedMessageCode() {
    return GTNetMessageCodeType.GT_NET_UPDATE_SERVERLIST_ACCEPT_S;
  }

  @Override
  public Set<GTNetMessageCodeType> getSupportedMessageCodes() {
    return Set.of(GTNetMessageCodeType.GT_NET_UPDATE_SERVERLIST_ACCEPT_S,
        GTNetMessageCodeType.GT_NET_UPDATE_SERVERLIST_REJECTED_S);
  }

  @Override
  protected void processResponseSideEffects(GTNetMessageContext context, GTNetMessage storedMessage) {
    GTNetMessageCodeType messageCode = context.getMessageCode();

    if (messageCode == GTNetMessageCodeType.GT_NET_UPDATE_SERVERLIST_ACCEPT_S) {
      log.info("Server list request accepted by {} - message stored with id {}", context.getSourceDomain(),
          storedMessage.getIdGtNetMessage());
      // TODO: Process server list payload if present (context.getPayload())
    } else {
      log.info("Server list request rejected by {} - message stored with id {}", context.getSourceDomain(),
          storedMessage.getIdGtNetMessage());
    }
  }
}
