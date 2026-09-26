import { SecurityaccountService } from '../../securityaccount/service/securityaccount.service';
import { ActivePanelService } from '../../lib/mainmenubar/service/active.panel.service';
import { ActivatedRoute, Router } from '@angular/router';
import { TransactionCallParam } from '../../transaction/component/transaction.call.parm';
import { Component, Injector, OnDestroy, OnInit, ChangeDetectionStrategy } from '@angular/core';
import { SecurityPositionGrandSummary } from '../../entities/view/security.position.grand.summary';
import { SecurityPositionSummary } from '../../entities/view/security.position.summary';
import { GlobalparameterService } from '../../lib/services/globalparameter.service';
import { ChartDataService } from '../../shared/chart/service/chart.data.service';
import { MessageToastService } from '../../lib/message/message.toast.service';
import { UserSettingsService } from '../../lib/services/user.settings.service';
import { TranslateService, TranslateModule } from '@ngx-translate/core';
import { SecurityaccountAssetclassCategortypeGroup } from '../../securityaccount/component/securityaccount.assetclass.categortype.group';
import { SecurityaccountAlgoBucketGroup } from '../../securityaccount/component/securityaccount.algo.bucket.group';
import { ParentChildRegisterService } from '../../shared/service/parent.child.register.service';
import { SecurityaccountBaseTable } from '../../securityaccount/component/securityaccount.base.table';
import { AppSettings } from '../../shared/app.settings';
import { OptionalParameters, TimeSeriesQuotesService } from '../../historyquote/service/time.series.quotes.service';
import { ProductIconService } from '../../securitycurrency/service/product.icon.service';
import { FilterService, SelectItem } from '@openng/optimus-ui/api';
import { AlarmSetupService } from '../../algo/service/alarm.setup.service';
import { AlgoTopService } from '../../algo/service/algo.top.service';
import { AlgoTop } from '../../algo/model/algo.top';
import { HelpIds } from '../../lib/help/help.ids';
import { CommonModule } from '@angular/common';
import { TableModule } from '@openng/optimus-ui/table';
import { DatePicker } from '@openng/optimus-ui/datepicker';
import { FormsModule } from '@angular/forms';
import { SelectModule } from '@openng/optimus-ui/select';
import { TooltipModule } from '@openng/optimus-ui/tooltip';
import { ContextMenuModule } from '@openng/optimus-ui/contextmenu';
import { TransactionSecurityTableComponent } from '../../transaction/component/transaction-security-table.component';
import { TransactionSecurityMarginTreetableComponent } from '../../transaction/component/transaction-security-margin-treetable.component';
import { TransactionCashaccountTableComponent } from '../../transaction/component/transaction-cashaccount-table.component';
import { TransactionSecurityEditComponent } from '../../transaction/component/transaction-security-edit.component';
import { ColumnConfig, ColumnGroupConfig } from '../../lib/datashowbase/column.config';
import { DataType } from '../../lib/dynamic-form/models/data.type';
import { TranslateValue } from '../../lib/datashowbase/column.config';
import { ShowRecordConfigBase } from '../../lib/datashowbase/show.record.config.base';
import { FeatureType } from '../../lib/login/model/configuration-with-login';
import { GlobalSessionNames } from '../../lib/global.session.names';

/**
 * It groups asset classes of securities and includes balance of cash accounts as an asset class.
 *
 * Selecting one of the rule based trading strategies turns the same report into an allocation comparison: the
 * positions are then grouped by the buckets of that strategy, and target share, actual share, deviation and the
 * recommended action are shown per instrument, per bucket and for the book as a whole. Without a selection the report
 * is exactly what it was before, which is why the strategy is a selection on this report rather than a report of its
 * own.
 */
