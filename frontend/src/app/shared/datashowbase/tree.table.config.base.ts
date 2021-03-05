import {ShowRecordConfigBase} from './show.record.config.base';
import {TranslateService} from '@ngx-translate/core';
import {GlobalparameterService} from '../service/globalparameter.service';
import {DataType} from '../../dynamic-form/models/data.type';
import {TableTreetableTotalBase} from './table.treetable.total.base';

export abstract class TreeTableConfigBase extends TableTreetableTotalBase {

  constructor(translateService: TranslateService, globalparameterService: GlobalparameterService) {
    super(translateService, globalparameterService);
  }
}
