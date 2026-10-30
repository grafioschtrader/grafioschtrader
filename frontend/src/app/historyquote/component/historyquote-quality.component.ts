import { Component, OnInit, input, inject } from '@angular/core';

import { SingleRecordConfigBase } from '../../lib/datashowbase/single.record.config.base';
import { DataType } from '../../lib/dynamic-form/models/data.type';
import { IHistoryquoteQuality } from '../../entities/view/ihistoryquote.quality';
import { Securitycurrency } from '../../entities/securitycurrency';
import { ColumnConfig } from '../../lib/datashowbase/column.config';
import { SecurityService } from '../../securitycurrency/service/security.service';
import { SecurityCurrencyHelper } from '../../securitycurrency/service/security.currency.helper';
import { CurrencypairService } from '../../securitycurrency/service/currencypair.service';
import { TooltipModule } from '@openng/optimus-ui/tooltip';

/**
 * Shows some statistical data on the quality of the historical price data
 */
@Component({
  selector: 'historyquote-quality',
  template: `
    <div class="gbox">
      @for (field of fields; track field) {
        <div>
          <div class="glabel" [pTooltip]="field.headerTooltipTranslated">
            {{ field.headerTranslated }}
          </div>
          <div class="gvalue">
            {{ getValueByPath(historyquoteQuality(), field) }}
          </div>
        </div>
      }
    </div>
  `,
  imports: [TooltipModule]
})
export class HistoryquoteQualityComponent extends SingleRecordConfigBase implements OnInit {
  private securityService: SecurityService = inject(SecurityService);
  private currencypairService: CurrencypairService = inject(CurrencypairService);

  readonly historyquoteQuality = input<IHistoryquoteQuality>();
  readonly securitycurrency = input<Securitycurrency>();
  private feedConnectorsKV: { [id: string]: string } = {};

  constructor() {
    super();
  }

  ngOnInit(): void {
    this.addFieldPropertyFeqH(DataType.DateString, 'minDate');
    this.addFieldPropertyFeqH(DataType.DateString, 'maxDate');
    this.addFieldPropertyFeqH(DataType.NumericInteger, 'missingStart');
    this.addFieldPropertyFeqH(DataType.NumericInteger, 'missingEnd');
    this.addFieldPropertyFeqH(DataType.NumericInteger, 'totalMissing');
    this.addFieldPropertyFeqH(DataType.NumericInteger, 'expectedTotal');
    this.addFieldPropertyFeqH(DataType.Numeric, 'qualityPercentage', {
      headerSuffix: '%'
    });
    this.addFieldPropertyFeqH(DataType.Numeric, 'ohlPercentage', {
      headerSuffix: '%'
    });
    this.addColumn(DataType.String, 'idConnectorHistory', 'HISTORY_DATA_PROVIDER', true, true, {
      fieldValueFN: this.getFeedConnectorReadableName.bind(this)
    });
    this.addFieldPropertyFeqH(DataType.NumericInteger, 'toManyAsCalendar');
    this.addFieldPropertyFeqH(DataType.NumericInteger, 'quoteSaturday');
    this.addFieldPropertyFeqH(DataType.NumericInteger, 'quoteSunday');
    this.addFieldPropertyFeqH(DataType.NumericInteger, 'connectorCreated');
    this.addFieldPropertyFeqH(DataType.NumericInteger, 'manualImported');
    this.addFieldPropertyFeqH(DataType.NumericInteger, 'filledLinear');
    this.addFieldPropertyFeqH(DataType.NumericInteger, 'calculated');
    this.addFieldPropertyFeqH(DataType.NumericInteger, 'userModified');

    this.translateHeadersAndColumns();
    SecurityCurrencyHelper.loadAllConnectors(
      this.securityService,
      this.currencypairService,
      this.feedConnectorsKV,
      () => this.notifyViewChanged()
    );
  }

  getFeedConnectorReadableName(dataobject: unknown, field: ColumnConfig, valueField: unknown): string | null {
    const connector = this.securitycurrency()?.idConnectorHistory;
    return Object.entries(this.feedConnectorsKV).length !== 0 && connector != null
      ? (this.feedConnectorsKV[connector] ?? null)
      : null;
  }
}
