package grafioschtrader.gtnet.model.msg;

import java.time.LocalDate;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.Future;
import jakarta.validation.constraints.NotNull;

@Schema(description = "The server will be shut down on this date and can therefore no longer be contacted.")
public class DiscontinuedMsg {

  @Schema(description = "As of this date, the server is no longer accessible.")
  @NotNull
  @Future
  public LocalDate closeStartDate;
}
