import { requireFormControl } from '../../lib/dynamic-form/models/form-control-access';
import { FieldConfig } from '../../lib/dynamic-form/models/field.config';
import { DynamicFieldHelper } from '../../lib/helper/dynamic.field.helper';
import { DataType } from '../../lib/dynamic-form/models/data.type';
import { Subscription } from 'rxjs';
import moment from 'moment';
import { CorrelationLimit, SamplingPeriodType } from '../../entities/correlation.set';
import { ValueKeyHtmlSelectOptions } from '../../lib/dynamic-form/models/value.key.html.select.options';
import { BaseSettings } from '../../lib/base.settings';
import Base = moment.unitOfTime.Base;

/**
 * It is used for editing a correlation set.
 */
export class CorrelationEditingSupport {
  /** Subscription for sampling period form control value changes */
  private changeOnSamplingPeriodSub: Subscription;

  /** Field name constant for sampling period control */
  private readonly samplingPeriod = 'samplingPeriod';

  /** Field name constant for rolling period control */
  private readonly rolling = 'rolling';

  /** Subscription for date from form control value changes */
  private changeOnDateFromSub: Subscription;

  /** Moment.js time unit for period calculations, defaults to months */
  private periodMoment: Base = 'M';

  /** Minimum required periods for correlation calculation */
  private requiredMinPeriods = 3;

  /** Currently selected sampling period type */
  private actualSamplingPeriod: SamplingPeriodType | undefined;

  /**
   * Creates field configuration array for correlation set form including name, date range,
   * sampling period, rolling window, currency adjustment and total return (include dividends) fields.
   *
   * @param mainField - Optional main field name for dropdown selection, if null creates input field
   * @param usedLayoutColumns - Number of columns to use for field layout (applied if less than 12)
   * @param submitTextKey - Translation key for submit button text, defaults to standard submit if null
   * @returns Array of configured form field definitions ready for dynamic form rendering
   */
  getCorrelationFieldDefinition(
    mainField: string | null,
    usedLayoutColumns: number,
    submitTextKey: string | null = null
  ): FieldConfig[] {
    const fieldConfig: FieldConfig[] = [
      DynamicFieldHelper.createFieldInputString('name', 'CORRELATION_SET_NAME', 25, true),
      DynamicFieldHelper.createFieldTextareaInputStringHeqF('note', BaseSettings.FID_MAX_LETTERS, false),
      DynamicFieldHelper.createFieldPcalendarHeqF(DataType.DateString, 'dateFrom', false),
      DynamicFieldHelper.createFieldPcalendarHeqF(DataType.DateString, 'dateTo', false),
      DynamicFieldHelper.createFieldSelectStringHeqF(this.samplingPeriod, true),
      DynamicFieldHelper.createFieldSelectNumberHeqF(this.rolling, false),
      DynamicFieldHelper.createFieldCheckboxHeqF('adjustCurrency'),
      DynamicFieldHelper.createFieldCheckboxHeqF('includeDividends'),
      DynamicFieldHelper.createSubmitButton(submitTextKey ?? undefined)
    ];
    if (mainField) {
      fieldConfig.splice(
        0,
        1,
        DynamicFieldHelper.createFieldSelectNumber(mainField, 'CORRELATION_SET_NAME', true, { usedLayoutColumns: 6 })
      );
    }
    if (usedLayoutColumns < 12) {
      fieldConfig.forEach((fc) => (fc.usedLayoutColumns = usedLayoutColumns));
    }
    return fieldConfig;
  }

  /**
   * Initializes form control value change subscriptions and sets up dynamic field behavior
   * including sampling period changes and date range validation.
   *
   * @param configObject - Map of field names to their FieldConfig objects for form control access
   * @param correlationLimit - Correlation limit configuration containing period requirements and rolling options
   */
  setUpValueChange(configObject: { [name: string]: FieldConfig }, correlationLimit: CorrelationLimit): void {
    this.destroy();
    this.actualSamplingPeriod = undefined;
    this.requiredMinPeriods = correlationLimit.requiredMinPeriods;
    this.valueChangedOnSamplingPeriod(configObject, correlationLimit);
    this.valueChangedOnDateFrom(configObject);
  }

