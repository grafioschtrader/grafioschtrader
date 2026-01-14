import {Component, EventEmitter, Input, OnInit, Output} from '@angular/core';
import {CommonModule} from '@angular/common';
import {TranslateModule, TranslateService} from '@ngx-translate/core';
import {ButtonModule} from 'primeng/button';

import {ShowRecordConfigBase} from '../../lib/datashowbase/show.record.config.base';
import {ConfigurableTableComponent} from '../../lib/datashowbase/configurable-table.component';
import {DataType} from '../../lib/dynamic-form/models/data.type';
import {TranslateValue} from '../../lib/datashowbase/column.config';
import {GlobalparameterService} from '../../lib/services/globalparameter.service';
import {SecurityGtnetLookupDTO} from '../model/gtnet-security-lookup';

/**
 * Table component for displaying security lookup results from GTNet peers.
 * Displays a list of SecurityGtnetLookupDTO entries with single selection support.
 * When user selects an entry and clicks "Apply Selected", emits the selected DTO.
 */
@Component({
  selector: 'gtnet-security-lookup-table',
  standalone: true,
  imports: [CommonModule, ConfigurableTableComponent, TranslateModule, ButtonModule],
  template: `
    <configurable-table
      [data]="securities"
      [fields]="fields"
      dataKey="isin"
      [selectionMode]="'single'"
      [(selection)]="selectedSecurity"
      [paginator]="securities.length > 10"
      [rows]="10"
      [enableCustomSort]="true"
      [valueGetterFn]="getValueByPath.bind(this)"
      [contextMenuEnabled]="false"
      [baseLocale]="baseLocale">
    </configurable-table>
    <div class="flex justify-content-end mt-3">
      <p-button [label]="'APPLY_SELECTED' | translate"
                icon="pi pi-check"
                (onClick)="applySelected()"
                [disabled]="!selectedSecurity">
      </p-button>
    </div>
  `
})
export class GtnetSecurityLookupTableComponent extends ShowRecordConfigBase implements OnInit {

  @Input() securities: SecurityGtnetLookupDTO[] = [];

  @Output() securitySelected = new EventEmitter<SecurityGtnetLookupDTO>();

  selectedSecurity: SecurityGtnetLookupDTO;

  constructor(translateService: TranslateService, gps: GlobalparameterService) {
    super(translateService, gps);
  }

  ngOnInit(): void {
    this.addColumnFeqH(DataType.String, 'name', true, false, {width: 200});
    this.addColumn(DataType.String, 'isin', 'ISIN', true, false, {width: 120});
    this.addColumnFeqH(DataType.String, 'currency', true, false, {width: 60});
    this.addColumnFeqH(DataType.String, 'tickerSymbol', true, false, {width: 80});
    this.addColumn(DataType.String, 'stockexchangeName', 'STOCKEXCHANGE', true, false, {width: 150});
    this.addColumn(DataType.String, 'categoryType', 'ASSETCLASS', true, false,
      {width: 100, translateValues: TranslateValue.NORMAL});
    this.addColumn(DataType.String, 'specialInvestmentInstrument', 'FINANCIAL_INSTRUMENT', true, false,
      {width: 120, translateValues: TranslateValue.NORMAL});
    this.addColumn(DataType.String, 'sourceDomain', 'SOURCE_DOMAIN', true, false, {width: 150});

    this.translateHeadersAndColumns();
  }

  /**
   * Called when data is loaded to update translated value store.
   */
  onDataLoaded(): void {
    if (this.securities && this.securities.length > 0) {
      this.createTranslatedValueStore(this.securities);
    }
  }

  applySelected(): void {
    if (this.selectedSecurity) {
      this.securitySelected.emit(this.selectedSecurity);
    }
  }
}
