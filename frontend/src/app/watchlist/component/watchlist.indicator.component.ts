import { CellMark } from '../../lib/datashowbase/configurable-table.component';
import { ChangeDetectorRef, Component, Injector, OnDestroy, OnInit, ChangeDetectionStrategy } from '@angular/core';
import { DialogService } from '@openng/optimus-ui/dynamicdialog';
import { WatchlistTable, WatchListType } from './watchlist.table';
import { TimeSeriesQuotesService } from '../../historyquote/service/time.series.quotes.service';
import { DataChangedService } from '../../lib/maintree/service/data.changed.service';
import { ActivePanelService } from '../../lib/mainmenubar/service/active.panel.service';
import { WatchlistService } from '../service/watchlist.service';
import { ActivatedRoute, Router } from '@angular/router';
import { ConfirmationService, FilterService } from '@openng/optimus-ui/api';
import { MessageToastService } from '../../lib/message/message.toast.service';
import { ProductIconService } from '../../securitycurrency/service/product.icon.service';
import { TranslateService, TranslateModule } from '@ngx-translate/core';
import { GlobalparameterService } from '../../lib/services/globalparameter.service';
import { UserSettingsService } from '../../lib/services/user.settings.service';
import { AppSettings } from '../../shared/app.settings';
import { combineLatest } from 'rxjs';

import { DataType } from '../../lib/dynamic-form/models/data.type';
import { ColumnConfig } from '../../lib/datashowbase/column.config';
import { FilterType } from '../../lib/datashowbase/filter.type';

import { AlarmSetupService } from '../../algo/service/alarm.setup.service';
import { GlobalparameterGTService } from '../../gtservice/globalparameter.gt.service';
import { HelpIds } from '../../lib/help/help.ids';

import { MenuItem } from '@openng/optimus-ui/api';

