import {Component, Input, OnInit} from '@angular/core';
import {InstrumentStatisticsResult} from '../../entities/view/instrument.statistics.result';
import {SecurityService} from '../service/security.service';

@Component({
  selector: 'instrument-statistics-summary',
  template: `
    <instrument-year-performance-table *ngIf="isr" [values]="isr.annualisedPerformance.lastYears"
                                       [mainCurrency]="isr.annualisedPerformance.mainCurrency"> >
    </instrument-year-performance-table>
    <instrument-annualised-return-table *ngIf="isr" [values]="isr.annualisedPerformance.annualisedYears"
                                        [mainCurrency]="isr.annualisedPerformance.mainCurrency">
    </instrument-annualised-return-table>
  `
})
export class InstrumentStatisticsSummaryComponent implements OnInit {
  @Input() idSecuritycurrency: number;
  isr: InstrumentStatisticsResult;

  constructor(private securityService: SecurityService) {
  }

  ngOnInit(): void {
    this.securityService.getSecurityStatisticsReturnResult(this.idSecuritycurrency).subscribe(
      (isr: InstrumentStatisticsResult) => {
        this.isr = isr;
      });
  }
}