  /**
   * Sets up subscription for sampling period form control changes and configures rolling period options
   * based on the selected sampling period type (daily, monthly, or annual).
   *
   * @param configObject - Map of field names to FieldConfig objects for accessing form controls
   * @param correlationLimit - Configuration containing rolling period options for different sampling types
   */
  private valueChangedOnSamplingPeriod(
    configObject: {
      [name: string]: FieldConfig;
    },
    correlationLimit: CorrelationLimit
  ): void {
    this.changeOnSamplingPeriodSub = requireFormControl(configObject[this.samplingPeriod]).valueChanges.subscribe(
      (key: string) => {
        if (this.actualSamplingPeriod !== SamplingPeriodType[key as keyof typeof SamplingPeriodType]) {
          this.actualSamplingPeriod = SamplingPeriodType[key as keyof typeof SamplingPeriodType];
          requireFormControl(configObject[this.rolling]).enable();
          switch (this.actualSamplingPeriod) {
            case SamplingPeriodType.DAILY_RETURNS:
              this.periodMoment = 'd';
              this.createOptionsForRolling(configObject, correlationLimit.dailyConfiguration);
              break;
            case SamplingPeriodType.MONTHLY_RETURNS:
              this.periodMoment = 'M';
              this.createOptionsForRolling(configObject, correlationLimit.monthlyConfiguration);
              break;
            default:
              this.periodMoment = 'y';
              this.createOptionsForRolling(configObject, correlationLimit.annualConfiguration);
          }
        }
        this.setDateToMin(configObject);
      }
    );
  }

  /**
   * Generates parameter prefix strings for correlation period and rolling window values
   * used in translation or parameter passing.
   *
   * @param configObject - Map of field names to FieldConfig objects for accessing current form values
   * @returns Array containing formatted parameter strings with 'p0@' and 'p1@' prefixes
   */
  public getPeriodAndRollingWithParamPrefix(configObject: { [name: string]: FieldConfig }): string[] {
    return [
      'p0@' + requireFormControl(configObject[this.samplingPeriod]).value,
      'p1@' + requireFormControl(configObject[this.rolling]).value
    ];
  }

  /**
   * Sets up subscription for date from form control changes to automatically update
   * the minimum date constraint for the date to field.
   *
   * @param configObject - Map of field names to FieldConfig objects for accessing form controls
   */
  private valueChangedOnDateFrom(configObject: { [name: string]: FieldConfig }): void {
    this.changeOnDateFromSub = requireFormControl(configObject.dateFrom).valueChanges.subscribe((dateFrom) =>
      this.setDateToMin(configObject)
    );
  }

  /**
   * Updates the minimum date constraint for the date to field based on the current date from value
   * and required minimum periods using the current period moment unit.
   *
   * @param configObject - Map of field names to FieldConfig objects for updating calendar constraints
   */
  private setDateToMin(configObject: { [name: string]: FieldConfig }): void {
    const calendar = configObject.dateTo.calendarConfig;
    if (!calendar) {
      throw new Error('Correlation dateTo requires a calendar configuration');
    }
    calendar.minDate = moment(requireFormControl(configObject.dateFrom).value)
      .add(this.requiredMinPeriods, this.periodMoment)
      .toDate();
  }

  /**
   * Creates and configures rolling period dropdown options based on the provided configuration string.
   * Parses step, min, and max values to generate sequential options for the rolling period field.
   *
   * @param configObject - Map of field names to FieldConfig objects for updating rolling field options
   * @param configuration - Comma-separated string containing step,min,max values for rolling options
   */
  private createOptionsForRolling(configObject: { [name: string]: FieldConfig }, configuration: string): void {
    if (configuration) {
      requireFormControl(configObject[this.rolling]).enable();
      const valueKeyHtmlSelectOptions: ValueKeyHtmlSelectOptions[] = [];
      const stepMinMax: number[] = configuration.split(',').map((x) => +x);
      for (let i = stepMinMax[1]; i <= stepMinMax[2]; i += stepMinMax[0]) {
        valueKeyHtmlSelectOptions.push(new ValueKeyHtmlSelectOptions(i, '' + i));
      }
      configObject[this.rolling].valueKeyHtmlOptions = valueKeyHtmlSelectOptions;
      requireFormControl(configObject[this.rolling]).setValue(valueKeyHtmlSelectOptions[0].value);
    } else {
      requireFormControl(configObject[this.rolling]).setValue('');
      requireFormControl(configObject[this.rolling]).disable();
    }
  }

  /** Unsubscribes from all form control value change subscriptions to prevent memory leaks */
  destroy(): void {
    this.changeOnSamplingPeriodSub && this.changeOnSamplingPeriodSub.unsubscribe();
    this.changeOnDateFromSub && this.changeOnDateFromSub.unsubscribe();
  }
}
