import { Component, DestroyRef, inject, OnInit, signal } from '@angular/core';
import { takeUntilDestroyed } from '@angular/core/rxjs-interop';
import { ActivatedRoute, ParamMap } from '@angular/router';
import { AppHelper } from '../../lib/helper/app.helper';
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
      <historyquote-table [timeSeriesParams]="timeSeriesParams()" (showLegacyRequested)="showLegacy()">
      </historyquote-table>
    } @else if (idSecuritycurrency() != null) {
      <historyquote-legacy [idSecuritycurrency]="idSecuritycurrency()" (showLiveRequested)="viewMode.set('live')">
      </historyquote-legacy>
    }
  `,
  standalone: true,
  imports: [HistoryquoteTableComponent, HistoryquoteLegacyComponent]
})
export class HistoryquoteHostComponent implements OnInit {
  readonly viewMode = signal<'live' | 'legacy'>('live');
  readonly timeSeriesParams = signal<TimeSeriesParam[]>([]);
  readonly idSecuritycurrency = signal<number>(null);

  private readonly destroyRef = inject(DestroyRef);

  constructor(private activatedRoute: ActivatedRoute) {}

  ngOnInit(): void {
    this.activatedRoute.paramMap.pipe(takeUntilDestroyed(this.destroyRef)).subscribe((paramMap: ParamMap) => {
      const timeSeriesParams: TimeSeriesParam[] = AppHelper.createParamObjectFromParamMap(paramMap).allParam;
      this.timeSeriesParams.set(timeSeriesParams);
      this.idSecuritycurrency.set(timeSeriesParams?.[0]?.idSecuritycurrency ?? null);
      this.viewMode.set('live');
    });
  }

  showLegacy(): void {
    if (this.idSecuritycurrency() != null) {
      this.viewMode.set('legacy');
    }
  }
}
