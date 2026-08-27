package grafiosch.gtnet.handler.impl;

import java.time.LocalDateTime;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

import grafiosch.entities.GTNet;
import grafiosch.entities.GTNetMaintenanceWindow;
import grafiosch.entities.GTNetMessage;
import grafiosch.gtnet.GNetCoreMessageCode;
import grafiosch.gtnet.GTNetMessageCode;
import grafiosch.gtnet.MessageParamDateParser;
import grafiosch.gtnet.handler.AbstractAnnouncementHandler;
import grafiosch.gtnet.handler.GTNetMessageContext;
import grafiosch.repository.GTNetMaintenanceWindowJpaRepository;

/**
 * Handler for GT_NET_MAINTENANCE_ALL_C messages.
 *
 * <p>
 * Records the announced window as a {@link GTNetMaintenanceWindow} of the sending remote. It does not change the
 * remote's server state: the announcement usually arrives long before the window starts, and a state written now would
 * be both premature and short-lived, because the next status check and the remote's next message overwrite it. Every
 * place that would contact the peer asks instead whether the current time falls into one of its windows, so the effect
 * begins exactly at {@code fromDateTime} and ends by itself at {@code toDateTime}.
 * </p>
 *
 * <p>
 * A remote may announce several windows. Re-delivery of the same announcement is an update of the existing row, keyed
 * by remote and the two window bounds.
 * </p>
 */
@Component
public class MaintenanceAnnouncementHandler extends AbstractAnnouncementHandler {

  @Autowired
  private GTNetMaintenanceWindowJpaRepository gtNetMaintenanceWindowJpaRepository;

  @Override
  public GTNetMessageCode getSupportedMessageCode() {
    return GNetCoreMessageCode.GT_NET_MAINTENANCE_ALL_C;
  }

  @Override
  protected void processAnnouncementSideEffects(GTNetMessageContext context, GTNetMessage storedMessage) {
    GTNet remoteGTNet = context.getRemoteGTNet();
    if (remoteGTNet == null) {
      return;
    }
    LocalDateTime fromDateTime = MessageParamDateParser.parseDateTime(context.getParams(), "fromDateTime");
    LocalDateTime toDateTime = MessageParamDateParser.parseDateTime(context.getParams(), "toDateTime");
    if (fromDateTime == null || toDateTime == null || !toDateTime.isAfter(fromDateTime)) {
      // A window we cannot read is not turned into an outage: the message stays visible to the administrator.
      return;
    }
    GTNetMaintenanceWindow window = gtNetMaintenanceWindowJpaRepository
        .findByIdGtNetAndFromDateTimeAndToDateTime(remoteGTNet.getIdGtNet(), fromDateTime, toDateTime)
        .orElseGet(() -> new GTNetMaintenanceWindow(remoteGTNet.getIdGtNet(), storedMessage.getIdGtNetMessage(),
            fromDateTime, toDateTime));
    window.setIdGtNetMessage(storedMessage.getIdGtNetMessage());
    gtNetMaintenanceWindowJpaRepository.save(window);
  }
}
