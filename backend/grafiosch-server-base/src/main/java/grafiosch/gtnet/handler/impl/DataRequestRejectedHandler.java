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
import grafiosch.gtnet.handler.AbstractDataResponseHandler;
import grafiosch.gtnet.handler.GTNetMessageContext;

/**
 * Handler for GT_NET_DATA_REQUEST_REJECTED_S messages.
 *
 * <p>
 * Processes rejection responses to our data exchange requests. The remote server has declined our request to exchange
 * data, and the optional message field may carry the reason.
 * </p>
 *
 * <p>
 * A rejection used to be logged and otherwise discarded, so both sides went on offering another data request as if
 * nothing had been decided. It now ends the grant for the kinds the request named, which is the same state the
 * answering side reaches — whether it rejected automatically or an administrator did it by hand.
 * </p>
 */
@Component
public class DataRequestRejectedHandler extends AbstractDataResponseHandler {

  private static final Logger log = LoggerFactory.getLogger(DataRequestRejectedHandler.class);

  @Override
  public GTNetMessageCode getSupportedMessageCode() {
    return GNetCoreMessageCode.GT_NET_DATA_REQUEST_REJECTED_S;
  }

  @Override
  protected void processResponseSideEffects(GTNetMessageContext context, GTNetMessage storedMessage) {
    String reason = context.getMessage();
    log.info("Data request rejected by {} - reason: {}, message stored with id {}", context.getSourceDomain(),
        reason != null ? reason : "(none)", storedMessage.getIdGtNetMessage());

    GTNet remoteGTNet = context.getRemoteGTNet();
    if (remoteGTNet == null) {
      log.warn("No remote GTNet found for reject response from {}", context.getSourceDomain());
      return;
    }

    Set<IExchangeKindType> rejectedKinds = getRespondedEntityKinds(context);
    if (rejectedKinds.isEmpty()) {
      log.warn("No entity kinds found in original request for reject from {}", context.getSourceDomain());
      return;
    }

    boolean changed = false;
    for (IExchangeKindType kind : rejectedKinds) {
      changed |= grantService.clearGrant(remoteGTNet, kind);
    }
    if (changed) {
      saveRemoteGTNet(remoteGTNet);
      log.info("Ended the exchange grant for {} entity kinds with {}", rejectedKinds.size(), context.getSourceDomain());
    }
  }
}
