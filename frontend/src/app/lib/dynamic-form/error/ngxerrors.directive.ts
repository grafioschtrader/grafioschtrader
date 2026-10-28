import { Directive, Input, OnChanges, OnDestroy } from '@angular/core';
import { AbstractControl } from '@angular/forms';
import { BehaviorSubject } from 'rxjs';
import { ErrorOptions } from './ngxerrors';
import { toArray } from './toArray';

/**
 * Container of the error messages of one control. The control is published through {@link control$}, so each
 * contained ngxError directive follows the events of the current control, also when the form replaces the control of
 * a form group.
 */
@Directive({
  selector: '[ngxErrors]',
  exportAs: 'ngxErrors'
})
export class NgxErrorsDirective implements OnChanges, OnDestroy {
  @Input('ngxErrors') control: AbstractControl;

  /** The current control; emits on every replacement */
  readonly control$ = new BehaviorSubject<AbstractControl>(null);

  get errors() {
    return this.control ? this.control.errors : null;
  }

  get hasErrors() {
    return !!this.errors;
  }

  hasError(name: string, conditions: ErrorOptions): boolean {
    return this.checkPropState('invalid', name, conditions);
  }

  isValid(name: string, conditions: ErrorOptions): boolean {
    return this.checkPropState('valid', name, conditions);
  }

  getError(name: string) {
    return this.control?.getError(name);
  }

  ngOnChanges(): void {
    if (this.control$.value !== this.control) {
      this.control$.next(this.control);
    }
  }

  ngOnDestroy(): void {
    this.control$.complete();
  }

  private checkPropState(prop: string, field: string, conditions: ErrorOptions): boolean {
    if (!this.control) {
      return null;
    }
    const controlPropsState = !conditions || toArray(conditions).every((condition: string) => this.control[condition]);
    if (field.charAt(0) === '*') {
      return this.control[prop] && controlPropsState;
    }
    return prop === 'valid' ? !this.control.hasError(field) : this.control.hasError(field) && controlPropsState;
  }
}
