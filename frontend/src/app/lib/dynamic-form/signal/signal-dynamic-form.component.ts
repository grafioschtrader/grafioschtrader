import {
  Component,
  computed,
  effect,
  EffectRef,
  inject,
  Injector,
  input,
  output,
  signal,
  untracked
} from '@angular/core';
import { FieldTree, form, SchemaPathTree } from '@angular/forms/signals';
import { TranslateModule } from '@ngx-translate/core';
import { ButtonModule } from '@openng/optimus-ui/button';
import { FormHelper } from '../components/FormHelper';
import { DynamicFormLayoutComponent } from '../containers/dynamic-form/dynamic-form-layout.component';
import { businessObjectFieldValue } from '../models/business-object-value';
import { DYNAMIC_FORM, DynamicFormApi, FormButtonState } from '../models/dynamic-form-api';
import { FieldConfig } from '../models/field.config';
import { requireFieldName } from '../models/form-control-access';
import { FormConfig } from '../models/form.config';
import { FieldFormGroup, FormGroupDefinition } from '../models/form.group.definition';
import { Helper } from '../../helper/helper';
import type { FieldError } from '../../login/service/validation.error';
import { SignalDynamicFieldDirective } from './signal-dynamic-field.directive';
import { SignalErrorMessageComponent } from './signal-error-message.component';
import {
  FIELD_DISPLAY,
  SignalFieldDisplay,
  SignalFormContext,
  SignalFormModel,
  SignalFormRules,
  SignalValueLink
} from './signal-form.model';
import {
  applyFieldConfigRules,
  applyGroupRules,
  fromModelValue,
  groupModel,
  initialSignalFormModel,
  isDataField,
  ReportedFieldError,
  SignalFieldEntry,
  signalFieldEntries,
  toModelValue
} from './signal-form.schema';

/** The form built for one configuration; replaced as a whole when the configuration changes. */
interface BuiltSignalForm {
  tree: FieldTree<SignalFormModel>;
  entries: SignalFieldEntry[];
  entryByConfig: Map<FieldConfig, SignalFieldEntry>;
  fieldsets: SignalFieldset[];
  showWithFieldset: boolean;
  controlsWithGroups: FieldFormGroup[];
  buttons: FieldConfig[];
}

/** Fields shown in one fieldset, which draws a box with the translated fieldset name. */
interface SignalFieldset {
  fieldsetName: string | undefined;
  fieldConfig: FieldConfig[];
}

/** Error kind of a validation message returned by the server for a field. */
const SERVER_ERROR = 'server';

/**
 * Prototype of the dynamic form on signal forms (`@angular/forms/signals`). It renders the same FieldConfig
 * configuration with the same layout as DynamicFormComponent, but holds the values in a model signal and derives the
 * field state from a schema built at runtime:
 *
 * - the validation of each FieldConfig (`required`, length, range, the remaining validator functions) and the
 *   validators of each FormGroupDefinition;
 * - the dependency logic of the dialog, passed as {@link rules}, for example `disabled(p.x, { when })`, `hidden`,
 *   conditional `required`, `minDate`, and changing display properties through FIELD_DISPLAY;
 * - values that follow other fields, passed as {@link valueLinks}.
 *
 * The start values come from {@link source} and {@link values} and are in place when the form first renders, so no
 * later reset can discard an early entry. It needs no FormNotifier: every display state that changes at runtime is a
 * signal of the field tree or a schema rule. Display properties of FieldConfig are read once; a change after
 * rendering is expressed as a FIELD_DISPLAY rule, the state of a button with {@link setButtonState}.
 *
 * @template TModel - Model interface the rules of the dialog are typed against
 */
