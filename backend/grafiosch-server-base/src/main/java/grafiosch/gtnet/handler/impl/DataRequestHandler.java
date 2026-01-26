package grafiosch.gtnet.handler.impl;

import java.util.Set;

import org.springframework.stereotype.Component;

import grafiosch.entities.GTNet;
import grafiosch.entities.GTNetMessage;
import grafiosch.gtnet.GNetCoreMessageCode;
import grafiosch.gtnet.GTNetMessageCode;
import grafiosch.gtnet.IExchangeKindType;
import grafiosch.gtnet.handler.AbstractDataRequestHandler;
import grafiosch.gtnet.handler.GTNetMessageContext;
import grafiosch.gtnet.handler.ValidationResult;

/**
 * Handler for GT_NET_DATA_REQUEST_SEL_RR_C messages.
 *
 * Processes requests for data exchange via the DataRequestMsg payload. The payload contains an
 * entityKinds set specifying which syncable data types the requester wants to exchange.
 */
@Component
public class DataRequestHandler extends AbstractDataRequestHandler {

  @Override
  public GTNetMessageCode getSupportedMessageCode() {
    return GNetCoreMessageCode.GT_NET_DATA_REQUEST_SEL_RR_C;
  }

  @Override
  protected ValidationResult validateRequest(GTNetMessageContext context) {
    if (context.getRemoteGTNet() == null) {
      return ValidationResult.invalid("UNKNOWN_REMOTE",
          "Data request from unknown domain - handshake required first");
    }

    Set<IExchangeKindType> requestedKinds = getRequestedEntityKinds(context);
    if (requestedKinds.isEmpty()) {
      return ValidationResult.invalid("MISSING_ENTITY_KINDS",
          "Data request must specify at least one entity kind in the payload");
    }

    return ValidationResult.ok();
  }

  @Override
  protected void processRequestSideEffects(GTNetMessageContext context, GTNetMessage storedRequest) {
    // No side effects on request receipt - side effects applied after response determination
  }

  @Override
  protected void applyResponseSideEffects(GTNetMessageContext context, GTNetMessageCode responseCode,
      GTNetMessage storedRequest) {
    GTNet remoteGTNet = context.getRemoteGTNet();
    if (remoteGTNet == null) {
      return;
    }

    Set<IExchangeKindType> requestedKinds = getRequestedEntityKinds(context);

    if (responseCode.getValue() == GNetCoreMessageCode.GT_NET_DATA_REQUEST_ACCEPT_S.getValue()) {
      // Step 1: Create/update GTNetEntity without config entities to get IDs
      for (IExchangeKindType kind : requestedKinds) {
        updateEntityForAccept(remoteGTNet, kind);
      }
      remoteGTNet = saveRemoteGTNet(remoteGTNet);

      // Step 2: Now add GTNetConfigEntity with proper IDs
      createConfigEntitiesForAcceptedKinds(remoteGTNet, requestedKinds);
      saveRemoteGTNet(remoteGTNet);

      // Also update myGTNet to reflect that this server now offers these entity kinds.
      GTNet myGTNet = context.getMyGTNet();
      if (myGTNet != null) {
        for (IExchangeKindType kind : requestedKinds) {
          updateMyEntityForAccept(myGTNet, kind);
        }
        saveRemoteGTNet(myGTNet);
      }

      triggerExchangeSyncTask();
    } else if (responseCode.getValue() == GNetCoreMessageCode.GT_NET_DATA_REQUEST_REJECTED_S.getValue()) {
      for (IExchangeKindType kind : requestedKinds) {
        updateEntityForReject(remoteGTNet, kind);
      }
      saveRemoteGTNet(remoteGTNet);
    }
  }
}
