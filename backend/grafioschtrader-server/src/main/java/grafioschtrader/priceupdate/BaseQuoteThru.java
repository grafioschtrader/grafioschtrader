package grafioschtrader.priceupdate;

import grafioschtrader.entities.Security;
import grafioschtrader.entities.Securitycurrency;
import grafioschtrader.rest.RequestMappings;

public abstract class BaseQuoteThru {
  protected final String LINK_DOWNLOAD_LAZY = "lazy"; 
  
  public static <S extends Securitycurrency<S>> String getDownlinkWithApiKey(S securitycurrency, boolean isIntraday) {
    return "--" + RequestMappings.WATCHLIST_MAP + RequestMappings.SECURITY_DATAPROVIDER_RESPONSE
        + securitycurrency.getIdSecuritycurrency() + "?isIntraday=" + isIntraday + "&isSecurity="
        + (securitycurrency instanceof Security ? true : false);
  }
}
