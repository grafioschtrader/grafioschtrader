import { Component, computed, OnInit, input } from '@angular/core';
import { AnnualisedYears } from '../../entities/view/instrument.statistics.result';
import { TranslateModule } from '@ngx-translate/core';
import { DataType } from '../../lib/dynamic-form/models/data.type';
import { TableConfigBase } from '../../lib/datashowbase/table.config.base';

import { TableModule } from '@openng/optimus-ui/table';
import { TooltipModule } from '@openng/optimus-ui/tooltip';

/**
 * Shows the annualised return for an instrument.
 */
@Component({
  selector: 'instrument-annualised-return-table',
  templateUrl: './view/instrument.statistic.table.html',
  imports: [TranslateModule, TableModule, TooltipModule]
})
export class InstrumentAnnualisedReturnComponent extends TableConfigBase implements OnInit {
  readonly values = input<AnnualisedYears[]>(undefined);
  readonly mainCurrency = input<string>();
  datakey = 'numberOfYears';
  groupTitle = 'ANNUALISED_RETURN';
  titleRemark = 'WITHOUT_CURRENT_YEAR';
  sortOrder = 1;
  /** A new columns reference re-renders the header and body templates of the OnPush p-table after translation. */
  readonly tableFields = computed(() => {
    this.fieldsRevision();
    return [...this.fields];
  });

  constructor() {
    super();
  }

  ngOnInit(): void {
    this.addColumnFeqH(DataType.String, 'numberOfYears', true, false);
    this.addColumn(DataType.Numeric, 'performanceAnnualised', 'PERFORMANCE', true, false, {
      templateName: 'greenRed',
      headerSuffix: '%'
    });
    const mainCurrency = this.mainCurrency();
    this.addColumn(DataType.Numeric, 'performanceAnnualisedMC', 'PERFORMANCE', true, false, {
      templateName: 'greenRed',
      headerSuffix: (mainCurrency ? mainCurrency + ' ' : '') + '%'
    });
    this.addColumn(DataType.Numeric, 'sharpeRatio', 'SHARPE_RATIO', true, false, { templateName: 'greenRed' });
    this.addColumn(DataType.Numeric, 'sharpeRatioMC', 'SHARPE_RATIO', true, false, {
      templateName: 'greenRed',
      headerSuffix: mainCurrency ? mainCurrency : ''
    });
    this.prepareTableAndTranslate();
  }
}
