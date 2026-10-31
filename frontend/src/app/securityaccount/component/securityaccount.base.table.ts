import { requireInitialized } from '../../lib/helper/required-state';
import type { PanelClickEvent } from '../../lib/mainmenubar/component/iglobal.menu.attach';
import { IGlobalMenuAttach } from '../../lib/mainmenubar/component/iglobal.menu.attach';
import { GlobalparameterGTService } from '../../gtservice/globalparameter.gt.service';
import { DisposalCostHelper } from '../../shared/helper/disposal.cost.helper';
import type { SecurityPositionGroupSummary } from '../../entities/view/security.position.group.summary';
import { TableConfigBase } from '../../lib/datashowbase/table.config.base';
import type { SecurityPositionDynamicGroupSummary } from '../../entities/view/security.position.dynamic.group.summary';
import { Directive, ElementRef, viewChild, inject, signal } from '@angular/core';
import { SecurityaccountGroupBase } from './securityaccount.group.base';
import type { SecurityPositionCurrenyGroupSummary } from '../../entities/view/security.position.curreny.group.summary';
import { Subscription } from 'rxjs';
import { ColumnConfig, ColumnGroupConfig } from '../../lib/datashowbase/column.config';
import type { SecurityPositionSummary } from '../../entities/view/security.position.summary';
import { TransactionCallParam } from '../../transaction/component/transaction.call.parm';
import { MenuItem, MenuItemCommandEvent, SelectItem } from '@openng/optimus-ui/api';
import { Portfolio } from '../../entities/portfolio';
import type { SecurityPositionGrandSummary } from '../../entities/view/security.position.grand.summary';
import { SecurityaccountService } from '../service/securityaccount.service';
import { ActivePanelService } from '../../lib/mainmenubar/service/active.panel.service';
import { ActivatedRoute, Router } from '@angular/router';
import { ChartDataService } from '../../shared/chart/service/chart.data.service';
import { MessageToastService } from '../../lib/message/message.toast.service';
import { AppSettings } from '../../shared/app.settings';
import { DataType } from '../../lib/dynamic-form/models/data.type';
import { TransactionType } from '../../shared/types/transaction.type';
import { Security } from '../../entities/security';
import { ProcessedAction } from '../../lib/types/processed.action';
import { ProcessedActionData } from '../../lib/types/processed.action.data';
import { HelpIds } from '../../lib/help/help.ids';
import { OptionalParameters, TimeSeriesQuotesService } from '../../historyquote/service/time.series.quotes.service';
import { AssetclassType } from '../../shared/types/assetclass.type';
import { LastpriceOrigin } from '../../entities/types/lastprice.origin';
import { Securitycurrency } from '../../entities/securitycurrency';
import { TranslateHelper } from '../../lib/helper/translate.helper';
import { BusinessHelper } from '../../shared/helper/business.helper';
import { ProductIconService } from '../../securitycurrency/service/product.icon.service';
import { SpecialInvestmentInstruments } from '../../shared/types/special.investment.instruments';
import { AlarmSetupService } from '../../algo/service/alarm.setup.service';
import { BaseSettings } from '../../lib/base.settings';
import { Securityaccount } from '../../entities/securityaccount';
import type { NgxFileDropEntry } from 'ngx-file-drop';

@Directive()
export abstract class SecurityaccountBaseTable extends TableConfigBase implements IGlobalMenuAttach {
  protected timeSeriesQuotesService: TimeSeriesQuotesService = inject(TimeSeriesQuotesService);
  protected alarmSetupService: AlarmSetupService = inject(AlarmSetupService);
  protected activePanelService: ActivePanelService = inject(ActivePanelService);
  protected messageToastService: MessageToastService = inject(MessageToastService);
  protected securityaccountService: SecurityaccountService = inject(SecurityaccountService);
  protected productIconService: ProductIconService = inject(ProductIconService);
  protected activatedRoute: ActivatedRoute = inject(ActivatedRoute);
  protected router: Router = inject(Router);
  private chartDataService: ChartDataService = inject(ChartDataService);

  // @ViewChild('cm', { static: false }) contextMenu: ContextMenu;
  readonly contextMenu = viewChild<any>('cm');
  readonly titleElementRef = viewChild<ElementRef>('title');

