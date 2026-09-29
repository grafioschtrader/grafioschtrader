package grafiosch.gtnet.handler.impl;

import java.util.Set;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

import grafiosch.entities.GTNetMessage;
import grafiosch.gtnet.GNetCoreMessageCode;
import grafiosch.gtnet.GTNetMessageCode;
import grafiosch.gtnet.handler.AbstractResponseHandler;
import grafiosch.gtnet.handler.GTNetMessageContext;
import grafiosch.gtnet.handler.GTNetServerListImporter;

/**
 * Handler for server list response messages (accept and reject).
 *
 * Processes both GT_NET_UPDATE_SERVERLIST_ACCEPT_S and GT_NET_UPDATE_SERVERLIST_REJECTED_S responses.
 *
 * When accepted, the response includes a list of known servers in the payload, which {@link GTNetServerListImporter}
 * merges into the local GTNet table.
 */
@Component
public class ServerlistResponseHandler extends AbstractResponseHandler {

  private static final Logger log = LoggerFactory.getLogger(ServerlistResponseHandler.class);

  @Autowired
  private GTNetServerListImporter serverListImporter;

  @Override
  public GTNetMessageCode getSupportedMessageCode() {
    return GNetCoreMessageCode.GT_NET_UPDATE_SERVERLIST_ACCEPT_S;
  }

  @Override
  public Set<? extends GTNetMessageCode> getSupportedMessageCodes() {
    return Set.of(GNetCoreMessageCode.GT_NET_UPDATE_SERVERLIST_ACCEPT_S,
        GNetCoreMessageCode.GT_NET_UPDATE_SERVERLIST_REJECTED_S);
  }

  @Override
  protected void processResponseSideEffects(GTNetMessageContext context, GTNetMessage storedMessage) {
    byte messageCodeValue = context.getMessageCodeValue();

    if (messageCodeValue == GNetCoreMessageCode.GT_NET_UPDATE_SERVERLIST_ACCEPT_S.getValue()) {
      log.info("Server list request accepted by {} - message stored with id {}", context.getSourceDomain(),
          storedMessage.getIdGtNetMessage());
      serverListImporter.importServerList(context.getMyGTNet(), context.getSourceDomain(), context.getPayload(),
          context.getObjectMapper());
    } else {
      log.info("Server list request rejected by {} - message stored with id {}", context.getSourceDomain(),
          storedMessage.getIdGtNetMessage());
    }
  }
}
