package grafioschtrader.priceupdate.historyquote;

import java.time.LocalDate;
import java.util.List;

import grafioschtrader.connector.instrument.IFeedConnector;
import grafioschtrader.entities.Historyquote;
import grafioschtrader.entities.Securitycurrency;
import grafioschtrader.repository.HistoryquoteJpaRepository;

public interface IHistoryquoteEntityAccess<S extends Securitycurrency<S>> extends IHistoryqouteEntityBaseAccess<S> {

  /**
   * Gets the new prices and creates history quotes for adding to the security or currency pair.
   */
  List<Historyquote> getHistoryQuote(S securitycurrency, LocalDate fromDate, LocalDate toDate, IFeedConnector feedConector)
      throws Exception;

  HistoryquoteJpaRepository getHistoryquoteJpaRepository();
}
