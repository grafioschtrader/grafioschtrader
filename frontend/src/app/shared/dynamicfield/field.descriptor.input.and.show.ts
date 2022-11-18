import {DataType} from '../../dynamic-form/models/data.type';
import {FieldFormGroup} from '../../dynamic-form/models/form.group.definition';


export enum DynamicFormPropertyHelps {
  PERCENTAGE,
  PASSWORD,
  EMAIL,
  SELECT_OPTIONS,
  DATE_FUTURE = 4
}

export enum ConstraintValidatorType {
  DateRange
}

export class ClassDescriptorInputAndShow {
  fieldDescriptorInputAndShows: FieldDescriptorInputAndShow[];
  constraintValidatorMap: { [key: string]: any} | Map<ConstraintValidatorType, any>;
}

export class FieldDescriptorInputAndShow {
  fieldName: string;
  dynamicFormPropertyHelps: string[] | DynamicFormPropertyHelps[];
  dataType: string | DataType;
  enumType: string;
  required: boolean;
  min: number;
  max: number;
}

export class ReplaceFieldWithGroup {
  constructor(public replaceFieldName: string, public fieldFormGroup: FieldFormGroup, public removeFieldName: string) {
  }
}
