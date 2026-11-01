import { Signal } from '@angular/core';
import { AbstractControl, FormControl, FormGroup, ValidationErrors, ValidatorFn, Validators } from '@angular/forms';
import {
  disabled,
  max,
  maxLength,
  min,
  PathKind,
  readonly,
  required,
  SchemaPath,
  SchemaPathRules,
  SchemaPathTree,
  validate,
  ValidationError
} from '@angular/forms/signals';
import { FieldConfig } from '../models/field.config';
import { FieldFormGroup, FormGroupDefinition } from '../models/form.group.definition';
import { InputType } from '../models/input.type';
import { DataType } from '../models/data.type';
import { requireFieldName } from '../models/form-control-access';
import { SignalFormModel } from './signal-form.model';

/** A path of the runtime schema whose value type is known from the FieldConfig, not from a model interface. */
type FieldPath<TValue> = SchemaPath<TValue, SchemaPathRules.Supported, PathKind.Child>;

/** An error a field reported itself, valid while the field keeps the value it had when the error was reported. */
export interface ReportedFieldError {
  kind: string;
  message?: string;
  value: unknown;
}

/** Data field of a signal dynamic form with the group it belongs to, if any. */
export interface SignalFieldEntry {
  config: FieldConfig;
  group?: FormGroupDefinition;
}

/**
 * Lists the data fields of a configuration. Buttons are no data fields; a field inside a FormGroupDefinition keeps a
 * reference to its group, because its value lives in the nested model object of that group.
 *
 * @param config - The configuration of the form, groups included
 * @returns Every data field in configuration order
 */
export function signalFieldEntries(config: FieldFormGroup[]): SignalFieldEntry[] {
  const entries: SignalFieldEntry[] = [];
  for (const fieldFormGroup of config) {
    if ('formGroupName' in fieldFormGroup) {
      fieldFormGroup.fieldConfig
        .filter(isDataField)
        .forEach((fieldConfig) => entries.push({ config: fieldConfig, group: fieldFormGroup }));
    } else if (isDataField(fieldFormGroup)) {
      entries.push({ config: fieldFormGroup });
    }
  }
  return entries;
}

/** Whether a FieldConfig holds a value; buttons do not. */
export function isDataField(config: FieldConfig): boolean {
  return config.inputType !== InputType.Pbutton;
}

/**
 * Whether a native input holds a string. With null Signal Forms parse text as a number and time as a Date, so these
 * fields start with an empty string instead.
 */
export function isTextInput(config: FieldConfig): boolean {
  return (
    config.inputType === InputType.Input &&
    config.dataType !== DataType.Numeric &&
    config.dataType !== DataType.NumericInteger
  );
}

/**
 * Converts a value of a business object or of application code to the value the model holds: an absent value becomes
 * null, because signal forms treat `undefined` as a missing field, and the empty string for a text input.
 *
 * @param config - Configuration of a data field
 * @param value - Value to store
 * @returns The value for the model
 */
export function toModelValue(config: FieldConfig, value: unknown): unknown {
  if (value === undefined || value === null) {
    return isTextInput(config) ? '' : null;
  }
  return value;
}

/**
 * Converts a model value back for the business object. An empty text input the user has not edited transfers null,
 * as its reactive control would; one the user has cleared transfers the empty string, also as in the reactive form.
 *
 * @param config - Configuration of a data field
 * @param value - Value of the model
 * @param dirty - Whether the user has edited the field
 * @returns The value for the business object
 */
export function fromModelValue(config: FieldConfig, value: unknown, dirty: boolean): unknown {
  return value === '' && !dirty && isTextInput(config) ? null : value;
}

/**
 * The value of a field before a business object is transferred: the default value, otherwise empty, and a checkbox
 * without default starts unchecked.
 *
 * @param config - Configuration of a data field
 * @returns The defined initial value
 */
export function initialFieldValue(config: FieldConfig): unknown {
  if (config.defaultValue !== undefined && config.defaultValue !== null) {
    return config.defaultValue;
  }
  return config.inputType === InputType.Checkbox ? false : toModelValue(config, null);
}

/**
 * Builds the model of a form with its default values; the fields of a group form a nested object named after the
 * group, as the controls of a FormGroupDefinition do in the reactive form.
 *
 * @param entries - Data fields of the form
 * @returns A model with a defined value for every field
 */
export function initialSignalFormModel(entries: SignalFieldEntry[]): SignalFormModel {
  const model: SignalFormModel = {};
  for (const { config, group } of entries) {
    const target = group ? groupModel(model, group.formGroupName) : model;
    target[requireFieldName(config)] = initialFieldValue(config);
  }
  return model;
}

/**
 * Returns the nested model object of a group, creating it when missing.
 *
 * @param model - The model of the form
 * @param groupName - Name of the FormGroupDefinition
 * @returns The object holding the values of that group
 */
export function groupModel(model: SignalFormModel, groupName: string): SignalFormModel {
  const existing = model[groupName];
  if (existing && typeof existing === 'object') {
    return existing as SignalFormModel;
  }
  const created: SignalFormModel = {};
  model[groupName] = created;
  return created;
}

