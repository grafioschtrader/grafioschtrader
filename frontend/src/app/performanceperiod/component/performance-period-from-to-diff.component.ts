import {SingleRecordConfigBase} from '../../lib/datashowbase/single.record.config.base';
import {Component, Input, OnChanges, OnInit, SimpleChanges} from '@angular/core';
import {TranslateService} from '@ngx-translate/core';
import {GlobalparameterService} from '../../lib/services/globalparameter.service';
import {PeriodHoldingAndDiff} from '../model/performance.period';
import {DataType} from '../../lib/dynamic-form/models/data.type';
import {AppHelper} from '../../lib/helper/app.helper';
import {CommonModule} from '@angular/common';
import {TooltipModule} from 'primeng/tooltip';

@Component({
    selector: 'performance-period-from-to-diff',
  template: `
    <div class="fcontainer">
      @for (phad of periodHoldingsAndDiff; track phad; let i = $index) {
        <fieldset class="out-border fbox">
          <legend class="out-border-legend">{{titles[i]}}</legend>
          @for (field of fields; track field) {
            <div class="row gx-1">
              <div class="col-9 text-end" [pTooltip]="field.headerTooltipTranslated">
                {{field.headerTranslated}}
              </div>
              <div class="col-3 text-end">
                <span [style.color]='isValueByPathMinus(phad, field)? "red": "inherit"'>
                  {{getValueByPath(phad, field)}}
                </span>
              </div>
            </div>
          }
        </fieldset>
      }
    </div>
  `,
    standalone: true,
    imports: [CommonModule, TooltipModule]
})
export class TenantPerformanceFromToDiffComponent extends SingleRecordConfigBase implements OnInit, OnChanges {
  @Input() periodHoldingsAndDiff: PeriodHoldingAndDiff[];

  titles: string[] = new Array(3);
It
  constructor(translateService: TranslateService, gps: GlobalparameterService) {
    super(translateService, gps);
  }


  ngOnInit(): void {
    this.addFieldPropertyFeqH(DataType.Numeric, 'dividendRealMC');
    this.addFieldPropertyFeqH(DataType.Numeric, 'feeRealMC');
    this.addFieldPropertyFeqH(DataType.Numeric, 'interestCashaccountRealMC');
    this.addFieldPropertyFeqH(DataType.Numeric, 'externalCashTransferMC');
    this.addFieldPropertyFeqH(DataType.Numeric, 'accumulateReduceMC');
    this.addFieldPropertyFeqH(DataType.Numeric, 'securitiesAndMarginGainMC');
    this.addFieldPropertyFeqH(DataType.Numeric, 'securityRiskMC');
    this.addFieldPropertyFeqH(DataType.Numeric, 'cashBalanceMC');
    this.addFieldPropertyFeqH(DataType.Numeric, 'totalBalanceMC');
    this.addFieldPropertyFeqH(DataType.Numeric, 'gainMC');
    this.addFieldPropertyFeqH(DataType.Numeric, 'marginCloseGainMC');
    this.addFieldPropertyFeqH(DataType.Numeric, 'totalGainMC');
    this.translateHeadersAndColumns();
    this.translateService.get('DIFFERENCE').subscribe(trans => this.titles[2] = trans);
  }

  ngOnChanges(changes: SimpleChanges): void {
    if (this.periodHoldingsAndDiff && this.periodHoldingsAndDiff.length > 0) {
      this.titles[0] = AppHelper.getDateByFormat(this.gps, this.periodHoldingsAndDiff[0].date);
      this.titles[1] = AppHelper.getDateByFormat(this.gps, this.periodHoldingsAndDiff[1].date);
    }
  }
}
