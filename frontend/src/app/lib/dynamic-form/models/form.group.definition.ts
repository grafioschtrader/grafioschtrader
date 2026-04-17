import {UntypedFormGroup} from '@angular/forms';
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

  /**
   * Narrowed from BaseFieldFieldgroupConfig.formControl (AbstractControl): a
   * FormGroupDefinition's control is always a FormGroup built via
   * FormBuilder.group({}).
   */
  formControl?: UntypedFormGroup;
}

export type FieldFormGroup = FieldConfig | FormGroupDefinition;

