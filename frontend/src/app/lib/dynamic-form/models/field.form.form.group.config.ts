import { UntypedFormGroup } from '@angular/forms';
import { FieldConfig } from './field.config';
import { FormConfig } from './form.config';
import { FormNotifier } from './form.notifier';

/**
 * It is used for the creation of dynamic HTML-Input components.
 */
export interface FieldFormFormGroupConfig {
  config: FieldConfig;
  formConfig: FormConfig;
  group: UntypedFormGroup;
  /** Change notification of the form; undefined for a configuration that was never instrumented */
  readonly notifier?: FormNotifier;
}
