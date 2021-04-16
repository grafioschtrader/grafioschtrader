import {EntityMapping, PrepareCallParam} from './request.for.you.table.component';
import {StockexchangeService} from '../../stockexchange/service/stockexchange.service';
import {Stockexchange} from '../../entities/stockexchange';
import {StockexchangeCallParam} from '../../stockexchange/component/stockexchange.call.param';
import {BasePrepareEdit} from './base.prepare.edit';
import {combineLatest} from 'rxjs';
import {GlobalparameterService} from '../../shared/service/globalparameter.service';

export class StockexchangePrepareEdit extends BasePrepareEdit implements PrepareCallParam {
  constructor(private stockexchangeService: StockexchangeService,
              private globalparameterService: GlobalparameterService) {
    super();
  }

  prepareForEditEntity(entity: Stockexchange, entityMapping: EntityMapping): void {
    combineLatest([this.stockexchangeService.stockexchangeHasSecurity(entity.idStockexchange),
      this.globalparameterService.getCountries()]).subscribe(data => {
      entityMapping.callParam = new StockexchangeCallParam();
      entityMapping.callParam.stockexchange = new Stockexchange();
      Object.assign(entityMapping.callParam.stockexchange, entity);
      entityMapping.callParam.hasSecurity = data[0];
      entityMapping.callParam.countriesAsHtmlOptions = data[1];
      entityMapping.visibleDialog = true;
    });
  }
}
