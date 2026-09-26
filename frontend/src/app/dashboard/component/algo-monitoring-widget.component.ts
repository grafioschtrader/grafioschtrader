import { ChangeDetectionStrategy, Component, Input, OnChanges } from '@angular/core';
import { Router } from '@angular/router';
import { TranslateModule, TranslateService } from '@ngx-translate/core';
import { ButtonModule } from '@openng/optimus-ui/button';
import { DashboardResult } from '../../lib/dashboard/dashboard.types';
import { ColumnConfig } from '../../lib/datashowbase/column.config';
import { ShowRecordConfigBase } from '../../lib/datashowbase/show.record.config.base';
import { DataType } from '../../lib/dynamic-form/models/data.type';
import { BaseSettings } from '../../lib/base.settings';
import { GlobalparameterService } from '../../lib/services/globalparameter.service';
import { AppSettings } from '../../shared/app.settings';
import { AlgoMonitoringSummary } from '../model/algo.monitoring.summary';

/**
 * Dashboard card telling whether the hierarchy assigned to monitoring needs attention and when its next checkpoint
 * falls due.
 *
 * <p>
 * The stored plan has a line per bucket and instrument and nearly always proposes nothing, so the card shows a state,
 * the largest bucket drift and at most three trades. The full comparison is one click away in the rebalancing report,
 * which calculates it live.
 * </p>
 */
@Component({
  selector: 'algo-monitoring-widget',
  changeDetection: ChangeDetectionStrategy.Eager,
  standalone: true,
  imports: [TranslateModule, ButtonModule],
  template: `
    @if (summary) {
      @if (summary.reasonKey) {
        <small class="d-block">{{ summary.reasonKey | translate }}</small>
      } @else {
        <div class="mb-2">
          <strong>{{ summary.algoTopName }}</strong>
        </div>
        <div class="mb-2">
          <strong>{{ summary.statusKey | translate: statusParams }}</strong>
        </div>
        @for (field of summaryFields; track field.field) {
          @if (getValueByPath(summary, field)) {
            <div>
              <strong>{{ field.headerTranslated }}:</strong>
              @if (field.field === 'largestDeviationPercentage') {
                {{ summary.largestDeviationBucket }}
              }
              {{ getValueByPath(summary, field) }}
            </div>
          }
        }
        @if (summary.topTrades.length > 0) {
          <ul class="mt-2 mb-2">
            @for (trade of summary.topTrades; track $index) {
              <li>
                {{ trade.recommendedAction | translate }} {{ trade.securityName }}
                {{ getValueByPath(trade, tradeAmountField) }}
              </li>
            }
          </ul>
        }
        <p-button [label]="'DASHBOARD_ALGO_OPEN_REPORT' | translate" [text]="true" (onClick)="openReport()" />
      }
    }
  `
})
export class AlgoMonitoringWidgetComponent extends ShowRecordConfigBase implements OnChanges {
  @Input() result: DashboardResult;

  summary: AlgoMonitoringSummary;
  statusParams: { [name: string]: number } = {};
  summaryFields: ColumnConfig[] = [];
  tradeAmountField: ColumnConfig = ShowRecordConfigBase.createColumnConfig(
    DataType.Numeric,
    'recommendedAmount',
    'AMOUNT'
  );

  constructor(
    public override translateService: TranslateService,
    gps: GlobalparameterService,
    private router: Router
  ) {
    super(translateService, gps);
    this.initSummaryFields();
  }

  ngOnChanges(): void {
    this.summary = this.result?.payload?.custom as AlgoMonitoringSummary;
    this.statusParams = {
      buys: this.summary?.buyCount ?? 0,
      sells: this.summary?.sellCount ?? 0,
      blocked: this.summary?.blockedCount ?? 0
    };
    const currency = this.summary?.currency;
    [...this.summaryFields, this.tradeAmountField]
      .filter((field) => field.dataType === DataType.Numeric)
      .forEach((field) => (field.fixedCurrency = currency));
  }

  /** Opens the live rebalancing report with the monitored hierarchy already selected. */
  openReport(): void {
    this.router.navigate(
      [BaseSettings.MAINVIEW_KEY + '/' + AppSettings.TENANT_TAB_MENU_KEY + '/' + AppSettings.DEPOT_CASH_KEY],
      { queryParams: { idAlgoTop: this.summary.idAlgoTop } }
    );
  }

  /**
   * The figures of the card, each formatted by its data type. A figure that is empty, such as the amount of a direction
   * nothing is proposed in, is left out rather than shown as zero.
   */
  private initSummaryFields(): void {
    this.summaryFields = [
      ShowRecordConfigBase.createColumnConfig(DataType.DateString, 'valuationDate', 'VALUATION_DATE'),
      ShowRecordConfigBase.createColumnConfig(DataType.DateString, 'nextCheckpointDate', 'NEXT_CHECKPOINT_DATE'),
      ShowRecordConfigBase.createColumnConfig(
        DataType.NumericRaw,
        'largestDeviationPercentage',
        'DASHBOARD_ALGO_LARGEST_DEVIATION'
      ),
      ShowRecordConfigBase.createColumnConfig(DataType.Numeric, 'sellAmount', 'DASHBOARD_ALGO_SELL_AMOUNT'),
      ShowRecordConfigBase.createColumnConfig(DataType.Numeric, 'buyAmount', 'DASHBOARD_ALGO_BUY_AMOUNT'),
      ShowRecordConfigBase.createColumnConfig(
        DataType.NumericInteger,
        'meanReversionSignals',
        'DASHBOARD_ALGO_MEAN_REVERSION_SIGNALS'
      )
    ];
    this.translateService
      .get(this.summaryFields.map((field) => field.headerKey))
      .subscribe((translations: { [key: string]: string }) =>
        this.summaryFields.forEach((field) => (field.headerTranslated = translations[field.headerKey]))
      );
  }
}
