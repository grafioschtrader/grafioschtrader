package grafioschtrader.gtnet.handler.impl;

import org.springframework.stereotype.Component;

import grafioschtrader.entities.GTNet;
import grafioschtrader.entities.GTNetMessage;
import grafioschtrader.gtnet.GTNetMessageCodeType;
import grafioschtrader.gtnet.GTNetServerStateTypes;
import grafioschtrader.gtnet.handler.AbstractRequestHandler;
import grafioschtrader.gtnet.handler.GTNetMessageContext;
import grafioschtrader.gtnet.handler.ValidationResult;

/**
 * Handler for GT_NET_BOTH_REQUEST_SEL_C messages.
 *
 * Processes requests from remote servers to exchange both entity data and intraday prices. This is a combined request
 * that, when accepted, enables both types of data exchange.
 */
@Component
public class BothRequestHandler extends AbstractRequestHandler {

  @Override
  public GTNetMessageCodeType getSupportedMessageCode() {
    return GTNetMessageCodeType.GT_NET_BOTH_REQUEST_SEL_C;
  }

  @Override
  protected ValidationResult validateRequest(GTNetMessageContext context) {
    // Must be from a known remote (completed handshake)
    if (context.getRemoteGTNet() == null) {
      return ValidationResult.invalid("UNKNOWN_REMOTE", "Both request from unknown domain - handshake required first");
    }
    return ValidationResult.ok();
  }

  @Override
  protected void processRequestSideEffects(GTNetMessageContext context, GTNetMessage storedRequest) {
    // No side effects on request receipt
  }

  @Override
  protected void applyResponseSideEffects(GTNetMessageContext context, GTNetMessageCodeType responseCode,
      GTNetMessage storedRequest) {
    GTNet remoteGTNet = context.getRemoteGTNet();
    if (remoteGTNet == null) {
      return;
    }

    // Update GTNet state based on response
    if (responseCode == GTNetMessageCodeType.GT_NET_BOTH_REQUEST_ACCEPT_S) {
      remoteGTNet.setAcceptEntityRequest(true);
      remoteGTNet.setEntityServerState(GTNetServerStateTypes.SS_OPEN);
      remoteGTNet.setAcceptLastpriceRequest(true);
      remoteGTNet.setLastpriceServerState(GTNetServerStateTypes.SS_OPEN);
      saveRemoteGTNet(remoteGTNet);
    } else if (responseCode == GTNetMessageCodeType.GT_NET_BOTH_REQUEST_IN_PROCESS_S) {
      // In process - no state change yet
    } else if (responseCode == GTNetMessageCodeType.GT_NET_BOTH_REQUEST_REJECTED_S) {
      remoteGTNet.setAcceptEntityRequest(false);
      remoteGTNet.setEntityServerState(GTNetServerStateTypes.SS_CLOSED);
      remoteGTNet.setAcceptLastpriceRequest(false);
      remoteGTNet.setLastpriceServerState(GTNetServerStateTypes.SS_CLOSED);
      saveRemoteGTNet(remoteGTNet);
    }
  }
}