  translatedTitle?: string;

  /**
   * Optional comparison of the report against the allocation of one rule based trading strategy. Declared here rather
   * than on the one report that offers it, because all reports built on this class share one template: a member the
   * template names has to exist on every one of them. Only the asset class report fills these; everywhere else they
   * stay undefined and the corresponding blocks of the template are not rendered.
   */
  algoTopOptions?: SelectItem[];
  selectedIdAlgoTop: number | null = null;
  rebalancingSummaryFields: ColumnConfig[] | null = null;
  /** Summary figures whose header key has a {@code _TOOLTIP} companion explaining how the figure is measured. */
  readonly summaryFieldsWithTooltip: string[] = [
    'overallAllocationMismatchPercentage',
    'topTargetPercentage',
    'topActualPercentage',
    'topDeviationPercentage'
  ];

  /**
   * Selection of the grouping offered by the reports on {@link SecurityaccountTable}. Declared here for the same reason
   * as {@link algoTopOptions}; the asset class report leaves groupOptions undefined and shows no selection.
   */
  idTenant?: number;
  idPortfolio?: number;
  groupOptions?: SelectItem[];
  selectedGroup?: string;
  protected securityAccount: Securityaccount | null = null;

  /**
   * PDF drop zone of the report of a single securities account. Declared here for the same reason as
   * {@link algoTopOptions}; only SecurityaccountSummaryComponent overrides the getters and shows the zone.
   */
  useGtPlatformDrop = false;

  showTable = true;
  untilDate: Date;

  private initializedSecurityaccountGroupBase?: SecurityaccountGroupBase<
    SecurityPositionDynamicGroupSummary<any> | SecurityPositionCurrenyGroupSummary,
    any
  >;

  get securityaccountGroupBase(): SecurityaccountGroupBase<
    SecurityPositionDynamicGroupSummary<any> | SecurityPositionCurrenyGroupSummary,
    any
  > {
    return requireInitialized(
      this.initializedSecurityaccountGroupBase,
      'SecurityaccountBaseTable.securityaccountGroupBase'
    );
  }

  set securityaccountGroupBase(
    value: SecurityaccountGroupBase<SecurityPositionDynamicGroupSummary<any> | SecurityPositionCurrenyGroupSummary, any>
  ) {
    this.initializedSecurityaccountGroupBase = value;
  }
  securityPositionAll: SecurityPositionSummary[] | null = null;
  securityPositionSummary: SecurityPositionGrandSummary | null = null;
  groupMap: Map<any, SecurityaccountGroupBase<any, any>> = new Map();
  groupChangeIndexMap: Map<number, SecurityPositionGroupSummary> = new Map();
  // For child transaction dialog
  private initializedTransactionCallParam?: TransactionCallParam;

  get transactionCallParam(): TransactionCallParam {
    return requireInitialized(this.initializedTransactionCallParam, 'SecurityaccountBaseTable.transactionCallParam');
  }

  set transactionCallParam(value: TransactionCallParam) {
    this.initializedTransactionCallParam = value;
  }
  visibleSecurityTransactionDialog = false;
  // For row expansion, used when a transaction was changed
  expandedSecurityPositionSummary: SecurityPositionSummary[] = [];
  expandedIdSecuritycurrency: number[] = [];
  contextMenuItems: MenuItem[] | null = [];
  selectedSecurityPositionSummary: SecurityPositionSummary | null = null;
  portfolio?: Portfolio;
  protected includeClosedPosition = false;
  protected internalColumnConfigs: ColumnConfig[] = [];
  private subscriptionRequestFromChart?: Subscription;

  readonly dataLoading = signal(false);
  constructor() {
    super();
    this.untilDate = BusinessHelper.getUntilDateBySessionStorage();
  }

  /**
   * Selecting a strategy to compare against. A report that offers no strategy never renders the dropdown, so the
   * default does nothing rather than pretending to switch something.
   *
   * @param event - Selection event of the strategy dropdown
   */
  handleChangeAlgoTop(event: any): void {}

  /**
   * Selecting another grouping. Only the reports on {@link SecurityaccountTable} render the dropdown and override this.
   *
   * @param event - Selection event of the grouping dropdown
   */
  handleChangeGroup(event: any): void {}

