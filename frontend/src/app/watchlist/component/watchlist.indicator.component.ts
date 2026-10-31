import { signal, Component, OnDestroy, OnInit, inject } from '@angular/core';
import { takeUntilDestroyed } from '@angular/core/rxjs-interop';
import { CellMark } from '../../lib/datashowbase/configurable-table.component';
import { DialogService } from '@openng/optimus-ui/dynamicdialog';
import { WatchlistTable, WatchListType } from './watchlist.table';
import { TranslateModule } from '@ngx-translate/core';
import { AppSettings } from '../../shared/app.settings';
import { combineLatest } from 'rxjs';

import { DataType } from '../../lib/dynamic-form/models/data.type';
import { ColumnConfig } from '../../lib/datashowbase/column.config';
import { FilterType } from '../../lib/datashowbase/filter.type';

import { HelpIds } from '../../lib/help/help.ids';

import { MenuItem } from '@openng/optimus-ui/api';

import { ButtonModule } from '@openng/optimus-ui/button';
import { DatePipe } from '@angular/common';
import { ConfigurableTableComponent } from '../../lib/datashowbase/configurable-table.component';
import { AngularSvgIconModule } from 'angular-svg-icon';
import { TooltipModule } from '@openng/optimus-ui/tooltip';
import { WatchlistAddInstrumentDialogComponent } from './watchlist-add-instrument-dialog.component';
import { CurrencypairEditComponent } from '../../shared/securitycurrency/currencypair-edit.component';
import { SecurityEditComponent } from '../../shared/securitycurrency/security-edit.component';
import { SecurityDerivedEditComponent } from '../../securitycurrency/component/security-derived-edit.component';
import { SecurityUDFEditComponent } from '../../securitycurrency/component/security-udf-edit.component';
import { AlgoStrategyEditComponent } from '../../algo/component/algo-strategy-edit.component';
import { TransactionSecurityEditComponent } from '../../transaction/component/transaction-security-edit.component';
import { UDFGeneralEditComponent } from '../../lib/udfmeta/components/udf-general-edit.component';

import { WatchlistIndicatorService } from '../service/watchlist-indicator.service';
import { WatchlistIndicatorColumnsDialogComponent } from './watchlist-indicator-columns-dialog.component';
import { SecuritycurrencyIndicatorGroup, WatchlistIndicatorColumn } from '../model/watchlist-indicator';
import { Currencypair } from '../../entities/currencypair';
import { Security } from '../../entities/security';
import type { SecuritycurrencyPosition } from '../../entities/view/securitycurrency.position';
@Component({
  templateUrl: '../view/watchlist.indicator.html',
  styles: [
    `
      .cell-move {
        cursor: move !important;
      }
    `
  ],
  providers: [DialogService],
  imports: [
    ButtonModule,
    TranslateModule,
    ConfigurableTableComponent,
    AngularSvgIconModule,
    TooltipModule,
    WatchlistAddInstrumentDialogComponent,
    CurrencypairEditComponent,
    SecurityEditComponent,
    SecurityDerivedEditComponent,
    SecurityUDFEditComponent,
    AlgoStrategyEditComponent,
    TransactionSecurityEditComponent,
    UDFGeneralEditComponent,
    DatePipe
  ]
})
/** Daily-series indicator table; column identity and types are supplied by the backend. */
export class WatchlistIndicatorComponent extends WatchlistTable implements OnInit, OnDestroy {
  private indicatorService: WatchlistIndicatorService = inject(WatchlistIndicatorService);

