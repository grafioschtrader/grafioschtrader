import { takeUntilDestroyed } from '@angular/core/rxjs-interop';
import { Component, computed, DestroyRef, inject, OnDestroy, signal, input } from '@angular/core';
import { TableConfigBase } from '../../lib/datashowbase/table.config.base';
import { MenuItem } from '@openng/optimus-ui/api';
import {
  CorrelationInstrument,
  CorrelationResult,
  CorrelationRollingResult,
  CorrelationSet,
  MinMaxDateHistoryquote,
  SamplingPeriodType
} from '../../entities/correlation.set';
import { DataType } from '../../lib/dynamic-form/models/data.type';
import { AppSettings } from '../../shared/app.settings';
import { ColumnConfig } from '../../lib/datashowbase/column.config';
import { Securitycurrency } from '../../entities/securitycurrency';
import { Security } from '../../entities/security';
import { InfoLevelType } from '../../lib/message/info.leve.type';
import { ProcessedActionData } from '../../lib/types/processed.action.data';
import { CorrelationSetService } from '../service/correlation.set.service';
import { MessageToastService } from '../../lib/message/message.toast.service';
import { DataChangedService } from '../../lib/maintree/service/data.changed.service';
import { TenantLimit } from '../../shared/types/tenant.limit';
import { ProcessedAction } from '../../lib/types/processed.action';
import { Subscription } from 'rxjs';
import { CorrelationChartService } from '../service/correlation-chart.service';
import { Router } from '@angular/router';
import { BusinessHelper } from '../../shared/helper/business.helper';
import { AppHelper } from '../../lib/helper/app.helper';
import { BaseSettings } from '../../lib/base.settings';

import { FormsModule } from '@angular/forms';
import { TranslateModule } from '@ngx-translate/core';
import { TableModule } from '@openng/optimus-ui/table';
import { CorrelationAddInstrumentDialogComponent } from './correlation-add-instrument-dialog.component';
import { InstrumentStatisticsResultComponent } from '../../shared/securitycurrency/instrument-statistics-result.component';
import { TooltipModule } from '@openng/optimus-ui/tooltip';

/**
 * This component serves as the main table view for correlation sets, displaying securities and currency pairs
 * in a dynamic table format with correlation values. It provides extensive functionality for:
 *
 * **Core Features:**
 * - Dynamic table generation with correlation matrix visualization
 * - Interactive instrument management (add/remove securities and currency pairs)
 * - Correlation calculation display with color-coded background
 * - Rolling correlation chart integration with Plotly visualization
 * - Context menu operations for instrument and chart management
 *
 * **Table Functionality:**
 * - Expandable rows showing detailed instrument statistics
 * - Dynamic column creation based on correlation set contents
 * - Custom cell background coloring based on correlation values (-1 to +1 range)
 * - Support for both correlation matrix view and min/max date display
 *
 * **Chart Integration:**
 * - Creates interactive line charts for rolling correlation analysis
 * - Supports multiple correlation pairs on the same chart
 * - Chart navigation and data management through router outlets
 * - Real-time chart updates when correlation set changes
 *
 * **User Interactions:**
 * - Click-to-select instruments for correlation pair analysis
 * - Context menu with add/remove/chart operations
 * - Dialog-based instrument addition with tenant limit validation
 * - Responsive design with Optimus table components
 *
 * The component extends TableConfigBase to leverage common table functionality including sorting,
 * filtering, column management, and internationalization support.
 */
