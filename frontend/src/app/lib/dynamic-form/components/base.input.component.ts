import { FieldFormFormGroupConfig } from '../models/field.form.form.group.config';
import { AfterViewInit, Directive, ElementRef, OnInit, input, viewChild } from '@angular/core';
import { FieldConfig } from '../models/field.config';
import { FormConfig } from '../models/form.config';
import { UntypedFormGroup, Validators } from '@angular/forms';
import { DataType } from '../models/data.type';
import { requireFieldName } from '../models/form-control-access';
import { FormNotifier } from '../models/form.notifier';
import { FocusableInput, focusInputTarget } from '../models/focusable-input';

@Directive()
export abstract class BaseInputComponent implements FieldFormFormGroupConfig, FocusableInput, OnInit, AfterViewInit {
  // Otherwise enum DataType can't be used in a html template
  DataType: typeof DataType = DataType;

  // @ViewChild('input', {read: ElementRef}) el: ElementRef;
  readonly el = viewChild<ElementRef>('input');

  readonly config = input.required<FieldConfig>();
  readonly formConfig = input.required<FormConfig>();
  readonly group = input.required<UntypedFormGroup>();

  isRequired = false;

  get fieldName(): string {
    return requireFieldName(this.config());
  }

  /** Change notification of the form this field belongs to; the template reads its revision. */
  get notifier(): FormNotifier | undefined {
    return FormNotifier.of(this.config());
  }

  ngOnInit(): void {
    this.reEvaluateRequired();
  }

  ngAfterViewInit(): void {
    this.config().elementRef = this.el();
    this.config().baseInputComponent = this;
  }

  reEvaluateRequired(): void {
    this.isRequired = this.config().validation?.includes(Validators.required) ?? false;
  }

  /** Moves the keyboard focus to this input. */
  focus(): void {
    focusInputTarget(this.el());
  }
}
