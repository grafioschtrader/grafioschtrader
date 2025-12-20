package grafioschtrader.gtnet.handler.impl;

import org.springframework.stereotype.Component;

import grafioschtrader.entities.GTNet;
import grafioschtrader.entities.GTNetConfig;
import grafioschtrader.entities.GTNetMessage;
import grafioschtrader.gtnet.GTNetExchangeStatusTypes;
import grafioschtrader.gtnet.GTNetMessageCodeType;
import grafioschtrader.gtnet.GTNetServerStateTypes;
import grafioschtrader.gtnet.handler.AbstractRequestHandler;
import grafioschtrader.gtnet.handler.GTNetMessageContext;
import grafioschtrader.gtnet.handler.ValidationResult;

/**
 * Handler for GT_NET_LASTPRICE_REQUEST_SEL_C messages.
 *
 * Processes requests from remote servers to exchange intraday/last price data. The request may be:
 * <ul>
 *   <li>Auto-accepted via GTNetMessageAnswer rules</li>
 *   <li>Stored for manual admin review</li>
 * </ul>
 *
 * When accepted, updates the remote GTNet entity to enable lastprice data exchange.
 */
@Component
public class LastpriceRequestHandler extends AbstractRequestHandler {

  @Override
  public GTNetMessageCodeType getSupportedMessageCode() {
    return GTNetMessageCodeType.GT_NET_LASTPRICE_REQUEST_SEL_C;
  }

  @Override
  protected ValidationResult validateRequest(GTNetMessageContext context) {
    // Must be from a known remote (completed handshake)
    if (context.getRemoteGTNet() == null) {
      return ValidationResult.invalid("UNKNOWN_REMOTE",
          "Lastprice request from unknown domain - handshake required first");
    }
    return ValidationResult.ok();
  }

  @Override
  protected void processRequestSideEffects(GTNetMessageContext context, GTNetMessage storedRequest) {
    // No side effects on request receipt - side effects applied after response determination
  }

  @Override
  protected void applyResponseSideEffects(GTNetMessageContext context, GTNetMessageCodeType responseCode,
      GTNetMessage storedRequest) {
    GTNet remoteGTNet = context.getRemoteGTNet();
    if (remoteGTNet == null) {
      return;
    }

    // Update GTNet state based on response
    if (responseCode == GTNetMessageCodeType.GT_NET_LASTPRICE_REQUEST_ACCEPT_S) {
      remoteGTNet.setAcceptLastpriceRequest(true);
      remoteGTNet.setLastpriceServerState(GTNetServerStateTypes.SS_OPEN);
      saveRemoteGTNet(remoteGTNet);
      // Update GTNetConfig exchange status to bidirectional
      GTNetConfig gtNetConfig = remoteGTNet.getGtNetConfig();
      if (gtNetConfig != null) {
        gtNetConfig.setLastpriceExchange(GTNetExchangeStatusTypes.ES_BOTH);
        saveGTNetConfig(gtNetConfig);
      }
    } else if (responseCode == GTNetMessageCodeType.GT_NET_LASTPRICE_REQUEST_IN_PROCESS_S) {
      // In process - no state change yet
    } else if (responseCode == GTNetMessageCodeType.GT_NET_LASTPRICE_REQUEST_REJECTED_S) {
      remoteGTNet.setAcceptLastpriceRequest(false);
      remoteGTNet.setLastpriceServerState(GTNetServerStateTypes.SS_CLOSED);
      saveRemoteGTNet(remoteGTNet);
    }
  }
}
