import {StockexchangeService} from '../../stockexchange/service/stockexchange.service';
import {Stockexchange} from '../../entities/stockexchange';
import {StockexchangeCallParam} from '../../stockexchange/component/stockexchange.call.param';
import {Observable} from 'rxjs';
import {map} from 'rxjs/operators';
import {BasePrepareEdit} from '../../lib/proposechange/component/base.prepare.edit';
import {StockexchangeBaseData} from '../../stockexchange/model/stockexchange.base.data';
import {GlobalparameterService} from '../../lib/services/globalparameter.service';
import {EntityMapping, PrepareCallParam} from '../../lib/proposechange/component/general.entity.prepare.edit';

/**
 * Preparation handler for Stockexchange entities in the propose change workflow.
 * Loads stock exchange base data including country lists, existing MIC codes, and security associations
 * needed for the stock exchange edit dialog.
 */
export class StockexchangePrepareEdit extends BasePrepareEdit<Stockexchange> implements PrepareCallParam<Stockexchange> {
  /**
   * Creates a stock exchange preparation handler.
   *
   * @param stockexchangeService - Service for loading stock exchange data
   * @param gps - Global parameter service for system configuration
   */
  constructor(private stockexchangeService: StockexchangeService,
    private gps: GlobalparameterService) {
    super();
  }

  /**
   * Prepares a stock exchange for editing by loading base data asynchronously.
   * Fetches all stock exchanges, countries, MIC codes, and security associations
   * to populate the edit dialog with validation data.
   *
   * @param entity - The stock exchange to prepare for editing
   * @param entityMapping - Container for dialog state and parameters to be populated
   * @returns Observable that completes when base data is loaded
   */
  prepareForEditEntity(entity: Stockexchange, entityMapping: EntityMapping): Observable<void> {
    return this.stockexchangeService.getAllStockexchangesBaseData().pipe(
      map((sbd: StockexchangeBaseData) => {
        entityMapping.callParam = new StockexchangeCallParam();
        entityMapping.callParam.stockexchange = new Stockexchange();
        Object.assign(entityMapping.callParam.stockexchange, entity);
        entityMapping.callParam.hasSecurity = sbd.hasSecurity;
        entityMapping.callParam.countriesAsHtmlOptions = sbd.countries;
        entityMapping.callParam.stockexchangeMics = sbd.stockexchangeMics;
        entityMapping.callParam.existingMic = new Set(sbd.stockexchanges.map(se => se.mic));
        entityMapping.callParam.proposeChange = true;
        entityMapping.visibleDialog = true;
      })
    );
  }
}
