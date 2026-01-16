import {Component, Injector} from '@angular/core';
import {TranslateService, TranslateModule} from '@ngx-translate/core';
import {UserSettingsService} from '../../lib/services/user.settings.service';
import {DataType} from '../../lib/dynamic-form/models/data.type';
import {SecuritysplitService} from '../../securitycurrency/service/securitysplit.service';
import {MessageToastService} from '../../lib/message/message.toast.service';
import {GlobalparameterService} from '../../lib/services/globalparameter.service';
import {SplitPeriodTableBase} from './split.period.table.base';
import {CreateType, Securitysplit} from '../../entities/dividend.split';
import {SvgIconRegistryService, AngularSvgIconModule} from 'angular-svg-icon';
import {DividendSplitSvgCreator} from '../../shared/dividendsplit/dividend.split.svg.creator';
import {ColumnConfig} from '../../lib/datashowbase/column.config';
import {FilterService} from 'primeng/api';
import {CommonModule} from '@angular/common';
import {TableModule} from 'primeng/table';
import {ButtonModule} from 'primeng/button';

/**
 * Shows the table of splits
 */
@Component({
    selector: 'securitysplit-edit-table',
    templateUrl: './view/split.period.table.html',
    standalone: true,
    imports: [CommonModule, TranslateModule, TableModule, ButtonModule, AngularSvgIconModule]
})
export class SecuritysplitEditTableComponent extends SplitPeriodTableBase<Securitysplit> {

  constructor(private iconReg: SvgIconRegistryService,
              securitysplitService: SecuritysplitService,
              messageToastService: MessageToastService,
              filterService: FilterService,
              usersettingsService: UserSettingsService,
              translateService: TranslateService,
              gps: GlobalparameterService,
              injector: Injector) {
    super('splitDate', 'SECURITY_SPLITS_FROM_MAX', Securitysplit, messageToastService, securitysplitService,
      filterService, usersettingsService, translateService, gps, injector);
    this.addColumnFeqH(DataType.DateString, 'splitDate', true, false);
    this.addColumn(DataType.NumericInteger, 'createType', 'C', true, false,
      {fieldValueFN: this.getCreateTypeIcon.bind(this), templateName: 'icon', width: 20});
    this.addColumnFeqH(DataType.NumericInteger, 'fromFactor', true, false);
    this.addColumnFeqH(DataType.NumericInteger, 'toFactor', true, false);
    this.prepareTableAndTranslate();
    DividendSplitSvgCreator.registerIcons(iconReg);
  }

  public override addDataRow(rowData: Securitysplit): void {
    rowData.createType = CreateType.ADD_MODIFIED_USER;
    rowData.createModifyTime = new Date();
    super.addDataRow(rowData);
  }

  getCreateTypeIcon(entity: Securitysplit, field: ColumnConfig): string {
    return DividendSplitSvgCreator.createTypeIconMap[entity.createType];
  }

}