@Component({
  exportAs: 'signalDynamicForm',
  selector: 'signal-dynamic-form',
  providers: [
    { provide: SignalFormContext, useExisting: SignalDynamicFormComponent },
    { provide: DYNAMIC_FORM, useExisting: SignalDynamicFormComponent }
  ],
  template: `
    <ng-template #fieldTemplate let-config>
      @if (!config.invisible && !isHidden(config)) {
        <signalDynamicField [config]="config" [formConfig]="formConfig()" />
      }
    </ng-template>
    <ng-template #errorTemplate let-config>
      <signal-error-message [rules]="config" [field]="fieldOf(config)" />
    </ng-template>
    <div class="container-fluid dynamic-form-container">
      <form novalidate (submit)="handleSubmit($event)">
        @if (built(); as built) {
          @if (built.showWithFieldset) {
            @for (fieldset of built.fieldsets; track fieldset) {
              <fieldset [class]="fieldset.fieldsetName ? 'out-border' : ''">
                <legend [class]="fieldset.fieldsetName ? 'out-border-legend' : ''">
                  {{ fieldset.fieldsetName | translate }}
                </legend>
                <div class="row">
                  @for (field of fieldset.fieldConfig; track field) {
                    <dynamic-form-layout
                      [config]="field"
                      [formConfig]="formConfig()"
                      [fieldTemplate]="fieldTemplate"
                      [errorTemplate]="errorTemplate"
                      [fieldHidden]="isHidden(field)"
                      [display]="displayOf(field)" />
                  }
                </div>
              </fieldset>
            }
          } @else {
            <div class="row">
              @for (field of built.controlsWithGroups; track field) {
                @if ('inputType' in field) {
                  <dynamic-form-layout
                    [config]="field"
                    [formConfig]="formConfig()"
                    [fieldTemplate]="fieldTemplate"
                    [errorTemplate]="errorTemplate"
                    [fieldHidden]="isHidden(field)"
                    [display]="displayOf(field)" />
                } @else {
                  @for (childField of field.fieldConfig; track childField) {
                    <dynamic-form-layout
                      [config]="childField"
                      [formConfig]="formConfig()"
                      [fieldTemplate]="fieldTemplate"
                      [errorTemplate]="errorTemplate"
                      [fieldHidden]="isHidden(childField)"
                      [display]="displayOf(childField)" />
                  }
                  <div class="col-12">
                    <signal-error-message [rules]="field" [field]="groupOf(field)" />
                  </div>
                }
              }
            </div>
          }

          <!-- Buttons -->
          <div class="d-flex align-items-center" [class.ui-widget-content]="!formConfig().nonModal">
            @if (formConfig().helpLinkFN) {
              <p-button [rounded]="true" (click)="helpLink()">
                <i class="pi pi-question" pButtonIcon></i>
              </p-button>
            }
            <div class="ms-auto">
              @for (field of built.buttons; track field) {
                @if (!(buttonState(field).invisible ?? field.invisible)) {
                  <signalDynamicField [config]="field" [formConfig]="formConfig()" />
                }
              }
            </div>
          </div>
        }
      </form>
    </div>
  `,
  imports: [
    TranslateModule,
    ButtonModule,
    DynamicFormLayoutComponent,
    SignalDynamicFieldDirective,
    SignalErrorMessageComponent
  ]
})
export class SignalDynamicFormComponent<TModel extends object = SignalFormModel>
  extends SignalFormContext
  implements DynamicFormApi
{
  readonly formConfig = input.required<FormConfig>();
  readonly config = input<FieldFormGroup[]>([]);
  /** Dependency logic of the dialog; read once when the form is built from a configuration. */
  readonly rules = input<SignalFormRules<TModel>>();
  /** Business object whose values the form shows from the start; a change rebuilds the form. */
  readonly source = input<object | null>();
  /** Further start values by field name, laid over those of {@link source}, e.g. for a field the object lacks. */
  readonly values = input<Partial<TModel>>();
  /** Values that follow other fields; read once when the form is built. */
  readonly valueLinks = input<readonly SignalValueLink<TModel>[]>([]);
  /**
   * When true a disabled or hidden field transfers null to the business object instead of its retained value. The
   * reactive form clears such a field with `setValue(null)` when it disables it; the signal form keeps the value, so
   * it reappears when the field is enabled again, and clears it on transfer.
   */
  readonly transferDisabledAsNull = input(false);

  /** Emits the values of the enabled and visible fields, nested by group as the reactive form does. */
  readonly submitBt = output<SignalFormModel>();

  private readonly injector = inject(Injector);
  private readonly model = signal<SignalFormModel>({});
  protected readonly built = signal<BuiltSignalForm | undefined>(undefined);
  private readonly reportedErrors = signal<Record<string, ReportedFieldError[]>>({});
  private readonly submittingState = signal(false);
  private readonly buttonStates = signal<Record<string, FormButtonState>>({});
  private linkEffects: EffectRef[] = [];

  readonly submitting = this.submittingState.asReadonly();
  readonly valid = computed(() => this.built()?.tree().valid() ?? false);

  constructor() {
    super();
    effect(() => {
      const config = this.config();
      const source = this.source();
      const values = this.values();
      untracked(() => this.build(config, source, values));
    });
  }

  fieldOf(config: FieldConfig): FieldTree<unknown> | undefined {
    const built = this.built();
    const entry = built?.entryByConfig.get(config);
    if (!built || !entry) {
      return undefined;
    }
    const parent = entry.group ? childField(built.tree, entry.group.formGroupName) : built.tree;
    return childField(parent, requireFieldName(entry.config));
  }

  /** Display properties of FIELD_DISPLAY rules for a field, which the layout lays over its labels. */
  displayOf(config: FieldConfig): SignalFieldDisplay | undefined {
    return this.fieldOf(config)?.().metadata(FIELD_DISPLAY)?.();
  }

  buttonState(config: FieldConfig): FormButtonState {
    return (config.field && this.buttonStates()[config.field]) || {};
  }

  /**
   * Changes the state of a button at runtime, for example its loading indicator while a request runs. The state is
   * merged with the one set before; a property never set falls back to the FieldConfig.
   *
   * @param name - Field name of the button
   * @param state - The properties to change
   */
  setButtonState(name: string, state: FormButtonState): void {
    this.buttonStates.update((states) => ({ ...states, [name]: { ...states[name], ...state } }));
  }

  /**
   * Whether a hidden rule hides the field, or the button state an in-form button; the layout combines it with
   * FieldConfig.invisible.
   */
  isHidden(config: FieldConfig): boolean {
    return this.fieldOf(config)?.().hidden() ?? !!this.buttonState(config).invisible;
  }

  groupOf(definition: FormGroupDefinition): FieldTree<unknown> | undefined {
    const built = this.built();
    return built ? childField(built.tree, definition.formGroupName) : undefined;
  }

  reportInputError(config: FieldConfig, kind: string | null): void {
    const name = requireFieldName(config);
    const value = this.fieldOf(config)?.().value();
    this.reportedErrors.update((errors) => ({
      ...errors,
      [name]: [
        ...(errors[name] ?? []).filter((error) => error.kind === SERVER_ERROR),
        ...(kind ? [{ kind, value }] : [])
      ]
    }));
  }

  showServerErrors(fieldErrors: FieldError[]): void {
    const built = this.built();
    if (!built) {
      return;
    }
    const byField: Record<string, ReportedFieldError[]> = {};
    for (const { field, message } of fieldErrors) {
      const entry = built.entries.find((e) => e.config.field === field);
      if (entry) {
        const value = this.fieldOf(entry.config)?.().value();
        (byField[field] ??= []).push({ kind: SERVER_ERROR, message, value });
      }
    }
    this.reportedErrors.set(byField);
  }

  /** Enables the submit button after a failed save; the reactive form does this through FieldConfig.disabled. */
  enableSubmit(): void {
    this.submittingState.set(false);
  }

  setDefaultValuesAndEnableSubmit(): void {
    const built = this.built();
    if (!built) {
      return;
    }
    this.model.set(initialSignalFormModel(built.entries));
    built.tree().reset();
    this.reportedErrors.set({});
    this.submittingState.set(false);
  }

  transferBusinessObjectToForm(sourceObject: object): void {
    const built = this.built();
    if (!built) {
      return;
    }
    this.model.update((model) =>
      withFieldValues(model, built.entries, (entry) => businessObjectFieldValue(entry.config, sourceObject))
    );
  }

  /**
   * Copies the field values to a business object with the conversions of the reactive form. A disabled or hidden
   * field transfers its retained value, or null with {@link transferDisabledAsNull}.
   *
   * @param targetObject - Object receiving the values
   * @param createProperty - When true a property missing on the target is created
   */
  cleanMaskAndTransferValuesToBusinessObject(targetObject: object, createProperty = false): void {
    const disabledAsNull = this.transferDisabledAsNull();
    const built = this.built();
    if (!built) {
      return;
    }
    for (const entry of built.entries) {
      const state = this.fieldOf(entry.config)?.();
      const inactive = !!state && (state.disabled() || state.hidden());
      const value = disabledAsNull && inactive ? null : fromModelValue(entry.config, state?.value(), !!state?.dirty());
      Helper.copyFieldValueToBusinessObject(entry.config, value, targetObject, createProperty);
    }
  }

  /**
   * Reads the current value of a data field.
   *
   * @param name - Field name
   * @returns The value, undefined for an unknown field
   */
  value(name: string): unknown {
    const config = this.built()?.entries.find((e) => e.config.field === name)?.config;
    const state = config ? this.fieldOf(config)?.() : undefined;
    return config && state ? fromModelValue(config, state.value(), state.dirty()) : undefined;
  }

  /**
   * Sets the value of a data field from code; the field does not become dirty.
   *
   * @param name - Field name
   * @param value - New value; an absent value is stored as null, or as the empty string of a text input
   */
  setValue(name: string, value: unknown): void {
    const config = this.built()?.entries.find((e) => e.config.field === name)?.config;
    const state = config ? this.fieldOf(config)?.() : undefined;
    if (!config || !state) throw new Error(`Signal form has no field "${name}".`);
    state.value.set(toModelValue(config, value));
  }

  isFieldActive(name: string): boolean {
    const config = this.built()?.entries.find((e) => e.config.field === name)?.config;
    const state = config ? this.fieldOf(config)?.() : undefined;
    return !!config && !!state && !config.invisible && !state.hidden() && !state.disabled();
  }

  handleSubmit(event: Event): void {
    event.preventDefault();
    event.stopPropagation();
    const built = this.built();
    if (!built || !built.tree().valid() || this.submittingState()) {
      return;
    }
    this.submittingState.set(true);
    this.submitBt.emit(this.activeValues(built));
  }

  helpLink(): void {
    this.formConfig().helpLinkFN?.();
  }

  /** Values of the enabled and visible fields; the reactive group value omits disabled controls likewise. */
  private activeValues(built: BuiltSignalForm): SignalFormModel {
    const values: SignalFormModel = {};
    for (const entry of built.entries) {
      const state = this.fieldOf(entry.config)?.();
      if (state && !state.disabled() && !state.hidden()) {
        const target = entry.group ? groupModel(values, entry.group.formGroupName) : values;
        target[requireFieldName(entry.config)] = fromModelValue(entry.config, state.value(), state.dirty());
      }
    }
    return values;
  }

  private build(
    config: FieldFormGroup[],
    source: object | null | undefined,
    values: Partial<TModel> | undefined
  ): void {
    const entries = signalFieldEntries(config);
    const groups = FormHelper.getFormGroupDefinition(config);
    let model = initialSignalFormModel(entries);
    if (source) {
      model = withFieldValues(model, entries, (entry) => businessObjectFieldValue(entry.config, source));
    }
    if (values) {
      const given: Record<string, unknown> = values;
      const named = entries.filter((entry) => requireFieldName(entry.config) in given);
      model = withFieldValues(model, named, (entry) => given[requireFieldName(entry.config)]);
    }
    this.model.set(model);
    this.reportedErrors.set({});
    this.submittingState.set(false);
    const rules = this.rules();
    const tree = form(
      this.model,
      (path) => {
        entries.forEach((entry) => applyFieldConfigRules(path, entry, this.reportedErrors));
        groups.forEach((group) => applyGroupRules(path, group));
        rules?.(retypeRoot<TModel>(path));
      },
      { injector: this.injector }
    );
    const controls = FormHelper.getFieldConfigs(config).filter(isDataField);
    this.built.set({
      tree,
      entries,
      entryByConfig: new Map(entries.map((entry) => [entry.config, entry])),
      fieldsets: createFieldsets(controls),
      showWithFieldset: controls.length > 0 && !!controls[0].fieldsetName,
      controlsWithGroups: config.filter((f) => !FormHelper.isFieldConfig(f) || f.buttonInForm || isDataField(f)),
      buttons: FormHelper.getFieldConfigs(config).filter((f) => !f.buttonInForm && !isDataField(f))
    });
    this.linkValues(this.valueLinks());
  }

  /**
   * Creates one effect per value link; it reads the sources, so it reruns when one changes, and writes the derived
   * target value. The effects of a former configuration end first.
   */
  private linkValues(links: readonly SignalValueLink<TModel>[]): void {
    this.linkEffects.forEach((linkEffect) => linkEffect.destroy());
    this.linkEffects = links.map((link) => {
      let previousValues: Record<string, unknown> | undefined;
      return effect(
        () => {
          const values: Record<string, unknown> = {};
          link.sources.forEach((name) => (values[name] = this.value(name)));
          const initial = previousValues === undefined;
          const changed = initial || link.sources.some((name) => !sameValue(previousValues?.[name], values[name]));
          previousValues = values;
          if (!changed || (initial && link.skipInitial)) return;
          untracked(() => {
            const current = this.value(link.target);
            const next = link.derive(values as Partial<TModel>, current);
            if (!sameValue(current, next)) {
              this.setValue(link.target, next);
            }
          });
        },
        { injector: this.injector }
      );
    });
  }
}

