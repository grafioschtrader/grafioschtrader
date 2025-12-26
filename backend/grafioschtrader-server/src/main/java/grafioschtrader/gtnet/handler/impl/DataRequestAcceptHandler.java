package grafioschtrader.gtnet.handler.impl;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import grafioschtrader.entities.GTNetMessage;
import grafioschtrader.gtnet.GTNetMessageCodeType;
import grafioschtrader.gtnet.handler.AbstractResponseHandler;
import grafioschtrader.gtnet.handler.GTNetMessageContext;

/**
 * Handler for GT_NET_DATA_REQUEST_ACCEPT_S messages.
 *
 * Processes acceptance responses to our data exchange requests. The remote server has accepted our request
 * to exchange data (last prices, historical data, etc.).
 *
 * This is a simple acknowledgment - the actual data exchange happens through separate mechanisms.
 */
@Component
public class DataRequestAcceptHandler extends AbstractResponseHandler {

  private static final Logger log = LoggerFactory.getLogger(DataRequestAcceptHandler.class);

  @Override
  public GTNetMessageCodeType getSupportedMessageCode() {
    return GTNetMessageCodeType.GT_NET_DATA_REQUEST_ACCEPT_S;
  }

  @Override
  protected void processResponseSideEffects(GTNetMessageContext context, GTNetMessage storedMessage) {
    log.info("Data request accepted by {} - message stored with id {}",
        context.getSourceDomain(), storedMessage.getIdGtNetMessage());
    // Future: Could update GTNetEntity state or trigger data exchange setup
  }
}