  /** True when the securities account is mapped to its own import platform; only the single account report knows. */
  get accountHasOwnPlatform(): boolean {
    return false;
  }

  /** True when the tenant provides a Grafioschtrader import platform; only the single account report asks. */
  get gtPlatformConfigured(): boolean {
    return false;
  }

  /** Whether the template renders the PDF drop zone. */
  get showDropZone(): boolean {
    return false;
  }

  onDropUseGtPlatformToggle(event: Event): void {
    this.useGtPlatformDrop = (event.target as HTMLInputElement).checked;
  }

  /**
   * Receives the files dropped onto the PDF drop zone, which only a report overriding {@link showDropZone} renders.
   *
   * @param files - The dropped files
   */
  dropped(files: NgxFileDropEntry[]): void {}

  getGroupValueByRowIndex(columnConfig: ColumnConfig, securityPositionSummary: SecurityPositionSummary): string {
    const groupValue = this.securityaccountGroupBase.getGroupValue(securityPositionSummary.security);
    return this.getValueColumnTotal(columnConfig, 0, this.groupMap, groupValue);
  }

  getGroupValue(columnConfig: ColumnConfig, securityPositionSummary: SecurityPositionSummary): string {
    const groupValue = this.securityaccountGroupBase.getGroupValue(securityPositionSummary.security);
    return this.getValueColumnTotal(columnConfig, 0, this.groupMap, groupValue);
  }

  transformToFlatArray(securityPositionGrandSummary: SecurityPositionGrandSummary): void {
    const positions: SecurityPositionSummary[] = [];
    this.securityPositionAll = positions;
    this.groupMap = new Map();
    this.groupChangeIndexMap = new Map();
    let rowIndex = -1;
    for (const securityPositionGroupSummary of securityPositionGrandSummary.securityPositionGroupSummaryList) {
      for (const securityPositionSummary of securityPositionGroupSummary.securityPositionSummaryList) {
        positions.push(securityPositionSummary);
        if (
          securityPositionSummary.security.idSecuritycurrency != null &&
          this.expandedIdSecuritycurrency.indexOf(securityPositionSummary.security.idSecuritycurrency) >= 0
        ) {
          this.expandedSecurityPositionSummary.push(securityPositionSummary);
        }
        rowIndex++;
      }
      this.groupChangeIndexMap.set(rowIndex, securityPositionGroupSummary);
      this.groupMap.set(
        this.securityaccountGroupBase.getGroupValueFromGroupRow(<any>securityPositionGroupSummary),
        <any>securityPositionGroupSummary
      );
    }
    this.showTable = true;
  }

  getMenuShowOptionsParam(securitycurrencySelected: Securitycurrency | null): MenuItem[] {
    const otherMenuShowOptions = super.getMenuShowOptions();

    let menuItems: MenuItem[] = [];
    if (otherMenuShowOptions) {
      menuItems.push(...otherMenuShowOptions);
    }

    if (securitycurrencySelected?.idSecuritycurrency != null && securitycurrencySelected.idSecuritycurrency >= 0) {
      menuItems = this.addTimeSeriesMenus(<Security>securitycurrencySelected, menuItems, false);
    }

    menuItems.push({ separator: true });
    menuItems.push({
      label: 'SHOW_CHART',
      command: (event) => this.navigateToChartRoute()
    });

    menuItems.push({ separator: true });
    menuItems.push({
      label: 'SHOW_CLOSE_POSITION',
      command: (event) => this.handleOpenClosePosition(event),
      icon: this.includeClosedPosition ? BaseSettings.ICONNAME_SQUARE_CHECK : BaseSettings.ICONNAME_SQUARE_EMTPY
    });

    if (securitycurrencySelected?.idSecuritycurrency != null && securitycurrencySelected.idSecuritycurrency >= 0) {
      menuItems.push(...BusinessHelper.getUrlLinkMenus(securitycurrencySelected));
    }

    TranslateHelper.translateMenuItems(menuItems, this.translateService);
    return menuItems;
  }

  public hideContextMenu() {
    const contextMenu = this.contextMenu();
    contextMenu && contextMenu.hide();
  }

  public override filterDate(event: unknown) {
    this.readData();
  }

