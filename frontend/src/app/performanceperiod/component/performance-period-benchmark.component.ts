import { Component, computed, effect, input, untracked } from '@angular/core';
import { TranslateModule } from '@ngx-translate/core';
import { TooltipModule } from '@openng/optimus-ui/tooltip';
import { SingleRecordConfigBase } from '../../lib/datashowbase/single.record.config.base';
import { TranslateValue } from '../../lib/datashowbase/column.config';
import { DataType } from '../../lib/dynamic-form/models/data.type';
import { AppHelper } from '../../lib/helper/app.helper';
import { AppSettings } from '../../shared/app.settings';
import type { BenchmarkNotice, PerformanceBenchmarkResult } from '../model/performance.period';

/**
 * Shows the comparison of the period performance with its benchmark: which benchmark was compared, from where it
 * came, what its return contains, the dates both sides were compared on, the two returns and their difference, and in
 * a second fieldset the shadow portfolio that invests the flows of the portfolio in the benchmark.
 * Every figure and every notice is decided by the backend; this component only formats them. Nothing is rendered
 * while the report has no benchmark.
 */
@Component({
  selector: 'performance-period-benchmark',
  template: `
    @if (benchmark(); as result) {
      <div class="fcontainer">
        <fieldset class="out-border fbox">
          <legend class="out-border-legend">{{ PERFORMANCE_BENCHMARK | translate }}</legend>
          @for (field of getFieldsForFieldSet(PERFORMANCE_BENCHMARK); track field.field) {
            <div class="row gx-1">
              <div class="col-6 text-end" [pTooltip]="field.headerTooltipTranslated">
                {{ field.headerTranslated }}
              </div>
              <div class="col-6 text-end">
                <span [style.color]="isValueByPathMinus(result, field) ? 'red' : 'inherit'">
                  {{ getValueByPath(result, field) }}
                </span>
              </div>
            </div>
          }
          @for (notice of noticeTexts(); track $index) {
            <p class="benchmark-notice">{{ notice }}</p>
          }
        </fieldset>
        @if (result.metrics) {
          <fieldset class="out-border fbox">
            <legend class="out-border-legend" [pTooltip]="PERFORMANCE_BENCHMARK_RISK + '_TOOLTIP' | translate">
              {{ PERFORMANCE_BENCHMARK_RISK | translate }}
            </legend>
            @for (field of getFieldsForFieldSet(PERFORMANCE_BENCHMARK_RISK); track field.field) {
              <div class="row gx-1">
                <div class="col-6 text-end" [pTooltip]="field.headerTooltipTranslated">
                  {{ field.headerTranslated }}
                </div>
                <div class="col-6 text-end">
                  <span [style.color]="isValueByPathMinus(result, field) ? 'red' : 'inherit'">
                    {{ getValueByPath(result, field) }}
                  </span>
                </div>
              </div>
            }
          </fieldset>
        }
        @if (result.shadow) {
          <fieldset class="out-border fbox">
            <legend class="out-border-legend" [pTooltip]="PERFORMANCE_SHADOW + '_TOOLTIP' | translate">
              {{ PERFORMANCE_SHADOW | translate }}
            </legend>
            @for (field of getFieldsForFieldSet(PERFORMANCE_SHADOW); track field.field) {
              @if (getValueByPath(result, field)) {
                <div class="row gx-1">
                  <div class="col-6 text-end" [pTooltip]="field.headerTooltipTranslated">
                    {{ field.headerTranslated }}
                  </div>
                  <div class="col-6 text-end">
                    <span [style.color]="isValueByPathMinus(result, field) ? 'red' : 'inherit'">
                      {{ getValueByPath(result, field) }}
                    </span>
                  </div>
                </div>
              }
            }
          </fieldset>
        }
      </div>
    }
  `,
  styles: [
    `
      .benchmark-notice {
        margin: 0.4rem 0 0;
        font-style: italic;
      }
    `
  ],
  imports: [TranslateModule, TooltipModule]
})
export class PerformancePeriodBenchmarkComponent extends SingleRecordConfigBase {
  /** Comparison of the report, null or undefined without a benchmark. */
  readonly benchmark = input<PerformanceBenchmarkResult | null>(null);

