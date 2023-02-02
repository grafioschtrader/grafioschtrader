package grafioschtrader.priceupdate.historyquote;

import java.util.Date;
import java.util.List;

import grafioschtrader.connector.instrument.IFeedConnector;
import grafioschtrader.entities.Historyquote;
import grafioschtrader.entities.Securitycurrency;
import grafioschtrader.repository.HistoryquoteJpaRepository;

public interface IHistoryquoteEntityAccess<S extends Securitycurrency<S>> extends IHistoryqouteEntityBaseAccess<S> {

  /**
   * Gets the new prices and creates history quotes for adding to the security or
   * currency pair.
   *
   * @param securitycurrency
   * @param fromDate
   * @param toDate
   * @param feedConector
   * @return
   * @throws Exception
   */
  List<Historyquote> getHistoryQuote(S securitycurrency, Date fromDate, Date toDate, IFeedConnector feedConector)
      throws Exception;

  HistoryquoteJpaRepository getHistoryquoteJpaRepository();
}