@Component({
  templateUrl: '../../securityaccount/view/securityaccount.table.html',
  standalone: true,
  changeDetection: ChangeDetectionStrategy.Eager,
  imports: [
    CommonModule,
    TranslateModule,
    TableModule,
    DatePicker,
    FormsModule,
    SelectModule,
    TooltipModule,
    ContextMenuModule,
    TransactionSecurityTableComponent,
    TransactionSecurityMarginTreetableComponent,
    TransactionCashaccountTableComponent,
    TransactionSecurityEditComponent
  ]
})
export class TenantSummariesAssetclassComponent extends SecurityaccountBaseTable implements OnInit, OnDestroy {
  constructor(
    private parentChildRegisterService: ParentChildRegisterService,
    private algoTopService: AlgoTopService,
    timeSeriesQuotesService: TimeSeriesQuotesService,
    alarmSetupService: AlarmSetupService,
    activePanelService: ActivePanelService,
    messageToastService: MessageToastService,
    securityaccountService: SecurityaccountService,
    productIconService: ProductIconService,
    activatedRoute: ActivatedRoute,
    router: Router,
    chartDataService: ChartDataService,
    filterService: FilterService,
    translateService: TranslateService,
    gps: GlobalparameterService,
    usersettingsService: UserSettingsService,
    injector: Injector
  ) {
    super(
      timeSeriesQuotesService,
      alarmSetupService,
      activePanelService,
      messageToastService,
      securityaccountService,
      productIconService,
      activatedRoute,
      router,
      chartDataService,
      filterService,
      translateService,
      gps,
      usersettingsService,
      injector
    );
    this.securityaccountGroupBase = new SecurityaccountAssetclassCategortypeGroup(translateService, this);
    this.createColumns();
  }

  ngOnInit() {
    this.translateService
      .get('SECURITY_ASSETCLASS_WITH_CASH')
      .subscribe((translatedTitle) => (this.translatedTitle = translatedTitle));
    this.parentChildRegisterService.initRegistry();
    this.loadAlgoTopOptions();
    this.preselectAlgoTop();
    this.readData();
    this.onComponentClick(null);
  }

  readData(): void {
    this.selectedSecurityPositionSummary = null;
    const observable = this.selectedIdAlgoTop
      ? this.securityaccountService.getRebalancingSummaryTenant(
          this.selectedIdAlgoTop,
          this.includeClosedPosition,
          this.untilDate
        )
      : this.securityaccountService.getSecurityPositionSummaryTenant(
          'assetclasstypewithcash',
          this.includeClosedPosition,
          this.untilDate
        );
    observable.subscribe((data: SecurityPositionGrandSummary) => {
      this.getDataToView(data);
      this.initTableTextTranslation();
      if (this.selectedIdAlgoTop) {
        // The action and the reason are stored as locale independent tokens, so the table needs their translations
        // before it can show or sort them.
        this.createTranslatedValueStore(this.securityPositionAll);
      }
    });
  }

  /**
   * Switches between the plain asset class grouping and the comparison against one strategy. Both the grouping and
   * the set of columns change, so the table is rebuilt rather than only refilled.
   *
   * @param event - Selection event of the strategy dropdown
   */
  override handleChangeAlgoTop(event: any): void {
    this.selectedIdAlgoTop = event.value;
    this.securityaccountGroupBase = this.selectedIdAlgoTop
      ? new SecurityaccountAlgoBucketGroup(this.translateService, this)
      : new SecurityaccountAssetclassCategortypeGroup(this.translateService, this);
    this.createColumns();
    this.readData();
  }

  ngOnDestroy(): void {
    super.destroy();
  }

  public override getHelpContextId(): string {
    return HelpIds.HELP_PORTFOLIOS_SECURITY_CASH_ACCOUNT_REPORT;
  }

  /**
   * Marks a deviation that left the configured tolerance, which is the whole point of the comparison and would
   * otherwise be one number among many.
   */
  public override getCellStyle(
    positionSummary: SecurityPositionSummary,
    field: ColumnConfig
  ): { [key: string]: string } {
    const style = super.getCellStyle(positionSummary, field);
    return field.field === 'parentDeviation' &&
      positionSummary?.parentDeviation != null &&
      Math.abs(positionSummary.parentDeviation) > positionSummary.securityDeviationPercentage
      ? { ...style, 'font-weight': 'bold', 'background-color': 'rgba(234, 179, 8, 0.30)' }
      : style;
  }