import { ButtonModule } from '@openng/optimus-ui/button';
import { CommonModule } from '@angular/common';
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
  standalone: true,
  changeDetection: ChangeDetectionStrategy.Eager,
  imports: [
    CommonModule,
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
    UDFGeneralEditComponent
  ]
})
/** Daily-series indicator table; column identity and types are supplied by the backend. */
export class WatchlistIndicatorComponent extends WatchlistTable implements OnInit, OnDestroy {
  indicatorGroup: SecuritycurrencyIndicatorGroup;
  private definitions = new Map<string, WatchlistIndicatorColumn>();
  constructor(
    private indicatorService: WatchlistIndicatorService,
    dialogService: DialogService,
    alarmSetupService: AlarmSetupService,
    timeSeriesQuotesService: TimeSeriesQuotesService,
    dataChangedService: DataChangedService,
    activePanelService: ActivePanelService,
    watchlistService: WatchlistService,
    router: Router,
    activatedRoute: ActivatedRoute,
    confirmationService: ConfirmationService,
    messageToastService: MessageToastService,
    productIconService: ProductIconService,
    changeDetectionStrategy: ChangeDetectorRef,
    filterService: FilterService,
    translateService: TranslateService,
    gpsGT: GlobalparameterGTService,
    gps: GlobalparameterService,
    usersettingsService: UserSettingsService,
    injector: Injector
  ) {
    super(
      WatchListType.INDICATOR,
      AppSettings.WATCHLIST_INDICATOR_TABLE_SETTINGS_STORE,
      dialogService,
      alarmSetupService,
      timeSeriesQuotesService,
      dataChangedService,
      activePanelService,
      watchlistService,
      router,
      activatedRoute,
      confirmationService,
      messageToastService,
      productIconService,
      changeDetectionStrategy,
      filterService,
      translateService,
      gpsGT,
      gps,
      usersettingsService,
      WatchlistTable.SINGLE,
      injector
    );

    this.addBaseColumns();
    this.addColumn(DataType.DateTimeString, 'securitycurrency.sTimestamp', 'TIMEDATE', true, true, {
      cellTooltipFN: this.priceCellTooltip.bind(this)
    });
    this.addColumn(DataType.Numeric, 'securitycurrency.sLast', 'LAST', true, true, {
      maxFractionDigits: gps.getMaxFractionDigits(),
      cellTooltipFN: this.priceCellTooltip.bind(this)
    });
    this.addColumn(DataType.DateString, 'youngestHistoryDate', 'YOUNGEST_EOD', true, true);
    this.prepareTableAndTranslate();
    this.watchlistHasModifiedFromOutside();
  }
  ngOnInit(): void {
    this.init();
    this.getWatchlistWithoutUpdate();
  }
  protected override getWatchlistWithoutUpdate(): void {
    combineLatest([
      this.indicatorService.report(this.idWatchlist),
      this.watchlistService.getSecuritiesCurrenciesWatchlistLimits(this.idWatchlist)
    ]).subscribe({
      next: ([group, limits]) => {
        this.indicatorGroup = group;
        this.tenantLimits = limits;
        this.fields = this.fields.filter((f) => !f.field.startsWith('ind_'));
        this.definitions.clear();
        group.columns.forEach((column) => {
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
          const benchmark = group.benchmarks[column.idBenchmark];
          cc.headerTooltipTranslated =
            column.expression +
            (benchmark
              ? '\n' + benchmark.name
              : column.idBenchmark == null && /\b(RS|BETA)\s*\(/i.test(column.expression)
                ? '\n' + this.translateService.instant('WIC_BENCHMARK_MISSING')
                : '');
        });
        this.prepareTableAndTranslate();
        this.fields.forEach((field) => {
          const column = this.definitions.get(field.field);
          if (column) field.headerTranslated = column.label;
        });
        this.readTableDefinition(AppSettings.WATCHLIST_INDICATOR_TABLE_SETTINGS_STORE);
        [...group.securityPositionList, ...group.currencypairPositionList].forEach((row) =>
          group.columns.forEach((column) => {
            row['ind_' + column.idWatchlistIndicatorColumn] =
              group.cells[row.securitycurrency.idSecuritycurrency]?.[column.idWatchlistIndicatorColumn]?.value ?? null;
          })
        );
        this.createSecurityPositionList(group);
        this.loading = false;
      },
      error: () => (this.loading = false)
    });
  }
  createDefaults(): void {
    this.indicatorService.defaults().subscribe(() => this.getWatchlistWithoutUpdate());
  }
  editColumns(): void {
    const ref = this.dialogService.open(WatchlistIndicatorColumnsDialogComponent, {
      header: this.translateService.instant('WIC_EDIT_COLUMNS'),
      width: '1000px',
      modal: true,
      closable: true,
      closeOnEscape: true
    });
    ref.onClose.subscribe(() => this.getWatchlistWithoutUpdate());
  }
  protected override getEditMenuItems(position): MenuItem[] {
    return [...super.getEditMenuItems(position), { label: 'WIC_EDIT_COLUMNS', command: () => this.editColumns() }];
  }
  private indicatorTooltip(row, column: WatchlistIndicatorColumn): string {
    const cell =
      this.indicatorGroup?.cells[row.securitycurrency.idSecuritycurrency]?.[column.idWatchlistIndicatorColumn];
    if (!cell) return '';
    if (cell.status !== 'OK') return this.translateService.instant('WIC_STATUS_' + cell.status);
    const benchmark = this.indicatorGroup.benchmarks[column.idBenchmark];
    const currency = row.securitycurrency.currency ?? row.securitycurrency.toCurrency;
    return (
      (cell.lastCommonDate ?? '') +
      (benchmark && benchmark.currency !== currency ? ' ' + currency + ' / ' + benchmark.currency : '') +
      this.fundamentalTooltip(row.securitycurrency.idSecuritycurrency, column)
    );
  }

  /**
   * One line per fundamental variable the column reads: source, basis, period end, grade, fetch date and, for a
   * converted value, the original currency and the exchange rate.
   */
  private fundamentalTooltip(idSecuritycurrency: number, column: WatchlistIndicatorColumn): string {
    const variables = this.indicatorGroup.columnFundamentalVariables?.[column.idWatchlistIndicatorColumn] ?? [];
    const infos = this.indicatorGroup.fundamentalInfo?.[idSecuritycurrency] ?? {};
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
  override cellMark(position, field: ColumnConfig): CellMark | null {
    const column = this.definitions.get(field.field);
    if (!column) return null;
    const cell =
      this.indicatorGroup?.cells[position.securitycurrency.idSecuritycurrency]?.[column.idWatchlistIndicatorColumn];
    return cell && (cell.status !== 'OK' || (column.resultType === 'BOOLEAN' && cell.lastCommonDate))
      ? { iconClass: 'fa fa-info-circle', tooltip: this.indicatorTooltip(position, column) }
      : null;
  }
  override priceCellStyle(position, field: ColumnConfig): { [key: string]: string } | null {
    const column = this.definitions.get(field.field);
    const value = position[field.field];
    if (!column || typeof value !== 'number') return super.priceCellStyle(position, field);
    const low = column.lowerMark != null && value <= column.lowerMark;
    const high = column.upperMark != null && value >= column.upperMark;
    if (!low && !high) return null;
    if (column.markColors === 'NEUTRAL') return { 'background-color': 'rgba(234, 179, 8, 0.30)' };
    const positive = column.markColors === 'LOW_POSITIVE_HIGH_NEGATIVE' ? low : high;
    return { color: positive ? 'green' : 'red' };
  }
  protected override updateAllPrice(): void {
    this.loading = true;
    this.getWatchlistWithoutUpdate();
  }
  override getHelpContextId(): string {
    return HelpIds.HELP_WATCHLIST_INDICATOR;
  }
}
