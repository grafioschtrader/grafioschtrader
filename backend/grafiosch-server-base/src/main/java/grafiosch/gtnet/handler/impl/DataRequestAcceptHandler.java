package grafiosch.gtnet.handler.impl;

import java.util.Set;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import grafiosch.entities.GTNet;
import grafiosch.entities.GTNetMessage;
import grafiosch.gtnet.GNetCoreMessageCode;
import grafiosch.gtnet.GTNetMessageCode;
import grafiosch.gtnet.IExchangeKindType;
import grafiosch.gtnet.handler.AbstractDataRequestAcceptHandler;
import grafiosch.gtnet.handler.GTNetMessageContext;

/**
 * Handler for GT_NET_DATA_REQUEST_ACCEPT_S messages.
 *
 * Processes acceptance responses to our data exchange requests. The remote server has accepted our request
 * to exchange data. Updates the local GTNetConfigEntity exchange status to add RECEIVE capability for the
 * accepted entity kinds.
 */
@Component
public class DataRequestAcceptHandler extends AbstractDataRequestAcceptHandler {

  private static final Logger log = LoggerFactory.getLogger(DataRequestAcceptHandler.class);

  @Override
  public GTNetMessageCode getSupportedMessageCode() {
    return GNetCoreMessageCode.GT_NET_DATA_REQUEST_ACCEPT_S;
  }

  @Override
  protected void processResponseSideEffects(GTNetMessageContext context, GTNetMessage storedMessage) {
    log.info("Data request accepted by {} - message stored with id {}",
        context.getSourceDomain(), storedMessage.getIdGtNetMessage());

    GTNet remoteGTNet = context.getRemoteGTNet();
    if (remoteGTNet == null) {
      log.warn("No remote GTNet found for accept response from {}", context.getSourceDomain());
      return;
    }

    Set<IExchangeKindType> acceptedKinds = getAcceptedEntityKinds(context);

    if (acceptedKinds.isEmpty()) {
      log.warn("No entity kinds found in original request for accept from {}", context.getSourceDomain());
      return;
    }

    for (IExchangeKindType kind : acceptedKinds) {
      updateEntityForReceive(remoteGTNet, kind);
    }

    saveRemoteGTNet(remoteGTNet);
    log.info("Created GTNetConfigEntity with RECEIVE capability for {} entity kinds from {}",
        acceptedKinds.size(), context.getSourceDomain());

    triggerExchangeSyncTask();
  }
}
