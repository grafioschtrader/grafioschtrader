import {Component, EventEmitter, Input, OnChanges, Output, SimpleChanges, ViewChild} from '@angular/core';
import {FilterService} from 'primeng/api';
import {TranslateModule, TranslateService} from '@ngx-translate/core';
import {ButtonModule} from 'primeng/button';
import {GlobalparameterService} from '../../lib/services/globalparameter.service';
import {UserSettingsService} from '../../lib/services/user.settings.service';
import {MessageToastService} from '../../lib/message/message.toast.service';
import {InfoLevelType} from '../../lib/message/info.leve.type';
import {TableEditConfigBase} from '../../lib/datashowbase/table.edit.config.base';
import {EditableTableComponent, RowEditEvent, RowEditSaveEvent} from '../../lib/datashowbase/editable-table.component';
import {EditInputType, TranslateValue} from '../../lib/datashowbase/column.config';
import {DataType} from '../../lib/dynamic-form/models/data.type';
import {SelectOptionsHelper} from '../../lib/helper/select.options.helper';
import {SecaccountTradingPeriod} from '../../entities/secaccount.trading.period';
import {GlobalSessionNames} from '../../lib/global.session.names';
import {TradingPeriodTransactionSummary} from '../../entities/trading.period.transaction.summary';
import {AssetclassType} from '../../shared/types/assetclass.type';
import {SpecialInvestmentInstruments} from '../../shared/types/special.investment.instruments';
import {BaseSettings} from '../../lib/base.settings';

/**
 * Standalone table component for editing security account trading periods.
 * Uses row-by-row editing mode with per-row edit/save/cancel and delete buttons.
 * For existing rows, only dateTo is editable. Validates overlap and transaction conflicts.
 */
@Component({
  selector: 'trading-period-table',
  template: `
    <div style="display: flex; align-items: center; margin-top: 1rem; margin-bottom: 0.5rem;">
      <h4 style="margin: 0;">{{ 'TRADING_PERIODS' | translate }}</h4>
      <p-button icon="pi pi-plus" [rounded]="true" [text]="true" (click)="entityTable.addNewRow()" [style]="{'margin-left': '0.5rem'}" />
    </div>
    <editable-table #entityTable
                    [data]="tradingPeriods"
                    (dataChange)="onDataChange($event)"
                    [fields]="fields"
                    dataKey="idSecaccountTradingPeriod"
                    [showEditColumn]="true"
                    [editColumnWidth]="120"
                    [selectionMode]="null"
                    [contextMenuEnabled]="false"
                    [createNewEntityFn]="createNewEntity.bind(this)"
                    [validateRowFn]="validateRow.bind(this)"
                    [canDeleteRowFn]="canDeleteRow.bind(this)"
                    (rowEditSave)="onRowEditSave($event)"
                    (rowEditCancel)="onRowEditCancel($event)"
                    (rowDelete)="onRowDelete($event)"
                    (rowAdded)="onRowAdded($event)"
                    [valueGetterFn]="getValueByPath.bind(this)"
                    [customSortFn]="customSort.bind(this)"
                    [baseLocale]="baseLocale"
                    [scrollable]="false"
                    [containerClass]="''"
                    [stripedRows]="false">
    </editable-table>
  `,
  standalone: true,
  imports: [EditableTableComponent, TranslateModule, ButtonModule]
})
export class TradingPeriodTableComponent extends TableEditConfigBase implements OnChanges {

  @ViewChild('entityTable') entityTable: EditableTableComponent<SecaccountTradingPeriod>;

  /** Trading periods to display and edit. Strings are converted to Date objects internally. */
  @Input() tradingPeriods: SecaccountTradingPeriod[] = [];

  /** Preloaded transaction summaries for conflict checks. */
  @Input() transactionSummaries: TradingPeriodTransactionSummary[] = [];

  /** Whether the parent security account is new (not yet persisted). */
  @Input() isNewSecurityAccount = false;

  @Output() tradingPeriodsChange = new EventEmitter<SecaccountTradingPeriod[]>();

  private fieldsInitialized = false;
  private inputTempIdCounter = -1000;
  private static get MIN_DATE(): Date {
    return new Date(sessionStorage.getItem(GlobalSessionNames.OLDEST_TRADING_DAY) ?? BaseSettings.OLDEST_TRADING_DAY_FALLBACK);
  }

  constructor(filterService: FilterService,
              usersettingsService: UserSettingsService,
              translateService: TranslateService,
              gps: GlobalparameterService,
              private messageToastService: MessageToastService) {
    super(filterService, usersettingsService, translateService, gps);
    this.setupColumns();
  }

