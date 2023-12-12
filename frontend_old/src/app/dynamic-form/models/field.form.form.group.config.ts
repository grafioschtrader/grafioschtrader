import {UntypedFormGroup} from '@angular/forms';
import {FieldConfig} from './field.config';
import {FormConfig} from './form.config';

/**
 * It is used for the creation of dynamic HTML-Input components.
 */
export interface FieldFormFormGroupConfig {
  config: FieldConfig;
  formConfig: FormConfig;
  group: UntypedFormGroup;

}
