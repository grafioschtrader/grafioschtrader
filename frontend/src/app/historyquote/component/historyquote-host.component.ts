import {Component, OnDestroy, OnInit} from '@angular/core';
import {ActivatedRoute, ParamMap} from '@angular/router';
import {Subscription} from 'rxjs';
import {AppHelper} from '../../lib/helper/app.helper';
import {TimeSeriesParam} from './time.series.chart.component';
import {HistoryquoteTableComponent} from './historyquote-table.component';
import {HistoryquoteLegacyComponent} from './historyquote-legacy.component';

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
    @if (viewMode === 'live') {
      <historyquote-table [timeSeriesParams]="timeSeriesParams"
                          (showLegacyRequested)="showLegacy()">
      </historyquote-table>
    } @else if (idSecuritycurrency != null) {
      <historyquote-legacy [idSecuritycurrency]="idSecuritycurrency"
                           (showLiveRequested)="viewMode = 'live'">
      </historyquote-legacy>
    }
  `,
  standalone: true,
  imports: [HistoryquoteTableComponent, HistoryquoteLegacyComponent]
})
export class HistoryquoteHostComponent implements OnInit, OnDestroy {

  viewMode: 'live' | 'legacy' = 'live';
  timeSeriesParams: TimeSeriesParam[] = [];
  idSecuritycurrency: number;

  private routeSubscribe: Subscription;

  constructor(private activatedRoute: ActivatedRoute) {
  }

  ngOnInit(): void {
    this.routeSubscribe = this.activatedRoute.paramMap.subscribe((paramMap: ParamMap) => {
      const paramObject = AppHelper.createParamObjectFromParamMap(paramMap);
      this.timeSeriesParams = paramObject.allParam;
      this.idSecuritycurrency = this.timeSeriesParams?.[0]?.idSecuritycurrency;
      this.viewMode = 'live';
    });
  }

  ngOnDestroy(): void {
    this.routeSubscribe && this.routeSubscribe.unsubscribe();
  }

  showLegacy(): void {
    if (this.idSecuritycurrency != null) {
      this.viewMode = 'legacy';
    }
  }
}
