package grafiosch.gtnet.handler.impl;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import grafiosch.entities.GTNetMessage;
import grafiosch.gtnet.GNetCoreMessageCode;
import grafiosch.gtnet.GTNetMessageCode;
import grafiosch.gtnet.handler.AbstractResponseHandler;
import grafiosch.gtnet.handler.GTNetMessageContext;

/**
 * Handler for GT_NET_DATA_REQUEST_REJECTED_S messages.
 *
 * Processes rejection responses to our data exchange requests. The remote server has declined our request
 * to exchange data (last prices, historical data, etc.).
 *
 * The optional message field may contain the reason for rejection.
 */
@Component
public class DataRequestRejectedHandler extends AbstractResponseHandler {

  private static final Logger log = LoggerFactory.getLogger(DataRequestRejectedHandler.class);

  @Override
  public GTNetMessageCode getSupportedMessageCode() {
    return GNetCoreMessageCode.GT_NET_DATA_REQUEST_REJECTED_S;
  }

  @Override
  protected void processResponseSideEffects(GTNetMessageContext context, GTNetMessage storedMessage) {
    String reason = context.getMessage();
    log.info("Data request rejected by {} - reason: {}, message stored with id {}",
        context.getSourceDomain(), reason != null ? reason : "(none)", storedMessage.getIdGtNetMessage());
    // Future: Could update GTNetEntity state to reflect rejection
  }
}
