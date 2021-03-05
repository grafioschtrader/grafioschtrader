import {EntityMapping, PrepareCallParam} from './request.for.you.table.component';
import {StockexchangeService} from '../../stockexchange/service/stockexchange.service';
import {Stockexchange} from '../../entities/stockexchange';
import {StockexchangeCallParam} from '../../stockexchange/component/stockexchange.call.param';
import {BasePrepareEdit} from './base.prepare.edit';

export class StockexchangePrepareEdit extends BasePrepareEdit implements PrepareCallParam {
  constructor(private stockexchangeService: StockexchangeService) {
    super();
  }

  prepareForEditEntity(entity: Stockexchange, entityMapping: EntityMapping): void {
    this.stockexchangeService.stockexchangeHasSecurity(entity.idStockexchange).subscribe(hasSecurity => {
      entityMapping.callParam = new StockexchangeCallParam();
      entityMapping.callParam.stockexchange = new Stockexchange();
      Object.assign(entityMapping.callParam.stockexchange, entity);
      entityMapping.callParam.hasSecurity = hasSecurity;
      entityMapping.visibleDialog = true;
    });
  }
}
