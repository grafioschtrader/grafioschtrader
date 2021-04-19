import {DataType} from '../../dynamic-form/models/data.type';

export enum DynamicFormPropertyHelps {
  PERCENTAGE
}

export class FieldDescriptorInputAndShow {
  fieldName: string;
  dynamicFormPropertyHelps: string[] | DynamicFormPropertyHelps[];
  dataType: string | DataType;
  required: boolean;
  min: number;
  max: number;

}