  readonly indicatorGroup = signal<SecuritycurrencyIndicatorGroup | undefined>(undefined);
  private definitions = new Map<string, WatchlistIndicatorColumn>();
  constructor() {
    super(WatchListType.INDICATOR, AppSettings.WATCHLIST_INDICATOR_TABLE_SETTINGS_STORE, WatchlistTable.SINGLE);

    this.addBaseColumns();
    this.addColumn(DataType.DateTimeString, 'securitycurrency.sTimestamp', 'TIMEDATE', true, true, {
      cellTooltipFN: this.priceCellTooltip.bind(this)
    });
    this.addColumn(DataType.Numeric, 'securitycurrency.sLast', 'LAST', true, true, {
      maxFractionDigits: this.gps.getMaxFractionDigits(),
      cellTooltipFN: this.priceCellTooltip.bind(this)
    });
    // Reddens with the sessions the historical data source missed, coloured by priceCellStyle of the base class.
    this.addColumn(DataType.DateString, 'youngestHistoryDate', 'YOUNGEST_EOD', true, true, {
      cellTooltipFN: this.priceCellTooltip.bind(this)
    });
    this.prepareTableAndTranslate();
    this.watchlistHasModifiedFromOutside();
  }
  ngOnInit(): void {
    this.init();
    this.getWatchlistWithoutUpdate();
  }
  protected override getWatchlistWithoutUpdate(): void {
    this.replaceWatchlistRequest('data').add(
      combineLatest([
        this.indicatorService.report(this.idWatchlist),
        this.watchlistService.getSecuritiesCurrenciesWatchlistLimits(this.idWatchlist)
      ])
        .pipe(takeUntilDestroyed(this.destroyRef))
        .subscribe({
          next: ([group, limits]) => {
            this.indicatorGroup.set(group);
            this.tenantLimits.set(limits);
            this.fields = this.fields.filter((f) => !f.field.startsWith('ind_'));
            this.definitions.clear();
            group.columns.forEach((column) => {
              if (column.idWatchlistIndicatorColumn == null) return;
              const key = 'ind_' + column.idWatchlistIndicatorColumn;
              this.definitions.set(key, column);
              const cc = this.addColumn(
                column.resultType === 'BOOLEAN' ? DataType.Boolean : DataType.NumericShowZero,
                key,
                key,
                true,
                true,
                {
                  fixedFractionDigits: column.decimals,
                  filterType: FilterType.likeDataType,
                  templateName: column.resultType === 'BOOLEAN' ? 'check' : undefined,
                  cellTooltipFN: (row) => this.indicatorTooltip(row, column)
                }
              );
              const benchmark = column.idBenchmark == null ? undefined : group.benchmarks[column.idBenchmark];
              cc.headerTooltipTranslated =
                column.expression == null
                  ? undefined
                  : column.expression +
                    (benchmark
                      ? '\n' + benchmark.name
                      : column.idBenchmark == null && /\b(RS|BETA)\s*\(/i.test(column.expression)
                        ? '\n' + this.translateService.instant('WIC_BENCHMARK_MISSING')
                        : '');
            });
            this.prepareTableAndTranslate();
            this.fields.forEach((field) => {
              const column = this.definitions.get(field.field);
              if (column) field.headerTranslated = column.label ?? undefined;
            });
            this.readTableDefinition(AppSettings.WATCHLIST_INDICATOR_TABLE_SETTINGS_STORE);
            [...group.securityPositionList, ...group.currencypairPositionList].forEach((row) =>
              group.columns.forEach((column) => {
                const columnId = column.idWatchlistIndicatorColumn;
                const id = row.securitycurrency.idSecuritycurrency;
                if (columnId == null || id == null) return;
                row[`ind_${columnId}`] = group.cells[id]?.[columnId]?.value ?? null;
              })
            );
            this.createSecurityPositionList(group);
            this.loading.set(false);
          },
          error: () => this.loading.set(false)
        })
    );
  }
  createDefaults(): void {
    this.indicatorService
      .defaults()
      .pipe(takeUntilDestroyed(this.destroyRef))
      .subscribe(() => this.getWatchlistWithoutUpdate());
  }
  editColumns(): void {
    const ref = this.dialogService.open(WatchlistIndicatorColumnsDialogComponent, {
      header: this.translateService.instant('WIC_EDIT_COLUMNS'),
      width: '1000px',
      modal: true,
      closable: true,
      closeOnEscape: true
    });
    if (!ref) return;
    ref.onClose.pipe(takeUntilDestroyed(this.destroyRef)).subscribe(() => this.getWatchlistWithoutUpdate());
  }
  protected override getEditMenuItems(
    position: SecuritycurrencyPosition<Security | Currencypair> | null | undefined
  ): MenuItem[] {
    return [...super.getEditMenuItems(position), { label: 'WIC_EDIT_COLUMNS', command: () => this.editColumns() }];
  }
  private indicatorTooltip(
    row: SecuritycurrencyPosition<Security | Currencypair>,
    column: WatchlistIndicatorColumn
  ): string {
    const group = this.indicatorGroup();
    const id = row.securitycurrency.idSecuritycurrency;
    const columnId = column.idWatchlistIndicatorColumn;
    if (!group || id == null || columnId == null) return '';
    const cell = group.cells[id]?.[columnId];
    if (!cell) return '';
    if (cell.status !== 'OK') return this.translateService.instant('WIC_STATUS_' + cell.status);
    const benchmark = column.idBenchmark == null ? undefined : group.benchmarks[column.idBenchmark];
    const currency = (row.securitycurrency as Security).currency ?? (row.securitycurrency as Currencypair).toCurrency;
    return (
      (cell.lastCommonDate ?? '') +
      (benchmark && benchmark.currency !== currency ? ' ' + currency + ' / ' + benchmark.currency : '') +
      this.fundamentalTooltip(id, column)
    );
  }

  /**
   * One line per fundamental variable the column reads: source, basis, period end, grade, fetch date and, for a
   * converted value, the original currency and the exchange rate.
   */
  private fundamentalTooltip(idSecuritycurrency: number, column: WatchlistIndicatorColumn): string {
    const group = this.indicatorGroup();
    const columnId = column.idWatchlistIndicatorColumn;
    if (!group || columnId == null) return '';
    const variables = group.columnFundamentalVariables?.[columnId] ?? [];
    const infos = group.fundamentalInfo?.[idSecuritycurrency] ?? {};
    return variables
      .filter((variable) => infos[variable])
      .map((variable) => {
        const info = infos[variable];
        const t = (key: string) => this.translateService.instant(key);
        const parts = [info.source];
        if (info.basis) parts.push(t('WIC_FUNDAMENTAL_' + info.basis));
        if (info.periodEnd) parts.push(t('WIC_FUNDAMENTAL_PERIOD') + ' ' + info.periodEnd);
        parts.push(t('WIC_GRADE_' + info.state));
        if (info.fetchedDate) parts.push(t('WIC_FUNDAMENTAL_FETCHED') + ' ' + info.fetchedDate);
        if (info.rate != null) parts.push(t('WIC_FUNDAMENTAL_RATE') + ' ' + info.currency + ' × ' + info.rate);
        return '\n' + variable + ': ' + parts.join(', ');
      })
      .join('');
  }
  override cellMark(position: SecuritycurrencyPosition<Security | Currencypair>, field: ColumnConfig): CellMark | null {
    const column = this.definitions.get(field.field);
    const id = position.securitycurrency.idSecuritycurrency;
    if (!column || column.idWatchlistIndicatorColumn == null || id == null) return null;
    const cell = this.indicatorGroup()?.cells[id]?.[column.idWatchlistIndicatorColumn];
    return cell && (cell.status !== 'OK' || (column.resultType === 'BOOLEAN' && cell.lastCommonDate))
      ? { iconClass: 'fa fa-info-circle', tooltip: this.indicatorTooltip(position, column) }
      : null;
  }
  override priceCellStyle(
    position: SecuritycurrencyPosition<Security | Currencypair>,
    field: ColumnConfig
  ): { [key: string]: string } | null {
    const column = this.definitions.get(field.field);
    const value = position[field.field as keyof typeof position];
    if (!column || typeof value !== 'number') return super.priceCellStyle(position, field);
    const low = column.lowerMark != null && value <= column.lowerMark;
    const high = column.upperMark != null && value >= column.upperMark;
    if (!low && !high) return null;
    if (column.markColors === 'NEUTRAL') return { 'background-color': 'rgba(234, 179, 8, 0.30)' };
    const positive = column.markColors === 'LOW_POSITIVE_HIGH_NEGATIVE' ? low : high;
    return { color: positive ? 'green' : 'red' };
  }
  protected override updateAllPrice(): void {
    this.loading.set(true);
    this.getWatchlistWithoutUpdate();
  }
  override getHelpContextId(): string {
    return HelpIds.HELP_WATCHLIST_INDICATOR;
  }
}
