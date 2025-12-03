import {Component, EventEmitter, Input, OnChanges, OnDestroy, OnInit, Output, input, output, signal, computed, effect} from '@angular/core';
import {AbstractControl, UntypedFormBuilder, UntypedFormGroup} from '@angular/forms';
import {Subscription} from 'rxjs';
import {FieldConfig} from '../../models/field.config';
import {InputType} from '../../models/input.type';
import {Helper} from '../../../helper/helper';
import {DataType} from '../../models/data.type';
import {TranslateService} from '@ngx-translate/core';
import {FormConfig} from '../../models/form.config';
import {FieldFormGroup, FormGroupDefinition} from '../../models/form.group.definition';
import {FormHelper} from '../../components/FormHelper';
import {ValueKeyHtmlSelectOptions} from '../../models/value.key.html.select.options';

/**
 * The form with its label, input fields and buttons.
 */
@Component({
  exportAs: 'dynamicForm',
  selector: 'dynamic-form',
  template: `
    <div #actualTarget class="container-fluid dynamic-form-container">
      <form [formGroup]="form" (ngSubmit)="handleSubmit($event)">
        @if (showWithFieldset) {
          @for (fieldsetConfig of fieldsetConfigs; track fieldsetConfig) {
            <fieldset [ngClass]="fieldsetConfig.fieldsetName? 'out-border': ''">
              <legend
                [ngClass]="fieldsetConfig.fieldsetName? 'out-border-legend': ''">{{ fieldsetConfig.fieldsetName | translate }}
              </legend>
              @for (field of fieldsetConfig.fieldConfig; track field) {
                <dynamic-form-layout
                  [config]="field"
                  [formConfig]="formConfig"
                  [group]="form">
                </dynamic-form-layout>
              }
            </fieldset>
          }
        } @else {
          <div class="row">
            @for (field of controlsWithGroups; track field) {
              @if (field['inputType']) {
                <dynamic-form-layout [config]="field"
                                     [formConfig]="formConfig"
                                     [group]="form">
                </dynamic-form-layout>
              } @else {
                <ng-container [formGroupName]="field['formGroupName']">
                  @for (childField of field['fieldConfig']; track childField) {
                    <dynamic-form-layout [config]="childField"
                                         [formConfig]="formConfig"
                                         [group]="field.formControl">
                    </dynamic-form-layout>
                  }
                </ng-container>
                <div class="col-12">
                  <error-message [baseFieldFieldgroupConfig]="field"></error-message>
                </div>
              }
            }
          </div>
        }

        <!-- Buttons -->
        <div [class.ui-widget-content]="!formConfig.nonModal">
          @if (formConfig.helpLinkFN) {
            <button pButton pRipple type="button" icon="pi pi-question"
                    (click)="helpLink($event)" class="p-button-rounded"></button>
          }
          <div class="float-end">
            @for (field of buttons; track field) {
              @if (!field.invisible) {
                <dynamicField [config]="field"
                              [formConfig]="formConfig"
                              [group]="form">
                </dynamicField>
              }
            }
          </div>
        </div>
      </form>
    </div>
  `,
  standalone: false
})
export class DynamicFormComponent implements OnChanges, OnInit, OnDestroy {
  // ============================================
  // EXISTING API (Backward Compatible)
  // ============================================
  @Input() formConfig: FormConfig;
  @Input() config: FieldFormGroup[] = [];
  @Input() formGroupDefinition: FormGroupDefinition;
  @Input() translateService: TranslateService;

  @Output() submitBt: EventEmitter<any> = new EventEmitter<any>();

  // ============================================
  // NEW SIGNAL API (Opt-In)
  // ============================================

  /**
   * Signal-based form configuration. Use this instead of @Input() formConfig
   * when working with Signals. Mutually exclusive with traditional inputs.
   */
  formConfigSignal = input<FormConfig>();

  /**
   * Signal-based field configuration. Use this instead of @Input() config
   * when working with Signals.
   */
  configSignal = input<FieldFormGroup[]>();

  /**
   * Signal-based form group definition. Use this instead of @Input() formGroupDefinition
   * when working with Signals.
   */
  formGroupDefinitionSignal = input<FormGroupDefinition>();

  // ============================================
  // SIGNAL-BASED STATE (Reactive)
  // ============================================

  /**
   * Signal containing current form value. Automatically updated when form changes.
   * Read-only signal that reflects the state of the underlying FormGroup.
   */
  formValue = signal<any>({});

  /**
   * Signal containing form validation state. Updates reactively when validation changes.
   */
  formValid = signal<boolean>(false);

