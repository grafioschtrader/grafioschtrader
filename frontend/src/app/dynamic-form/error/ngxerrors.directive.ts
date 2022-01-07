import {AfterViewInit, Directive, Input, OnChanges, OnDestroy} from '@angular/core';
import {AbstractControl, FormGroupDirective} from '@angular/forms';
import {BehaviorSubject} from 'rxjs';
import {ErrorDetails, ErrorOptions} from './ngxerrors';
import {toArray} from './toArray';

@Directive({
  selector: '[ngxErrors]',
  exportAs: 'ngxErrors',
})
export class NgxErrorsDirective implements OnChanges, OnDestroy, AfterViewInit {

//  @Input('ngxErrors') controlName: string;

  @Input('ngxErrors') control: AbstractControl;

  subject = new BehaviorSubject<ErrorDetails>(null);

  // control: AbstractControl;

  ready = false;

  constructor(private form: FormGroupDirective) {
  }

  get errors() {
    if (!this.ready) {
      return;
    }
    return this.control.errors;
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
    if (!this.ready) {
      return;
    }
    return this.control.getError(name);
  }

  ngOnChanges() {
    // this.control = this.form.control.get(this.control);
  }

  ngAfterViewInit() {

    setTimeout(() => {
      // this.form.valueChanges.subscribe(data => {
      this.checkStatus();
      this.control.statusChanges.subscribe(this.checkStatus.bind(this));
    });

  }

  ngOnDestroy(): void {
    this.subject.unsubscribe();
  }

  private checkPropState(prop: string, field: string, conditions: ErrorOptions): boolean {
    if (!this.ready) {
      return;
    }
    const controlPropsState = (
      !conditions || toArray(conditions).every((condition: string) => this.control[condition])
    );
    if (field.charAt(0) === '*') {
      return this.control[prop] && controlPropsState;
    }
    return (
      prop === 'valid' ? !this.control.hasError(field) : this.control.hasError(field) && controlPropsState
    );
  }

  private checkStatus() {
    const control = this.control;
    const errors = control.errors;
    this.ready = true;
    if (!errors) {
      return;
    }
    for (const errorName of Object.keys(errors)) {
      this.subject.next({control, errorName});
    }
  }

}
