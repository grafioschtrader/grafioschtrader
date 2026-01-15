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
      dataKey="_uniqueKey"
      [selectionMode]="'single'"
      [(selection)]="selectedSecurity"
      (rowSelect)="onRowSelect($event)"
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

  @Input() securities: SecurityGtnetLookupDTO[] = [];

  @Output() securitySelected = new EventEmitter<SecurityGtnetLookupDTO>();

  selectedSecurity: SecurityGtnetLookupDTO;

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

  /**
   * Handles row selection event from the table.
   * Explicitly sets selectedSecurity to ensure the selection is captured.
   */
  onRowSelect(event: any): void {
    this.selectedSecurity = event.data;
  }

  applySelected(): void {
    if (this.selectedSecurity) {
      this.securitySelected.emit(this.selectedSecurity);
    }
  }
}