@Component({
  selector: 'correlation-table',
  template: `
    <p-table
      [columns]="fields"
      [value]="securitycurrencyList()"
      selectionMode="single"
      [(selection)]="selectedEntity"
      dataKey="idSecuritycurrency"
      (sortFunction)="customSort($event)"
      [customSort]="true"
      stripedRows
      showGridlines>
      <ng-template #header let-fields>
        <tr>
          <th style="width:24px"></th>
          @for (field of fields; track field.field) {
            <th
              [pSortableColumn]="field.field"
              [pTooltip]="field.headerTooltipTranslated"
              [style.max-width.px]="field.width"
              [style]="field.width ? { 'flex-basis': '0 0 ' + field.width + 'px' } : {}">
              {{ field.headerTranslated }}
              <p-sortIcon [field]="field.field" />
            </th>
          }
        </tr>
      </ng-template>
      <ng-template #body let-expanded="expanded" let-el let-columns="fields">
        <tr [pSelectableRow]="el">
          <td>
            <a href="#" [pRowToggler]="el">
              <i [class]="expanded ? 'fa fa-fw fa-chevron-circle-down' : 'fa fa-fw fa-chevron-circle-right'"></i>
            </a>
          </td>
          @for (field of fields; track field.field) {
            <td
              [style.background-color]="getBackgroundColor(el, field)"
              [class]="field.dataType === DataType.NumericShowZero ? 'text-end' : ''"
              (click)="cellClick(field)"
              [style.max-width.px]="field.width"
              [style]="field.width ? { 'flex-basis': '0 0 ' + field.width + 'px' } : {}">
              @switch (field.templateName) {
                @case ('check') {
                  <span
                    ><i
                      [class.fa]="getValueByPath(el, field)"
                      [class.fa-check]="getValueByPath(el, field)"
                      aria-hidden="true"></i
                  ></span>
                }
                @default {
                  {{ getValueByPath(el, field) }}
                }
              }
            </td>
          }
        </tr>
      </ng-template>
      <ng-template #expandedrow let-securitycrrency let-columns="fields">
        <tr>
          <td [attr.colspan]="numberOfVisibleColumns + 1" style="overflow:visible;">
            <instrument-statistics-result
              [idSecuritycurrency]="securitycrrency.idSecuritycurrency"
              [dateFrom]="correlationSet().dateFrom"
              [dateTo]="correlationSet().dateTo" />
          </td>
        </tr>
      </ng-template>
    </p-table>
    @if (visibleAddInstrumentDialog()) {
      <correlation-add-instrument
        [idCorrelationSet]="correlationSet().idCorrelationSet"
        [tenantLimits]="tenantLimits()"
        [visibleAddInstrumentDialog]="visibleAddInstrumentDialog()"
        (closeDialog)="handleCloseAddInstrumentDialog($event)" />
    }
  `,
  imports: [
    FormsModule,
    TranslateModule,
    TableModule,
    TooltipModule,
    CorrelationAddInstrumentDialogComponent,
    InstrumentStatisticsResultComponent
  ]
})
export class CorrelationTableComponent extends TableConfigBase implements OnDestroy {
  private router: Router = inject(Router);
  private correlationSetService: CorrelationSetService = inject(CorrelationSetService);
  private messageToastService: MessageToastService = inject(MessageToastService);
  private dataChangedService: DataChangedService = inject(DataChangedService);
  private chartDataService: CorrelationChartService = inject(CorrelationChartService);

  /** Interface for communication with parent component */
  readonly childToParent = input<ChildToParent>(undefined);

  /** Limit instruments per correlation set */
  readonly tenantLimits = signal<TenantLimit[]>(null);
  private readonly destroyRef = inject(DestroyRef);
  private limitRequest?: Subscription;

  /** List of securities and currencies in the correlation set */
  readonly securitycurrencyList = computed(() => this.correlationSet()?.securitycurrencyList ?? []);

  /** Currently selected entity in the table */
  readonly selectedEntity = signal<Securitycurrency>(null);

  /** Controls visibility of the add instrument dialog */
  readonly visibleAddInstrumentDialog = signal(false);

  /** Current correlation set being displayed */
  readonly correlationSet = signal<CorrelationSet>(null);

  /** Header key for name column */
  private readonly nameHeader = 'name';

  /** Header key for ticker symbol column */
  private readonly tickerSymbol = 'tickerSymbol';

  /** Header key for minimum date column */
  private readonly minDate = 'minDate';

  /** Current correlation calculation results */
  private readonly correlationResult = signal<CorrelationResult>(null);

  /** Subscription for instrument addition events */
  private subscriptionInstrumentAdded: Subscription;