  handleTransaction(transactionType: TransactionType, security: Security) {
    this.transactionCallParam = Object.assign(new TransactionCallParam(), {
      transactionType,
      security
    });
    this.extendTransactionParamData(this.transactionCallParam);
    this.visibleSecurityTransactionDialog = true;
    // The command may come from the menu bar, outside this view
    this.notifyViewChanged();
  }

  handleCloseTransactionDialog(processedActionData: ProcessedActionData) {
    this.visibleSecurityTransactionDialog = false;
    if (processedActionData.action !== ProcessedAction.NO_CHANGE) {
      this.readData();
    }
  }

  handleOpenClosePosition(event: MenuItemCommandEvent) {
    const item = event.item;
    if (!item) return;
    if (item.icon === BaseSettings.ICONNAME_SQUARE_EMTPY) {
      item.icon = BaseSettings.ICONNAME_SQUARE_CHECK;
      this.includeClosedPosition = true;
    } else {
      item.icon = BaseSettings.ICONNAME_SQUARE_EMTPY;
      this.includeClosedPosition = false;
    }
    this.readData();
  }

  transactionDataChanged(event: ProcessedActionData) {
    this.readData();
  }

  isActivated(): boolean {
    return this.activePanelService.isActivated(this);
  }

  callMeDeactivate(): void {}

  onRightClick(event: unknown): void {
    this.isActivated() && this.resetMenu();
  }

  onResetToDay(event: unknown): void {
    this.untilDate = new Date();
    this.readData();
  }

  ////////////////////////////////////////////////
  // For Chart

  onComponentClick(event: PanelClickEvent | null): void {
    if (!event?.[this.consumedGT]) {
      this.resetMenu();
    }
  }

  ///////////////////////////////////////////////
  onRowExpand(event: { data: { security: { idSecuritycurrency: number } } }) {
    this.expandedIdSecuritycurrency.push(event.data.security.idSecuritycurrency);
  }

  onRowCollapse(event: { data: { security: { idSecuritycurrency: number } } }) {
    const index = this.expandedIdSecuritycurrency.indexOf(event.data.security.idSecuritycurrency);
    this.expandedIdSecuritycurrency.splice(index, 1);
  }

  public getHelpContextId(): string {
    return HelpIds.HELP_PORTFOLIOS_SECURITY_ACCOUNT_REPORT;
  }

  destroy(): void {
    BusinessHelper.saveUntilDateInSessionStorage(this.untilDate);
    this.activePanelService.destroyPanel(this);
    this.subscriptionRequestFromChart?.unsubscribe();
  }

  /**
   * Style of one body cell, the column width plus the marking of a price that was not traded.
   *
   * A position whose instrument no longer receives intraday data is valued with its newest historical closing price. If
   * that closing price was itself produced by filling gaps in the historical prices, the number is a calculated one and
   * must be recognisable as such, which is what the yellow background says.
   *
   * @param positionSummary - Row of the table
   * @param field - Column configuration of the cell
   * @returns Style object for the native style binding
   */
  getCellStyle(positionSummary: SecurityPositionSummary, field: ColumnConfig): { [key: string]: string } {
    const widthStyle: Record<string, string> = field.width ? { 'flex-basis': '0 0 ' + field.width + 'px' } : {};
    if (DisposalCostHelper.DISPOSAL_FIELDS.includes(field.field) && positionSummary?.disposalComplete === false) {
      return { ...widthStyle, ...DisposalCostHelper.INCOMPLETE_STYLE };
    }
    return field.field === 'closePrice' && positionSummary?.closePriceOrigin === LastpriceOrigin.HISTORY_INTERPOLATED
      ? { ...widthStyle, 'background-color': 'rgba(234, 179, 8, 0.30)' }
      : widthStyle;
  }

  /**
   * Highlights the disposal cost total of a group whose estimate is incomplete.
   *
   * @param field - Column of the group row
   * @param group - Group summary of that row
   * @returns The highlighting style or an empty style
   */
  getGroupCellStyle(field: ColumnConfig, group: SecurityPositionGroupSummary | undefined): { [key: string]: string } {
    return DisposalCostHelper.DISPOSAL_FIELDS.includes(field.field) && group?.groupDisposalComplete === false
      ? DisposalCostHelper.INCOMPLETE_STYLE
      : {};
  }

