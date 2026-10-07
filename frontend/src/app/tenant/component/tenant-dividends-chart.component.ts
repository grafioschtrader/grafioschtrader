import {
  AfterViewInit,
  ChangeDetectionStrategy,
  Component,
  ElementRef,
  OnDestroy,
  OnInit,
  ViewChild
} from '@angular/core';
import { CommonModule } from '@angular/common';
import { TranslateModule, TranslateService } from '@ngx-translate/core';
import { combineLatest, Subscription } from 'rxjs';
import { filter } from 'rxjs/operators';
import { FormBase } from '../../lib/edit/form.base';
import { DynamicFormComponent } from '../../lib/dynamic-form/containers/dynamic-form/dynamic-form.component';
import { DynamicFormModule } from '../../lib/dynamic-form/dynamic-form.module';
import { DynamicFieldHelper } from '../../lib/helper/dynamic.field.helper';
import { TranslateHelper } from '../../lib/helper/translate.helper';
import { AppHelper } from '../../lib/helper/app.helper';
import { ValueKeyHtmlSelectOptions } from '../../lib/dynamic-form/models/value.key.html.select.options';
import { GlobalparameterService } from '../../lib/services/globalparameter.service';
import { UserSettingsService } from '../../lib/services/user.settings.service';
import { IGlobalMenuAttach } from '../../lib/mainmenubar/component/iglobal.menu.attach';
import { ActivePanelService } from '../../lib/mainmenubar/service/active.panel.service';
import { ViewSizeChangedService } from '../../lib/layout/service/view.size.changed.service';
import { AppHelpIds } from '../../shared/help/help.ids';
import { PortfolioService } from '../../portfolio/service/portfolio.service';
import { AppSettings } from '../../shared/app.settings';
import { PlotlyLocales } from '../../shared/plotlylocale/plotly.locales';
import { BusinessSelectOptionsHelper } from '../../shared/securitycurrency/business.select.options.helper';
import {
  IncomeAssetclass,
  IncomeYear,
  SecurityDividendsChart
} from '../../entities/view/securitydividends/security.dividends.chart';
import { IdsAccounts } from '../model/ids.accounts';
import { TenantDividendsChartService } from '../service/tenant.dividends.chart.service';

declare let Plotly: any;

/**
 * The charts the user can choose from. The keys are also their translation keys.
 */
enum IncomeChartType {
  INCOME_CHART_MONTHLY = 'INCOME_CHART_MONTHLY',
  INCOME_CHART_INCOME_COST = 'INCOME_CHART_INCOME_COST',
  INCOME_CHART_ASSETCLASS_YEARS = 'INCOME_CHART_ASSETCLASS_YEARS',
  INCOME_CHART_ASSETCLASS_SHARE = 'INCOME_CHART_ASSETCLASS_SHARE',
  INCOME_CHART_HEATMAP = 'INCOME_CHART_HEATMAP',
  INCOME_CHART_CUMULATIVE = 'INCOME_CHART_CUMULATIVE'
}

/** Whether a chart refers to one selected year, which shows the year select. */
const CHART_NEEDS_YEAR: { [key in IncomeChartType]: boolean } = {
  INCOME_CHART_MONTHLY: true,
  INCOME_CHART_INCOME_COST: false,
  INCOME_CHART_ASSETCLASS_YEARS: false,
  INCOME_CHART_ASSETCLASS_SHARE: true,
  INCOME_CHART_HEATMAP: false,
  INCOME_CHART_CUMULATIVE: true
};

/**
 * Categorical series colors in their fixed order, plus the chart chrome. The order is the colorblind safety
 * mechanism, so a series keeps its slot and slots are never cycled.
 */
const SERIES = ['#2a78d6', '#eb6834', '#1baf7a', '#eda100', '#e87ba4', '#008300', '#4a3aa7', '#e34948'];
const SURFACE = '#fcfcfb';
const GRID = '#e1e0d9';
const BASELINE = '#c3c2b7';
const MUTED = '#898781';
const TEXT_SECONDARY = '#52514e';
const SEQUENTIAL_BLUE: [number, string][] = [
  [0, '#cde2fb'],
  [0.25, '#86b6ef'],
  [0.5, '#3987e5'],
  [0.75, '#1c5cab'],
  [1, '#0d366b']
];