  /** Security currency selected in column header */
  private securityCurrencyColumn: Securitycurrency;

  /** Cancels obsolete chart requests when the selection changes. */
  private chartRequest: Subscription;

  /** Canonical instrument pairs currently selected for the chart. */
  private chartPairs: { [key: string]: number[] } = {};

  /**
   * Creates an instance of CorrelationTableComponent.
   */
  constructor() {
    super();
    this.createDynamicTableDefinition(null, null);
    this.addInstrumentsToCorrelationSet();
  }

  /**
   * Handles cell click events to select column security currency.
   * @param field The column configuration that was clicked
   */
  cellClick(field: ColumnConfig): void {
    const columnHeader =
      field.headerKey === this.nameHeader || field.headerKey === this.tickerSymbol ? null : field.headerKey;

    this.securityCurrencyColumn =
      columnHeader !== null && field.userValue != null
        ? this.correlationSet()?.securitycurrencyList[field.userValue]
        : null;
  }

  /**
   * Called from the parent component when correlation set or results change.
   * @param correlationSet The selected correlation set
   * @param correlationResult The correlation calculation results
   */
  parentSelectionChanged(correlationSet: CorrelationSet, correlationResult: CorrelationResult): void {
    if (this.correlationSet()?.idCorrelationSet !== correlationSet?.idCorrelationSet) {
      this.limitRequest?.unsubscribe();
      this.tenantLimits.set(null);
      this.visibleAddInstrumentDialog.set(false);
      this.chartPairs = {};
      this.selectedEntity.set(null);
      this.securityCurrencyColumn = null;
    }
    this.createDynamicTableDefinition(correlationSet, correlationResult);
    const selectedId = this.selectedEntity()?.idSecuritycurrency;
    this.selectedEntity.set(this.securitycurrencyList().find((row) => row.idSecuritycurrency === selectedId) ?? null);
    this.readListLimitOnce(correlationSet);
    this.refreshChartWhenCorrelationSetChanges();
  }

  /**
   * Prepares the edit menu items for the correlation table.
   * @returns Array of menu items for editing operations
   */
  public prepareEditMenu(): MenuItem[] {
    const menuItems: MenuItem[] = [];
    menuItems.push({ separator: true });
    menuItems.push({
      label: 'ADD_EXISTING_SECURITY' + BaseSettings.DIALOG_MENU_SUFFIX,
      command: (e) => this.addExistingSecurity(e),
      disabled:
        !this.tenantLimits() ||
        !this.correlationSet() ||
        this.correlationSet().securitycurrencyList.length >= this.tenantLimits()[0].limit
    });
    if (this.selectedEntity()) {
      menuItems.push({
        label: 'REMOVE_INSTRUMENT',
        command: (event) => this.removeInstrumentFromCorrelationSet(this.selectedEntity().idSecuritycurrency)
      });
    }
    return menuItems;
  }

  /**
   * Prepares the show menu items for correlation visualization.
   * @returns Array of menu items for show operations
   */
  public prepareShowMenu(): MenuItem[] {
    const menuItems: MenuItem[] = [];
    if (
      this.correlationSet() &&
      SamplingPeriodType[this.correlationSet().samplingPeriod] !== SamplingPeriodType.ANNUAL_RETURNS
    ) {
      if (
        this.selectedEntity() &&
        this.securityCurrencyColumn &&
        this.selectedEntity() !== this.securityCurrencyColumn
      ) {
        menuItems.push({ separator: true });
        menuItems.push({
          label: 'CORRELATION_SET_GRAPH|' + this.getMenuName(this.selectedEntity(), this.securityCurrencyColumn),
          command: (event) => this.navigateToChartRoute()
        });
        if (
          this.isChartShow() &&
          Object.keys(this.chartPairs).length > 0 &&
          !this.isGraphForPairShowing(
            this.selectedEntity().idSecuritycurrency,
            this.securityCurrencyColumn.idSecuritycurrency
          )
        ) {
          menuItems.push({
            label: 'CORRELATION_ADD_GRAPH|' + this.getMenuName(this.selectedEntity(), this.securityCurrencyColumn),
            command: (event) => this.changeToOpenChart()
          });
        }
      }
    }
    this.selectedEntity() && menuItems.push(...BusinessHelper.getUrlLinkMenus(this.selectedEntity()));
    return menuItems;
  }