/**
 * Adds the rules a FieldConfig declares to the runtime schema: required, length, range, readonly and initially
 * disabled state, the remaining validator functions and errors reported by the field or the server.
 *
 * @param root - Root path of the schema
 * @param entry - The field and its group
 * @param reportedErrors - Errors reported by controls and by the server, by field name
 */
export function applyFieldConfigRules(
  root: SchemaPathTree<SignalFormModel>,
  entry: SignalFieldEntry,
  reportedErrors: Signal<Record<string, ReportedFieldError[]>>
): void {
  const { config } = entry;
  const name = requireFieldName(config);
  const path = fieldPath<unknown>(root, entry);
  const validations = config.validation ?? [];
  if (validations.includes(Validators.required)) {
    required(path);
  }
  validations
    .filter((validator) => validator !== Validators.required)
    .forEach((validator) => validate(path, ({ value }) => validatorErrors(validator, new FormControl(value()))));
  applyConstraintRules(path, config);
  if (config.readonly) {
    readonly(path);
  }
  if (config.disabled) {
    disabled(path);
  }
  validate(path, ({ value }) =>
    (reportedErrors()[name] ?? [])
      .filter((reported) => Object.is(reported.value, value()))
      .map(({ kind, message }) => ({ kind, message }))
  );
}

/**
 * Adds the length and range constraints of a FieldConfig. They are rules rather than template bindings, because the
 * FormField directive owns the maxLength, min and max properties of a bound control.
 */
function applyConstraintRules(path: FieldPath<unknown>, config: FieldConfig): void {
  const textual =
    config.inputType === InputType.Input ||
    config.inputType === InputType.Pinputtextarea ||
    config.inputType === InputType.InputSuggestion;
  const numeric = config.dataType === DataType.Numeric || config.dataType === DataType.NumericInteger;
  if (textual && !numeric && config.maxLength != null) {
    maxLength(retype<string>(path), config.maxLength);
  }
  if (numeric && config.min != null) {
    min(retype<number | null>(path), config.min);
  }
  if (numeric && config.max != null) {
    max(retype<number | null>(path), config.max);
  }
}

/**
 * Adds the group validators of a FormGroupDefinition. The validators of the reactive form keep working: they receive
 * a FormGroup built from the current values of the group, and their errors are reported on the group field.
 *
 * @param root - Root path of the schema
 * @param definition - The group with its validators
 */
export function applyGroupRules(root: SchemaPathTree<SignalFormModel>, definition: FormGroupDefinition): void {
  const path = childPath<SignalFormModel>(root, definition.formGroupName);
  (definition.validation ?? []).forEach((validator) =>
    validate(path, ({ value }) => validatorErrors(validator, valueGroup(value())))
  );
}

/**
 * Runs a validator function of the reactive form against a control holding the value of a signal form field.
 *
 * @param validator - Validator function, such as `Validators.pattern(...)` or `gteDate(...)`
 * @param control - Control carrying the current value
 * @returns The errors as signal form errors; the error name becomes the kind, matching the existing error rules
 */
export function validatorErrors(validator: ValidatorFn, control: AbstractControl): ValidationError.WithoutFieldTree[] {
  return reactiveErrorsToKinds(validator(control));
}

/**
 * Converts the errors of a validator function. The values of the reactive errors are not kept, because the
 * displayed texts come from the error rules of the FieldConfig.
 *
 * @param errors - Result of a validator function
 * @returns One error per name, an empty array when valid
 */
export function reactiveErrorsToKinds(errors: ValidationErrors | null): ValidationError.WithoutFieldTree[] {
  return errors ? Object.keys(errors).map((kind) => ({ kind })) : [];
}

/** A FormGroup with one control per value, which group validators read through `get(name)` or `controls`. */
function valueGroup(value: SignalFormModel): FormGroup {
  return new FormGroup(Object.fromEntries(Object.entries(value).map(([key, v]) => [key, new FormControl(v)])));
}

/**
 * Returns the path of a field, nested below its group when it belongs to one.
 *
 * @param root - Root path of the schema
 * @param entry - The field and its group
 * @returns The schema path of the field
 */
export function fieldPath<TValue>(root: SchemaPathTree<SignalFormModel>, entry: SignalFieldEntry): FieldPath<TValue> {
  const parent = entry.group ? childPath<SignalFormModel>(root, entry.group.formGroupName) : root;
  return childPath<TValue>(parent, requireFieldName(entry.config));
}

/**
 * Navigates to a child path whose name is only known at runtime. The schema path tree is a proxy that supplies a
 * path for every key; its static type cannot know the keys of a runtime configuration.
 */
function childPath<TValue>(parent: unknown, key: string): FieldPath<TValue> {
  const path = (parent as Record<string, FieldPath<TValue> | undefined>)[key];
  if (!path) throw new Error(`Signal form has no path for "${key}".`);
  return path;
}

/** Gives a runtime path the value type its FieldConfig guarantees, as the typed rules min, max and maxLength require. */
function retype<TValue>(path: FieldPath<unknown>): FieldPath<TValue> {
  return path as FieldPath<TValue>;
}