  /**
   * Signal containing form touched state.
   */
  formTouched = signal<boolean>(false);

  /**
   * Signal containing form dirty state.
   */
  formDirty = signal<boolean>(false);

  /**
   * Signal-based output for form submission. Alternative to @Output() submitBt
   */
  submitSignal = output<any>();

  // ============================================
  // COMPUTED SIGNALS (Derived State)
  // ============================================

  /**
   * Computed signal returning all form groups from configuration.
   * Auto-updates when config changes.
   */
  formGroupsSignal = computed<FormGroupDefinition[]>(() => {
    const cfg = this.getActiveConfig();
    return cfg.filter((fieldFormGroup: any) => !!fieldFormGroup.formGroupName) as FormGroupDefinition[];
  });

  /**
   * Computed signal returning all control fields (non-button fields).
   * Auto-updates when config changes.
   */
  controlsSignal = computed<FieldConfig[]>(() => {
    const cfg = this.getActiveConfig();
    return FormHelper.getFieldConfigs(cfg).filter(({inputType}) =>
      inputType !== InputType.Button && inputType !== InputType.Pbutton);
  });

  /**
   * Computed signal returning flattened controls (including nested groups).
   */
  controlsFlattenSignal = computed<FieldConfig[]>(() => {
    const cfg = this.getActiveConfig();
    return FormHelper.flattenConfigMap(cfg).filter(({inputType}) =>
      inputType !== InputType.Button && inputType !== InputType.Pbutton);
  });

  /**
   * Computed signal returning all button fields.
   */
  buttonsSignal = computed<FieldConfig[]>(() => {
    const cfg = this.getActiveConfig();
    return FormHelper.getFieldConfigs(cfg).filter((config) =>
      !config.buttonInForm && (config.inputType === InputType.Button || config.inputType === InputType.Pbutton));
  });

  /**
   * Computed signal returning the submit button configuration.
   */
  submitButtonSignal = computed<FieldConfig | undefined>(() => {
    const cfg = this.getActiveConfig();
    return FormHelper.getFieldConfigs(cfg).find((config) =>
      (config.inputType === InputType.Button || config.inputType === InputType.Pbutton) && !config.buttonFN);
  });

  // ============================================
  // INTERNAL STATE
  // ============================================

  /** Flag indicating if component is using Signal-based API */
  private usingSignalAPI = signal<boolean>(false);

  /** Subscriptions for form state synchronization */
  private subscriptions: Subscription[] = [];

  form: UntypedFormGroup;

  fieldsetConfigs: FieldsetConfig[];
  showWithFieldset: boolean;

  constructor(private _formBuilder: UntypedFormBuilder) {
    // Set up effect to sync form state to signals
    effect(() => {
      if (this.usingSignalAPI() && this.form) {
        this.syncFormStateToSignals();
      }
    });
  }

  get formGroups(): FormGroupDefinition[] {
    return this.config.filter((fieldFormGroup: any) => !!fieldFormGroup.formGroupName) as FormGroupDefinition[];
  }

  get controlsWithGroups(): FieldFormGroup[] {
    return this.config.filter((fieldFormGroup: any) => fieldFormGroup.formGroupName ||
      fieldFormGroup.buttonInForm || !(fieldFormGroup.inputType === InputType.Button || fieldFormGroup.inputType === InputType.Pbutton));
  }

  get controlsFlatten(): FieldConfig[] {
    return FormHelper.flattenConfigMap(this.config).filter(({inputType}) =>
      inputType !== InputType.Button && inputType !== InputType.Pbutton);
  }

  get controls(): FieldConfig[] {
    return FormHelper.getFieldConfigs(this.config).filter(({inputType}) =>
      inputType !== InputType.Button && inputType !== InputType.Pbutton);
  }

  get buttons(): FieldConfig[] {
    return FormHelper.getFieldConfigs(this.config).filter((config) =>
      !config.buttonInForm && (config.inputType === InputType.Button || config.inputType === InputType.Pbutton));
  }

  get submitButton(): FieldConfig {
    return FormHelper.getFieldConfigs(this.config).find((config) =>
      (config.inputType === InputType.Button || config.inputType === InputType.Pbutton) && !config.buttonFN);
  }

  get changes() {
    return this.form.valueChanges;
  }

  get valid() {
    return this.form.valid;
  }

  get value() {
    return this.form.value;
  }

