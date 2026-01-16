import {Component, EventEmitter, Injector, Input, OnChanges, OnInit, Output, SimpleChanges} from '@angular/core';
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
import {AppSettings} from '../../shared/app.settings';

/**
 * Table component for displaying security lookup results from GTNet peers.
 * Displays a list of SecurityGtnetLookupDTO entries with single selection support.
 * Rows are expandable to show connector configuration boxes (History, Intraday, Dividend, Split).
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
      [baseLocale]="baseLocale"
      [expandable]="true"
      [expandedRowTemplate]="connectorExpansion">
    </configurable-table>

    <ng-template #connectorExpansion let-security>
      <div class="connector-expansion">
        <div class="connector-boxes">
          <!-- History Connector Box -->
          @if (security.matchedHistoryConnector) {
            <fieldset class="connector-box">
              <legend>{{ 'HISTORY_SETTINGS' | translate }}</legend>
              <div class="connector-row">
                <span class="connector-label">{{ 'ID_CONNECTOR_HISTORY' | translate }}:</span>
                <span class="connector-value">{{ security.matchedHistoryConnector }}</span>
              </div>
              @if (security.matchedHistoryUrlExtension) {
                <div class="connector-row">
                  <span class="connector-label">{{ 'URL_HISTORY_EXTEND' | translate }}:</span>
                  <span class="connector-value text-break">{{ security.matchedHistoryUrlExtension }}</span>
                </div>
              }
            </fieldset>
          }

          <!-- Intraday Connector Box -->
          @if (security.matchedIntraConnector) {
            <fieldset class="connector-box">
              <legend>{{ 'INTRA_SETTINGS' | translate }}</legend>
              <div class="connector-row">
                <span class="connector-label">{{ 'ID_CONNECTOR_INTRA' | translate }}:</span>
                <span class="connector-value">{{ security.matchedIntraConnector }}</span>
              </div>
              @if (security.matchedIntraUrlExtension) {
                <div class="connector-row">
                  <span class="connector-label">{{ 'URL_INTRA_EXTEND' | translate }}:</span>
                  <span class="connector-value text-break">{{ security.matchedIntraUrlExtension }}</span>
                </div>
              }
            </fieldset>
          }

          <!-- Dividend Connector Box -->
          @if (security.matchedDividendConnector) {
            <fieldset class="connector-box">
              <legend>{{ 'DIVIDEND_SETTINGS' | translate }}</legend>
              <div class="connector-row">
                <span class="connector-label">{{ 'ID_CONNECTOR_DIVIDEND' | translate }}:</span>
                <span class="connector-value">{{ security.matchedDividendConnector }}</span>
              </div>
              @if (security.matchedDividendUrlExtension) {
                <div class="connector-row">
                  <span class="connector-label">{{ 'URL_DIVIDEND_EXTEND' | translate }}:</span>
                  <span class="connector-value text-break">{{ security.matchedDividendUrlExtension }}</span>
                </div>
              }
            </fieldset>
          }

          <!-- Split Connector Box -->
          @if (security.matchedSplitConnector) {
            <fieldset class="connector-box">
              <legend>{{ 'SPLIT_SETTING' | translate }}</legend>
              <div class="connector-row">
                <span class="connector-label">{{ 'ID_CONNECTOR_SPLIT' | translate }}:</span>
                <span class="connector-value">{{ security.matchedSplitConnector }}</span>
              </div>
              @if (security.matchedSplitUrlExtension) {
                <div class="connector-row">
                  <span class="connector-label">{{ 'URL_SPLIT_EXTEND' | translate }}:</span>
                  <span class="connector-value text-break">{{ security.matchedSplitUrlExtension }}</span>
                </div>
              }
            </fieldset>
          }
        </div>
      </div>
    </ng-template>

    <div class="flex justify-content-end mt-3">
      <p-button [label]="'APPLY_SELECTED' | translate"
                icon="pi pi-check"
                (onClick)="applySelected()"
                [disabled]="!selectedSecurity">
      </p-button>
    </div>
  `,
  styles: [`
    .connector-expansion {
      padding: 0.5rem 1rem;
      background-color: var(--surface-ground);
    }
    .connector-boxes {
      display: flex;
      flex-wrap: wrap;
      gap: 1rem;
    }
    .connector-box {
      border: 1px solid var(--surface-border);
      border-radius: 4px;
      min-width: 200px;
      max-width: 300px;
      padding: 0.5rem;
      background-color: var(--surface-card);
    }
    .connector-box legend {
      font-weight: bold;
      font-size: 0.9rem;
      padding: 0 0.25rem;
    }
    .connector-row {
      display: flex;
      flex-direction: column;
      margin-bottom: 0.25rem;
    }
    .connector-label {
      font-size: 0.85rem;
      color: var(--text-color-secondary);
    }
    .connector-value {
      font-size: 0.9rem;
    }
    .text-break {
      word-break: break-all;
    }
  `]
})
export class GtnetSecurityLookupTableComponent extends TableConfigBase implements OnInit, OnChanges {

  @Input() securities: SecurityGtnetLookupDTO[] = [];

  @Output() securitySelected = new EventEmitter<SecurityGtnetLookupDTO>();

  selectedSecurity: SecurityGtnetLookupDTO;

  /** Flag to track if fields have been initialized in ngOnInit */
  private fieldsInitialized = false;

  constructor(filterService: FilterService,
              usersettingsService: UserSettingsService,
              translateService: TranslateService,
              gps: GlobalparameterService,
              injector: Injector) {
    super(filterService, usersettingsService, translateService, gps, injector);
  }

  ngOnChanges(changes: SimpleChanges): void {
    if (changes['securities'] && this.securities) {
      this.addUniqueKeys();
      // Only create translated value store if fields have been initialized
      if (this.fieldsInitialized) {
        this.createTranslatedValueStore(this.securities);
      }
    }
  }

  ngOnInit(): void {
    this.addColumnFeqH(DataType.String, 'name', true, false, {width: 200});
    this.addColumn(DataType.String, 'isin', 'ISIN', true, false, {width: 120});
    this.addColumnFeqH(DataType.String, 'currency', true, false, {width: 60});
    this.addColumnFeqH(DataType.String, 'tickerSymbol', true, false, {width: 80});
    this.addColumn(DataType.String, 'stockexchangeName', 'STOCKEXCHANGE', true, false, {width: 150});
    this.addColumn(DataType.String, 'categoryType', AppSettings.ASSETCLASS.toUpperCase(), true, false,
      {width: 100, translateValues: TranslateValue.NORMAL});
    this.addColumn(DataType.String, 'specialInvestmentInstrument', 'FINANCIAL_INSTRUMENT', true, false,
      {width: 120, translateValues: TranslateValue.NORMAL});
    this.addColumn(DataType.String, 'sourceDomain', 'SOURCE_DOMAIN', true, false, {width: 120});

    this.prepareTableAndTranslate();
    this.fieldsInitialized = true;

    // If data was already provided before ngOnInit, create translated value store now
    if (this.securities?.length > 0) {
      this.createTranslatedValueStore(this.securities);
    }
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
