import { ChangeDetectionStrategy, Component, Input, OnChanges, OnInit } from '@angular/core';
import { TranslateModule, TranslateService } from '@ngx-translate/core';
import { TooltipModule } from '@openng/optimus-ui/tooltip';
import { SingleRecordConfigBase } from '../../lib/datashowbase/single.record.config.base';
import { TranslateValue } from '../../lib/datashowbase/column.config';
import { DataType } from '../../lib/dynamic-form/models/data.type';
import { GlobalparameterService } from '../../lib/services/globalparameter.service';
import { AppSettings } from '../../shared/app.settings';
import { PerformancePeriodMetrics } from '../model/performance.period';

/**
 * Shows the relative figures of the period performance report in four cards: return, risk, costs and the data basis
 * they were calculated on. Every figure is calculated by the backend; this component only formats it. An empty value
 * is explained in the data basis card - the calendar days for the p.a. figures, the number of daily returns for the
 * volatility and the status of the money-weighted return.
 */
@Component({
  selector: 'performance-period-metrics',
  template: `
    @if (metrics) {
      <div class="fcontainer">
        @for (fieldSetName of FIELD_SETS; track fieldSetName) {
          <fieldset class="out-border fbox">
            <legend class="out-border-legend">{{ fieldSetName | translate }}</legend>
            @for (field of getFieldsForFieldSet(fieldSetName); track field.field) {
              <div class="row gx-1">
                <div class="col-8 text-end" [pTooltip]="field.headerTooltipTranslated">
                  {{ field.headerTranslated }}
                </div>
                <div class="col-4 text-end">
                  <span [style.color]="isValueByPathMinus(metrics, field) ? 'red' : 'inherit'">
                    {{ getValueByPath(metrics, field) }}
                  </span>
                </div>
              </div>
            }
          </fieldset>
        }
      </div>
    }
  `,
  standalone: true,
  changeDetection: ChangeDetectionStrategy.Eager,
  imports: [TranslateModule, TooltipModule]
})
export class PerformancePeriodMetricsComponent extends SingleRecordConfigBase implements OnInit, OnChanges {
  /** Figures of the period, null or undefined while no report was loaded; nothing is rendered then. */
  @Input() metrics: PerformancePeriodMetrics;

  readonly PERFORMANCE_RETURN = 'PERFORMANCE_RETURN';
  readonly PERFORMANCE_RISK = 'PERFORMANCE_RISK';
  readonly PERFORMANCE_COST = 'PERFORMANCE_COST';
  readonly PERFORMANCE_DATA_BASIS = 'PERFORMANCE_DATA_BASIS';
  /** Card order: the returns first, the data basis that explains empty values last. */
  readonly FIELD_SETS = [
    this.PERFORMANCE_RETURN,
    this.PERFORMANCE_RISK,
    this.PERFORMANCE_COST,
    this.PERFORMANCE_DATA_BASIS
  ];

  private fieldsInitialized = false;

  /**
   * Creates the metrics display.
   *
   * @param translateService - Translation of the labels, tooltips and the status of the money-weighted return
   * @param gps - Locale settings for number and date formatting
   */
  constructor(translateService: TranslateService, gps: GlobalparameterService) {
    super(translateService, gps);
  }

  ngOnInit(): void {
    this.addPercentField('twrPercent', this.PERFORMANCE_RETURN);
    this.addPercentField('twrAnnualizedPercent', this.PERFORMANCE_RETURN);
    this.addPercentField('mwrPercent', this.PERFORMANCE_RETURN);
    this.addPercentField('mwrAnnualizedPercent', this.PERFORMANCE_RETURN);
    this.addPercentField('maxDrawdownPercent', this.PERFORMANCE_RETURN);

    this.addFieldPropertyFeqH(DataType.DateString, 'drawdownPeakDate', { fieldsetName: this.PERFORMANCE_RISK });
    this.addFieldPropertyFeqH(DataType.DateString, 'drawdownTroughDate', { fieldsetName: this.PERFORMANCE_RISK });
    this.addFieldPropertyFeqH(DataType.DateString, 'drawdownRecoveryDate', { fieldsetName: this.PERFORMANCE_RISK });
    this.addPercentField('currentDrawdownPercent', this.PERFORMANCE_RISK);
    this.addPercentField('volatilityAnnualizedPercent', this.PERFORMANCE_RISK);
    this.addPercentField('bestStepPercent', this.PERFORMANCE_RISK);
    this.addFieldPropertyFeqH(DataType.DateString, 'bestStepDate', { fieldsetName: this.PERFORMANCE_RISK });
    this.addPercentField('worstStepPercent', this.PERFORMANCE_RISK);
    this.addFieldPropertyFeqH(DataType.DateString, 'worstStepDate', { fieldsetName: this.PERFORMANCE_RISK });

    this.addFieldPropertyFeqH(DataType.NumericShowZero, 'averageCapitalMC', { fieldsetName: this.PERFORMANCE_COST });
    this.addFieldPropertyFeqH(DataType.NumericShowZero, 'feesMC', { fieldsetName: this.PERFORMANCE_COST });
    this.addPercentField('feeRatioPercent', this.PERFORMANCE_COST);
    this.addPercentField('feeRatioAnnualizedPercent', this.PERFORMANCE_COST);

    this.addCountField('calendarDays', { fieldsetName: this.PERFORMANCE_DATA_BASIS });
    this.addCountField('valuedSessions', { fieldsetName: this.PERFORMANCE_DATA_BASIS });
    this.addCountField('expectedSessions', {
      fieldsetName: this.PERFORMANCE_DATA_BASIS
    });
    this.addCountField('gapIntervals', { fieldsetName: this.PERFORMANCE_DATA_BASIS });
    this.addCountField('feesWithoutRate', { fieldsetName: this.PERFORMANCE_DATA_BASIS });
    this.addCountField('returnObservations', {
      fieldsetName: this.PERFORMANCE_DATA_BASIS
    });
    this.addFieldPropertyFeqH(DataType.String, 'mwrStatus', {
      fieldsetName: this.PERFORMANCE_DATA_BASIS,
      translateValues: TranslateValue.NORMAL
    });
    this.translateHeadersAndColumns();
    this.fieldsInitialized = true;
    this.translateStatus();
  }

  ngOnChanges(): void {
    this.translateStatus();
  }

  /**
   * Percentages arrive already in percent from the backend, so they are only formatted, never scaled. A return of
   * exactly 0 % is a result and is shown, only null stays empty.
   */
  private addPercentField(field: string, fieldsetName: string): void {
    this.addFieldPropertyFeqH(DataType.NumericShowZero, field, {
      fieldsetName,
      headerSuffix: '%',
      maxFractionDigits: AppSettings.FID_PERCENTAGE_FRACTION
    });
  }

  /** A counter of the data basis; 0 is meaningful (no gap), so it is shown rather than left empty. */
  private addCountField(field: string, optionalParams: { fieldsetName: string }): void {
    this.addFieldPropertyFeqH(DataType.NumericShowZero, field, { ...optionalParams, maxFractionDigits: 0 });
  }

  /** The status of the money-weighted return is an NLS key; its text is resolved once the fields exist. */
  private translateStatus(): void {
    if (this.fieldsInitialized && this.metrics) {
      this.createTranslatedValueStore([this.metrics]);
    }
  }
}
