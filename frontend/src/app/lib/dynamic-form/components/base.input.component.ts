import {FieldFormFormGroupConfig} from '../models/field.form.form.group.config';
import {AfterViewInit, Directive, ElementRef, OnInit, ViewChild} from '@angular/core';
import {FieldConfig} from '../models/field.config';
import {FormConfig} from '../models/form.config';
import {UntypedFormGroup, Validators} from '@angular/forms';
import {DataType} from '../models/data.type';

@Directive()
export abstract class BaseInputComponent implements FieldFormFormGroupConfig, OnInit, AfterViewInit {

  // Otherwise enum DataType can't be used in a html template
  DataType: typeof DataType = DataType;

  // @ViewChild('input', {read: ElementRef}) el: ElementRef;
  @ViewChild('input') el: ElementRef;

  config: FieldConfig;
  formConfig: FormConfig;
  group: UntypedFormGroup;

  isRequired = false;

  ngOnInit(): void {
    this.reEvaluateRequired();
  }

  ngAfterViewInit(): void {
    this.config.elementRef = this.el;
    this.config.baseInputComponent = this;
  }

  reEvaluateRequired(): void {
    this.isRequired = this.config.validation && this.config.validation.indexOf(Validators.required) >= 0;
  }

  /**
   * Moves the keyboard focus to this input.
   *
   * The '#input' view child is a native element for the plain HTML controls, but a PrimeNG component
   * instance for the wrapped ones (p-select, ...), where 'nativeElement' does not exist and the component
   * offers its own focus(). Both cases are handled here so that callers do not have to know which kind of
   * control a field happens to use.
   */
  focus(): void {
    const target: any = this.el;
    if (target?.nativeElement?.focus) {
      target.nativeElement.focus();
    } else if (typeof target?.focus === 'function') {
      target.focus();
    }
  }

}
