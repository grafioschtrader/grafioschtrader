import {Component, Input, OnInit} from '@angular/core';
import {SecurityStatisticsReturnResult} from '../../entities/view/security.statistics.return.result';
import {SecurityService} from '../service/security.service';

@Component({
  selector: 'instrument-statistics-summary',
  template: `
    <instrument-year-performance-table *ngIf="ssrr" [values]="ssrr.annualisedPerformance.lastYears"
                                       [mainCurrency]="ssrr.annualisedPerformance.mainCurrency">                                    >
    </instrument-year-performance-table>
    <instrument-annualised-return-table *ngIf="ssrr" [values]="ssrr.annualisedPerformance.annualisedYears"
                                        [mainCurrency]="ssrr.annualisedPerformance.mainCurrency">
    </instrument-annualised-return-table>
    `
})
export class InstrumentStatisticsSummaryComponent implements OnInit {
  @Input() idSecuritycurrency: number;
  ssrr: SecurityStatisticsReturnResult;

  constructor(private securityService: SecurityService) {
  }

  ngOnInit(): void {
    this.securityService.getSecurityStatisticsReturnResult(this.idSecuritycurrency).subscribe(
      (ssrr: SecurityStatisticsReturnResult) => {
        this.ssrr = ssrr;
      });
  }
}
