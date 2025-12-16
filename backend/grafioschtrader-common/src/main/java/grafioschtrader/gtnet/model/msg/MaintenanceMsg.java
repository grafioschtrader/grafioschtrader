package grafioschtrader.gtnet.model.msg;

import java.time.LocalDateTime;

import grafiosch.validation.DateRange;
import grafioschtrader.gtnet.m2m.model.IMsgDetails;
import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.Future;
import jakarta.validation.constraints.NotNull;

/**
 * Payload for maintenance window announcements (GT_NET_MAINTENANCE_ALL_C).
 *
 * This message is broadcast to all domains that have active data exchange agreements with this server.
 * It informs consumers that the server will be unavailable during the specified time window, allowing
 * them to adjust their data fetching strategies accordingly.
 *
 * Upon receiving this message, consumers should:
 * <ul>
 *   <li>Update the sender's lastpriceServerState to SS_MAINTENANCE</li>
 *   <li>Skip querying this provider during the maintenance window</li>
 *   <li>Optionally display a notification to administrators</li>
 * </ul>
 *
 * @see grafioschtrader.gtnet.GTNetServerStateTypes#SS_MAINTENANCE
 */
@Schema(description = """
    Payload for maintenance window announcements broadcast to all connected domains. Specifies the time window
    during which the sender's services will be unavailable. Consumers should update the sender's server state
    to SS_MAINTENANCE and skip queries during this period.""")
@DateRange(start = "fromDateTime", end = "toDateTime")
public class MaintenanceMsg implements IMsgDetails {
  private static final long serialVersionUID = 1L;

  @Schema(description = """
      UTC start time of the maintenance window. Must be in the future. Services will become unavailable at
      this time.""")
  @NotNull
  @Future
  public LocalDateTime fromDateTime;

  @Schema(description = """
      UTC end time of the maintenance window. Must be after fromDateTime. Services are expected to resume
      normal operation after this time.""")
  @NotNull
  @Future
  public LocalDateTime toDateTime;
}
