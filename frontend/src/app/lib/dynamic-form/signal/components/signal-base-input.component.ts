import { AfterViewInit, computed, Directive, ElementRef, input, OnDestroy, viewChild } from '@angular/core';
import { FieldTree } from '@angular/forms/signals';
import { DataType } from '../../models/data.type';
import { FieldConfig } from '../../models/field.config';
import { FocusableInput, focusInputTarget } from '../../models/focusable-input';
import { requireFieldName } from '../../models/form-control-access';
import { FormConfig } from '../../models/form.config';
import { ValueKeyHtmlSelectOptions } from '../../models/value.key.html.select.options';
import { displayConfig, FIELD_DISPLAY } from '../signal-form.model';

/**
 * Base of the input components of a signal dynamic form. The field tree replaces the FormGroup of BaseInputComponent:
 * value, disabled, readonly, required and range reach the control through `[formField]`, so the templates bind none
 * of them themselves.
 *
 * @template TValue - Value type of the bound control, for the template type check of `[formField]`
 */
@Directive()
export abstract class SignalBaseInputComponent<TValue = unknown> implements FocusableInput, AfterViewInit, OnDestroy {
  // Otherwise enum DataType can't be used in a html template
  readonly DataType: typeof DataType = DataType;

  readonly el = viewChild<ElementRef>('input');

  readonly config = input.required<FieldConfig>();
  readonly formConfig = input.required<FormConfig>();
  /** Field of the input; TValue is the value type the bound control accepts. */
  readonly field = input.required<FieldTree<TValue>>();

  /** Marks a required field, as long as it can be edited. */
  readonly isRequired = computed(() => {
    const state = this.field()();
    return state.required() && !state.disabled() && !state.readonly();
  });

  /**
   * The FieldConfig with the display properties of the schema laid over it (FIELD_DISPLAY). Templates read their
   * display state from it; the static `config` stays the identity of the field.
   */
  readonly view = computed<FieldConfig>(() => displayConfig(this.config(), this.field()().metadata(FIELD_DISPLAY)?.()));

  /** Options of a select field. */
  readonly options = computed<ValueKeyHtmlSelectOptions[]>(() => this.view().valueKeyHtmlOptions ?? []);

  get fieldName(): string {
    return requireFieldName(this.config());
  }

  /** Registers this input on its FieldConfig, so code of the dialog can focus it as on the reactive form. */
  ngAfterViewInit(): void {
    this.config().elementRef = this.el();
    this.config().baseInputComponent = this;
  }

  /** A hidden field loses its view but retains its model; focus must not reach its destroyed input. */
  ngOnDestroy(): void {
    const config = this.config();
    if (config.baseInputComponent === this) {
      config.baseInputComponent = undefined;
      config.elementRef = undefined;
    }
  }

  /** Moves the keyboard focus to this input. */
  focus(): void {
    focusInputTarget(this.el());
  }
}
