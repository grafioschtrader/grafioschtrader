import {Component, Input, OnInit} from '@angular/core';
import {InstrumentStatisticsResult} from '../../entities/view/instrument.statistics.result';
import {SecurityService} from '../service/security.service';

/**
 * Shows the yield and statistical data about an instrument.
 */
@Component({
  selector: 'instrument-statistics-result',
  template: `
    <div>
      <h4>{{"RETURN_STATISTICAL_DATA" | translate}}</h4>
      <div class="fcontainer">
        @if (isr) {
          <instrument-year-performance-table [values]="isr.annualisedPerformance.lastYears" class="tabletree"
                                             [mainCurrency]="isr.annualisedPerformance.mainCurrency">
          </instrument-year-performance-table>
        }
        @if (isr) {
          <instrument-annualised-return-table [values]="isr.annualisedPerformance.annualisedYears"
                                              class="tabletree"
                                              [mainCurrency]="isr.annualisedPerformance.mainCurrency">
          </instrument-annualised-return-table>
        }
        @if (isr) {
          <instrument-statistics-summary [statisticsSummary]="isr.statisticsSummary" class="tabletree"
                                         [mainCurrency]="isr.annualisedPerformance.mainCurrency">
          </instrument-statistics-summary>
        }
      </div>
    </div>
  `,
  styles: [`
    .tabletree {
      min-width: 250px;
      max-width: 33.12%;
      margin: 0.1%;
      border-style: solid;
      border-color: darkgrey;
    }
  `],
  standalone: false
})
export class InstrumentStatisticsResultComponent implements OnInit {
  @Input() idSecuritycurrency: number;
  @Input() dateFrom: Date | string;
  @Input() dateTo: Date | string;
  isr: InstrumentStatisticsResult;

  constructor(private securityService: SecurityService) {
  }

  ngOnInit(): void {
    this.securityService.getSecurityStatisticsReturnResult(this.idSecuritycurrency, this.dateFrom, this.dateTo).subscribe(
      (isr: InstrumentStatisticsResult) => {
        this.isr = isr;
      });
  }
}
