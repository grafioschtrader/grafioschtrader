import {DataType} from '../../dynamic-form/models/data.type';
import {FieldConfig} from '../../dynamic-form/models/field.config';
import {DynamicFieldHelper} from '../helper/dynamic.field.helper';

export abstract class FormDefinitionHelper {

  public static getTransactionTime(excludeSatSunday = false): FieldConfig {
    const transactionTime: FieldConfig = DynamicFieldHelper.createFieldPcalendarHeqF(DataType.DateTimeNumeric, 'transactionTime', true);
    transactionTime.calendarConfig.disabledDays = [0, 6];
    return transactionTime;
  }

}
