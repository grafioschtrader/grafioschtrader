package grafioschtrader.priceupdate.intraday;

import java.util.List;

import grafioschtrader.connector.instrument.IFeedConnector;
import grafioschtrader.entities.Securitycurrency;

public interface IIntradayLoad<S extends Securitycurrency<S>> {

  /**
   * Update intraday prices of securities or currency pairs. This is done
   * concurrent.
   */
  List<S> updateLastPriceOfSecuritycurrency(final List<S> securtycurrencies, boolean singleThread);

  /**
   * Update intraday prices of securities or currency pairs. This is done
   * concurrent.
   */
  List<S> updateLastPriceOfSecuritycurrency(final List<S> securtycurrencies, final short maxIntraRetry, boolean singleThread);

  /**
   * Update intraday prices of a single security or currency pair.
   */
  S updateLastPriceSecurityCurrency(final S securitycurrency, final short maxIntraRetry,
      final int scIntradayUpdateTimeout);

  String getSecuritycurrencyIntraDownloadLinkAsUrlStr(S securitycurrency);

  String createDownloadLink(S securitycurrency, IFeedConnector feedConnector);

}
