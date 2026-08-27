package grafiosch.gtnet.model.msg;

import java.time.LocalDateTime;

import com.fasterxml.jackson.annotation.JsonFormat;

import grafiosch.BaseConstants;
import grafiosch.gtnet.m2m.model.IMsgDetails;
import grafiosch.validation.DateRange;
import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.Future;
import jakarta.validation.constraints.NotNull;

/**
 * Payload for maintenance window announcements (GT_NET_MAINTENANCE_ALL_C).
 *
 * This message is broadcast to all domains that have active data exchange agreements with this server. It informs
 * consumers that the server will be unavailable during the specified time window, allowing them to adjust their data
 * fetching strategies accordingly.
 *
 * Upon receiving this message the consumer records the window as a {@link grafiosch.entities.GTNetMaintenanceWindow}
 * of the sender and skips the sender for its duration — as a data supplier and as the target of outgoing messages.
 * The window is evaluated at query time rather than turned into a server state, so it takes effect exactly at
 * {@code fromDateTime} and ends by itself at {@code toDateTime}. A sender may announce several windows, as long as
 * they do not overlap.
 */
@Schema(description = """
    Payload for maintenance window announcements broadcast to all connected domains. Specifies the time window
    during which the sender's services will be unavailable. Consumers record the window and do not contact the
    sender while it is running. Several non-overlapping windows may be announced.""")
@DateRange(start = "fromDateTime", end = "toDateTime")
public class MaintenanceMsg implements IMsgDetails {
  private static final long serialVersionUID = 1L;

  @Schema(description = """
      UTC start time of the maintenance window. Must be in the future. Services will become unavailable at
      this time.""")
  @NotNull
  @Future
  @JsonFormat(pattern = BaseConstants.STANDARD_LOCAL_DATE_TIME)
  public LocalDateTime fromDateTime;

  @Schema(description = """
      UTC end time of the maintenance window. Must be after fromDateTime. Services are expected to resume
      normal operation after this time.""")
  @NotNull
  @Future
  @JsonFormat(pattern = BaseConstants.STANDARD_LOCAL_DATE_TIME)
  public LocalDateTime toDateTime;
}
