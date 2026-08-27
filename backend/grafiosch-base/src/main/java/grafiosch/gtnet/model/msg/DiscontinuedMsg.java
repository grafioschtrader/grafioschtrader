package grafiosch.gtnet.model.msg;

import java.time.LocalDate;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.Future;
import jakarta.validation.constraints.NotNull;

/**
 * Payload for server discontinuation announcements (GT_NET_OPERATION_DISCONTINUED_ALL_C).
 *
 * This message is broadcast to all connected domains to inform them that the server will be permanently shut down as
 * of the specified date. Until that date the sender keeps being used normally; from it the recipient puts the sender
 * to {@code SOS_OUT_OF_SERVICE}, which is terminal — the peer is never contacted again and an administrator can then
 * delete it. Only one discontinuation may be pending at a time, and it can be withdrawn with
 * {@code GT_NET_OPERATION_DISCONTINUED_CANCEL_ALL_C}.
 */
@Schema(description = "The server will be shut down on this date and can therefore no longer be contacted.")
public class DiscontinuedMsg {

  @Schema(description = "As of this date, the server is no longer accessible.")
  @NotNull
  @Future
  public LocalDate closeStartDate;
}
