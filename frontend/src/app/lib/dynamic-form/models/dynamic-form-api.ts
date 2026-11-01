import { InjectionToken, ProviderToken } from '@angular/core';
import type { FieldError } from '../../login/service/validation.error';

/**
 * The part of a dynamic form an edit dialog and its base classes use, independent of the forms API behind it. Both
 * DynamicFormComponent (reactive forms) and SignalDynamicFormComponent (signal forms) implement it, so the edit base
 * classes can serve dialogs on either form while the prototype of the signal variant runs next to the reactive one.
 */
export interface DynamicFormApi {
  /** Resets every field to its default value and enables the submit button. */
  setDefaultValuesAndEnableSubmit(): void;

  /**
   * Shows the values of a business object in the form.
   *
   * @param sourceObject - Object whose properties, or `dataproperty` paths, supply the field values
   */
  transferBusinessObjectToForm(sourceObject: object): void;

  /**
   * Copies the values of every data field to a business object, values of disabled fields included.
   *
   * @param targetObject - Object receiving the values
   * @param createProperty - When true a property missing on the target is created, otherwise it is skipped
   */
  cleanMaskAndTransferValuesToBusinessObject(targetObject: object, createProperty?: boolean): void;

  /**
   * Shows validation errors returned by the server at the fields they belong to. Optional, because the reactive form
   * shows them in the toast only.
   *
   * @param fieldErrors - Rejected fields with the message of the server
   */
  showServerErrors?(fieldErrors: FieldError[]): void;

  /**
   * Enables the submit button again after a failed save. Optional, because the reactive form follows the `disabled`
   * property of the submit button's FieldConfig, which the dialog resets.
   */
  enableSubmit?(): void;

  /**
   * Reads the current value of a data field, also of one inside a field group.
   *
   * @param name - Field name
   * @returns The value, undefined for a name without a data field in this form
   */
  value(name: string): unknown;

  /**
   * Sets the value of a data field from code, also of one inside a field group.
   *
   * @param name - Field name
   * @param value - The new value
   * @throws Error when the form has no data field of that name
   */
  setValue(name: string, value: unknown): void;

  /**
   * Tells whether a data field takes part in the submitted values: it exists, is visible and enabled.
   *
   * @param name - Field name
   * @returns False for a hidden, disabled or unknown field
   */
  isFieldActive(name: string): boolean;

  /**
   * Changes the state of a button at runtime. A property left out keeps its current state; a name without a button
   * in this form is ignored, so shared code can address a button only some dialogs have.
   *
   * @param name - Field name of the button
   * @param state - The properties to change
   */
  setButtonState(name: string, state: FormButtonState): void;
}

/** Runtime state of a button of a dynamic form; buttons are no data fields and get no schema rules. */
export interface FormButtonState {
  disabled?: boolean;
  /** Shows the loading indicator of an Optimus button. */
  loading?: boolean;
  invisible?: boolean;
  /** Translation key of the button label. */
  labelKey?: string;
}

/** Provided by every dynamic form component, so a dialog base class can query its form without knowing its type. */
export const DYNAMIC_FORM = new InjectionToken<DynamicFormApi>('DYNAMIC_FORM');

/**
 * The DYNAMIC_FORM token under the concrete form type a dialog renders, for its view query. The provider of a form
 * component is the component itself (`useExisting`), so the query result has the type the dialog declares.
 *
 * @template F - Form component type of the dialog
 * @returns The token typed for that form
 */
export function dynamicFormToken<F extends DynamicFormApi>(): ProviderToken<F> {
  return DYNAMIC_FORM as ProviderToken<F>;
}
