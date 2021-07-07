package grafioschtrader.priceupdate.intraday;

import java.util.List;

import grafioschtrader.entities.Securitycurrency;

public interface IIntradayLoad<S extends Securitycurrency<S>> {

  /**
   * Update intraday prices of securities or currency pairs. This is done
   * concurrent.
   *
   * @param securtycurrencies List of securities or currency pairs.
   * @return
   */
  List<S> updateLastPriceOfSecuritycurrency(final List<S> securtycurrencies);

  /**
   * Update intraday prices of securities or currency pairs. This is done
   * concurrent.
   *
   * @param securtycurrencies
   * @param maxIntraRetry     With minus value there is no retry check.
   * @return
   */
  List<S> updateLastPriceOfSecuritycurrency(final List<S> securtycurrencies, final short maxIntraRetry);

  /**
   * Update intraday prices of a single security or currency pair.
   *
   * @param securitycurrency
   * @param maxIntraRetry
   * @param scIntradayUpdateTimeout
   * @return
   */
  S updateLastPriceSecurityCurrency(final S securitycurrency, final short maxIntraRetry,
      final int scIntradayUpdateTimeout);

  String getSecuritycurrencyIntraDownloadLinkAsUrlStr(S securitycurrency);

}
