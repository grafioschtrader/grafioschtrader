import {Component, EventEmitter, Input, OnChanges, OnInit, Output, SimpleChanges} from '@angular/core';
import {CommonModule} from '@angular/common';
import {TranslateModule, TranslateService} from '@ngx-translate/core';
import {ButtonModule} from 'primeng/button';
import {FilterService} from 'primeng/api';

import {TableConfigBase} from '../../lib/datashowbase/table.config.base';
import {ConfigurableTableComponent} from '../../lib/datashowbase/configurable-table.component';
import {DataType} from '../../lib/dynamic-form/models/data.type';
import {TranslateValue} from '../../lib/datashowbase/column.config';
import {GlobalparameterService} from '../../lib/services/globalparameter.service';
import {UserSettingsService} from '../../lib/services/user.settings.service';
import {SecurityGtnetLookupWithMatch} from '../model/gtnet-security-lookup';

/**
 * Table component for displaying security lookup results from GTNet peers.
 * Displays a list of SecurityGtnetLookupWithMatch entries with single selection support.
 * Shows connector match score to indicate how well each result matches local connector configuration.
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
      dataKey="_uniqueKey"
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
export class GtnetSecurityLookupTableComponent extends TableConfigBase implements OnInit, OnChanges {

  @Input() securities: SecurityGtnetLookupWithMatch[] = [];

  @Output() securitySelected = new EventEmitter<SecurityGtnetLookupWithMatch>();

  selectedSecurity: SecurityGtnetLookupWithMatch;

  constructor(filterService: FilterService,
              usersettingsService: UserSettingsService,
              translateService: TranslateService,
              gps: GlobalparameterService) {
    super(filterService, usersettingsService, translateService, gps);
  }

  ngOnChanges(changes: SimpleChanges): void {
    if (changes['securities'] && this.securities) {
      this.addUniqueKeys();
      this.createTranslatedValueStore(this.securities);
    }
  }

  ngOnInit(): void {
    this.addColumn(DataType.NumericInteger, 'connectorMatchScore', 'CONNECTOR_MATCH', true, false, {width: 80});
    this.addColumnFeqH(DataType.String, 'name', true, false, {width: 200});
    this.addColumn(DataType.String, 'isin', 'ISIN', true, false, {width: 120});
    this.addColumnFeqH(DataType.String, 'currency', true, false, {width: 60});
    this.addColumnFeqH(DataType.String, 'tickerSymbol', true, false, {width: 80});
    this.addColumn(DataType.String, 'stockexchangeName', 'STOCKEXCHANGE', true, false, {width: 150});
    this.addColumn(DataType.String, 'categoryType', 'ASSETCLASS', true, false,
      {width: 100, translateValues: TranslateValue.NORMAL});
    this.addColumn(DataType.String, 'specialInvestmentInstrument', 'FINANCIAL_INSTRUMENT', true, false,
      {width: 120, translateValues: TranslateValue.NORMAL});
    this.addColumn(DataType.String, 'sourceDomain', 'SOURCE_DOMAIN', true, false, {width: 120});

    this.prepareTableAndTranslate();
  }

  /**
   * Adds unique keys to each security for table row identification.
   * Combines isin, stockexchangeMic, and sourceDomain to create a unique identifier.
   */
  private addUniqueKeys(): void {
    this.securities.forEach((security, index) => {
      (security as any)._uniqueKey = `${security.isin}_${security.stockexchangeMic}_${security.sourceDomain || index}`;
    });
  }

  applySelected(): void {
    if (this.selectedSecurity) {
      this.securitySelected.emit(this.selectedSecurity);
    }
  }
}