/**
 * Returns a model with new values for some fields. The root and every group become new objects, so the model signal
 * and its fields see the change.
 *
 * @param model - The current model
 * @param entries - Fields to set
 * @param valueOf - The value of a field, converted to its model value
 * @returns The new model
 */
function withFieldValues(
  model: SignalFormModel,
  entries: SignalFieldEntry[],
  valueOf: (entry: SignalFieldEntry) => unknown
): SignalFormModel {
  const next: SignalFormModel = { ...model };
  new Set(entries.flatMap((entry) => (entry.group ? [entry.group.formGroupName] : []))).forEach(
    (groupName) => (next[groupName] = { ...groupModel(model, groupName) })
  );
  for (const entry of entries) {
    const target = entry.group ? groupModel(next, entry.group.formGroupName) : next;
    target[requireFieldName(entry.config)] = toModelValue(entry.config, valueOf(entry));
  }
  return next;
}

/** Whether two field values are equal, arrays compared element by element as a multi-select holds them. */
function sameValue(a: unknown, b: unknown): boolean {
  if (Array.isArray(a) && Array.isArray(b)) {
    return a.length === b.length && a.every((value, index) => Object.is(value, b[index]));
  }
  return Object.is(a, b);
}

/** Groups consecutive fields with the same fieldset name. */
function createFieldsets(controls: FieldConfig[]): SignalFieldset[] {
  const fieldsets: SignalFieldset[] = [];
  for (const fieldConfig of controls) {
    const last = fieldsets[fieldsets.length - 1];
    if (last && last.fieldsetName === fieldConfig.fieldsetName) {
      last.fieldConfig.push(fieldConfig);
    } else {
      fieldsets.push({ fieldsetName: fieldConfig.fieldsetName, fieldConfig: [fieldConfig] });
    }
  }
  return fieldsets;
}

/**
 * Navigates to a child field whose name is only known at runtime. The field tree is a proxy that supplies a field for
 * every key of the model; its static type cannot know the keys of a runtime configuration.
 */
function childField(parent: unknown, key: string): FieldTree<unknown> {
  const field = (parent as Record<string, FieldTree<unknown> | undefined>)[key];
  if (!field) throw new Error(`Signal form has no field "${key}".`);
  return field;
}

/**
 * Hands the root path to the rules of the dialog under the model interface of that dialog. The runtime model holds a
 * key for every field of the configuration, which the interface describes.
 */
function retypeRoot<TModel extends object>(path: SchemaPathTree<SignalFormModel>): SchemaPathTree<TModel> {
  return path as unknown as SchemaPathTree<TModel>;
}
