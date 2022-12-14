import {FieldConfig} from '../../dynamic-form/models/field.config';
import {DynamicFieldHelper} from '../../shared/helper/dynamic.field.helper';
import {AppSettings} from '../../shared/app.settings';
import {DataType} from '../../dynamic-form/models/data.type';
import {Subscription} from 'rxjs';
import * as moment from 'moment';
import {CorrelationLimit, SamplingPeriodType} from '../../entities/correlation.set';
import {ValueKeyHtmlSelectOptions} from '../../dynamic-form/models/value.key.html.select.options';
import Base = moment.unitOfTime.Base;

/**
 * It is used for editing a correlation set.
 */
export class CorrelationEditingSupport {

  private changeOnSamplingPeriodSub: Subscription;
  private readonly samplingPeriod = 'samplingPeriod';
  private readonly rolling = 'rolling';
  private changeOnDateFromSub: Subscription;
  private periodMoment: Base = 'M';
  private requiredMinPeriods = 3;
  private actualSamplingPeriod: SamplingPeriodType;


  getCorrelationFieldDefinition(mainField: string, usedLayoutColumns: number, submitTextKey: string = null): FieldConfig[] {
    const fieldConfig: FieldConfig[] = [
      DynamicFieldHelper.createFieldInputString('name', 'CORRELATION_SET_NAME', 25, true),
      DynamicFieldHelper.createFieldTextareaInputStringHeqF('note', AppSettings.FID_MAX_LETTERS, false),
      DynamicFieldHelper.createFieldPcalendarHeqF(DataType.DateString, 'dateFrom', false),
      DynamicFieldHelper.createFieldPcalendarHeqF(DataType.DateString, 'dateTo', false),
      DynamicFieldHelper.createFieldSelectStringHeqF(this.samplingPeriod, true),
      DynamicFieldHelper.createFieldSelectNumberHeqF(this.rolling, false),
      DynamicFieldHelper.createFieldCheckboxHeqF('adjustCurrency'),
      DynamicFieldHelper.createSubmitButton(submitTextKey)
    ];
    if (mainField) {
      fieldConfig.splice(0, 1,
        DynamicFieldHelper.createFieldSelectNumber(mainField, 'CORRELATION_SET_NAME', true,
          {usedLayoutColumns: 6}));
    }

    if (usedLayoutColumns < 12) {
      fieldConfig.forEach(fc => fc.usedLayoutColumns = usedLayoutColumns);
    }

    return fieldConfig;
  }

  setUpValueChange(configObject: { [name: string]: FieldConfig }, correlationLimit: CorrelationLimit): void {
    this.requiredMinPeriods = correlationLimit.requiredMinPeriods;
    this.valueChangedOnSamplingPeriod(configObject, correlationLimit);
    this.valueChangedOnDateFrom(configObject);
  }

  private valueChangedOnSamplingPeriod(configObject: { [name: string]: FieldConfig }, correlationLimit: CorrelationLimit): void {
    this.changeOnSamplingPeriodSub = configObject[this.samplingPeriod].formControl.valueChanges.subscribe((key: string) => {

      if (this.actualSamplingPeriod !== SamplingPeriodType[key]) {
        this.actualSamplingPeriod = SamplingPeriodType[key];
        configObject[this.rolling].formControl.enable();
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
    });
  }

  public getPeriodAndRollingWithParamPrefix(configObject: { [name: string]: FieldConfig }): string[] {
    return ['p0@' + configObject[this.samplingPeriod].formControl.value, 'p1@' + configObject[this.rolling].formControl.value];
  }

  private valueChangedOnDateFrom(configObject: { [name: string]: FieldConfig }): void {
    this.changeOnDateFromSub = configObject.dateFrom.formControl.valueChanges.subscribe(dateFrom =>
      this.setDateToMin(configObject));
  }

  private setDateToMin(configObject: { [name: string]: FieldConfig }): void {
    configObject.dateTo.calendarConfig.minDate = moment(configObject.dateFrom.formControl.value).add(this.requiredMinPeriods,
      this.periodMoment).toDate();
  }

  private createOptionsForRolling(configObject: { [name: string]: FieldConfig }, configuration: string): void {
    if (configuration) {
      configObject[this.rolling].formControl.enable();
      const valueKeyHtmlSelectOptions: ValueKeyHtmlSelectOptions[] = [];
      const stepMinMax: number[] = configuration.split(',').map(x => +x);
      for (let i = stepMinMax[1]; i <= stepMinMax[2]; i += stepMinMax[0]) {
        valueKeyHtmlSelectOptions.push(new ValueKeyHtmlSelectOptions(i, '' + i));
      }
      configObject[this.rolling].valueKeyHtmlOptions = valueKeyHtmlSelectOptions;
      configObject[this.rolling].formControl.setValue(valueKeyHtmlSelectOptions[0].value);
    } else {
      configObject[this.rolling].formControl.setValue('');
      configObject[this.rolling].formControl.disable();
    }
  }

  destroy(): void {
    this.changeOnSamplingPeriodSub && this.changeOnSamplingPeriodSub.unsubscribe;
    this.changeOnDateFromSub && this.changeOnDateFromSub.unsubscribe();
  }
}
