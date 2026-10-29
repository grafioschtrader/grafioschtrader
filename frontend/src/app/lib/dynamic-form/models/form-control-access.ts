import type { AbstractControl } from '@angular/forms';

/** Data controls require a binding name; command buttons can remain unnamed. */
export function requireFieldName(config: { field?: string | null }): string {
  const name = config.field;
  if (name == null) throw new Error('A data form control requires a field name.');
  return name;
}

/**
 * Accesses a field/group control after DynamicFormComponent has built the form. Before that lifecycle point,
 * formControl remains optional on the definition; buttons and optional fields may never receive a control.
 */
export function requireFormControl<T extends AbstractControl>(config: {
  formControl?: T | null;
  field?: string | null;
  formGroupName?: string;
}): T {
  const control = config.formControl;
  if (control == null) {
    const name = config.field ?? config.formGroupName ?? '(unnamed)';
    throw new Error(`Form control "${name}" is not initialized. Access it after the dynamic form has been built.`);
  }
  return control;
}