  /**
   * Checks if a graph is already showing for the given security pair.
   * @param id1 First security ID
   * @param id2 Second security ID
   * @returns True if graph is already showing for this pair
   */
  private isGraphForPairShowing(id1: number, id2: number): boolean {
    return !!Object.keys(this.chartPairs).find((key) => {
      const ids: number[] = key.split(',').map(Number);
      return ids.indexOf(id1) >= 0 && ids.indexOf(id2) >= 0;
    });
  }

  /**
   * Creates a display name for the correlation menu between two securities.
   * @param securityCurrencyRow The row security currency
   * @param securityCurrencyColumn The column security currency
   * @returns Formatted menu name
   */
  private getMenuName(securityCurrencyRow: Securitycurrency, securityCurrencyColumn: Securitycurrency): string {
    return this.getTickerOrName(securityCurrencyRow) + '<->' + this.getTickerOrName(securityCurrencyColumn);
  }

  /**
   * Gets ticker symbol or name for display purposes.
   * @param securityCurrency The security currency to get display name for
   * @returns Ticker symbol or truncated name
   */
  private getTickerOrName(securityCurrency: Securitycurrency): string {
    if (securityCurrency.hasOwnProperty(this.tickerSymbol)) {
      const ticker = (<Security>securityCurrency)[this.tickerSymbol];
      return ticker ? ticker : securityCurrency.name.slice(0, 20);
    }
    return securityCurrency.name;
  }

  /**
   * Opens the add existing security dialog.
   * @param event The triggering event
   */
  addExistingSecurity(event) {
    this.updateLimitCount();
    this.visibleAddInstrumentDialog.set(true);
  }

  /**
   * Handles the close event of the add instrument dialog.
   * @param processedActionData The action data from the dialog
   */
  handleCloseAddInstrumentDialog(processedActionData: ProcessedActionData) {
    this.visibleAddInstrumentDialog.set(false);
    this.childToParent().refreshData(null);
  }

  /**
   * Removes an instrument from the correlation set.
   * @param idSecuritycurrency The ID of the security currency to remove
   */
  removeInstrumentFromCorrelationSet(idSecuritycurrency: number) {
    this.correlationSetService
      .removeInstrumentFromCorrelationSet(this.correlationSet().idCorrelationSet, idSecuritycurrency)
      .pipe(takeUntilDestroyed(this.destroyRef))
      .subscribe((correlationSet) => {
        this.messageToastService.showMessageI18n(InfoLevelType.SUCCESS, 'REMOVED_INSTRUMENT_FROM_CORRELATIONSET');
        this.selectedEntity.set(null);
        this.childToParent().refreshData(correlationSet);
      });
  }

  /**
   * Gets the correlation value for a specific security and field.
   * @param dataobject The security currency object
   * @param field The column configuration
   * @param valueField The value field (unused)
   * @returns The correlation value or null
   */
  getCorrelation(dataobject: Securitycurrency, field: ColumnConfig, valueField: any): string | number {
    if (this.correlationResult()) {
      const ci: CorrelationInstrument = this.correlationResult().correlationInstruments.find(
        (cIn) => cIn.idSecuritycurrency === dataobject.idSecuritycurrency
      );
      return ci ? ci.correlations[field.userValue] : null;
    } else {
      return null;
    }
  }

  /**
   * Gets the min/max date for a specific security.
   * @param dataobject The security currency object
   * @param field The column configuration
   * @param valueField The value field (unused)
   * @returns The formatted date or null
   */
  getMinMaxDate(dataobject: Securitycurrency, field: ColumnConfig, valueField: any): string | number {
    if (this.correlationResult()) {
      const mmdh: MinMaxDateHistoryquote = this.correlationResult().mmdhList.find(
        (mmdHist) => mmdHist.idSecuritycurrency === dataobject.idSecuritycurrency
      );
      return AppHelper.getValueByPathWithField(this.gps, this.translateService, mmdh, field, field.field);
    } else {
      return null;
    }
  }