  readonly PERFORMANCE_BENCHMARK = 'PERFORMANCE_BENCHMARK';
  readonly PERFORMANCE_SHADOW = 'PERFORMANCE_SHADOW';
  readonly PERFORMANCE_BENCHMARK_RISK = 'PERFORMANCE_BENCHMARK_RISK';

  /** The notices translated with their parameters; dates are formatted for the user, a leg is an NLS key itself. */
  readonly noticeTexts = computed(() => (this.benchmark()?.notices ?? []).map((notice) => this.noticeText(notice)));

  constructor() {
    super();
    this.initializeFields();
    effect(() => {
      const benchmark = this.benchmark();
      untracked(() => {
        if (benchmark) this.createTranslatedValueStore([benchmark]);
      });
    });
  }

  private initializeFields(): void {
    const fieldsetName = this.PERFORMANCE_BENCHMARK;
    this.addFieldProperty(DataType.String, 'name', 'BENCHMARK_NAME', { fieldsetName });
    this.addFieldProperty(DataType.String, 'source', 'BENCHMARK_SOURCE', {
      fieldsetName,
      translateValues: TranslateValue.NORMAL
    });
    this.addFieldPropertyFeqH(DataType.String, 'returnBasis', {
      fieldsetName,
      translateValues: TranslateValue.NORMAL
    });
    this.addFieldPropertyFeqH(DataType.String, 'fxPair', { fieldsetName });
    this.addFieldPropertyFeqH(DataType.DateString, 'effectiveFrom', { fieldsetName });
    this.addFieldPropertyFeqH(DataType.DateString, 'effectiveTo', { fieldsetName });
    this.addPercentField('metrics.portfolioTwrPercent', 'PORTFOLIO_TWR_PERCENT');
    this.addPercentField('metrics.benchmarkTwrPercent', 'BENCHMARK_TWR_PERCENT');
    this.addPercentField('metrics.excessReturnPercent', 'EXCESS_RETURN_PERCENT', false);
    this.addPercentField('metrics.portfolioTwrAnnualizedPercent', 'PORTFOLIO_TWR_ANNUALIZED_PERCENT');
    this.addPercentField('metrics.benchmarkTwrAnnualizedPercent', 'BENCHMARK_TWR_ANNUALIZED_PERCENT');
    this.addPercentField('metrics.excessReturnAnnualizedPercent', 'EXCESS_RETURN_ANNUALIZED_PERCENT', false);
    this.addFieldProperty(DataType.NumericShowZero, 'metrics.includedIntervals', 'INCLUDED_INTERVALS', {
      fieldsetName,
      maxFractionDigits: 0
    });
    this.initializeRiskFields();
    this.initializeShadowFields();
    this.translateHeadersAndColumns();
  }

  /**
   * The relative risk over the risk sample. A figure below the minimum number of observations stays empty; the number
   * of observations tells why.
   */
  private initializeRiskFields(): void {
    const fieldsetName = this.PERFORMANCE_BENCHMARK_RISK;
    this.addFieldProperty(DataType.NumericShowZero, 'metrics.riskObservations', 'RISK_OBSERVATIONS', {
      fieldsetName,
      maxFractionDigits: 0
    });
    this.addPercentField('metrics.trackingErrorPercent', 'TRACKING_ERROR_PERCENT', true, fieldsetName);
    this.addRatioField('metrics.informationRatio', 'INFORMATION_RATIO');
    this.addRatioField('metrics.beta', 'BENCHMARK_BETA');
    this.addRatioField('metrics.correlation', 'BENCHMARK_CORRELATION');
    this.addPercentField('metrics.jensenAlphaAnnualizedPercent', 'JENSEN_ALPHA_ANNUALIZED_PERCENT', true, fieldsetName);
    this.addPercentField('metrics.upCapturePercent', 'UP_CAPTURE_PERCENT', true, fieldsetName);
    this.addPercentField('metrics.downCapturePercent', 'DOWN_CAPTURE_PERCENT', true, fieldsetName);
    this.addPercentField(
      'metrics.benchmarkVolatilityAnnualizedPercent',
      'BENCHMARK_VOLATILITY_ANNUALIZED_PERCENT',
      true,
      fieldsetName
    );
    this.addPercentField('metrics.benchmarkMaxDrawdownPercent', 'BENCHMARK_MAX_DRAWDOWN_PERCENT', true, fieldsetName);
  }

