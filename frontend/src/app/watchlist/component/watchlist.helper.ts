import {SecuritycurrencyPosition} from '../../entities/view/securitycurrency.position';
import {Security} from '../../entities/security';
import {Currencypair} from '../../entities/currencypair';
import {BusinessHelper} from '../../shared/helper/business.helper';
import {WatchlistService} from '../service/watchlist.service';
import {Securitycurrency} from '../../entities/securitycurrency';

/**
 * Is intended for static methods. This means that the methods cannot be in a possible inheritance or association.
 */
export class WatchlistHelper {
  public static readonly INTRADAY_URL = 'intradayUrl';
  public static readonly SECURITYCURRENCY = 'securitycurrency'

  public static getDownloadLinkHistoricalIntra(url: string, targetPage: string,
    securitycurrency: Securitycurrency, isIntra: boolean, watchlistService: WatchlistService): void {
    if (url === 'lazy') {
      watchlistService.getDataProviderLinkForUser(securitycurrency.idSecuritycurrency, isIntra,
        !(securitycurrency instanceof Currencypair)).subscribe(
        urlWebpage => BusinessHelper.toExternalWebpage(urlWebpage, targetPage))
    } else {
      BusinessHelper.toExternalWebpage(url, targetPage);
    }
  }
}