  /**
   * Gets the background color based on correlation value.
   * @param dataobject The security currency object
   * @param field The column configuration
   * @returns HSL color string or null
   */
  getBackgroundColor(dataobject: Securitycurrency, field: ColumnConfig): string {
    if (field.userValue != null) {
      const value: number = this.getCorrelation(dataobject, field, null) as number;
      return 'hsl(' + (value + 1) * 58 + ',100%, 50%)';
    }
    return null;
  }

  /** Component cleanup */
  ngOnDestroy(): void {
    this.subscriptionInstrumentAdded?.unsubscribe();
    this.chartRequest?.unsubscribe();
    this.chartDataService.publish(null);
  }

  /**
   * Reads tenant limits once when correlation set is available.
   * @param correlationSet The correlation set to get limits for
   */
  private readListLimitOnce(correlationSet: CorrelationSet): void {
    if (!this.tenantLimits() && correlationSet) {
      this.correlationSetService
        .getCorrelationSetInstrumentLimit(correlationSet.idCorrelationSet)
        .pipe(takeUntilDestroyed(this.destroyRef))
        .subscribe((limit) => {
          this.tenantLimits.set([{ ...limit, actual: this.securitycurrencyList().length }]);
          this.childToParent().refreshMenus();
        });
    }
  }

  private updateLimitCount(): void {
    this.tenantLimits.update(
      (limits) => limits?.map((limit) => ({ ...limit, actual: this.securitycurrencyList().length })) ?? null
    );
    this.childToParent()?.refreshMenus();
  }

  /**
   * Creates dynamic table definition based on correlation set and results.
   * @param correlationSet The correlation set to display
   * @param correlationResult The correlation calculation results
   */
  private createDynamicTableDefinition(correlationSet: CorrelationSet, correlationResult: CorrelationResult): void {
    this.removeAllColumns();
    this.correlationSet.set(
      correlationSet
        ? Object.assign(new CorrelationSet(), correlationSet, {
            securitycurrencyList: correlationSet.securitycurrencyList.map((row, index) =>
              Object.hasOwn(row, this.tickerSymbol) && !(row as Security).tickerSymbol
                ? Object.assign(new Security(), row, { tickerSymbol: '(' + index + ')' })
                : row
            )
          })
        : null
    );
    this.correlationResult.set(correlationResult);
    this.addColumnFeqH(DataType.String, this.nameHeader, true, false, {
      width: 250,
      templateName: BaseSettings.OWNER_TEMPLATE
    });
    this.addColumnFeqH(DataType.String, 'currency', true, false);
    if (correlationSet) {
      if (correlationResult) {
        if (correlationResult.mmdhList.length === 0) {
          this.addTickerAndCorrelationColumnDefinition(this.correlationSet());
        } else {
          this.addMinMaxColumnDefinition();
        }
      }
    }
    this.translateHeadersAndColumns();
  }

  /**
   * Adds ticker and correlation columns to the table.
   * @param correlationSet The correlation set containing securities
   */
  private addTickerAndCorrelationColumnDefinition(correlationSet: CorrelationSet): void {
    this.addColumnFeqH(DataType.String, this.tickerSymbol, true, false);
    let i = 0;
    correlationSet.securitycurrencyList.forEach((sc) => {
      let label = sc.hasOwnProperty(this.tickerSymbol) ? (<Security>sc)[this.tickerSymbol] : sc.name;
      if (!label) {
        label = '(' + i + ')';
      }
      this.addColumnFeqH(DataType.NumericShowZero, label, true, false, {
        fieldValueFN: this.getCorrelation.bind(this),
        userValue: i++
      });
    });
  }

  /** Adds min/max date columns to the table */
  private addMinMaxColumnDefinition(): void {
    this.addColumnFeqH(DataType.DateString, this.minDate, true, false, {
      fieldValueFN: this.getMinMaxDate.bind(this)
    });
    this.addColumnFeqH(DataType.DateString, 'maxDate', true, false, {
      fieldValueFN: this.getMinMaxDate.bind(this)
    });
  }