  ngOnInit() {
    // Detect which API is being used
    this.detectAPIMode();

    const activeConfig = this.getActiveConfig();
    if (this.form && activeConfig.length > 0) {
      this.form = this.createGroupAndControl();

      this.fieldsetConfigs = this.createFieldsetConfigs();
      this.showWithFieldset = this.hasFieldset();

      // Set up reactive sync for Signal API
      if (this.usingSignalAPI()) {
        this.setupSignalSync();
      }
    }
  }

  ngOnChanges(): void {
    this.form = this.createGroupAndControl();
    this.fieldsetConfigs = this.createFieldsetConfigs();
    this.showWithFieldset = this.hasFieldset();
  }

  /**
   * Buttons are not created here!
   */
  createGroupAndControl(): UntypedFormGroup {
    const childGroups = {};

    this.formGroups.forEach(formGroupDefinition => {
      formGroupDefinition.formControl = this._formBuilder.group({});

      childGroups[formGroupDefinition.formGroupName] = formGroupDefinition.formControl;
      formGroupDefinition.fieldConfig.forEach(control =>
        childGroups[formGroupDefinition.formGroupName].addControl(control.field, this.createControl(control)));
      formGroupDefinition.formControl.setValidators(formGroupDefinition.validation);
    });

    const group = this._formBuilder.group(childGroups);
    this.controls.forEach(control => group.addControl(control.field, this.createControl(control)));
    return group;
  }

  createControl(config: FieldConfig): AbstractControl {
    if (!config.formControl) {
      const {disabled, validation, defaultValue: value} = config;
      config.formControl = this._formBuilder.control({disabled, value}, validation);
    }
    return config.formControl;
  }

  transferBusinessObjectToForm(sourceObject: any): void {
    this.controlsFlatten.forEach(config => {
      let value = config.defaultValue;

      if (config.dataproperty) {
        // field can not be used to access the input value
        value = Helper.getValueByPath(sourceObject, config.dataproperty);
      } else if (sourceObject.hasOwnProperty(config.field)) {
        if (config.dataType === DataType.DateNumeric || config.dataType === DataType.DateTimeNumeric
          || config.dataType === DataType.DateString) {
          // Date is a timestamp / numeric
          if (sourceObject[config.field]) {
            value = new Date(sourceObject[config.field]);
          }
        } else {
          value = sourceObject[config.field];
        }
      }
      if (config.inputType === InputType.Input && config.valueKeyHtmlOptions) {
        // Mapping value to a shown value, this mapping is happened with valueKeyHtmlOptions
        let valueKeyHtmlOption: ValueKeyHtmlSelectOptions;
        if (!value) {
          valueKeyHtmlOption = config.valueKeyHtmlOptions.find(valueKeyHtmlOptions => '' === valueKeyHtmlOptions.key);
        } else {
          valueKeyHtmlOption = config.valueKeyHtmlOptions.find(valueKeyHtmlOptions => value === valueKeyHtmlOptions.key);
        }
        value = valueKeyHtmlOption.value;
      }

      config.formControl.setValue(value);

    });
  }

  cleanMaskAndTransferValuesToBusinessObject(targetObject: any, createProperty: boolean = false): void {
    this.controlsFlatten.forEach(fieldConfig =>
      Helper.copyFormSingleFormConfigToBusinessObject(this.formConfig, fieldConfig, targetObject, createProperty));
  }

  setDefaultValuesAndEnableSubmit() {
    if (this.submitButton !== undefined) {
      this.submitButton.disabled = false;
    }
    this.setDefaultValues();
  }

  setDefaultValues() {
    this.form.reset();
    this.controlsFlatten.forEach(config => {
      if (config.defaultValue !== null && config.defaultValue !== undefined) {
        config.formControl.setValue(config.defaultValue);
      }
    });
  }

  handleSubmit(event: Event) {
    const submitBtn = this.usingSignalAPI()
      ? this.submitButtonSignal()
      : this.submitButton;

    if (submitBtn) {
      submitBtn.disabled = true;
    }

    event.preventDefault();
    event.stopPropagation();

    // Emit to both outputs (traditional and Signal)
    const value = this.form.value;
    this.submitBt.emit(value);        // Traditional EventEmitter
    this.submitSignal.emit(value);    // Signal-based output
  }

  setDisableAll(disable: boolean) {
    const method = disable ? 'disable' : 'enable';
    const activeConfig = this.getActiveConfig();
    FormHelper.flattenConfigMap(activeConfig).forEach(fieldConfig => {
      if (fieldConfig.inputType !== InputType.Button && fieldConfig.inputType !== InputType.Pbutton) {
        fieldConfig.formControl[method]();
      }
      fieldConfig.disabled = disable;
    });
  }

