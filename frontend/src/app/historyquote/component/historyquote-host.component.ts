import { Component, computed, input, linkedSignal } from '@angular/core';
import { TimeSeriesParam } from '../model/time.series.param';
import { HistoryquoteTableComponent } from './historyquote-table.component';
import { HistoryquoteLegacyComponent } from './historyquote-legacy.component';

/**
 * Route target for the historyquote view. Parses {@code timeSeriesParams} from the route, holds
 * the {@code viewMode} ('live' or 'legacy'), and renders the appropriate child component. The
 * children emit {@code showLegacyRequested} / {@code showLiveRequested} to swap the view.
 *
 * Splitting the route owner out of the table component keeps live and legacy as peers — neither
 * has to know how to host the other.
 */
@Component({
  template: `
    @if (viewMode() === 'live') {
      <historyquote-table [timeSeriesParams]="timeSeriesParams()" (showLegacyRequested)="showLegacy()" />
    } @else if (idSecuritycurrency() != null) {
      <historyquote-legacy [idSecuritycurrency]="idSecuritycurrency()" (showLiveRequested)="viewMode.set('live')" />
    }
  `,
  imports: [HistoryquoteTableComponent, HistoryquoteLegacyComponent]
})
export class HistoryquoteHostComponent {
  readonly allParam = input<string>();

  readonly viewMode = linkedSignal<string, 'live' | 'legacy'>({ source: this.allParam, computation: () => 'live' });
  readonly timeSeriesParams = computed<TimeSeriesParam[]>(() => JSON.parse(this.allParam() ?? '[]'));
  readonly idSecuritycurrency = computed(() => this.timeSeriesParams()?.[0]?.idSecuritycurrency ?? null);

  showLegacy(): void {
    if (this.idSecuritycurrency() != null) {
      this.viewMode.set('legacy');
    }
  }
}
