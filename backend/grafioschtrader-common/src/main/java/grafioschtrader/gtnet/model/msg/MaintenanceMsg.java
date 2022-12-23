package grafioschtrader.gtnet.model.msg;

import java.time.LocalDateTime;

import grafioschtrader.gtnet.m2m.model.IMsgDetails;
import grafioschtrader.validation.DateRange;
import jakarta.validation.constraints.Future;
import jakarta.validation.constraints.NotNull;

/**
 * Server which offers data will receive maintenance in the future.
 */
@DateRange(start = "fromDateTime", end = "toDateTime")
public class MaintenanceMsg implements IMsgDetails {
  private static final long serialVersionUID = 1L;

  @NotNull
  @Future
  public LocalDateTime fromDateTime;
  @NotNull
  @Future
  public LocalDateTime toDateTime;
}