  protected override createColumns(): void {
    super.createColumns();
    if (this.selectedIdAlgoTop) {
      this.addRebalancingColumns();
    } else {
      this.rebalancingSummaryFields = null;
    }
  }

  protected override getTitleChart(): string {
    return this.translatedTitle;
  }

  protected override getComponentId(): string {
    return AppSettings.DEPOT_CASH_KEY;
  }

  protected extendTransactionParamData(transactionCallParam: TransactionCallParam): void {}

  protected getOptionalParameters(): OptionalParameters {
    return null;
  }

  /**
   * Opens the comparison against the hierarchy named in the address, which is how the monitoring card of the dashboard
   * leads here. Without that parameter the report starts with the plain asset class grouping.
   */
  private preselectAlgoTop(): void {
    const idAlgoTop = Number(this.activatedRoute.snapshot.queryParamMap.get('idAlgoTop'));
    if (idAlgoTop) {
      this.selectedIdAlgoTop = idAlgoTop;
      this.securityaccountGroupBase = new SecurityaccountAlgoBucketGroup(this.translateService, this);
      this.createColumns();
    }
  }

  /**
   * The strategy dropdown is only offered where rule based trading is enabled at all, and only when the tenant has at
   * least one strategy: a dropdown whose single entry switches nothing off is noise.
   */
  private loadAlgoTopOptions(): void {
    const features = sessionStorage.getItem(GlobalSessionNames.USE_FEATURES);
    if (!features || JSON.parse(features).indexOf(FeatureType[FeatureType.ALGO]) < 0) {
      return;
    }
    this.algoTopService.getAlgoTopByIdTenantOrderByName().subscribe((algoTops: AlgoTop[]) => {
      if (algoTops.length > 0) {
        this.translateService.get('REBALANCING_NO_STRATEGY').subscribe((noStrategy: string) => {
          this.algoTopOptions = [
            { label: noStrategy, value: null },
            ...algoTops.map((algoTop) => this.algoTopOption(algoTop))
          ];
        });
      }
    });
  }

  /**
   * A strategy whose comparison the backend would refuse stays visible, so that the user sees it exists, but cannot be
   * chosen; hovering names the first reason.
   */
  private algoTopOption(algoTop: AlgoTop): SelectItem {
    const readiness = algoTop.readiness;
    const blocked = !!readiness && !readiness.readyForRebalancing;
    return {
      label: algoTop.name,
      value: algoTop.idAlgoAssetclassSecurity,
      disabled: blocked,
      title: blocked ? readiness.issues[0]?.message : undefined
    };
  }

