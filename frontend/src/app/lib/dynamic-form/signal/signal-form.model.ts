import { Signal } from '@angular/core';
import { createMetadataKey, FieldTree, SchemaPathTree } from '@angular/forms/signals';
import { FieldConfig } from '../models/field.config';
import { FormGroupDefinition } from '../models/form.group.definition';
import type { FormButtonState } from '../models/dynamic-form-api';
import type { FIELD_DISPLAY_PROPERTIES } from '../models/form.notifier';

/** Model of a signal dynamic form whose fields are only known at runtime. */
export type SignalFormModel = Record<string, unknown>;

/**
 * Dependency logic a dialog adds to the schema of its signal dynamic form, for example `disabled`, `hidden`,
 * `required` with a `when` condition, or changing display properties through {@link FIELD_DISPLAY}. It replaces the
 * `valueChanges` subscriptions with `enable()`, `disable()` and `resetValidator()` of a reactive form.
 *
 * @template TModel - Model interface of the dialog; its keys must match the field names of the configuration
 */
export type SignalFormRules<TModel extends object> = (path: SchemaPathTree<TModel>) => void;

/**
 * Display properties of a FieldConfig that a schema rule may change at runtime. `disabled` and `readonly` are
 * missing: they are field state and come from the `disabled` and `readonly` rules.
 */
export type SignalFieldDisplay = Partial<
  Pick<FieldConfig, Exclude<(typeof FIELD_DISPLAY_PROPERTIES)[number], 'disabled' | 'readonly'>>
>;

/**
 * Display properties supplied by schema rules, laid over the static FieldConfig: select options, dropdown groups,
 * tree nodes, suggestions, calendar limits, labels and the like. A field and its layout read them reactively, so a
 * value that loads asynchronously or depends on another field needs no notification of the form. Several rules on
 * the same field are merged, a later rule overriding an earlier one property by property.
 *
 * ```ts
 * metadata(p.currency, FIELD_DISPLAY, () => ({ valueKeyHtmlOptions: this.currencies() }));
 * ```
 */
export const FIELD_DISPLAY = createMetadataKey<SignalFieldDisplay, SignalFieldDisplay>({
  getInitial: () => ({}),
  reduce: (acc, item) => ({ ...acc, ...item })
});

/**
 * A value that follows other fields: when one of the source fields changes, `derive` computes the new value of the
 * target field. Signal form rules derive field state but cannot change a value, so such a dependency is declared
 * here instead of in an effect of the dialog.
 *
 * @template TModel - Model interface of the dialog
 */
export interface SignalValueLink<TModel extends object> {
  /** Fields whose changes recompute the target. */
  sources: readonly (keyof TModel & string)[];
  /** Field that receives the derived value. */
  target: keyof TModel & string;
  /** Preserve the initial target value and derive it only after a source changes, e.g. defaults from a new MIC. */
  skipInitial?: boolean;
  /**
   * Computes the new value of the target. Returning the current value leaves the field untouched.
   *
   * @param values - Current values of the source fields
   * @param current - Current value of the target field
   * @returns The new value of the target
   */
  derive: (values: Partial<TModel>, current: unknown) => unknown;
}

/**
 * What the components below a SignalDynamicFormComponent need of it. It is provided as this abstract class, so the
 * layout, field and button components do not import the form component and no import cycle arises.
 */
export abstract class SignalFormContext {
  /** True when no enabled and visible field has an error and no validation is pending. */
  abstract readonly valid: Signal<boolean>;

  /** True from a submit until the dialog enables the submit button again. */
  abstract readonly submitting: Signal<boolean>;

  /**
   * Returns the field tree of a data field.
   *
   * @param config - Configuration of a field of this form
   * @returns The field tree, undefined for buttons and before the form is built
   */
  abstract fieldOf(config: FieldConfig): FieldTree<unknown> | undefined;

  /**
   * Returns the field tree of a field group, which carries the errors of its group validators.
   *
   * @param definition - Group definition of this form
   * @returns The field tree, undefined before the form is built
   */
  abstract groupOf(definition: FormGroupDefinition): FieldTree<unknown> | undefined;

  /**
   * Returns the runtime state of a button set by the dialog.
   *
   * @param config - Configuration of a button of this form
   * @returns The state, empty when the dialog has set none
   */
  abstract buttonState(config: FieldConfig): FormButtonState;

  /**
   * Reports an entry a control rejected without changing the value, for example a date the date picker can not
   * parse. The error lasts while the value of the field stays the one it had when the entry was rejected.
   *
   * @param config - Configuration of the field
   * @param kind - Error name, matching an error rule of the field; null withdraws a former report
   */
  abstract reportInputError(config: FieldConfig, kind: string | null): void;
}

/**
 * Lays the display properties of the schema over a FieldConfig.
 *
 * @param config - The static configuration
 * @param display - Display properties of the schema, absent without a rule
 * @returns The configuration itself when no rule applies, otherwise a merged copy for reading
 */
export function displayConfig(config: FieldConfig, display: SignalFieldDisplay | undefined): FieldConfig {
  return display && Object.keys(display).length > 0 ? { ...config, ...display } : config;
}
