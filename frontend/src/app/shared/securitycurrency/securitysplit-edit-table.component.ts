import { Component, inject } from '@angular/core';
import { TranslateModule } from '@ngx-translate/core';
import { DataType } from '../../lib/dynamic-form/models/data.type';
import { SecuritysplitService } from '../../securitycurrency/service/securitysplit.service';
import { SplitPeriodTableBase } from './split.period.table.base';
import { CreateType, Securitysplit } from '../../entities/dividend.split';
import { SvgIconRegistryService, AngularSvgIconModule } from 'angular-svg-icon';
import { DividendSplitSvgCreator } from '../../shared/dividendsplit/dividend.split.svg.creator';
import { ColumnConfig } from '../../lib/datashowbase/column.config';

import { TableModule } from '@openng/optimus-ui/table';
import { ButtonModule } from '@openng/optimus-ui/button';

/**
 * Shows the table of splits
 */
@Component({
  selector: 'securitysplit-edit-table',
  templateUrl: './view/split.period.table.html',
  imports: [TranslateModule, TableModule, ButtonModule, AngularSvgIconModule]
})
export class SecuritysplitEditTableComponent extends SplitPeriodTableBase<Securitysplit> {
  private iconReg: SvgIconRegistryService = inject(SvgIconRegistryService);

  constructor() {
    super('splitDate', 'SECURITY_SPLITS_FROM_MAX', Securitysplit, inject(SecuritysplitService));
    this.addColumnFeqH(DataType.DateString, 'splitDate', true, false);
    this.addColumn(DataType.NumericInteger, 'createType', 'C', true, false, {
      fieldValueFN: this.getCreateTypeIcon.bind(this),
      templateName: 'icon',
      width: 20
    });
    this.addColumnFeqH(DataType.NumericInteger, 'fromFactor', true, false);
    this.addColumnFeqH(DataType.NumericInteger, 'toFactor', true, false);
    this.prepareTableAndTranslate();
    DividendSplitSvgCreator.registerIcons(this.iconReg);
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
