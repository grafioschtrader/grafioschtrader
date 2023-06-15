import {Component} from '@angular/core';
import {TranslateService} from '@ngx-translate/core';
import {UserSettingsService} from '../../shared/service/user.settings.service';
import {DataType} from '../../dynamic-form/models/data.type';
import {SecuritysplitService} from '../service/securitysplit.service';
import {MessageToastService} from '../../shared/message/message.toast.service';
import {GlobalparameterService} from '../../shared/service/globalparameter.service';
import {SplitPeriodTableBase} from './split.period.table.base';
import {CreateType, Securitysplit} from '../../entities/dividend.split';
import {SvgIconRegistryService} from 'angular-svg-icon';
import {DividendSplitSvgCreator} from '../../shared/dividendsplit/dividend.split.svg.creator';
import {ColumnConfig} from '../../shared/datashowbase/column.config';
import {FilterService} from 'primeng/api';

/**
 * Shows the table of splits
 */
@Component({
  selector: 'securitysplit-edit-table',
  templateUrl: '../view/split.period.table.html'
})
export class SecuritysplitEditTableComponent extends SplitPeriodTableBase<Securitysplit> {

  constructor(private iconReg: SvgIconRegistryService,
              securitysplitService: SecuritysplitService,
              messageToastService: MessageToastService,
              filterService: FilterService,
              usersettingsService: UserSettingsService,
              translateService: TranslateService,
              gps: GlobalparameterService) {
    super('splitDate', 'SECURITY_SPLITS_FROM_MAX', Securitysplit, messageToastService, securitysplitService,
      filterService, usersettingsService, translateService, gps);
    this.addColumnFeqH(DataType.DateString, 'splitDate', true, false);
    this.addColumn(DataType.NumericInteger, 'createType', 'C', true, false,
      {fieldValueFN: this.getCreateTypeIcon.bind(this), templateName: 'icon', width: 20});
    this.addColumnFeqH(DataType.NumericInteger, 'fromFactor', true, false);
    this.addColumnFeqH(DataType.NumericInteger, 'toFactor', true, false);
    this.prepareTableAndTranslate();
    DividendSplitSvgCreator.registerIcons(iconReg);
  }

  public addDataRow(rowData: Securitysplit): void {
    rowData.createType = CreateType.ADD_MODIFIED_USER;
    rowData.createModifyTime = new Date();
    super.addDataRow(rowData);
  }

  getCreateTypeIcon(entity: Securitysplit, field: ColumnConfig): string {
    return DividendSplitSvgCreator.createTypeIconMap[entity.createType];
  }

}


