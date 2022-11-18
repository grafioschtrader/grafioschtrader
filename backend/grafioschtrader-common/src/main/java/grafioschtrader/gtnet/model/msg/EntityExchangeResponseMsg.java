package grafioschtrader.gtnet.model.msg;

/**
 * The response to a request for the exchange of information objects can be automatic or manual. 
 * In case of automatic response, the corresponding information is taken over from GTNet.
 */
public class EntityExchangeResponseMsg {

  public boolean acceptEntityRequest;
  public Integer dailyRequestLimit;
}
