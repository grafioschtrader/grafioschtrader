package grafioschtrader.priceupdate.intraday;

import grafioschtrader.connector.instrument.IFeedConnector;
import grafioschtrader.entities.Securitycurrency;

public interface IIntradayEntityAccess<S extends Securitycurrency<S>> {
  void updateIntraSecurityCurrency(final S securitycurrency, final IFeedConnector feedConnector) throws Exception;
}
