import {DataType} from '../../dynamic-form/models/data.type';
import {DynamicFormPropertyHelps} from './dynamic.form.property.helps';

export class FieldDescriptorInputAndShow {
  fieldName: string;
  dynamicFormPropertyHelps: string[] | DynamicFormPropertyHelps[];
  dataType: string | DataType;
  required: boolean;
  min: number;
  max: number;

}
