import {combineLatest, Observable} from 'rxjs';
import {map} from 'rxjs/operators';

import {HistoryquoteLegacy} from '../../entities/historyquote.legacy';
import {HistoryquoteLegacySecurityCurrency} from '../../historyquote/component/historyquote-legacy-edit.component';
import {SecurityService} from '../../securitycurrency/service/security.service';
import {CurrencypairService} from '../../securitycurrency/service/currencypair.service';
import {Currencypair} from '../../entities/currencypair';
import {Security} from '../../entities/security';
import {BasePrepareEdit} from '../../lib/proposechange/component/base.prepare.edit';
import {EntityMapping, PrepareCallParam} from '../../lib/proposechange/component/general.entity.prepare.edit';

/**
 * Preparation handler for HistoryquoteLegacy entities in the propose-change workflow. Loads the associated security or
 * currency pair so the edit dialog has the auditable parent for rights/diff display. Mirrors
 * {@link HistoryquotePrepareEdit} for the live history quote.
 */
export class HistoryquoteLegacyPrepareEdit extends BasePrepareEdit<HistoryquoteLegacy>
  implements PrepareCallParam<HistoryquoteLegacy> {

  constructor(private securityService: SecurityService, private currencypairService: CurrencypairService) {
    super();
  }

  prepareForEditEntity(entity: HistoryquoteLegacy, entityMapping: EntityMapping): Observable<void> {
    const cpObservable = this.currencypairService.getCurrencypairByIdSecuritycurrency(entity.idSecuritycurrency);
    const securityObservable = this.securityService.getSecurityByIdSecuritycurrency(entity.idSecuritycurrency);

    return combineLatest([cpObservable, securityObservable]).pipe(
      map((data: any[]) => {
        const historyquoteLegacy = new HistoryquoteLegacy();
        Object.assign(historyquoteLegacy, entity);
        entityMapping.callParam = data[0]
          ? new HistoryquoteLegacySecurityCurrency(historyquoteLegacy, Object.assign(new Currencypair(), data[0]))
          : new HistoryquoteLegacySecurityCurrency(historyquoteLegacy, Object.assign(new Security(), data[1]));
        entityMapping.visibleDialog = true;
      })
    );
  }
}
