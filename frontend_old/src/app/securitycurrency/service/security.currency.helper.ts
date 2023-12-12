import {combineLatest} from 'rxjs';
import {IFeedConnector} from '../component/ifeed.connector';
import {SecurityService} from './security.service';
import {CurrencypairService} from './currencypair.service';

export class SecurityCurrencyHelper {
  public static loadAllConnectors(securityService: SecurityService, currencypairService: CurrencypairService,
                                  feedConnectorsKV: { [id: string]: string }, afterLoadCallback?: () => void): void {
    const feedConSecurityObs = securityService.getFeedConnectors();
    const feedConCurrencypairObs = currencypairService.getFeedConnectors();
    combineLatest([feedConSecurityObs, feedConCurrencypairObs]).subscribe(result => {
      const feedConnectors: IFeedConnector[] = result[0].concat(result[1]);
      Object.assign(feedConnectorsKV, ...feedConnectors.map((feedConnector) =>
        ({[feedConnector.id]: feedConnector.readableName})));
      afterLoadCallback && afterLoadCallback();
    });
  }
}
