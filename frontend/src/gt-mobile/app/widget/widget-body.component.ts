import { Component, computed, input } from '@angular/core';
import { IonSpinner } from '@ionic/angular';
import { TranslatePipe } from '@ngx-translate/core';
import { DashboardResult } from '../../../app/lib/dashboard/dashboard.types';
import { AlgoMonitoringWidgetComponent } from './algo-monitoring-widget.component';
import { HoldingMoversWidgetComponent } from './holding-movers-widget.component';
import { LastSessionsWidgetComponent } from './last-sessions-widget.component';
import { mobileCardErrorKey, mobileCardState } from './mobile.dashboard.state';
import { SummaryWidgetComponent } from './summary-widget.component';
import { TotalChartWidgetComponent } from './total-chart-widget.component';

/**
 * Content of one dashboard card: the renderer of the widget type, or the loading, empty or failed state. A type this
 * application has no renderer for says so instead of leaving the card blank.
 */
@Component({
  selector: 'mobile-widget-body',
  imports: [
    IonSpinner,
    TranslatePipe,
    SummaryWidgetComponent,
    HoldingMoversWidgetComponent,
    LastSessionsWidgetComponent,
    TotalChartWidgetComponent,
    AlgoMonitoringWidgetComponent
  ],
  template: `
    @let shown = result();
    @let cardState = state();
    @switch (cardState) {
      @case ('loading') {
        <div class="widget-loading"><ion-spinner name="dots" /></div>
      }
      @case ('empty') {
        <p class="widget-note">{{ 'MOBILE_WIDGET_NO_DATA' | translate }}</p>
      }
      @case ('error') {
        <p class="widget-note loss">{{ errorKey(shown) | translate }}</p>
      }
      @case ('data') {
        @if (shown) {
          @if (shown.stale) {
            <p class="widget-note">{{ 'DASHBOARD_STALE' | translate }}</p>
          }
          @switch (shown.type) {
            @case ('UNREAD_MAIL') {
              <mobile-summary-widget [result]="shown" />
            }
            @case ('PROPOSE_CHANGE_OPEN') {
              <mobile-summary-widget [result]="shown" />
            }
            @case ('USER_LIMIT_REQUESTS') {
              <mobile-summary-widget [result]="shown" />
            }
            @case ('HOLDING_WINNERS') {
              <mobile-holding-movers-widget [result]="shown" />
            }
            @case ('HOLDING_LOSERS') {
              <mobile-holding-movers-widget [result]="shown" />
            }
            @case ('PERFORMANCE_LAST_SESSIONS') {
              <mobile-last-sessions-widget [result]="shown" />
            }
            @case ('PERFORMANCE_TOTAL_CHART') {
              <mobile-total-chart-widget [result]="shown" [tall]="tall()" />
            }
            @case ('ALGO_MONITORING') {
              <mobile-algo-monitoring-widget [result]="shown" />
            }
            @default {
              <p class="widget-note">{{ 'MOBILE_WIDGET_DESKTOP_ONLY' | translate }}</p>
            }
          }
        }
      }
      @default never;
    }
  `
})
export class WidgetBodyComponent {
  /** Result of the widget, undefined while its load group is outstanding. */
  readonly result = input<DashboardResult>();
  /** Whether the dashboard is still reading load groups, so a missing result is pending rather than failed. */
  readonly loading = input(false);
  /** Set by the full-screen view. */
  readonly tall = input(false);

  protected readonly state = computed(() => mobileCardState(this.result(), this.loading()));
  protected readonly errorKey = mobileCardErrorKey;
}
