import { Directive, input } from '@angular/core';
import { AbstractControl } from '@angular/forms';
import { ErrorOptions } from './ngxerrors';
import { toArray } from './toArray';

/**
 * Container of the error messages of one control. The control is published through its input signal, so each
 * contained ngxError directive follows the events of the current control, also when the form replaces the control of
 * a form group.
 */
@Directive({
  selector: '[ngxErrors]',
  exportAs: 'ngxErrors'
})
export class NgxErrorsDirective {
  readonly control = input<AbstractControl>(undefined, { alias: 'ngxErrors' });

  get errors() {
    const control = this.control();
    return control ? control.errors : null;
  }

  get hasErrors() {
    return !!this.errors;
  }

  hasError(name: string, conditions: ErrorOptions | null | undefined): boolean {
    return this.checkPropState('invalid', name, conditions);
  }

  isValid(name: string, conditions: ErrorOptions | null | undefined): boolean {
    return this.checkPropState('valid', name, conditions);
  }

  getError(name: string) {
    return this.control()?.getError(name);
  }

  private checkPropState(
    prop: 'valid' | 'invalid',
    field: string,
    conditions: ErrorOptions | null | undefined
  ): boolean {
    const control = this.control();
    if (!control) {
      return false;
    }
    const controlPropsState =
      !conditions || toArray(conditions).every((condition: string) => control[condition as keyof AbstractControl]);
    if (field.charAt(0) === '*') {
      return control[prop] && controlPropsState;
    }
    return prop === 'valid' ? !control.hasError(field) : control.hasError(field) && controlPropsState;
  }
}
