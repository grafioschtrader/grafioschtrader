package grafioschtrader.repository;

import java.util.Date;
import java.util.List;

import org.springframework.data.jpa.repository.JpaRepository;

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
  
  
  JpaRepository<S, Integer> getJpaRepository();

  void fillGap(S securitycurrency, final IFeedConnector feedConnector,
      final Date fromDate, final Date toDate, List<Historyquote> historyquotes);
  
}
