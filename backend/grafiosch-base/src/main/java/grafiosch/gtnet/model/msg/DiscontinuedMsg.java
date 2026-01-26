package grafiosch.gtnet.model.msg;

import java.time.LocalDate;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.Future;
import jakarta.validation.constraints.NotNull;

/**
 * Payload for server discontinuation announcements (GT_NET_OPERATION_DISCONTINUED_ALL_C).
 *
 * This message is broadcast to all connected domains to inform them that the server
 * will be permanently shut down as of the specified date. Recipients should update
 * their server lists and find alternative data sources.
 */
@Schema(description = "The server will be shut down on this date and can therefore no longer be contacted.")
public class DiscontinuedMsg {

  @Schema(description = "As of this date, the server is no longer accessible.")
  @NotNull
  @Future
  public LocalDate closeStartDate;
}
