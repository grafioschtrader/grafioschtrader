import {FieldConfig} from './field.config';
import {BaseFieldFieldgroupConfig} from './base.field.fieldgroup.config';

/**
 * For the definition of a FormGroup, It may be used for cross validation
 */
export interface FormGroupDefinition extends BaseFieldFieldgroupConfig {
  /**
   * Name of form group
   */
  formGroupName: string;
  /**
   * Contains one or many fields
   */
  fieldConfig: FieldConfig[];

}

export type FieldFormGroup = FieldConfig | FormGroupDefinition;

