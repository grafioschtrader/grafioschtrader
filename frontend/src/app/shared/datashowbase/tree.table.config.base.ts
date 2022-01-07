import {TranslateService} from '@ngx-translate/core';
import {GlobalparameterService} from '../service/globalparameter.service';
import {TableTreetableTotalBase} from './table.treetable.total.base';
import {TreeNode} from 'primeng/api';
import {TranslateHelper} from '../helper/translate.helper';
import {AppSettings} from '../app.settings';

export abstract class TreeTableConfigBase extends TableTreetableTotalBase {

  constructor(translateService: TranslateService, gps: GlobalparameterService) {
    super(translateService, gps);
  }

  createTranslateValuesStoreForTranslation(root: TreeNode[]): void {
    const columnConfigs = this.fields.filter(columnConfig => !!columnConfig.translateValues);
    if (root === null) {
      return;
    }
    const q: TreeNode[] = [].concat(root);
    while (q.length !== 0) {
      let n = q.length;

      while (n > 0) {
        const p: TreeNode = q[0];
        q.shift();
        TranslateHelper.createTranslatedValueStoreForTranslation(this.translateService, columnConfigs, p.data);
        if (p.children) {
            q.push(...p.children);
        }
        n--;
      }
    }
    columnConfigs.forEach(columnConfig => columnConfig.fieldTranslated = columnConfig.field + AppSettings.FIELD_SUFFIX);
  }
}