  /**
   * Highlights the disposal cost grand total when the estimate of any position is incomplete.
   *
   * @param field - Column of the footer row
   * @returns The highlighting style or an empty style
   */
  getGrandCellStyle(field: ColumnConfig): { [key: string]: string } {
    return DisposalCostHelper.DISPOSAL_FIELDS.includes(field.field) &&
      this.securityPositionSummary?.grandDisposalComplete === false
      ? DisposalCostHelper.INCOMPLETE_STYLE
      : {};
  }

  /**
   * Tooltip of a position cell: for the disposal cost columns the matched rules and the reasons of unknown parts,
   * otherwise the displayed value.
   *
   * @param positionSummary - The position of the row
   * @param field - The column
   * @returns The tooltip text
   */
  getCellTooltip(positionSummary: SecurityPositionSummary, field: ColumnConfig): string | null {
    if (DisposalCostHelper.DISPOSAL_FIELDS.includes(field.field) && positionSummary?.disposalDetails) {
      return DisposalCostHelper.getDetailsText(positionSummary.disposalDetails, this.translateService, this.gps);
    }
    return this.getValueByPath(positionSummary, field);
  }

  getInstrumentIcon(securityPositionSummary: SecurityPositionSummary): string | null | undefined {
    return this.productIconService.getIconForInstrument(securityPositionSummary.security, null);
  }

  addUrlLinkMenus(securitycurrency: Securitycurrency, menuItems: MenuItem[], translate: boolean): MenuItem[] {
    const urlLinkMenuItems = BusinessHelper.getUrlLinkMenus(securitycurrency);
    translate && TranslateHelper.translateMenuItems(urlLinkMenuItems, this.translateService);
    return menuItems.concat(urlLinkMenuItems);
  }

  isMarginProduct(security: Security): boolean {
    return BusinessHelper.isMarginProduct(security);
  }

  protected extendEditMenu(menuItems: MenuItem[], securityPositionSummary: SecurityPositionSummary): void {}

  protected abstract readData(): void;

  protected abstract extendTransactionParamData(transactionCallParam: TransactionCallParam): void;

  protected abstract getOptionalParameters(): OptionalParameters | null;

  protected createColumns() {
    this.removeAllColumns();
    this.internalColumnConfigs = [];

    this.addColumn(DataType.String, 'security.name', 'NAME', true, false, {
      columnGroupConfigs: [
        new ColumnGroupConfig(
          'security.currency',
          'EXCHANGE_RATE',
          this.securityaccountGroupBase.getGroupRowFieldTextByRowIndex.bind(this.securityaccountGroupBase),
          { colspan: 99 }
        ),
        new ColumnGroupConfig(null, 'GRAND_TOTAL')
      ],
      width: 170
    });
    this.addColumn(DataType.String, AppSettings.SECURITY.toLowerCase(), AppSettings.INSTRUMENT_HEADER, true, true, {
      fieldValueFN: this.getInstrumentIcon.bind(this),
      templateName: 'icon',
      width: 20
    });

    this.addColumn(DataType.Numeric, 'units', 'HOLDING', true, false, { width: 55 });
    this.addColumnFeqH(DataType.String, 'security.currency', true, false, { width: 35 });

    this.addColumn(DataType.DateString, 'closeDate', 'TIMEDATE', true, false, { width: 90 });
    this.addColumn(DataType.Numeric, 'closePrice', 'LAST', true, false, {
      columnGroupConfigs: [new ColumnGroupConfig(null, 'TOTAL')],
      width: 60
    });

    this.addColumnFeqH(DataType.Numeric, 'gainLossSecurity', true, false, {
      currencyPrecisionField: 'security.currency',
      columnGroupConfigs: [
        new ColumnGroupConfig('groupGainLossSecurity', null, null, { currencyPrecisionField: 'currency' })
      ]
    });

    this.internalColumnConfigs.push(
      this.addColumnFeqH(DataType.Numeric, 'gainLossSecurityMC', true, true, {
        columnGroupConfigs: [
          new ColumnGroupConfig('groupGainLossSecurityMC'),
          new ColumnGroupConfig('grandGainLossSecurityMC')
        ]
      })
    );
    this.internalColumnConfigs.push(
      this.addColumnFeqH(DataType.Numeric, 'gainLossCurrencyMC', true, true, {
        columnGroupConfigs: [
          new ColumnGroupConfig('groupGainLossCurrencyMC'),
          new ColumnGroupConfig('grandGainLossCurrencyMC')
        ]
      })
    );

    this.addColumn(DataType.Numeric, 'accountValueSecurity', AppSettings.VALUE_SECURITY_ACCOUNT_HEADER, true, false, {
      currencyPrecisionField: 'security.currency',
      columnGroupConfigs: [
        new ColumnGroupConfig('groupAccountValueSecurity', null, null, { currencyPrecisionField: 'currency' })
      ]
    });

    this.internalColumnConfigs.push(
      this.addColumn(
        DataType.Numeric,
        AppSettings.VALUE_SECURITY_MAIN_CURRENCY_FIELD,
        AppSettings.VALUE_SECURITY_ACCOUNT_HEADER,
        true,
        true,
        {
          width: 100,
          columnGroupConfigs: [
            new ColumnGroupConfig('groupAccountValueSecurityMC'),
            new ColumnGroupConfig('grandAccountValueSecurityMC')
          ]
        }
      )
    );

    this.addDisposalCostColumns();

    this.addColumnFeqH(DataType.NumericRaw, 'shareOfTotalPercentage', true, true, {
      width: 60,
      templateName: 'greenRed',
      columnGroupConfigs: [
        new ColumnGroupConfig('groupShareOfTotalPercentage'),
        new ColumnGroupConfig('grandShareOfTotalPercentage')
      ]
    });

    this.securityaccountGroupBase.extendColumns(this.internalColumnConfigs);
    this.fields.filter((cc) => cc.dataType === DataType.Numeric).map((cc) => (cc.templateName = 'greenRed'));
  }

