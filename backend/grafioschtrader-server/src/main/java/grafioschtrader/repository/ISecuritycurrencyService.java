package grafioschtrader.repository;

import java.util.List;

import grafioschtrader.connector.instrument.IFeedConnector;
import grafioschtrader.entities.Securitycurrency;
import grafioschtrader.reportviews.securitycurrency.SecuritycurrencyPosition;

public interface ISecuritycurrencyService<S extends Securitycurrency<S>> extends BaseRepositoryCustom<S> {

  public void setSecuritycurrencyIntradayDownloadLink(SecuritycurrencyPosition<S> securitycurrencyPosition);

  public void setSecuritycurrencyHistoricalDownloadLink(SecuritycurrencyPosition<S> securitycurrencyPosition);

 
  
  /**
   * Update the last price of every currency pair
   */
  public void updateAllLastPrice();

  /**
   * Returns a list of IFeedConnector which supports currency or security
   * 
   * @param isCurrency
   * @return
   */
  public List<IFeedConnector> getFeedConnectors(boolean isCurrency);

}
