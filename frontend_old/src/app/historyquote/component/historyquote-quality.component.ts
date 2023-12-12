import {Component, Input, OnInit} from '@angular/core';
import {SingleRecordConfigBase} from '../../shared/datashowbase/single.record.config.base';
import {TranslateService} from '@ngx-translate/core';
import {GlobalparameterService} from '../../shared/service/globalparameter.service';
import {DataType} from '../../dynamic-form/models/data.type';
import {IHistoryquoteQuality} from '../../entities/view/ihistoryquote.quality';
import {Securitycurrency} from '../../entities/securitycurrency';
import {ColumnConfig} from '../../shared/datashowbase/column.config';
import {SecurityService} from '../../securitycurrency/service/security.service';
import {SecurityCurrencyHelper} from '../../securitycurrency/service/security.currency.helper';
import {CurrencypairService} from '../../securitycurrency/service/currencypair.service';

/**
 * Shows some statistical data on the quality of the historical price data
 */
@Component({
  selector: 'historyquote-quality',
  template: `
    <div class="gbox">
      <div *ngFor="let field of fields" >
        <div class="glabel" [pTooltip]="field.headerTooltipTranslated">
          {{field.headerTranslated}}
        </div>
        <div class="gvalue">
          {{getValueByPath(historyquoteQuality, field)}}
        </div>
      </div>
    </div>
  `
})
export class HistoryquoteQualityComponent extends SingleRecordConfigBase implements OnInit {
  @Input() historyquoteQuality: IHistoryquoteQuality;
  @Input() securitycurrency: Securitycurrency;
  private feedConnectorsKV: { [id: string]: string } = {};

  constructor(private securityService: SecurityService,
              private currencypairService: CurrencypairService,
              translateService: TranslateService,
              gps: GlobalparameterService) {
    super(translateService, gps);
  }

  ngOnInit(): void {
    this.addFieldPropertyFeqH(DataType.DateString, 'minDate');
    this.addFieldPropertyFeqH(DataType.DateString, 'maxDate');
    this.addFieldPropertyFeqH(DataType.NumericInteger, 'missingStart');
    this.addFieldPropertyFeqH(DataType.NumericInteger, 'missingEnd');
    this.addFieldPropertyFeqH(DataType.NumericInteger, 'totalMissing');
    this.addFieldPropertyFeqH(DataType.NumericInteger, 'expectedTotal');
    this.addFieldPropertyFeqH(DataType.Numeric, 'qualityPercentage', {headerSuffix: '%'});
    this.addColumn(DataType.String, 'idConnectorHistory', 'HISTORY_DATA_PROVIDER', true, true,
      {fieldValueFN: this.getFeedConnectorReadableName.bind(this)});
    this.addFieldPropertyFeqH(DataType.NumericInteger, 'toManyAsCalendar');
    this.addFieldPropertyFeqH(DataType.NumericInteger, 'quoteSaturday');
    this.addFieldPropertyFeqH(DataType.NumericInteger, 'quoteSunday');
    this.addFieldPropertyFeqH(DataType.NumericInteger, 'connectorCreated');
    this.addFieldPropertyFeqH(DataType.NumericInteger, 'manualImported');
    this.addFieldPropertyFeqH(DataType.NumericInteger, 'filledLinear');
    this.addFieldPropertyFeqH(DataType.NumericInteger, 'calculated');
    this.addFieldPropertyFeqH(DataType.NumericInteger, 'userModified');

    this.translateHeadersAndColumns();
    SecurityCurrencyHelper.loadAllConnectors(this.securityService, this.currencypairService, this.feedConnectorsKV);
  }

  getFeedConnectorReadableName(dataobject: any, field: ColumnConfig, valueField: any): string {
    return Object.entries(this.feedConnectorsKV).length !== 0 && this.securitycurrency
      ? this.feedConnectorsKV[this.securitycurrency.idConnectorHistory] : null;
  }
}
