package grafioschtrader.repository;

import java.util.List;

import grafioschtrader.connector.instrument.IFeedConnector;
import grafioschtrader.connector.instrument.IFeedConnector.FeedSupport;
import grafioschtrader.dto.ValueKeyHtmlSelectOptions;
import grafioschtrader.entities.Historyquote;
import grafioschtrader.entities.Securitycurrency;
import grafioschtrader.reportviews.securitycurrency.SecuritycurrencyPosition;

public interface ISecuritycurrencyService<S extends Securitycurrency<S>> extends BaseRepositoryCustom<S> {

  void setSecuritycurrencyIntradayDownloadLink(SecuritycurrencyPosition<S> securitycurrencyPosition);

  void setSecuritycurrencyHistoricalDownloadLink(SecuritycurrencyPosition<S> securitycurrencyPosition);

  /**
   * Update the last price of every currency pair
   */
//  void updateAllLastPrice();

  /**
   * Returns a list of IFeedConnector which supports currency or security
   *
   * @param isCurrency
   * @return
   */
  List<IFeedConnector> getFeedConnectors(boolean isCurrency);

  List<ValueKeyHtmlSelectOptions> getAllFeedConnectorsAsKeyValue(FeedSupport feedSupport);
  
  SecurityCurrencypairJpaRepository<S> getJpaRepository();

  List<Historyquote> fillGap(S securitycurrency);
  
}
