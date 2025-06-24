package grafioschtrader.priceupdate;

import grafioschtrader.GlobalConstants;
import grafioschtrader.entities.Security;
import grafioschtrader.entities.Securitycurrency;
import grafioschtrader.rest.RequestGTMappings;

/**
 * Abstract base class for data provider quote handling and secure API access management.
 * 
 * <p>This class provides foundation functionality for handling quote data retrieval from external data providers
 * while ensuring secure access to API-protected resources. It centralizes the logic for creating secure download
 * links that protect API keys from unauthorized access by routing requests through the backend infrastructure.</p>
 */
public abstract class BaseQuoteThru {
  /** Download link identifier for lazy loading operations. */
  protected final String LINK_DOWNLOAD_LAZY = "lazy";

  /**
   * Creates a secure backend-routed download link for API-protected data provider access.
   * 
   * <p>The URL for accessing data providers with an API key cannot be returned to unauthorized users. Therefore, this
   * method returns a link to this backend. The backend can then use this link to execute the request with the provider
   * itself and return the result to the frontend. This is used to handle links for historical and intraday data.</p>
   * 
   * <p>This security mechanism ensures that sensitive API keys are never exposed to the frontend while still allowing
   * authorized access to external market data through the backend proxy. The generated link includes the security
   * currency ID, data type flag, and entity type identification for proper request routing.</p>
   * 
   * @param <S> the type of security currency extending Securitycurrency
   * @param securitycurrency the security or currency pair for which to generate the download link
   * @param isIntraday true for intraday data requests, false for historical data requests
   * @return secure backend-routed URL string that proxies the external data provider request
   */
  public static <S extends Securitycurrency<S>> String getDownlinkWithApiKey(S securitycurrency, boolean isIntraday) {
    return GlobalConstants.PREFIX_FOR_DOWNLOAD_REDIRECT_TO_BACKEND + RequestGTMappings.WATCHLIST_MAP
        + RequestGTMappings.SECURITY_DATAPROVIDER_INTRA_HISTORICAL_RESPONSE + securitycurrency.getIdSecuritycurrency()
        + "?isIntraday=" + isIntraday + "&isSecurity=" + (securitycurrency instanceof Security ? true : false);
  }

}
