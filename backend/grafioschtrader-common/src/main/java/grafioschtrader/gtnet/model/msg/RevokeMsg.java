package grafioschtrader.gtnet.model.msg;

import java.time.LocalDateTime;

import jakarta.validation.constraints.Future;
import jakarta.validation.constraints.NotNull;

public class RevokeMsg {

  @NotNull
  @Future
  public LocalDateTime fromDateTime;
}
