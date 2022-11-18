package grafioschtrader.gtnet.model.msg;

import java.time.LocalDateTime;

import javax.validation.constraints.Future;
import javax.validation.constraints.NotNull;

import grafioschtrader.gtnet.m2m.model.IMsgDetails;
import grafioschtrader.validation.DateRange;

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