  ngOnChanges(changes: SimpleChanges): void {
    if (changes['tradingPeriods'] && this.tradingPeriods) {
      this.tradingPeriods = this.convertDatesToDateObjects(this.tradingPeriods);
      this.assignTempIds(this.tradingPeriods);
      if (this.fieldsInitialized) {
        this.createTranslatedValueStoreAndFilterField(this.tradingPeriods);
      }
    }
  }

  /**
   * Returns the current data converted back to ISO date strings for backend persistence.
   */
  getData(): SecaccountTradingPeriod[] {
    const data = this.entityTable?.getData() || this.tradingPeriods;
    return this.convertDatesToStrings(data).map(p => {
      const copy = {...p};
      if (copy.idSecaccountTradingPeriod != null && copy.idSecaccountTradingPeriod < 0) {
        copy.idSecaccountTradingPeriod = null;
      }
      return copy;
    });
  }

  createNewEntity = (): SecaccountTradingPeriod => {
    const period = new SecaccountTradingPeriod();
    period.dateFrom = new Date(TradingPeriodTableComponent.MIN_DATE);
    return period;
  };

  /**
   * Uniqueness validation callback. Returns false if another row with the same
   * (specInvestInstrument, categoryType) combination already exists.
   */
  validateRow = (row: SecaccountTradingPeriod): boolean => {
    const duplicates = this.tradingPeriods.filter(p => p !== row
      && p.specInvestInstrument === row.specInvestInstrument
      && this.sameCategoryType(p.categoryType, row.categoryType));
    if (duplicates.length > 0) {
      this.messageToastService.showMessageI18n(InfoLevelType.WARNING, 'TRADING_PERIOD_DUPLICATE');
      return false;
    }
    return true;
  };

  /**
   * Determines if a row can be deleted. New rows and rows on new security accounts are always
   * deletable. Existing rows with transactions cannot be deleted.
   */
  canDeleteRow = (row: SecaccountTradingPeriod): boolean => {
    if (this.isNewRow(row) || this.isNewSecurityAccount) {
      return true;
    }
    return !this.hasConflictingTransactions(row);
  };

  onRowEditSave(event: RowEditSaveEvent<SecaccountTradingPeriod>): void {
    const row = event.row;
    if (row.dateTo) {
      const summary = this.findMatchingSummary(row);
      if (summary && new Date(summary.maxTransactionDate + 'T00:00:00') > (row.dateTo instanceof Date ? row.dateTo : new Date(row.dateTo + 'T00:00:00'))) {
        this.messageToastService.showMessageI18n(InfoLevelType.ERROR, 'TRADING_PERIOD_DATE_CONFLICT');
        // Restore original dateTo
        row.dateTo = event.originalRow.dateTo;
        return;
      }
    }
    this.createTranslatedValueStoreAndFilterField(this.tradingPeriods);
    this.tradingPeriodsChange.emit(this.tradingPeriods);
  }

  onRowEditCancel(_event: RowEditEvent<SecaccountTradingPeriod>): void {
    // Cancelled — data restored by EditableTableComponent
  }

  onRowDelete(event: RowEditEvent<SecaccountTradingPeriod>): void {
    const row = event.row;
    if (this.hasConflictingTransactions(row)) {
      this.messageToastService.showMessageI18n(InfoLevelType.ERROR, 'TRADING_PERIOD_DELETE_CONFLICT');
      return;
    }
    this.tradingPeriods = this.tradingPeriods.filter((_, i) => i !== event.index);
    this.tradingPeriodsChange.emit(this.tradingPeriods);
  }

  onRowAdded(_event: RowEditEvent<SecaccountTradingPeriod>): void {
    this.createTranslatedValueStoreAndFilterField(this.tradingPeriods);
  }

  onDataChange(data: SecaccountTradingPeriod[]): void {
    this.tradingPeriods = data;
    this.tradingPeriodsChange.emit(this.tradingPeriods);
  }

