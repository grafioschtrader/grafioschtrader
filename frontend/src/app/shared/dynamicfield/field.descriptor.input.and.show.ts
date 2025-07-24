import {DataType} from '../../lib/dynamic-form/models/data.type';
import {FieldFormGroup} from '../../lib/dynamic-form/models/form.group.definition';


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

export interface FieldDescriptorInputAndShow {
  fieldName: string;
  dynamicFormPropertyHelps: string[] | DynamicFormPropertyHelps[];
  dataType: string | DataType;
  enumType: string;
  required: boolean;
  min: number;
  max: number;
}

export interface FieldDescriptorInputAndShowExtended extends FieldDescriptorInputAndShow {
  description: string;
  descriptionHelp: string;
  uiOrder: number;
  udfSpecialType: number;
  idUser: number;
}

export class ReplaceFieldWithGroup {
  constructor(public replaceFieldName: string, public fieldFormGroup: FieldFormGroup, public removeFieldName: string) {
  }
}
