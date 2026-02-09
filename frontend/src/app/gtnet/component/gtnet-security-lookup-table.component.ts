import {Component, EventEmitter, Injector, Input, OnChanges, OnInit, Output, SimpleChanges} from '@angular/core';
import {CommonModule} from '@angular/common';
import {TranslateModule, TranslateService} from '@ngx-translate/core';
import {ButtonModule} from 'primeng/button';
import {FilterService} from 'primeng/api';
import moment from 'moment';

import {TableConfigBase} from '../../lib/datashowbase/table.config.base';
import {ConfigurableTableComponent} from '../../lib/datashowbase/configurable-table.component';
import {DataType} from '../../lib/dynamic-form/models/data.type';
import {ColumnConfig, TranslateValue} from '../../lib/datashowbase/column.config';
import {GlobalparameterService} from '../../lib/services/globalparameter.service';
import {UserSettingsService} from '../../lib/services/user.settings.service';
import {SecurityGtnetLookupDTO} from '../model/gtnet-security-lookup';
import {SecurityService} from '../../securitycurrency/service/security.service';
import {CurrencypairService} from '../../securitycurrency/service/currencypair.service';
import {SecurityCurrencyHelper} from '../../securitycurrency/service/security.currency.helper';
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
          @for (cfg of connectorConfigs; track cfg.connectorField) {
            @if (security[cfg.connectorField]) {
              <fieldset class="connector-box">
                <legend>{{ cfg.legendKey | translate }}</legend>
                <div class="connector-row">
                  <span class="connector-label">{{ cfg.labelKey | translate }}:</span>
                  <span class="connector-value">{{ getConnectorReadableName(security[cfg.connectorField]) }}</span>
                </div>
                @if (security[cfg.urlField]) {
                  <div class="connector-row">
                    <span class="connector-label">{{ cfg.urlLabelKey | translate }}:</span>
                    <span class="connector-value text-break">{{ security[cfg.urlField] }}</span>
                  </div>
                }
                @for (extra of cfg.extraFields; track extra.field) {
                  @if (security[extra.field] != null) {
                    <div class="connector-row">
                      <span class="connector-label">{{ extra.labelKey | translate }}:</span>
                      <span class="connector-value">
                        @if (extra.isDate) {
                          {{ formatDate(security[extra.field]) }}
                        } @else if (extra.isDateTime) {
                          {{ formatDateTime(security[extra.field]) }}
                        } @else {
                          {{ security[extra.field] }}{{ extra.suffix || '' }}
                        }
                      </span>
                    </div>
                  }
                }
              </fieldset>
            }
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

  readonly connectorConfigs: ConnectorDisplayConfig[] = [
    {connectorField: 'matchedHistoryConnector', urlField: 'matchedHistoryUrlExtension',
      legendKey: 'HISTORY_SETTINGS', labelKey: 'HISTORY_DATA_PROVIDER', urlLabelKey: 'URL_HISTORY_EXTEND',
      extraFields: [
        {field: 'retryHistoryLoad', labelKey: 'RETRY_HISTORY_LOAD'},
        {field: 'historyMinDate', labelKey: 'MIN_DATE', isDate: true},
        {field: 'historyMaxDate', labelKey: 'MAX_DATE', isDate: true},
        {field: 'ohlPercentage', labelKey: 'OHL_PERCENTAGE', suffix: '%'},
      ]},
    {connectorField: 'matchedIntraConnector', urlField: 'matchedIntraUrlExtension',
      legendKey: 'INTRA_SETTINGS', labelKey: 'INTRA_DATA_PROVIDER', urlLabelKey: 'URL_INTRA_EXTEND',
      extraFields: [
        {field: 'retryIntraLoad', labelKey: 'RETRY_INTRA_LOAD'},
        {field: 'sTimestamp', labelKey: 'TIMEDATE', isDateTime: true},
      ]},
    {connectorField: 'matchedDividendConnector', urlField: 'matchedDividendUrlExtension',
      legendKey: AppSettings.DIVIDEND_SETTINGS, labelKey: 'ID_CONNECTOR_DIVIDEND', urlLabelKey: 'URL_DIVIDEND_EXTEND',
      extraFields: [
        {field: 'retryDividendLoad', labelKey: 'RETRY_DIVIDEND_LOAD'},
        {field: 'dividendCount', labelKey: 'DIVIDEND_COUNT'},
      ]},
    {connectorField: 'matchedSplitConnector', urlField: 'matchedSplitUrlExtension',
      legendKey: AppSettings.SPLIT_SETTINGS, labelKey: 'ID_CONNECTOR_SPLIT', urlLabelKey: 'URL_SPLIT_EXTEND',
      extraFields: [
        {field: 'retrySplitLoad', labelKey: 'RETRY_SPLIT_LOAD'},
        {field: 'splitCount', labelKey: 'SPLIT_COUNT'},
      ]},
  ];

  /** Map of connector ID to human-readable name */
  feedConnectorsKV: { [id: string]: string } = {};

  /** Flag to track if fields have been initialized in ngOnInit */
  private fieldsInitialized = false;

  /** User's language for subCategoryNLS lookup */
  private userLang: string;

  constructor(filterService: FilterService,
              usersettingsService: UserSettingsService,
              translateService: TranslateService,
              gps: GlobalparameterService,
              injector: Injector,
              private securityService: SecurityService,
              private currencypairService: CurrencypairService) {
    super(filterService, usersettingsService, translateService, gps, injector);
    this.userLang = gps.getUserLang();
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
    this.addColumn(DataType.String, 'subCategoryNLS', 'SUB_ASSETCLASS', true, false,
      {width: 120, fieldValueFN: this.getSubCategoryByLanguage.bind(this)});
    this.addColumn(DataType.String, 'sourceDomain', 'SOURCE_DOMAIN', true, false, {width: 120});

    this.prepareTableAndTranslate();
    this.fieldsInitialized = true;

    // Load connector readable names
    SecurityCurrencyHelper.loadAllConnectors(this.securityService, this.currencypairService, this.feedConnectorsKV);

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

  /**
   * Returns the human-readable name for a connector ID, or the raw ID if not found.
   */
  getConnectorReadableName(connectorId: string): string {
    return this.feedConnectorsKV[connectorId] || connectorId;
  }

  /**
   * Formats a date string (YYYY-MM-DD) using the locale-aware date format.
   */
  formatDate(value: string): string {
    return value ? moment(value).format(this.gps.getDateFormat()) : '';
  }

  /**
   * Formats a date-time value (numeric timestamp) using the locale-aware date-time format.
   */
  formatDateTime(value: number): string {
    return value ? moment(+value).format(this.gps.getTimeDateFormatForTable()) : '';
  }

  /**
   * Extracts the subcategory text for the user's language from subCategoryNLS.
   * Falls back to any available language if the user's language is not present.
   */
  private getSubCategoryByLanguage(dataobject: SecurityGtnetLookupDTO, field: ColumnConfig, valueField: any): string {
    const subCat = dataobject.subCategoryNLS;
    if (!subCat) {
      return '';
    }
    // Try user's language first
    if (subCat[this.userLang]) {
      return subCat[this.userLang];
    }
    // Fallback to any available language
    const keys = Object.keys(subCat);
    return keys.length > 0 ? subCat[keys[0]] : '';
  }
}

interface ConnectorExtraField {
  field: string;
  labelKey: string;
  isDate?: boolean;
  isDateTime?: boolean;
  suffix?: string;
}

interface ConnectorDisplayConfig {
  connectorField: string;
  urlField: string;
  legendKey: string;
  labelKey: string;
  urlLabelKey: string;
  extraFields?: ConnectorExtraField[];
}
