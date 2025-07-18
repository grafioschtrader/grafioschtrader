import {SecuritycurrencyPosition} from '../../entities/view/securitycurrency.position';
import {Security} from '../../entities/security';
import {Currencypair} from '../../entities/currencypair';
import {BusinessHelper} from '../../shared/helper/business.helper';
import {WatchlistService} from '../service/watchlist.service';
import {Securitycurrency} from '../../entities/securitycurrency';
import {AppHelper} from '../../lib/helper/app.helper';

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
        urlWebpage => AppHelper.toExternalWebpage(urlWebpage, targetPage))
    } else {
      AppHelper.toExternalWebpage(url, targetPage);
    }
  }
}
