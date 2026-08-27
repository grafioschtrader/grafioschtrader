package grafiosch.gtnet.handler.impl;

import java.time.LocalDate;

import org.springframework.stereotype.Component;

import grafiosch.entities.GTNet;
import grafiosch.entities.GTNetMessage;
import grafiosch.gtnet.GNetCoreMessageCode;
import grafiosch.gtnet.GTNetMessageCode;
import grafiosch.gtnet.MessageParamDateParser;
import grafiosch.gtnet.handler.AbstractAnnouncementHandler;
import grafiosch.gtnet.handler.GTNetMessageContext;

/**
 * Handler for GT_NET_OPERATION_DISCONTINUED_ALL_C messages.
 *
 * <p>
 * Records the announced shutdown date on the sending remote. The peer keeps being used until that date — the
 * announcement is validated as lying in the future and may precede the shutdown by months, so closing the peer on
 * receipt would throw away a working data source for the whole notice period. From the announced day on,
 * {@code GNetFutureMessageDeliveryTask} sets the remote to {@code SOS_OUT_OF_SERVICE}, which is terminal: it is not
 * lifted by a status check or by an inbound message, and the administrator can then delete the peer.
 * </p>
 */
@Component
public class OperationDiscontinuedHandler extends AbstractAnnouncementHandler {

  @Override
  public GTNetMessageCode getSupportedMessageCode() {
    return GNetCoreMessageCode.GT_NET_OPERATION_DISCONTINUED_ALL_C;
  }

  @Override
  protected void processAnnouncementSideEffects(GTNetMessageContext context, GTNetMessage storedMessage) {
    GTNet remoteGTNet = context.getRemoteGTNet();
    if (remoteGTNet == null) {
      return;
    }
    LocalDate closeStartDate = MessageParamDateParser.parseDate(context.getParams(), "closeStartDate");
    if (closeStartDate == null) {
      // Without a readable date nothing can be scheduled; the message stays visible to the administrator.
      return;
    }
    remoteGTNet.setCloseStartDate(closeStartDate);
    saveRemoteGTNet(remoteGTNet);
  }
}