  /** A dimensionless figure with two fraction digits and no unit. */
  private addRatioField(field: string, headerKey: string): void {
    this.addFieldProperty(DataType.NumericShowZero, field, headerKey, {
      fieldsetName: this.PERFORMANCE_BENCHMARK_RISK,
      maxFractionDigits: 2
    });
  }

  /**
   * The shadow portfolio: the deposits and withdrawals of the portfolio invested in the benchmark. A figure the server
   * left empty, for instance every terminal figure after a shortfall, is not shown at all.
   */
  private initializeShadowFields(): void {
    const fieldsetName = this.PERFORMANCE_SHADOW;
    this.addFieldProperty(DataType.NumericShowZero, 'shadow.valueStartMC', 'SHADOW_VALUE_START_MC', { fieldsetName });
    this.addFieldProperty(DataType.NumericShowZero, 'shadow.valueEndMC', 'SHADOW_VALUE_END_MC', { fieldsetName });
    this.addFieldProperty(DataType.NumericShowZero, 'shadow.gainMC', 'SHADOW_GAIN_MC', { fieldsetName });
    this.addFieldProperty(DataType.NumericShowZero, 'shadow.differenceMC', 'SHADOW_DIFFERENCE_MC', { fieldsetName });
    this.addPercentField('shadow.mwrPercent', 'MWR_PERCENT', true, fieldsetName);
    this.addPercentField('shadow.mwrAnnualizedPercent', 'MWR_ANNUALIZED_PERCENT', true, fieldsetName);
    this.addFieldProperty(DataType.String, 'shadow.mwrStatus', 'MWR_STATUS', {
      fieldsetName,
      translateValues: TranslateValue.NORMAL
    });
    this.addFieldProperty(DataType.DateString, 'shadow.shortfallDate', 'SHADOW_SHORTFALL_DATE', { fieldsetName });
  }

  /**
   * Percentages arrive already in percent from the backend, so they are only formatted, never scaled. A difference of
   * exactly 0 is a result and is shown, only null stays empty. A difference of two returns is in percentage points,
   * which its label already says, so it gets no percent sign.
   */
  private addPercentField(
    field: string,
    headerKey: string,
    percentSuffix = true,
    fieldsetName = this.PERFORMANCE_BENCHMARK
  ): void {
    this.addFieldProperty(DataType.NumericShowZero, field, headerKey, {
      fieldsetName,
      headerSuffix: percentSuffix ? '%' : undefined,
      maxFractionDigits: AppSettings.FID_PERCENTAGE_FRACTION
    });
  }

  private noticeText(notice: BenchmarkNotice): string {
    const parameters: Record<string, string> = {};
    Object.entries(notice.parameters ?? {}).forEach(([name, value]) => {
      if (name === 'leg') {
        parameters[name] = this.translateService.instant(value);
      } else if (name.toLowerCase().endsWith('date')) {
        parameters[name] = AppHelper.getDateByFormat(this.gps, value);
      } else {
        parameters[name] = value;
      }
    });
    return this.translateService.instant(notice.code, parameters);
  }
}