  /** Sets up subscription for instrument addition events */
  private addInstrumentsToCorrelationSet(): void {
    this.subscriptionInstrumentAdded = this.dataChangedService.dateChanged$
      .pipe(takeUntilDestroyed(this.destroyRef))
      .subscribe((processedActionData) => {
        if (
          processedActionData.data?.idCorrelationSet === this.correlationSet()?.idCorrelationSet &&
          processedActionData.action === ProcessedAction.UPDATED
        ) {
          this.messageToastService.showMessageI18n(InfoLevelType.SUCCESS, 'ADDED_SECURITY_TO_WATCHLIST');
          this.childToParent().refreshData(processedActionData.data);
          this.updateLimitCount();
        }
      });
  }

  /** Reloads surviving pairs when dates, adjustments or instruments change. */
  public refreshChartWhenCorrelationSetChanges(): void {
    const ids = new Set(this.correlationSet()?.securitycurrencyList.map((sc) => sc.idSecuritycurrency) ?? []);
    Object.keys(this.chartPairs).forEach((key) => {
      if (this.chartPairs[key].some((id) => !ids.has(id))) {
        delete this.chartPairs[key];
      }
    });
    if (
      !this.correlationSet() ||
      SamplingPeriodType[this.correlationSet().samplingPeriod] === SamplingPeriodType.ANNUAL_RETURNS
    ) {
      this.chartPairs = {};
    }
    this.loadChartPairs();
  }

  /** Adds a pair to the already open chart. */
  private changeToOpenChart(): void {
    if (this.isChartShow()) {
      this.addSelectedChartPair();
      this.loadChartPairs();
    }
  }

  /** Replaces the plotted selection and opens the chart in the lower outlet. */
  private navigateToChartRoute(): void {
    this.chartPairs = {};
    this.addSelectedChartPair();
    this.loadChartPairs();
    this.router.navigate([
      BaseSettings.MAINVIEW_KEY + '/',
      { outlets: { mainbottom: [AppSettings.CHART_GENERAL_PURPOSE, AppSettings.CORRELATION_CHART] } }
    ]);
  }

  private isChartShow(): boolean {
    return this.chartDataService.shown;
  }

  private addSelectedChartPair(): void {
    const pair = [this.securityCurrencyColumn.idSecuritycurrency, this.selectedEntity().idSecuritycurrency].sort(
      (a, b) => a - b
    );
    this.chartPairs[pair.join(',')] = pair;
  }

  /** Reloads all selected pairs together so every series has the same common trading dates. */
  private loadChartPairs(): void {
    this.chartRequest?.unsubscribe();
    this.chartDataService.publish(null);
    const pairs = Object.values(this.chartPairs);
    if (!pairs.length) {
      return;
    }
    const set = this.correlationSet();
    const title = 'CORRELATION_ROLLING|' + this.childToParent().getPeriodAndRollingWithParamPrefix().join('|');
    this.chartRequest = this.correlationSetService
      .getRollingCorrelations(set.idCorrelationSet, pairs)
      .pipe(takeUntilDestroyed(this.destroyRef))
      .subscribe({
        next: (results: CorrelationRollingResult[]) =>
          this.chartDataService.publish({
            results,
            title,
            includeDividends: set.includeDividends,
            adjustCurrency: set.adjustCurrency
          }),
        error: () => this.chartDataService.publish(null)
      });
  }
}

/**
 * Interface for communication between child correlation table component and parent component.
 */
export interface ChildToParent {
  /**
   * Refreshes data in the parent component.
   * @param correlationSet The updated correlation set or null
   */
  refreshData(correlationSet: CorrelationSet): void;

  /** Rebuilds the menus after asynchronously loaded instrument limits change. */
  refreshMenus(): void;

  /**
   * Gets period and rolling parameters with prefix for display.
   * @returns Array of formatted parameter strings
   */
  getPeriodAndRollingWithParamPrefix(): string[];
}
