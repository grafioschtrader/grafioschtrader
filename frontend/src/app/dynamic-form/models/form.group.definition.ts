import {FieldConfig} from './field.config';
import {BaseFieldFieldgroupConfig} from './base.field.fieldgroup.config';

/**
 * For the defintion of a FormGroup
 */
export interface FormGroupDefinition extends BaseFieldFieldgroupConfig {
  /**
   * Name of Formgroup
   */
  formGroupName: string;
  /**
   * Contains one or many fields
   */
  fieldConfig: FieldConfig[];


}

export type FieldFormGroup = FieldConfig | FormGroupDefinition;

