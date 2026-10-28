import { InputSignal } from '@angular/core';
import { UntypedFormGroup } from '@angular/forms';
import { FieldConfig } from './field.config';
import { FormConfig } from './form.config';
import { FormNotifier } from './form.notifier';

/**
 * It is used for the creation of dynamic HTML-Input components.
 */
export interface FieldFormFormGroupConfig {
  readonly config: InputSignal<FieldConfig>;
  readonly formConfig: InputSignal<FormConfig>;
  readonly group: InputSignal<UntypedFormGroup>;
  /** Change notification of the form; undefined for a configuration that was never instrumented */
  readonly notifier?: FormNotifier;
}
