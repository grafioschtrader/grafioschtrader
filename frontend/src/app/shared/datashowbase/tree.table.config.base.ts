import {TranslateService} from '@ngx-translate/core';
import {GlobalparameterService} from '../service/globalparameter.service';
import {TableTreetableTotalBase} from './table.treetable.total.base';

export abstract class TreeTableConfigBase extends TableTreetableTotalBase {

  constructor(translateService: TranslateService, gps: GlobalparameterService) {
    super(translateService, gps);
  }
}