  private addRebalancingColumns(): void {
    this.addColumnFeqH(DataType.NumericRaw, 'targetPercentage', true, false, {
      width: 70,
      columnGroupConfigs: [new ColumnGroupConfig('groupTargetPercentage')]
    });
    this.addColumnFeqH(DataType.NumericRaw, 'actualPercentage', true, false, {
      width: 70,
      columnGroupConfigs: [new ColumnGroupConfig('groupActualPercentage')]
    });
    this.addColumnFeqH(DataType.NumericRaw, 'deviationPercentage', true, false, {
      width: 80,
      templateName: 'greenRed',
      columnGroupConfigs: [new ColumnGroupConfig('groupDeviationPercentage')]
    });
    this.addColumnFeqH(DataType.String, 'recommendedAction', true, false, {
      width: 70,
      translateValues: TranslateValue.NORMAL,
      columnGroupConfigs: [this.translatedRebalancingGroupColumn('groupRecommendedAction')]
    });
    this.internalColumnConfigs.push(
      this.addColumnFeqH(DataType.Numeric, 'recommendedAmount', true, true, {
        width: 90,
        columnGroupConfigs: [new ColumnGroupConfig('groupRecommendedAmount')]
      })
    );
    this.addColumnFeqH(DataType.NumericShowZero, 'recommendedUnits', true, true, {
      width: 70,
      maxFractionDigits: 0
    });
    this.addColumnFeqH(DataType.String, 'recommendationReason', false, true, {
      translateValues: TranslateValue.NORMAL,
      columnGroupConfigs: [this.translatedRebalancingGroupColumn('groupRecommendationReason')]
    });
    this.addColumnFeqH(DataType.NumericRaw, 'parentDeviation', true, false, {
      width: 90,
      columnGroupConfigs: [new ColumnGroupConfig('groupParentDeviation')]
    });
    this.addColumnFeqH(DataType.NumericRaw, 'securityDeviationPercentage', false, true, {
      columnGroupConfigs: [new ColumnGroupConfig('groupSecurityDeviationPercentage')]
    });
    this.addColumnFeqH(DataType.NumericInteger, 'maxTradedSecuritiesPerAssetclass', false, true, {
      columnGroupConfigs: [new ColumnGroupConfig('groupMaxTradedSecuritiesPerAssetclass')]
    });
    this.addColumnFeqH(DataType.Numeric, 'requestedAdjustment', false, true, {
      columnGroupConfigs: [new ColumnGroupConfig('groupRequestedAdjustment')]
    });
    this.addColumnFeqH(DataType.Numeric, 'residual', true, true, {
      columnGroupConfigs: [new ColumnGroupConfig('groupResidual')]
    });
    this.initRebalancingSummaryFields();
  }

  /** Group totals read raw fields, so action and reason tokens need their own translation callback. */
  private translatedRebalancingGroupColumn(field: string): ColumnGroupConfig {
    return new ColumnGroupConfig(field, undefined, (_column, _index, groups, rowIndex) => {
      const key = groups.get(rowIndex)?.[field];
      return key ? this.translateService.instant(key) : '';
    });
  }

  /**
   * Net equity, the cash actually held, gross exposure and the budget are four different answers and are shown as
   * four figures rather than as one total; for a short or margin book they are not close to each other.
   */
  private initRebalancingSummaryFields(): void {
    this.rebalancingSummaryFields = [
      ShowRecordConfigBase.createColumnConfig(DataType.DateString, 'valuationDate', 'VALUATION_DATE'),
      ShowRecordConfigBase.createColumnConfig(DataType.DateString, 'lastCheckpointDate', 'LAST_CHECKPOINT_DATE'),
      ShowRecordConfigBase.createColumnConfig(DataType.DateString, 'nextCheckpointDate', 'NEXT_CHECKPOINT_DATE'),
      ShowRecordConfigBase.createColumnConfig(
        DataType.NumericRaw,
        'overallAllocationMismatchPercentage',
        'OVERALL_ALLOCATION_MISMATCH_PERCENTAGE'
      ),
      ShowRecordConfigBase.createColumnConfig(DataType.Numeric, 'grandNetEquityMC', 'NET_EQUITY'),
      ShowRecordConfigBase.createColumnConfig(DataType.Numeric, 'grandActualCashMC', 'ACTUAL_CASH'),
      ShowRecordConfigBase.createColumnConfig(DataType.Numeric, 'grandGrossExposureMC', 'GROSS_EXPOSURE'),
      ShowRecordConfigBase.createColumnConfig(DataType.Numeric, 'grandInvestmentBudgetMC', 'INVESTMENT_BUDGET'),
      ShowRecordConfigBase.createColumnConfig(
        DataType.Numeric,
        'grandUnusedTacticalBudgetMC',
        'UNUSED_TACTICAL_BUDGET'
      ),
      ShowRecordConfigBase.createColumnConfig(DataType.NumericRaw, 'toleranceThreshold', 'TOLERANCE_THRESHOLD')
    ];
    this.translateService
      .get(this.rebalancingSummaryFields.map((field) => field.headerKey))
      .subscribe((translations: { [key: string]: string }) =>
        this.rebalancingSummaryFields.forEach((field) => (field.headerTranslated = translations[field.headerKey]))
      );
  }
}
