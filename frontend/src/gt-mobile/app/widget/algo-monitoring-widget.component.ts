import { Component, computed, inject, input } from '@angular/core';
import { IonItem, IonLabel, IonList, IonNote } from '@ionic/angular';
import { TranslatePipe } from '@ngx-translate/core';
import { DashboardResult } from '../../../app/lib/dashboard/dashboard.types';
import { DataType } from '../../../app/lib/dynamic-form/models/data.type';
import { AlgoMonitoringSummary } from '../../../app/dashboard/model/algo.monitoring.summary';
import { MobileFormatService } from '../service/mobile.format.service';

/** One figure of the summary, its label and how it is formatted. */
interface AlgoFigure {
  field: keyof AlgoMonitoringSummary;
  key: string;
  dataType: DataType;
}

/**
 * Summary of the stored live plan of the monitored algorithmic hierarchy. Opening the rebalancing report stays with the
 * desktop application.
 */
@Component({
  selector: 'mobile-algo-monitoring-widget',
  imports: [IonList, IonItem, IonLabel, IonNote, TranslatePipe],
  template: `
    @if (summary(); as summary) {
      @if (summary.reasonKey) {
        <p class="widget-note">{{ summary.reasonKey | translate }}</p>
      } @else {
        <ion-list lines="none">
          <ion-item>
            <ion-label class="ion-text-wrap">
              <h2>{{ summary.algoTopName }}</h2>
              @if (summary.statusKey) {
                <p>{{ summary.statusKey | translate: statusParams() }}</p>
              }
            </ion-label>
          </ion-item>
          @for (figure of figures; track figure.field) {
            @let value = figureValue(summary, figure);
            @if (value) {
              <ion-item>
                <ion-label class="ion-text-wrap">{{ figure.key | translate }}</ion-label>
                <ion-note slot="end" class="figure">{{ value }}</ion-note>
              </ion-item>
            }
          }
          @for (trade of summary.topTrades; track $index) {
            <ion-item>
              <ion-label class="ion-text-wrap"
                >{{ trade.recommendedAction | translate }} {{ trade.securityName }}</ion-label
              >
              <ion-note slot="end" class="figure">{{
                format.amount(trade.recommendedAmount, summary.currency)
              }}</ion-note>
            </ion-item>
          }
        </ion-list>
      }
    }
  `
})
export class AlgoMonitoringWidgetComponent {
  readonly result = input.required<DashboardResult>();
  protected readonly format = inject(MobileFormatService);

  protected readonly summary = computed(() => this.result().payload?.custom as AlgoMonitoringSummary | undefined);
  protected readonly statusParams = computed(() => ({
    buys: this.summary()?.buyCount ?? 0,
    sells: this.summary()?.sellCount ?? 0,
    blocked: this.summary()?.blockedCount ?? 0
  }));

  /** The figures of the desktop card in the same order; an empty figure is left out rather than shown as zero. */
  protected readonly figures: AlgoFigure[] = [
    { field: 'valuationDate', key: 'VALUATION_DATE', dataType: DataType.DateString },
    { field: 'nextCheckpointDate', key: 'NEXT_CHECKPOINT_DATE', dataType: DataType.DateString },
    { field: 'largestDeviationPercentage', key: 'DASHBOARD_ALGO_LARGEST_DEVIATION', dataType: DataType.NumericRaw },
    { field: 'sellAmount', key: 'DASHBOARD_ALGO_SELL_AMOUNT', dataType: DataType.Numeric },
    { field: 'buyAmount', key: 'DASHBOARD_ALGO_BUY_AMOUNT', dataType: DataType.Numeric },
    { field: 'meanReversionSignals', key: 'DASHBOARD_ALGO_MEAN_REVERSION_SIGNALS', dataType: DataType.NumericInteger },
    { field: 'allocationBreaches', key: 'DASHBOARD_ALGO_ALLOCATION_BREACHES', dataType: DataType.NumericInteger }
  ];

  protected figureValue(summary: AlgoMonitoringSummary, figure: AlgoFigure): string {
    const value = this.format.format(summary, figure.field, figure.dataType, summary.currency);
    return value && figure.field === 'largestDeviationPercentage'
      ? `${summary.largestDeviationBucket ?? ''} ${value}`.trim()
      : value;
  }
}
