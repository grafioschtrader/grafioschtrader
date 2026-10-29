import { Component, DestroyRef, inject, OnInit, signal, input } from '@angular/core';
import { takeUntilDestroyed } from '@angular/core/rxjs-interop';
import { InstrumentStatisticsResult } from '../../entities/view/instrument.statistics.result';
import { SecurityService } from '../../securitycurrency/service/security.service';
import { InstrumentStatisticsCacheService } from '../../securitycurrency/service/instrument.statistics.cache.service';
import { TranslateModule } from '@ngx-translate/core';
import { InstrumentYearPerformanceTableComponent } from './instrument-year-performance-table.component';
import { InstrumentAnnualisedReturnComponent } from './instrument.annualised.return.component';
import { InstrumentStatisticsSummaryComponent } from './instrument-statistics-summary.component';

/**
 * Shows the yield and statistical data about an instrument.
 */
@Component({
  selector: 'instrument-statistics-result',
  template: `
    <div>
      @if (showTitle()) {
        <h4>{{ 'RETURN_STATISTICAL_DATA' | translate }}</h4>
      }
      @if (isr(); as result) {
        <div class="fcontainer">
          <instrument-year-performance-table
            [values]="result.annualisedPerformance.lastYears"
            class="tabletree"
            [mainCurrency]="result.annualisedPerformance.mainCurrency" />
          <instrument-annualised-return-table
            [values]="result.annualisedPerformance.annualisedYears"
            class="tabletree"
            [mainCurrency]="result.annualisedPerformance.mainCurrency" />
          <instrument-statistics-summary
            [statisticsSummary]="result.statisticsSummary"
            class="tabletree"
            [mainCurrency]="result.annualisedPerformance.mainCurrency" />
        </div>
        <small>{{ 'RETURN_STATISTICAL_DATA_DIVIDENDS' | translate }}</small>
      }
    </div>
  `,
  styles: [
    `
      .tabletree {
        min-width: 250px;
        max-width: 33.12%;
        margin: 0.1%;
        border-style: solid;
        border-color: darkgrey;
      }
    `
  ],
  imports: [
    TranslateModule,
    InstrumentYearPerformanceTableComponent,
    InstrumentAnnualisedReturnComponent,
    InstrumentStatisticsSummaryComponent
  ]
})
export class InstrumentStatisticsResultComponent implements OnInit {
  private securityService = inject(SecurityService);

  readonly idSecuritycurrency = input<number>(undefined);
  readonly dateFrom = input<Date | string>(undefined);
  readonly dateTo = input<Date | string>(undefined);
  /**
   * Whether the heading is rendered. A caller which already labels this block, for example the accordion panel of an
   * expanded watchlist row, sets this to false to avoid showing the same title twice.
   */
  readonly showTitle = input(true);
  readonly isr = signal<InstrumentStatisticsResult | null>(null);
  private readonly destroyRef = inject(DestroyRef);

  /**
   * Optional cache of a surrounding view. When a component up the injector chain provides it — the watchlist does so
   * for as long as the user stays in the watchlist area — the statistics are requested from the server only once.
   * Without such a provider, for example in the correlation matrix, every instance loads its own data.
   */
  private readonly statisticsCache = inject(InstrumentStatisticsCacheService, {
    optional: true
  });

  ngOnInit(): void {
    const statistics = this.statisticsCache
      ? this.statisticsCache.getStatistics(this.idSecuritycurrency(), this.dateFrom(), this.dateTo())
      : this.securityService.getSecurityStatisticsReturnResult(
          this.idSecuritycurrency(),
          this.dateFrom(),
          this.dateTo()
        );
    statistics
      .pipe(takeUntilDestroyed(this.destroyRef))
      .subscribe((isr: InstrumentStatisticsResult) => this.isr.set(isr));
  }
}