/** Asset classes beyond this number are folded into one "other" series, because there are only so many slots. */
const MAX_ASSETCLASS_SERIES = SERIES.length - 1;

/** Opacity of the withholding tax segment stacked on top of its net amount. */
const TAX_OPACITY = 0.4;

/**
 * Charts of the dividends view in the lower display area. The user chooses one of several charts of income and costs;
 * charts that refer to a single year take the year of the row selected in the dividends table. The figures are
 * aggregated by the backend with the same account selection as the dividends table; this component only maps them to
 * Plotly traces. The table publishes its account selection, the selected year row and data changes through
 * {@link TenantDividendsChartService}.
 */
@Component({
  template: `
    <div
      class="data-container dividends-chart"
      (click)="onComponentClick($event)"
      [ngClass]="{ 'active-border': isActivated(), 'passiv-border': !isActivated() }">
      <dynamic-form
        [config]="config"
        [formConfig]="formConfig"
        [translateService]="translateService"
        #form="dynamicForm">
      </dynamic-form>
      @if (chartNeedsYear) {
        <small>{{ 'DIVIDENDS_CHART_YEAR_HINT' | translate }}</small>
      }
      @if (noDataTextKey) {
        <h4>{{ noDataTextKey | translate }}</h4>
      }
      <div #chart class="dividends-chart-plot" [hidden]="!!noDataTextKey"></div>
    </div>
  `,
  styles: [
    `
      .dividends-chart {
        display: flex;
        flex-direction: column;
        height: calc(100% - 8px);
      }
      .dividends-chart-plot {
        flex: 1 1 auto;
        min-height: 320px;
        width: 100%;
      }
    `
  ],
  standalone: true,
  changeDetection: ChangeDetectionStrategy.Eager,
  imports: [CommonModule, TranslateModule, DynamicFormModule]
})
export class TenantDividendsChartComponent
  extends FormBase
  implements OnInit, AfterViewInit, OnDestroy, IGlobalMenuAttach
{
  @ViewChild(DynamicFormComponent, { static: true }) form: DynamicFormComponent;
  @ViewChild('chart', { static: true }) chartElement: ElementRef;

  /** Translation key of the hint shown instead of an empty chart, null when the chart has data. */
  noDataTextKey: string = null;

  /** True when the shown chart refers to one year; the year is then chosen in the dividends table. */
  chartNeedsYear = false;

  /** Year the year based charts refer to; set by clicking a year row of the dividends table. */
  private selectedYear: number = null;
  private chartData: SecurityDividendsChart;
  private idsAccounts: IdsAccounts;
  private formReady = false;
  private plotlyConfig: any;
  private monthNames: string[];
  private subscriptions: Subscription[] = [];

  constructor(
    private portfolioService: PortfolioService,
    private tenantDividendsChartService: TenantDividendsChartService,
    private activePanelService: ActivePanelService,
    private viewSizeChangedService: ViewSizeChangedService,
    private usersettingsService: UserSettingsService,
    private gps: GlobalparameterService,
    public translateService: TranslateService
  ) {
    super();
    this.formConfig = AppHelper.getDefaultFormConfig(this.gps, 2, null, true);
    const monthFormat = new Intl.DateTimeFormat(this.gps.getLocale(), { month: 'short' });
    this.monthNames = Array.from({ length: 12 }, (_, i) => monthFormat.format(new Date(2000, i, 1)));
  }

  ngOnInit(): void {
    this.plotlyConfig = PlotlyLocales.setPlotyLocales(Plotly, this.gps);
    this.plotlyConfig.displaylogo = false;
    this.plotlyConfig.responsive = true;
    this.createInputFormDefinition();
    this.subscriptions.push(
      this.tenantDividendsChartService.accounts$.pipe(filter((ids) => ids != null)).subscribe((ids) => {
        this.idsAccounts = ids;
        this.readData();
      }),
      this.tenantDividendsChartService.dataChanged$.subscribe(() => this.readData()),
      this.tenantDividendsChartService.yearSelected$
        .pipe(filter((year) => year != null))
        .subscribe((year) => this.selectYear(year)),
      this.viewSizeChangedService.viewSizeChanged$.subscribe(() => this.resizeChart())
    );
  }

  ngAfterViewInit(): void {
    // The dynamic-form builds its FormGroup in its own ngOnInit, so the controls only exist now.
    Promise.resolve().then(() => {
      const storedType = this.usersettingsService.readSingleValue(this.getStoreKey(AppSettings.DIV_CHART_TYPE));
      this.configObject.chartType.formControl.setValue(
        Object.values(IncomeChartType).includes(storedType) ? storedType : IncomeChartType.INCOME_CHART_MONTHLY
      );
      this.subscriptions.push(
        this.configObject.chartType.formControl.valueChanges.subscribe((chartType) => {
          this.usersettingsService.saveSingleValue(this.getStoreKey(AppSettings.DIV_CHART_TYPE), chartType);
          this.drawChart();
        })
      );
      this.formReady = true;
      this.drawChart();
    });
  }

  isActivated(): boolean {
    return this.activePanelService.isActivated(this);
  }

  onComponentClick(event): void {
    this.activePanelService.activatePanel(this);
  }

  hideContextMenu(): void {}

  callMeDeactivate(): void {}

  getHelpContextId(): string {
    return AppHelpIds.HELP_PORTFOLIOS_DIVIDENDS_CHARTS;
  }

  ngOnDestroy(): void {
    this.subscriptions.forEach((s) => s.unsubscribe());
    Plotly.purge(this.chartElement.nativeElement);
    this.activePanelService.destroyPanel(this);
  }

  private createInputFormDefinition(): void {
    this.config = [DynamicFieldHelper.createFieldSelectStringHeqF('chartType', true, { usedLayoutColumns: 6 })];
    this.configObject = TranslateHelper.prepareFieldsAndErrors(this.translateService, this.config);
    const chartTypes = Object.values(IncomeChartType);
    this.translateService.get(chartTypes).subscribe((t) => {
      this.configObject.chartType.valueKeyHtmlOptions = chartTypes.map(
        (ct) => new ValueKeyHtmlSelectOptions(ct, t[ct])
      );
    });
  }

  private getStoreKey(propertyKey: string): string {
    return this.gps.getIdTenant() + '_' + propertyKey;
  }

  private readData(): void {
    this.portfolioService
      .getSecurityDividendsChartByTenant(this.idsAccounts.idsSecurityaccount, this.idsAccounts.idsCashaccount)
      .subscribe((data: SecurityDividendsChart) => {
        this.chartData = data;
        this.resolveSelectedYear();
        this.drawChart();
      });
  }

  /**
   * Keeps the selected year when the data still contains it, otherwise falls back to the newest year with
   * distributions, or the newest year at all.
   */
  private resolveSelectedYear(): void {
    const iys = this.chartData.incomeYears;
    if (!iys.some((iy) => iy.year === this.selectedYear)) {
      this.selectedYear = [...iys].reverse().find((iy) => this.hasDistributions(iy))?.year ?? iys.at(-1)?.year ?? null;
    }
  }

  /**
   * Takes over the year of the row the user selected in the dividends table.
   *
   * @param year - The selected year
   */
  private selectYear(year: number): void {
    if (year !== this.selectedYear) {
      this.selectedYear = year;
      this.chartData && this.resolveSelectedYear();
      this.drawChart();
    }
  }

  private drawChart(): void {
    if (!this.formReady || !this.chartData) {
      return;
    }
    const chartType: IncomeChartType = this.configObject.chartType.formControl.value;
    this.chartNeedsYear = CHART_NEEDS_YEAR[chartType];
    const figure = this.buildFigure(chartType, this.selectedYear);
    this.noDataTextKey = figure ? null : CHART_NEEDS_YEAR[chartType] ? 'NO_INCOME_IN_YEAR' : 'NO_DATA_AVAILABLE';
    Plotly.purge(this.chartElement.nativeElement);
    if (figure) {
      // Wait until the chart element is no longer hidden, otherwise Plotly measures a zero size.
      setTimeout(() => Plotly.newPlot(this.chartElement.nativeElement, figure.data, figure.layout, this.plotlyConfig));
    }
  }

  private resizeChart(): void {
    if (!this.noDataTextKey && this.chartElement.nativeElement.data) {
      Plotly.Plots.resize(this.chartElement.nativeElement);
    }
  }

  private buildFigure(chartType: IncomeChartType, year: number): { data: any[]; layout: any } {
    switch (chartType) {
      case IncomeChartType.INCOME_CHART_MONTHLY:
        return this.buildMonthlyChart(this.chartData.incomeYears.find((iy) => iy.year === year));
      case IncomeChartType.INCOME_CHART_INCOME_COST:
        return this.buildIncomeCostChart();
      case IncomeChartType.INCOME_CHART_ASSETCLASS_YEARS:
        return this.buildAssetclassYearsChart();
      case IncomeChartType.INCOME_CHART_ASSETCLASS_SHARE:
        return this.buildAssetclassShareChart(year);
      case IncomeChartType.INCOME_CHART_HEATMAP:
        return this.buildHeatmapChart();
      case IncomeChartType.INCOME_CHART_CUMULATIVE:
        return this.buildCumulativeChart(year);
    }
    return null;
  }

  /** Distributions of the selected year per month, net with the withholding tax stacked on top. */
  private buildMonthlyChart(iy: IncomeYear): { data: any[]; layout: any } {
    if (!iy || (!this.hasDistributions(iy) && !iy.cashInterestMC)) {
      return null;
    }
    const x = this.monthNames;
    const data = [
      this.bar('DIVIDEND_NET', x, iy.dividendNetMonthMC, SERIES[0]),
      this.bar('DIVIDEND_WITHHOLDING_TAX', x, iy.dividendTaxMonthMC, SERIES[0], TAX_OPACITY),
      this.bar('INTEREST_NET', x, iy.interestNetMonthMC, SERIES[1]),
      this.bar('INTEREST_WITHHOLDING_TAX', x, iy.interestTaxMonthMC, SERIES[1], TAX_OPACITY),
      { ...this.bar('INTEREST_CASHACCOUNT', x, iy.cashInterestMonthMC, SERIES[2]), visible: 'legendonly' }
    ];
    return { data, layout: this.createLayout('INCOME_CHART_MONTHLY', String(iy.year), 'relative') };
  }

  /** Income upwards and costs downwards per year, with the net income as a line on the same axis. */
  private buildIncomeCostChart(): { data: any[]; layout: any } {
    const iys = this.chartData.incomeYears;
    if (iys.length === 0) {
      return null;
    }
    const x = iys.map((iy) => String(iy.year));
    const data: any[] = [
      this.bar(
        'DIVIDEND_NET',
        x,
        iys.map((iy) => iy.dividendNetMC),
        SERIES[0]
      ),
      this.bar(
        'DIVIDEND_WITHHOLDING_TAX',
        x,
        iys.map((iy) => iy.dividendTaxMC),
        SERIES[0],
        TAX_OPACITY
      ),
      this.bar(
        'INTEREST_NET',
        x,
        iys.map((iy) => iy.interestNetMC),
        SERIES[1]
      ),
      this.bar(
        'INTEREST_WITHHOLDING_TAX',
        x,
        iys.map((iy) => iy.interestTaxMC),
        SERIES[1],
        TAX_OPACITY
      ),
      this.bar(
        'INTEREST_CASHACCOUNT',
        x,
        iys.map((iy) => iy.cashInterestMC),
        SERIES[2]
      ),
      this.bar(
        'FINANCE_COST_CFD',
        x,
        iys.map((iy) => iy.financeCostCfdMC),
        SERIES[3]
      ),
      this.bar(
        'FINANCE_COST_FOREX',
        x,
        iys.map((iy) => iy.financeCostForexMC),
        SERIES[4]
      ),
      {
        ...this.bar(
          'FEE',
          x,
          iys.map((iy) => -iy.feeMC),
          SERIES[5]
        ),
        visible: 'legendonly'
      },
      {
        ...this.line(
          'NET_INCOME',
          x,
          iys.map((iy) => iy.netIncomeMC),
          SERIES[6]
        ),
        mode: 'lines+markers',
        marker: { size: 8, color: SERIES[6], line: { width: 2, color: SURFACE } }
      }
    ];
    return { data, layout: this.createLayout('INCOME_CHART_INCOME_COST', null, 'relative') };
  }

  /** Net distributions per year, stacked by asset class. */
  private buildAssetclassYearsChart(): { data: any[]; layout: any } {
    const groups = this.groupAssetclasses(this.chartData.incomeAssetclasses);
    if (groups.length === 0) {
      return null;
    }
    const years = this.chartData.incomeYears.map((iy) => iy.year);
    const x = years.map((y) => String(y));
    const data = groups.map((g, i) =>
      this.barRaw(
        g.name,
        x,
        years.map((y) => g.netByYear.get(y) ?? 0),
        SERIES[i]
      )
    );
    return { data, layout: this.createLayout('INCOME_CHART_ASSETCLASS_YEARS', null, 'relative') };
  }

  /** Share of each asset class in the net distributions of the selected year. */
  private buildAssetclassShareChart(year: number): { data: any[]; layout: any } {
    const groups = this.groupAssetclasses(this.chartData.incomeAssetclasses.filter((ia) => ia.year === year)).filter(
      (g) => g.total > 0
    );
    if (groups.length === 0) {
      return null;
    }
    const data = [
      {
        type: 'pie',
        hole: 0.55,
        sort: false,
        direction: 'clockwise',
        labels: groups.map((g) => g.name),
        values: groups.map((g) => g.total),
        marker: { colors: groups.map((_, i) => SERIES[i]), line: { color: SURFACE, width: 2 } },
        textinfo: 'percent',
        hovertemplate: `%{label}<br>%{value:${this.numberFormat()}} ${this.chartData.mainCurrency} (%{percent})<extra></extra>`
      }
    ];
    const layout = this.createLayout('INCOME_CHART_ASSETCLASS_SHARE', String(year), null);
    layout.legend = { orientation: 'v', x: 1.02, y: 0.5, font: { color: TEXT_SECONDARY } };
    return { data, layout };
  }

  /** Net income per year and month as a heat map, the withholding tax is shown in the tooltip. */
  private buildHeatmapChart(): { data: any[]; layout: any } {
    const iys = this.chartData.incomeYears;
    if (!iys.some((iy) => this.hasDistributions(iy) || iy.cashInterestMC)) {
      return null;
    }
    const currency = this.chartData.mainCurrency;
    const data = [
      {
        type: 'heatmap',
        x: this.monthNames,
        y: iys.map((iy) => String(iy.year)),
        z: iys.map((iy) => iy.netIncomeMonthMC),
        customdata: iys.map((iy) => iy.taxMonthMC),
        zmin: 0,
        colorscale: SEQUENTIAL_BLUE,
        xgap: 2,
        ygap: 2,
        colorbar: { title: { text: currency }, outlinewidth: 0 },
        hovertemplate:
          `%{y} %{x}<br>%{z:${this.numberFormat()}} ${currency}<br>` +
          `${this.translateService.instant('WITHHOLDING_TAX')}: %{customdata:${this.numberFormat()}} ${currency}` +
          '<extra></extra>'
      }
    ];
    const layout = this.createLayout('INCOME_CHART_HEATMAP', null, null);
    layout.yaxis = { type: 'category', autorange: 'reversed', gridcolor: GRID };
    layout.hovermode = 'closest';
    return { data, layout };
  }

  /**
   * One line per year with the net income accumulated over the months. The selected year and the year before keep
   * their series color, all other years recede to a muted gray so the comparison stays readable for many years.
   */
  private buildCumulativeChart(year: number): { data: any[]; layout: any } {
    const iys = this.chartData.incomeYears.filter((iy) => this.hasDistributions(iy) || iy.cashInterestMC);
    if (iys.length === 0) {
      return null;
    }
    const data = iys.map((iy) => {
      let sum = 0;
      const cumulated = iy.netIncomeMonthMC.map((v) => (sum += v));
      const color = iy.year === year ? SERIES[0] : iy.year === year - 1 ? SERIES[1] : BASELINE;
      const trace = this.lineRaw(String(iy.year), this.monthNames, cumulated, color);
      trace.line.width = iy.year === year ? 3 : 2;
      return trace;
    });
    // Draw the highlighted years last, so they lie above the muted lines.
    data.sort((a, b) => Number(a.line.color !== BASELINE) - Number(b.line.color !== BASELINE));
    return { data, layout: this.createLayout('INCOME_CHART_CUMULATIVE', String(year), null) };
  }

  /**
   * Groups the asset class rows by asset class, ordered by their total, and folds the classes beyond the available
   * series colors into one "other" group. This only serves the display, the totals come from the backend.
   */
  private groupAssetclasses(
    rows: IncomeAssetclass[]
  ): { name: string; total: number; netByYear: Map<number, number> }[] {
    const language = this.gps.getUserLang();
    const byId = new Map<number, { name: string; total: number; netByYear: Map<number, number> }>();
    rows.forEach((r) => {
      let group = byId.get(r.idAssetClass);
      if (!group) {
        const assetclass = this.chartData.assetclasses.find((ac) => ac.idAssetClass === r.idAssetClass);
        const name = BusinessSelectOptionsHelper.translateAssetclass(
          this.translateService,
          language,
          assetclass,
          null
        ).value;
        group = { name, total: 0, netByYear: new Map() };
        byId.set(r.idAssetClass, group);
      }
      group.total += r.netMC;
      group.netByYear.set(r.year, (group.netByYear.get(r.year) ?? 0) + r.netMC);
    });
    const groups = [...byId.values()].sort((a, b) => b.total - a.total);
    if (groups.length <= MAX_ASSETCLASS_SERIES + 1) {
      return groups;
    }
    const other = { name: this.translateService.instant('OTHER_ASSETCLASSES'), total: 0, netByYear: new Map() };
    groups.slice(MAX_ASSETCLASS_SERIES).forEach((g) => {
      other.total += g.total;
      g.netByYear.forEach((v, y) => other.netByYear.set(y, (other.netByYear.get(y) ?? 0) + v));
    });
    return [...groups.slice(0, MAX_ASSETCLASS_SERIES), other];
  }

  private hasDistributions(iy: IncomeYear): boolean {
    return !!(iy.dividendNetMC || iy.interestNetMC || iy.dividendTaxMC || iy.interestTaxMC);
  }

  private bar(nameKey: string, x: string[], y: number[], color: string, opacity = 1): any {
    return this.barRaw(this.translateService.instant(nameKey), x, y, color, opacity);
  }

  private barRaw(name: string, x: string[], y: number[], color: string, opacity = 1): any {
    return {
      type: 'bar',
      name,
      x,
      y,
      opacity,
      marker: { color, line: { color: SURFACE, width: 2 } },
      hovertemplate: `%{y:${this.numberFormat()}} ${this.chartData.mainCurrency}`
    };
  }

  private line(nameKey: string, x: string[], y: number[], color: string): any {
    return this.lineRaw(this.translateService.instant(nameKey), x, y, color);
  }

  private lineRaw(name: string, x: string[], y: number[], color: string): any {
    return {
      type: 'scatter',
      mode: 'lines',
      name,
      x,
      y,
      line: { color, width: 2 },
      hovertemplate: `%{y:${this.numberFormat()}} ${this.chartData.mainCurrency}`
    };
  }

  /** Plotly number format with thousands separator and the precision of the main currency. */
  private numberFormat(): string {
    return `,.${this.gps.getCurrencyPrecision(this.chartData.mainCurrency)}f`;
  }

  private createLayout(titleKey: string, titleSuffix: string, barmode: string): any {
    const title = this.translateService.instant(titleKey) + (titleSuffix ? ' ' + titleSuffix : '');
    return {
      title: { text: title, font: { size: 15 } },
      barmode,
      barcornerradius: 4,
      bargap: 0.3,
      hovermode: 'x unified',
      paper_bgcolor: SURFACE,
      plot_bgcolor: SURFACE,
      font: { color: TEXT_SECONDARY },
      margin: { t: 50, l: 80, r: 20, b: 40 },
      xaxis: { type: 'category', showgrid: false, linecolor: BASELINE, tickfont: { color: MUTED } },
      yaxis: {
        title: { text: this.chartData.mainCurrency },
        gridcolor: GRID,
        zerolinecolor: BASELINE,
        tickfont: { color: MUTED },
        separatethousands: true
      },
      legend: { orientation: 'h', x: 0, y: -0.12, yanchor: 'top', font: { color: TEXT_SECONDARY } }
    };
  }
}
