package grafioschtrader.gtnet.handler.impl;

import org.springframework.stereotype.Component;

import grafioschtrader.entities.GTNetMessage;
import grafioschtrader.gtnet.GTNetMessageCodeType;
import grafioschtrader.gtnet.handler.AbstractRequestHandler;
import grafioschtrader.gtnet.handler.GTNetMessageContext;
import grafioschtrader.gtnet.handler.ValidationResult;

/**
 * Handler for GT_NET_UPDATE_SERVERLIST_SEL_C messages.
 *
 * Processes requests from remote servers to share the local server list. When accepted, the response includes the list
 * of known GTNet servers that have spreadCapability enabled.
 */
@Component
public class UpdateServerlistRequestHandler extends AbstractRequestHandler {

  @Override
  public GTNetMessageCodeType getSupportedMessageCode() {
    return GTNetMessageCodeType.GT_NET_UPDATE_SERVERLIST_SEL_C;
  }

  @Override
  protected ValidationResult validateRequest(GTNetMessageContext context) {
    // Must be from a known remote
    if (context.getRemoteGTNet() == null) {
      return ValidationResult.invalid("UNKNOWN_REMOTE",
          "Server list request from unknown domain - handshake required first");
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
    // Server list sharing doesn't require GTNet state updates
  }

  // TODO: Override buildResponse to include server list in payload when accepted
}
