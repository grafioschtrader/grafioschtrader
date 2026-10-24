import { signal, untracked } from '@angular/core';
import type { FieldFormGroup, FormGroupDefinition } from './form.group.definition';

/** Module-private slot holding the notifier of a form on each of its configuration objects. */
const NOTIFIER = Symbol('formNotifier');

/** Module-private slot holding the values behind the accessor properties of an instrumented object. */
const VALUES = Symbol('formNotifierValues');

/**
 * The FieldConfig properties a template of the dynamic form reads. Assigning a different value to one of them notifies
 * the form. formControl, elementRef and baseInputComponent are deliberately missing: they are no display state, and
 * the latter two are written in BaseInputComponent.ngAfterViewInit, where a notification would trigger a second check
 * of the same pass.
 */
export const FIELD_DISPLAY_PROPERTIES = [
  'disabled',
  'invisible',
  'readonly',
  'placeholder',
  'valueKeyHtmlOptions',
  'treeNodes',
  'suggestions',
  'groupItem',
  'groupItemUseOrLoading',
  'labelKey',
  'labelTitle',
  'labelSuffix',
  'labelHelpText',
  'labelShowText',
  'fieldSuffix',
  'maxLength',
  'min',
  'max',
  'inputWidth',
  'textareaRows',
  'icon',
  'errors',
  'calendarConfig',
  'inputNumberSettings',
  'usedLayoutColumns',
  'buttonInForm'
] as const;

/** The FormGroupDefinition properties a template reads; its error-message shows the cross-field errors. */
export const GROUP_DISPLAY_PROPERTIES = ['errors'] as const;

/** Nested settings objects whose own properties are display state too, with the properties they are known to have. */
const NESTED_DISPLAY_PROPERTIES: { [property: string]: readonly string[] } = {
  calendarConfig: ['minDate', 'maxDate', 'disabledDays', 'disabledDates'],
  inputNumberSettings: [
    'maxFractionDigits',
    'currency',
    'prefix',
    'allowNegative',
    'allowEmpty',
    'treatZeroAsNull',
    'excludeZero'
  ]
};

/**
 * Change notification of one dynamic form. Every template of the form reads {@link revision}, so a change of the
 * configuration or of a control re-renders the form even when every component above it runs OnPush.
 *
 * The notifier is attached to the configuration itself. {@link instrument} turns the display properties of each
 * FieldConfig into accessor properties on the same object, so application code keeps assigning plain properties —
 * `configObject.apiKey.invisible = true` — and every holder of a reference to that object notifies the form. Arrays
 * are not observed element by element: an array property is changed by assigning a new array.
 */
export class FormNotifier {
  /** Raised on every change; the templates of the form read it once at their top. */
  readonly revision = signal(0);

  /**
   * Marks every view of the form for refresh. Allowed anywhere, also while a template is rendered or inside a
   * computed, because the write is untracked.
   */
  notify(): void {
    untracked(() => this.revision.update((r) => r + 1));
  }

  /**
   * Returns the notifier attached to a form configuration, a single FieldConfig or FormGroupDefinition, or one of
   * their nested settings objects.
   *
   * @param config - The configuration array of a form or one of its objects
   * @returns The attached notifier, undefined when the object was never instrumented
   */
  static of(config: FieldFormGroup[] | FieldFormGroup | object | null | undefined): FormNotifier | undefined {
    return config ? (config as any)[NOTIFIER] : undefined;
  }

  /**
   * Notifies the form the object belongs to, if any. Used by helpers that change content below a display property,
   * for example the text of an error rule.
   *
   * @param config - The configuration array of a form or one of its objects
   */
  static notifyOf(config: FieldFormGroup[] | FieldFormGroup | object | null | undefined): void {
    FormNotifier.of(config)?.notify();
  }

  /**
   * Instruments a form configuration, top level and inside each FormGroupDefinition. Repeated calls are cheap and
   * create no accessor chains; one form never ends up with two notifiers.
   *
   * @param fieldFormGroup - The configuration array of the form
   * @param notifier - The notifier to use when none of the objects carries one yet
   * @returns The notifier now attached to every object of the configuration
   */
  static instrument(fieldFormGroup: FieldFormGroup[], notifier?: FormNotifier): FormNotifier {
    if (!fieldFormGroup) {
      return notifier ?? new FormNotifier();
    }
    const objects = collectObjects(fieldFormGroup);
    const used =
      [fieldFormGroup, ...objects].map((o) => FormNotifier.of(o)).find((n) => !!n) ?? notifier ?? new FormNotifier();
    attach(fieldFormGroup, used);
    for (const object of objects) {
      const isGroup = Array.isArray((object as FormGroupDefinition).fieldConfig);
      instrumentObject(object, isGroup ? GROUP_DISPLAY_PROPERTIES : FIELD_DISPLAY_PROPERTIES, used);
    }
    return used;
  }
}

/** Collects every FieldConfig and FormGroupDefinition of a configuration, nested groups included. */
function collectObjects(fieldFormGroup: FieldFormGroup[], out: FieldFormGroup[] = []): FieldFormGroup[] {
  for (const object of fieldFormGroup ?? []) {
    if (object) {
      out.push(object);
      const children = (object as FormGroupDefinition).fieldConfig;
      if (Array.isArray(children)) {
        collectObjects(children, out);
      }
    }
  }
  return out;
}

/** Attaches the notifier under the private slot; not enumerable, so spreading or stringifying never copies it. */
function attach(target: object, notifier: FormNotifier): void {
  Object.defineProperty(target, NOTIFIER, { value: notifier, writable: true, configurable: true, enumerable: false });
}

/**
 * Turns the given properties of an object into notifying accessor properties, once per object. A later call only
 * re-attaches the notifier, also to the nested settings objects.
 */
function instrumentObject(target: object, properties: readonly string[], notifier: FormNotifier): void {
  attach(target, notifier);
  if (Object.prototype.hasOwnProperty.call(target, VALUES)) {
    for (const nestedProperty of Object.keys(NESTED_DISPLAY_PROPERTIES)) {
      const nested = (target as any)[VALUES][nestedProperty];
      nested && typeof nested === 'object' && attach(nested, notifier);
    }
    return;
  }
  const values: { [property: string]: any } = {};
  Object.defineProperty(target, VALUES, { value: values, enumerable: false });
  for (const property of properties) {
    const nestedProperties = NESTED_DISPLAY_PROPERTIES[property];
    values[property] = (target as any)[property];
    if (nestedProperties) {
      instrumentNested(values[property], nestedProperties, notifier);
    }
    Object.defineProperty(target, property, {
      enumerable: true,
      configurable: true,
      get: () => values[property],
      set: (value: any) => {
        if (!Object.is(values[property], value)) {
          values[property] = value;
          if (nestedProperties) {
            instrumentNested(value, nestedProperties, FormNotifier.of(target));
          }
          FormNotifier.notifyOf(target);
        }
      }
    });
  }
}

/** Instruments a nested settings object; its changes notify the form of the FieldConfig holding it. */
function instrumentNested(nested: any, properties: readonly string[], notifier: FormNotifier | undefined): void {
  if (!nested || typeof nested !== 'object' || !notifier) {
    return;
  }
  const own = Object.keys(nested).filter((key) => !properties.includes(key));
  instrumentObject(nested, [...properties, ...own], notifier);
}
