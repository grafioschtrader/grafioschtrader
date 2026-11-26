import { Component, computed, inject, input } from '@angular/core';
import { IonItem, IonLabel, IonList, IonNote } from '@ionic/angular';
import { TranslatePipe, TranslateService } from '@ngx-translate/core';
import { benchmarkNoticeTexts } from '../../../app/dashboard/model/benchmark.notice.text';
import { DashboardResult } from '../../../app/lib/dashboard/dashboard.types';
import { LastSessionsPerformance } from '../../../app/dashboard/model/last.sessions.performance';
import { MobileFormatService } from '../service/mobile.format.service';

/**
 * Performance of the last trading sessions: the totals first, then one row per session with the newest on top, because
 * the last session is what the reader came for.
 */
@Component({
  selector: 'mobile-last-sessions-widget',
  imports: [IonList, IonItem, IonLabel, IonNote, TranslatePipe],
  template: `
    @if (performance(); as performance) {
      @if (performance.reasonKey) {
        <p class="widget-note">{{ performance.reasonKey | translate }}</p>
      } @else {
        <ion-list lines="none">
          @for (total of totals; track total.field) {
            <ion-item>
              <ion-label>{{ total.key | translate }}</ion-label>
              <ion-note
                slot="end"
                class="figure"
                [class.gain]="performance[total.field] > 0"
                [class.loss]="performance[total.field] < 0">
                {{ format.amount(performance[total.field], performance.currency) }}
              </ion-note>
            </ion-item>
          }
          <ion-item>
            <ion-label>{{ 'TWR_PERCENT' | translate }}</ion-label>
            <ion-note slot="end" class="figure">{{ format.percentage(performance.twrPercent) }}</ion-note>
          </ion-item>
          @if (performance.benchmarkName) {
            <ion-item>
              <ion-label>{{ performance.benchmarkName }}</ion-label>
              <ion-note slot="end" class="figure">{{ format.percentage(performance.benchmarkReturnPercent) }}</ion-note>
            </ion-item>
          }
        </ion-list>
        <ion-list lines="inset">
          @for (session of sessions(); track session.date) {
            <ion-item>
              <ion-label>
                <h3>{{ format.date(session.date) }}{{ session.substitute ? ' *' : '' }}</h3>
                <p>
                  {{ 'DASHBOARD_LAST_SESSIONS_BALANCE_CHANGE' | translate }}:
                  {{ format.amount(session.totalBalanceChangeMC, performance.currency) }}
                </p>
                @if (session.twrPercent != null || session.benchmarkReturnPercent != null) {
                  <p>
                    {{ 'TWR_PERCENT' | translate }}: {{ format.percentage(session.twrPercent) }}
                    @if (performance.benchmarkName) {
                      · {{ performance.benchmarkName }}: {{ format.percentage(session.benchmarkReturnPercent) }}
                    }
                  </p>
                }
              </ion-label>
              <ion-note
                slot="end"
                class="figure"
                [class.gain]="session.totalGainMC > 0"
                [class.loss]="session.totalGainMC < 0">
                {{ format.amount(session.totalGainMC, performance.currency) }}
              </ion-note>
            </ion-item>
          }
        </ion-list>
        @if (hasSubstitute()) {
          <p class="widget-note">* {{ 'DASHBOARD_LAST_SESSIONS_SUBSTITUTE' | translate }}</p>
        }
        @for (notice of noticeTexts(); track $index) {
          <p class="widget-note">{{ notice }}</p>
        }
        <p class="widget-note">{{ 'DASHBOARD_LAST_SESSIONS_NOTE' | translate }}</p>
      }
    }
  `
})
export class LastSessionsWidgetComponent {
  readonly result = input.required<DashboardResult>();
  protected readonly format = inject(MobileFormatService);
  private readonly translateService = inject(TranslateService);

  protected readonly performance = computed(() => this.result().payload?.custom as LastSessionsPerformance | undefined);
  protected readonly sessions = computed(() => [...(this.performance()?.sessions ?? [])].reverse());
  protected readonly hasSubstitute = computed(() => this.sessions().some((session) => session.substitute));
  protected readonly noticeTexts = computed(() =>
    benchmarkNoticeTexts(
      this.performance()?.notices,
      (key, parameters) => this.translateService.instant(key, parameters),
      (date) => this.format.date(date)
    )
  );

  /** The totals over the listed sessions, with the same labels as the desktop card. */
  protected readonly totals: {
    field: 'totalGainMC' | 'totalBalanceChangeMC' | 'externalCashTransferMC';
    key: string;
  }[] = [
    { field: 'totalGainMC', key: 'TOTAL_GAIN' },
    { field: 'totalBalanceChangeMC', key: 'DASHBOARD_LAST_SESSIONS_BALANCE_CHANGE' },
    { field: 'externalCashTransferMC', key: 'EXTERNAL_CASH_TRANSFER' }
  ];
}
