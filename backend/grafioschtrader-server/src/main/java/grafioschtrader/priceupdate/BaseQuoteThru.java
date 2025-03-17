package grafioschtrader.priceupdate;

import grafioschtrader.GlobalConstants;
import grafioschtrader.entities.Security;
import grafioschtrader.entities.Securitycurrency;
import grafioschtrader.rest.RequestGTMappings;

public abstract class BaseQuoteThru {
  protected final String LINK_DOWNLOAD_LAZY = "lazy";

  /**
   * The URL for accessing data providers with an API key cannot be returned to
   * unauthorized users. Therefore, this method returns a link to this backend.
   * The backend can then use this link to execute the request with the provider
   * itself and return the result to the frontend. This is used to handle links
   * for historical and intraday data.
   * 
   * @param <S>
   * @param securitycurrency
   * @param isIntraday
   * @return
   */
  public static <S extends Securitycurrency<S>> String getDownlinkWithApiKey(S securitycurrency, boolean isIntraday) {
    return GlobalConstants.PREFIX_FOR_DOWNLOAD_REDIRECT_TO_BACKEND + RequestGTMappings.WATCHLIST_MAP
        + RequestGTMappings.SECURITY_DATAPROVIDER_INTRA_HISTORICAL_RESPONSE + securitycurrency.getIdSecuritycurrency()
        + "?isIntraday=" + isIntraday + "&isSecurity=" + (securitycurrency instanceof Security ? true : false);
  }

}
