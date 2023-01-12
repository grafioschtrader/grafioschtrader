import {EntityMapping, PrepareCallParam} from './request.for.you.table.component';
import {StockexchangeService} from '../../stockexchange/service/stockexchange.service';
import {Stockexchange} from '../../entities/stockexchange';
import {StockexchangeCallParam} from '../../stockexchange/component/stockexchange.call.param';
import {BasePrepareEdit} from './base.prepare.edit';
import {GlobalparameterService} from '../../shared/service/globalparameter.service';
import {StockexchangeBaseData} from '../../stockexchange/model/stockexchange.base.data';

export class StockexchangePrepareEdit extends BasePrepareEdit implements PrepareCallParam {
  constructor(private stockexchangeService: StockexchangeService,
              private gps: GlobalparameterService) {
    super();
  }

  prepareForEditEntity(entity: Stockexchange, entityMapping: EntityMapping): void {
    this.stockexchangeService.getAllStockexchangesBaseData().subscribe((sbd: StockexchangeBaseData) => {
      entityMapping.callParam = new StockexchangeCallParam();
      entityMapping.callParam.stockexchange = new Stockexchange();
      Object.assign(entityMapping.callParam.stockexchange, entity);
      entityMapping.callParam.hasSecurity = sbd.hasSecurity;
      entityMapping.callParam.countriesAsHtmlOptions = sbd.countries;
      entityMapping.callParam.stockexchangeMics = sbd.stockexchangeMics;
      entityMapping.callParam.existingMic = new Set(sbd.stockexchanges.map(se => se.mic));
      entityMapping.callParam.proposeChange = true;
      entityMapping.visibleDialog = true;
    });
  }
}
