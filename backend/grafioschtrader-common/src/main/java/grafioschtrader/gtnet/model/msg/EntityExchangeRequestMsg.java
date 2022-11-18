package grafioschtrader.gtnet.model.msg;

/**
 * 
 * Exchange entities includes EOD price data. Some field will have some
 * default values from GTNet which can be overridden.
 *
 */
public class EntityExchangeRequestMsg {
   
  
  public boolean spreadCapability;
  
  /**
   * If true, a mutual exchange is requested.
   */
  public boolean acceptEntityRequest;
  
  public Integer dailyRequestLimit;
}
