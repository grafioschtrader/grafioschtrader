package grafiosch.gtnet.handler.impl;

import java.util.Set;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

import grafiosch.entities.GTNet;
import grafiosch.entities.GTNetMessage;
import grafiosch.gtnet.GNetCoreMessageCode;
import grafiosch.gtnet.GTNetMessageCode;
import grafiosch.gtnet.GTNetServerOnlineStatusTypes;
import grafiosch.gtnet.SendReceivedType;
import grafiosch.gtnet.handler.AbstractAnnouncementHandler;
import grafiosch.gtnet.handler.GTNetMessageContext;
import grafiosch.gtnet.handler.ValidationResult;
import grafiosch.repository.GTNetMaintenanceWindowJpaRepository;
import grafiosch.repository.GTNetMessageJpaRepository;

/**
 * Handles maintenance and operation-discontinuation cancellations.
 *
 * <p>
 * Both codes undo exactly what their announcement recorded: a maintenance cancellation deletes the windows its
 * announcement created, a discontinuation cancellation drops the announced shutdown date and, if the peer has already
 * gone out of service, puts it back to {@code SOS_UNKNOWN} so the next status check re-probes it rather than assuming
 * it is up.
 * </p>
 */
@Component
public class CancellationAnnouncementHandler extends AbstractAnnouncementHandler {

  private static final String ORIGINAL_MESSAGE = "originalMessage";

  @Autowired
  private GTNetMessageJpaRepository gtNetMessageJpaRepositoryFull;

  @Autowired
  private GTNetMaintenanceWindowJpaRepository gtNetMaintenanceWindowJpaRepository;

  @Override
  public GTNetMessageCode getSupportedMessageCode() {
    return GNetCoreMessageCode.GT_NET_MAINTENANCE_CANCEL_ALL_C;
  }

  @Override
  public Set<? extends GTNetMessageCode> getSupportedMessageCodes() {
    return Set.of(GNetCoreMessageCode.GT_NET_MAINTENANCE_CANCEL_ALL_C,
        GNetCoreMessageCode.GT_NET_OPERATION_DISCONTINUED_CANCEL_ALL_C);
  }

  @Override
  protected ValidationResult validateAnnouncement(GTNetMessageContext context) {
    GTNet remoteGTNet = context.getRemoteGTNet();
    if (remoteGTNet == null) {
      return ValidationResult.invalid("UNKNOWN_REMOTE", "Cancellation from unknown domain");
    }
    if (context.getIdOriginalMessage() == null) {
      return ValidationResult.invalid("MISSING_ORIGINAL_MESSAGE", "Cancellation does not reference an announcement");
    }

    byte originalCode = context.getMessageCodeValue() == GNetCoreMessageCode.GT_NET_MAINTENANCE_CANCEL_ALL_C.getValue()
        ? GNetCoreMessageCode.GT_NET_MAINTENANCE_ALL_C.getValue()
        : GNetCoreMessageCode.GT_NET_OPERATION_DISCONTINUED_ALL_C.getValue();
    GTNetMessage original = gtNetMessageJpaRepositoryFull
        .findByIdGtNetAndSendRecvAndIdSourceGtNetMessageAndMessageCode(remoteGTNet.getIdGtNet(),
            SendReceivedType.RECEIVED.getValue(), context.getIdOriginalMessage(), originalCode)
        .orElse(null);
    if (original == null) {
      return ValidationResult.invalid("ORIGINAL_MESSAGE_NOT_FOUND",
          "The referenced announcement was not received from this domain");
    }
    context.setHandlerData(ORIGINAL_MESSAGE, original);
    return ValidationResult.ok();
  }

  @Override
  protected GTNetMessage storeIncomingMessage(GTNetMessageContext context) {
    GTNetMessage cancellation = super.storeIncomingMessage(context);
    GTNetMessage original = context.getHandlerData(ORIGINAL_MESSAGE, GTNetMessage.class);
    cancellation.setIdOriginalMessage(original.getIdGtNetMessage());
    return gtNetMessageJpaRepositoryFull.save(cancellation);
  }

  @Override
  protected void processAnnouncementSideEffects(GTNetMessageContext context, GTNetMessage storedMessage) {
    GTNetMessage original = context.getHandlerData(ORIGINAL_MESSAGE, GTNetMessage.class);
    if (context.getMessageCodeValue() == GNetCoreMessageCode.GT_NET_MAINTENANCE_CANCEL_ALL_C.getValue()) {
      // The windows the cancelled announcement created disappear with it, which is all it takes: whether a remote is
      // under maintenance is evaluated from those rows and nowhere else.
      gtNetMaintenanceWindowJpaRepository.deleteByIdGtNetMessage(original.getIdGtNetMessage());
      return;
    }
    // Discontinuation cancelled. Drop a shutdown that has not taken effect yet, and revive a peer that is already out
    // of service: SOS_UNKNOWN rather than SOS_ONLINE, so the next status check decides whether it is really reachable.
    GTNet remoteGTNet = context.getRemoteGTNet();
    remoteGTNet.setCloseStartDate(null);
    if (remoteGTNet.isOutOfService()) {
      remoteGTNet.setServerOnline(GTNetServerOnlineStatusTypes.SOS_UNKNOWN);
    }
    saveRemoteGTNet(remoteGTNet);
  }
}
