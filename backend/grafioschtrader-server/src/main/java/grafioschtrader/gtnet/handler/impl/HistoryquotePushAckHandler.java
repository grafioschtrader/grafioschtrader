package grafioschtrader.gtnet.handler.impl;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import grafiosch.entities.GTNetMessage;
import grafiosch.gtnet.handler.AbstractResponseHandler;
import grafiosch.gtnet.handler.GTNetMessageContext;
import grafioschtrader.gtnet.GTNetMessageCodeType;
import grafioschtrader.gtnet.model.msg.HistoryquoteExchangeMsg;

/**
 * Handler for GT_NET_HISTORYQUOTE_PUSH_ACK_S responses.
 *
 * Processes acknowledgments from remote servers after we pushed historical price data to them.
 * The acknowledgment contains the count of records the remote server accepted.
 *
 * This completes the push flow:
 * <ol>
 *   <li>We sent EXCHANGE request and received "want to receive" markers</li>
 *   <li>We sent PUSH with historical data</li>
 *   <li>Remote server responds with this ACK containing acceptedCount</li>
 * </ol>
 *
 * @see GTNetMessageCodeType#GT_NET_HISTORYQUOTE_PUSH_ACK_S
 * @see HistoryquotePushHandler
 */
@Component
public class HistoryquotePushAckHandler extends AbstractResponseHandler {

  private static final Logger log = LoggerFactory.getLogger(HistoryquotePushAckHandler.class);

  @Override
  public GTNetMessageCodeType getSupportedMessageCode() {
    return GTNetMessageCodeType.GT_NET_HISTORYQUOTE_PUSH_ACK_S;
  }

  @Override
  protected void processResponseSideEffects(GTNetMessageContext context, GTNetMessage storedMessage) {
    // Extract the accepted count from the response payload
    Integer acceptedCount = null;
    String remoteDomain = context.getRemoteGTNet() != null
        ? context.getRemoteGTNet().getDomainRemoteName()
        : "unknown";

    if (context.hasPayload()) {
      try {
        HistoryquoteExchangeMsg ackPayload = context.getPayloadAs(HistoryquoteExchangeMsg.class);
        if (ackPayload != null) {
          acceptedCount = ackPayload.acceptedCount;
        }
      } catch (Exception e) {
        log.warn("Failed to parse historyquote push ACK payload from {}: {}", remoteDomain, e.getMessage());
      }
    }

    if (acceptedCount != null) {
      log.info("Historyquote push to {} acknowledged: {} records accepted", remoteDomain, acceptedCount);
    } else {
      log.info("Historyquote push to {} acknowledged (no count in response)", remoteDomain);
    }
  }
}