  /**
   * Adds the estimated disposal costs of the hypothetical sale and the value after them, only when the global parameter
   * gt.disposal.cost.estimate and the switch of the tenant are both on.
   */
  private addDisposalCostColumns(): void {
    if (this.injector?.get(GlobalparameterGTService).useDisposalCostEstimate()) {
      this.internalColumnConfigs.push(
        this.addColumnFeqH(DataType.Numeric, 'disposalCostMC', true, true, {
          width: 80,
          columnGroupConfigs: [
            new ColumnGroupConfig('groupDisposalCostMC'),
            new ColumnGroupConfig('grandDisposalCostMC')
          ]
        })
      );
      this.internalColumnConfigs.push(
        this.addColumnFeqH(DataType.Numeric, 'valueAfterDisposalMC', true, true, {
          width: 100,
          columnGroupConfigs: [
            new ColumnGroupConfig('groupValueAfterDisposalMC'),
            new ColumnGroupConfig('grandValueAfterDisposalMC')
          ]
        })
      );
    }
  }

  protected getDataToView(data: SecurityPositionGrandSummary) {
    this.securityPositionSummary = data;
    this.securityPositionAll = [];
    this.transformToFlatArray(this.securityPositionSummary);
    // The reports keep their positions in plain fields shared by one template; a load marks the view
    this.notifyViewChanged();
  }

  protected initTableTextTranslation() {
    const summary = this.securityPositionSummary;
    if (!summary) return;
    this.internalColumnConfigs.forEach((columnConfig) => {
      columnConfig.headerSuffix = summary.currency;
      columnConfig.fixedCurrency = summary.currency;
    });
    this.prepareTableAndTranslate();
    this.securityaccountGroupBase.translateGroupValues(<any[]>summary.securityPositionGroupSummaryList);
    this.notifyViewChanged();
    this.changeToOpenChart();
  }

  protected addTimeSeriesMenus(security: Security, menuItems: MenuItem[], translate: boolean): MenuItem[] {
    if (security?.idSecuritycurrency != null) {
      menuItems = menuItems.concat(
        this.timeSeriesQuotesService.getMenuItems(
          security.idSecuritycurrency,
          security.currency ?? null,
          true,
          this.addNoMarketValueOpt(security)
        )
      );
      menuItems.push(...this.alarmSetupService.getMenuItem(security));
      translate && TranslateHelper.translateMenuItems(menuItems, this.translateService);
    }
    return menuItems;
  }