  setDisabled(name: string, disable: boolean) {
    if (this.form.controls[name]) {
      const method = disable ? 'disable' : 'enable';
      this.form.controls[name][method]();
      return;
    }

    const activeConfig = this.getActiveConfig();
    FormHelper.getFieldConfigs(activeConfig).forEach((item) => {
      if (item.field === name) {
        item.disabled = disable;
      }
    });
  }

  setValue(name: string, value: any) {
    this.form.controls[name].setValue(value, {emitEvent: true});
  }

  hasFieldset(): boolean {
    const fieldConfigs: FieldConfig[] = this.controls;
    return fieldConfigs.length > 0 && !!fieldConfigs[0].fieldsetName;
  }

  createFieldsetConfigs(): FieldsetConfig[] {
    const fieldsetConfigs: FieldsetConfig[] = [];
    const fieldConfigs: FieldConfig[] = this.controls;
    let oldFieldsetName = '';
    let fieldsetConfig: FieldsetConfig;
    fieldConfigs.forEach(fieldConfig => {
      if (fieldConfig.fieldsetName !== oldFieldsetName) {
        fieldsetConfig = new FieldsetConfig(fieldConfig.fieldsetName);
        fieldsetConfigs.push(fieldsetConfig);
        oldFieldsetName = fieldConfig.fieldsetName;
      }
      fieldsetConfig.fieldConfig.push(fieldConfig);
    });
    return fieldsetConfigs;
  }

  helpLink(event): void {
    this.formConfig.helpLinkFN();
  }

  // ============================================
  // SIGNAL API HELPERS
  // ============================================

  /**
   * Detects whether component is being used with Signal API or traditional API.
   * Signal API takes precedence if both are provided.
   */
  private detectAPIMode(): void {
    const hasSignalConfig = this.configSignal() !== undefined;
    this.usingSignalAPI.set(hasSignalConfig);
  }

  /**
   * Returns the active configuration (Signal or traditional).
   * Internal helper to avoid code duplication.
   */
  private getActiveConfig(): FieldFormGroup[] {
    return this.usingSignalAPI()
      ? (this.configSignal() || [])
      : this.config;
  }

  /**
   * Returns the active form configuration (Signal or traditional).
   */
  private getActiveFormConfig(): FormConfig {
    return this.usingSignalAPI()
      ? this.formConfigSignal()!
      : this.formConfig;
  }

  /**
   * Sets up reactive synchronization between FormGroup and Signals.
   * Subscribes to form value changes and updates signals accordingly.
   */
  private setupSignalSync(): void {
    // Sync initial state
    this.syncFormStateToSignals();

    // Subscribe to value changes
    const valueSubscription = this.form.valueChanges.subscribe(() => {
      this.syncFormStateToSignals();
    });
    this.subscriptions.push(valueSubscription);

    // Subscribe to status changes
    const statusSubscription = this.form.statusChanges.subscribe(() => {
      this.formValid.set(this.form.valid);
      this.formTouched.set(this.form.touched);
      this.formDirty.set(this.form.dirty);
    });
    this.subscriptions.push(statusSubscription);
  }

  /**
   * Synchronizes current FormGroup state to Signal properties.
   */
  private syncFormStateToSignals(): void {
    this.formValue.set(this.form.value);
    this.formValid.set(this.form.valid);
    this.formTouched.set(this.form.touched);
    this.formDirty.set(this.form.dirty);
  }

  // ============================================
  // SIGNAL-FRIENDLY PUBLIC METHODS
  // ============================================

  /**
   * Sets a field value. Works with both traditional and Signal APIs.
   * When using Signal API, automatically updates the formValue signal.
   */
  setValueSignal(name: string, value: any): void {
    this.setValue(name, value);

    if (this.usingSignalAPI()) {
      this.syncFormStateToSignals();
    }
  }

  /**
   * Sets disabled state for a field. Works with both APIs.
   */
  setDisabledSignal(name: string, disable: boolean): void {
    this.setDisabled(name, disable);

    if (this.usingSignalAPI()) {
      this.syncFormStateToSignals();
    }
  }

  /**
   * Sets disabled state for all fields. Works with both APIs.
   */
  setDisableAllSignal(disable: boolean): void {
    this.setDisableAll(disable);

    if (this.usingSignalAPI()) {
      this.syncFormStateToSignals();
    }
  }

  /**
   * Cleanup subscriptions when component is destroyed.
   */
  ngOnDestroy(): void {
    this.subscriptions.forEach(sub => sub.unsubscribe());
  }
}

class FieldsetConfig {
  fieldConfig: FieldConfig[] = [];

  constructor(public fieldsetName: string) {
  }
}
