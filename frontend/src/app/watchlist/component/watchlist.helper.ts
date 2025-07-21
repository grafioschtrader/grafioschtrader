import {Currencypair} from '../../entities/currencypair';
import {WatchlistService} from '../service/watchlist.service';
import {Securitycurrency} from '../../entities/securitycurrency';
import {AppHelper} from '../../lib/helper/app.helper';

/**
 * Utility class providing static helper methods for watchlist operations. Contains methods that cannot be in a
 * possible inheritance or association structure.
 */
export class WatchlistHelper {
  /** URL key for intraday data provider links */
  public static readonly INTRADAY_URL = 'intradayUrl';

  /** Base path key for securitycurrency objects */
  public static readonly SECURITYCURRENCY = 'securitycurrency'

  /**
   * Opens a download link for historical or intraday data. Handles both direct URLs and lazy-loaded provider links.
   *
   * @param url The URL to open, or 'lazy' to fetch from data provider
   * @param targetPage The target page identifier for the opened window
   * @param securitycurrency The security or currency pair to get data for
   * @param isIntra Whether this is for intraday data (true) or historical data (false)
   * @param watchlistService Service to fetch data provider links when URL is 'lazy'
   */
  public static getDownloadLinkHistoricalIntra(url: string, targetPage: string,
    securitycurrency: Securitycurrency, isIntra: boolean, watchlistService: WatchlistService): void {
    if (url === 'lazy') {
      watchlistService.getDataProviderLinkForUser(securitycurrency.idSecuritycurrency, isIntra,
        !(securitycurrency instanceof Currencypair)).subscribe(
        urlWebpage => AppHelper.toExternalWebpage(urlWebpage, targetPage))
    } else {
      AppHelper.toExternalWebpage(url, targetPage);
    }
  }
}