  protected changeToOpenChart() {
    const summary = this.securityPositionSummary;
    const title = this.getTitleChart();
    if (this.subscriptionRequestFromChart && summary && title != null) {
      this.chartDataService.sentToChart(this.securityaccountGroupBase.getChartDefinition(title, summary));
    }
  }

  protected getTitleChart(): string | undefined {
    return this.titleElementRef()?.nativeElement.innerHTML.replace(/\s+/g, ' ');
  }

  protected getComponentId(): string {
    const routeUrlComponent = /(\/\w+\/)\({0,1}(\w+)/.exec(this.router.url);
    if (!routeUrlComponent) throw new Error('A holdings chart requires its report route.');
    return routeUrlComponent[2];
  }

  private addNoMarketValueOpt(security: Security): OptionalParameters {
    const optionalParameters = this.getOptionalParameters() || {};
    optionalParameters.noMarketValue = security.stockexchange?.noMarketValue;
    return optionalParameters;
  }

  /**
   * Opens the chart of this report in the lower display area. The general purpose chart requests its definition by the
   * component id of this report; a report with a chart component of its own overrides this.
   */
  protected navigateToChartRoute(): void {
    !this.subscriptionRequestFromChart && this.prepareChartDataWithRequest();
    this.router.navigate([
      BaseSettings.MAINVIEW_KEY + '/',
      {
        outlets: {
          mainbottom: [AppSettings.CHART_GENERAL_PURPOSE, this.getComponentId()]
        }
      }
    ]);
  }

  private prepareChartDataWithRequest(): void {
    this.subscriptionRequestFromChart = this.chartDataService.requestFromChart$.subscribe((id) => {
      if (id === this.getComponentId()) {
        this.changeToOpenChart();
      }
    });
  }

  private resetMenu(): void {
    this.contextMenuItems = this.getEditMenu(this.selectedSecurityPositionSummary);
    this.activePanelService.activatePanel(this, {
      showMenu: this.getMenuShowOptionsParam(
        this.selectedSecurityPositionSummary ? this.selectedSecurityPositionSummary.security : null
      ),
      editMenu: this.contextMenuItems
    });
    const selection = this.selectedSecurityPositionSummary;
    if (this.contextMenuItems && selection) {
      this.contextMenuItems = this.addTimeSeriesMenus(selection.security, this.contextMenuItems, true);
      this.contextMenuItems = this.addUrlLinkMenus(selection.security, this.contextMenuItems, true);
    }
  }

  private getEditMenu(securityPositionSummary: SecurityPositionSummary | null): MenuItem[] | null {
    let menuItems: MenuItem[] | null = null;

    if (
      securityPositionSummary &&
      (AssetclassType[securityPositionSummary.security.assetClass?.categoryType as keyof typeof AssetclassType] <
        AssetclassType.CURRENCY_PAIR ||
        (AssetclassType[securityPositionSummary.security.assetClass?.categoryType as keyof typeof AssetclassType] ===
          AssetclassType.CURRENCY_PAIR &&
          [SpecialInvestmentInstruments.FOREX, SpecialInvestmentInstruments.ISSUER_RISK_PRODUCT].includes(
            SpecialInvestmentInstruments[
              securityPositionSummary.security.assetClass
                ?.specialInvestmentInstrument as keyof typeof SpecialInvestmentInstruments
            ]
          )))
    ) {
      menuItems = [];
      menuItems.push({
        label: 'ACCUMULATE',
        command: (e) => this.handleTransaction(TransactionType.ACCUMULATE, securityPositionSummary.security)
      });

      menuItems.push({
        label: 'REDUCE',
        command: (e) => this.handleTransaction(TransactionType.REDUCE, securityPositionSummary.security),
        disabled: !securityPositionSummary || securityPositionSummary.units === 0
      });

      if (!BusinessHelper.isMarginProduct(securityPositionSummary.security)) {
        menuItems.push({
          label: AppSettings.DIVIDEND.toUpperCase() + BaseSettings.DIALOG_MENU_SUFFIX,
          command: (e) => this.handleTransaction(TransactionType.DIVIDEND, securityPositionSummary.security)
        });
      }
      this.extendEditMenu(menuItems, securityPositionSummary);
      TranslateHelper.translateMenuItems(menuItems, this.translateService);
    }
    return menuItems;
  }
}