  private setupColumns(): void {
    // specInvestInstrument column — only editable on new rows
    const specInvestCol = this.addEditColumnFeqH(DataType.String, 'specInvestInstrument', false,
      {translateValues: TranslateValue.NORMAL, width: 160});
    specInvestCol.cec.inputType = EditInputType.Select;
    specInvestCol.cec.valueKeyHtmlOptions = SelectOptionsHelper.createHtmlOptionsFromEnum(
      this.translateService, SpecialInvestmentInstruments,
      [SpecialInvestmentInstruments.NON_INVESTABLE_INDICES], true);
    specInvestCol.cec.canEditFn = (row) => this.isNewRow(row);

    // categoryType column — only editable on new rows
    const categoryTypeCol = this.addEditColumnFeqH(DataType.String, 'categoryType', false,
      {translateValues: TranslateValue.NORMAL, width: 160});
    categoryTypeCol.cec.inputType = EditInputType.Select;
    categoryTypeCol.cec.valueKeyHtmlOptions = SelectOptionsHelper.createHtmlOptionsFromEnumAddEmpty(
      this.translateService, AssetclassType,
      [AssetclassType.CURRENCY_CASH, AssetclassType.CURRENCY_FOREIGN], true);
    categoryTypeCol.cec.canEditFn = (row) => this.isNewRow(row);

    // dateFrom column — only editable on new rows
    const dateFromCol = this.addEditColumnFeqH(DataType.DateString, 'dateFrom', false, {width: 130});
    dateFromCol.cec.inputType = EditInputType.DatePicker;
    dateFromCol.cec.minDate = new Date(TradingPeriodTableComponent.MIN_DATE);
    dateFromCol.cec.canEditFn = (row) => this.isNewRow(row);

    // dateTo column — always editable
    const dateToCol = this.addEditColumnFeqH(DataType.DateString, 'dateTo', false, {width: 130});
    dateToCol.cec.inputType = EditInputType.DatePicker;
    dateToCol.cec.minDate = new Date(TradingPeriodTableComponent.MIN_DATE);

    this.prepareTableAndTranslate();
    this.fieldsInitialized = true;
  }

  private isNewRow(row: SecaccountTradingPeriod): boolean {
    return row.idSecaccountTradingPeriod == null || row.idSecaccountTradingPeriod < 0;
  }

  private sameCategoryType(a: string | null, b: string | null): boolean {
    return (a == null && b == null) || a === b;
  }

  /**
   * Assigns negative temporary IDs to rows that arrive via @Input() without an ID
   * (e.g., default rows for new security accounts). Uses a counter starting at -1000
   * to avoid collision with EditableTableComponent's addNewRow() counter (starts at -1).
   */
  private assignTempIds(periods: SecaccountTradingPeriod[]): void {
    for (const p of periods) {
      if (p.idSecaccountTradingPeriod == null) {
        p.idSecaccountTradingPeriod = this.inputTempIdCounter--;
      }
    }
  }

  private hasConflictingTransactions(row: SecaccountTradingPeriod): boolean {
    return this.transactionSummaries.some(ts =>
      ts.specInvestInstrument === row.specInvestInstrument
      && (row.categoryType == null || ts.categoryType === row.categoryType)
    );
  }

  private findMatchingSummary(row: SecaccountTradingPeriod): TradingPeriodTransactionSummary | undefined {
    return this.transactionSummaries.find(ts =>
      ts.specInvestInstrument === row.specInvestInstrument
      && (row.categoryType == null || ts.categoryType === row.categoryType)
    );
  }

  /**
   * Converts ISO date strings (from backend JSON) to Date objects for PrimeNG DatePicker binding.
   */
  private convertDatesToDateObjects(periods: SecaccountTradingPeriod[]): SecaccountTradingPeriod[] {
    return periods.map(p => {
      const copy = {...p};
      if (typeof copy.dateFrom === 'string') {
        copy.dateFrom = new Date(copy.dateFrom + 'T00:00:00');
      }
      if (typeof copy.dateTo === 'string') {
        copy.dateTo = new Date(copy.dateTo + 'T00:00:00');
      }
      return copy;
    });
  }

  /**
   * Converts Date objects back to ISO date strings (yyyy-MM-dd) for backend JSON serialization.
   */
  private convertDatesToStrings(periods: SecaccountTradingPeriod[]): SecaccountTradingPeriod[] {
    return periods.map(p => {
      const copy = {...p};
      if (copy.dateFrom instanceof Date) {
        copy.dateFrom = this.formatDateToIso(copy.dateFrom);
      }
      if (copy.dateTo instanceof Date) {
        copy.dateTo = this.formatDateToIso(copy.dateTo);
      }
      return copy;
    });
  }

  private formatDateToIso(date: Date): string {
    const y = date.getFullYear();
    const m = String(date.getMonth() + 1).padStart(2, '0');
    const d = String(date.getDate()).padStart(2, '0');
    return `${y}-${m}-${d}`;
  }
}
