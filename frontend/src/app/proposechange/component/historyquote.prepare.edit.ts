import {EntityMapping, PrepareCallParam} from './request.for.you.table.component';
import {Historyquote} from '../../entities/historyquote';
import {SecurityService} from '../../securitycurrency/service/security.service';
import {CurrencypairService} from '../../securitycurrency/service/currencypair.service';
import {combineLatest} from 'rxjs';
import {HistoryquoteSecurityCurrency} from '../../historyquote/component/historyquote-table.component';
import {Currencypair} from '../../entities/currencypair';
import {Security} from '../../entities/security';
import {BasePrepareEdit} from './base.prepare.edit';

export class HistoryquotePrepareEdit extends BasePrepareEdit implements PrepareCallParam {

  constructor(private securityService: SecurityService, private currencypairService: CurrencypairService) {
    super();
  }

  prepareForEditEntity(entity: Historyquote, entityMapping: EntityMapping): void {
    const cpObservable = this.currencypairService.getCurrencypairByIdSecuritycurrency(entity.idSecuritycurrency);
    const securityObservable = this.securityService.getSecurityByIdSecuritycurrency(entity.idSecuritycurrency);

    combineLatest(cpObservable, securityObservable).subscribe((data: any[]) => {
      const historyquote = new Historyquote();
      Object.assign(historyquote, entity);
      /*
            if(data[0]) {
              // const currencypair = new Currencypair();
              entityMapping.callParam = new HistoryquoteSecurityCurrency(historyquote, Object.assign(new Currencypair(), data[0]));
            } else {
              const security = new Security();
              entityMapping.callParam = new HistoryquoteSecurityCurrency(historyquote, Object.assign(new Security(), data[1]));
            }
       */
      entityMapping.callParam = data[0] ? new HistoryquoteSecurityCurrency(historyquote, Object.assign(new Currencypair(), data[0])) :
        new HistoryquoteSecurityCurrency(historyquote, Object.assign(new Security(), data[1]));
      entityMapping.visibleDialog = true;
    });

  }
}
