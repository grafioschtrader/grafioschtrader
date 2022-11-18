package grafioschtrader.gtnet.model.msg;

import grafioschtrader.gtnet.GTNetServerStateTypes;
import grafioschtrader.gtnet.m2m.model.IMsgDetails;

/**
 * Request server list update
 */
public class UpdateServerlistRequestMsg implements IMsgDetails {
 
  private static final long serialVersionUID = 1L;

  public boolean spreadCapability;
  public GTNetServerStateTypes entityServerState;
  public Integer dailyRequestLimit;
  public GTNetServerStateTypes lastpriceServerState;
}
