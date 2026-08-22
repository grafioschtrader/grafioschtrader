import { Component, inject, Input, OnInit, ChangeDetectionStrategy } from '@angular/core';
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
      @if (showTitle) {
        <h4>{{ 'RETURN_STATISTICAL_DATA' | translate }}</h4>
      }
      <div class="fcontainer">
        @if (isr) {
          <instrument-year-performance-table
            [values]="isr.annualisedPerformance.lastYears"
            class="tabletree"
            [mainCurrency]="isr.annualisedPerformance.mainCurrency">
          </instrument-year-performance-table>
        }
        @if (isr) {
          <instrument-annualised-return-table
            [values]="isr.annualisedPerformance.annualisedYears"
            class="tabletree"
            [mainCurrency]="isr.annualisedPerformance.mainCurrency">
          </instrument-annualised-return-table>
        }
        @if (isr) {
          <instrument-statistics-summary
            [statisticsSummary]="isr.statisticsSummary"
            class="tabletree"
            [mainCurrency]="isr.annualisedPerformance.mainCurrency">
          </instrument-statistics-summary>
        }
      </div>
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
  ],
  changeDetection: ChangeDetectionStrategy.Eager,
  standalone: true
})
export class InstrumentStatisticsResultComponent implements OnInit {
  @Input() idSecuritycurrency: number;
  @Input() dateFrom: Date | string;
  @Input() dateTo: Date | string;
  /**
   * Whether the heading is rendered. A caller which already labels this block, for example the accordion panel of an
   * expanded watchlist row, sets this to false to avoid showing the same title twice.
   */
  @Input() showTitle = true;
  isr: InstrumentStatisticsResult;

  /**
   * Optional cache of a surrounding view. When a component up the injector chain provides it — the watchlist does so
   * for as long as the user stays in the watchlist area — the statistics are requested from the server only once.
   * Without such a provider, for example in the correlation matrix, every instance loads its own data.
   */
  private readonly statisticsCache = inject(InstrumentStatisticsCacheService, {
    optional: true
  });

  constructor(private securityService: SecurityService) {}

  ngOnInit(): void {
    const statistics = this.statisticsCache
      ? this.statisticsCache.getStatistics(this.idSecuritycurrency, this.dateFrom, this.dateTo)
      : this.securityService.getSecurityStatisticsReturnResult(this.idSecuritycurrency, this.dateFrom, this.dateTo);
    statistics.subscribe((isr: InstrumentStatisticsResult) => {
      this.isr = isr;
    });
  }
}
