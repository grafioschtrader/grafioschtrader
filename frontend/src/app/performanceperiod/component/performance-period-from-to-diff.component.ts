import { SingleRecordConfigBase } from '../../lib/datashowbase/single.record.config.base';
import { Component, computed, DestroyRef, inject, input, signal } from '@angular/core';
import { takeUntilDestroyed } from '@angular/core/rxjs-interop';
import { PeriodHoldingAndDiff } from '../model/performance.period';
import { DataType } from '../../lib/dynamic-form/models/data.type';
import { AppHelper } from '../../lib/helper/app.helper';

import { TooltipModule } from '@openng/optimus-ui/tooltip';

@Component({
  selector: 'performance-period-from-to-diff',
  template: `
    <div class="fcontainer">
      @for (phad of periodHoldingsAndDiff(); track phad; let i = $index) {
        <fieldset class="out-border fbox">
          <legend class="out-border-legend">{{ titles()[i] }}</legend>
          @for (field of fields; track field) {
            <div class="row gx-1">
              <div class="col-9 text-end" [pTooltip]="field.headerTooltipTranslated">
                {{ field.headerTranslated }}
              </div>
              <div class="col-3 text-end">
                <span [style.color]="isValueByPathMinus(phad, field) ? 'red' : 'inherit'">
                  {{ getValueByPath(phad, field) }}
                </span>
              </div>
            </div>
          }
        </fieldset>
      }
    </div>
  `,
  imports: [TooltipModule]
})
export class TenantPerformanceFromToDiffComponent extends SingleRecordConfigBase {
  readonly periodHoldingsAndDiff = input<PeriodHoldingAndDiff[]>([]);
  private readonly differenceTitle = signal('');
  private readonly destroyRef = inject(DestroyRef);
  readonly titles = computed(() => {
    const rows = this.periodHoldingsAndDiff();
    return rows?.length
      ? [
          AppHelper.getDateByFormat(this.gps, rows[0].date),
          AppHelper.getDateByFormat(this.gps, rows[1].date),
          this.differenceTitle()
        ]
      : [];
  });

  constructor() {
    super();
    this.initializeFields();
  }

  private initializeFields(): void {
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
    this.translateService
      .get('DIFFERENCE')
      .pipe(takeUntilDestroyed(this.destroyRef))
      .subscribe((trans) => this.differenceTitle.set(trans));
  }
}
