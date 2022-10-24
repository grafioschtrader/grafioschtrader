package grafioschtrader.gtnet.model.msg;

import java.time.LocalDateTime;

import grafioschtrader.gtnet.m2m.model.IMsgDetails;

public class MaintenanceMsg implements IMsgDetails {
  private static final long serialVersionUID = 1L;

  public LocalDateTime fromDateTime;
  public LocalDateTime toDateTime;
}
