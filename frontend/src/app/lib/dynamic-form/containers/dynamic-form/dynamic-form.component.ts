import { Component, OnDestroy, output, input, effect, untracked, inject } from '@angular/core';
import { AbstractControl, ReactiveFormsModule, UntypedFormBuilder, UntypedFormGroup } from '@angular/forms';
import { merge, Subscription } from 'rxjs';
import { FormNotifier } from '../../models/form.notifier';
import { FieldConfig } from '../../models/field.config';
import { InputType } from '../../models/input.type';
import { Helper } from '../../../helper/helper';
import { DataType } from '../../models/data.type';
import { TranslateModule, TranslateService } from '@ngx-translate/core';
import { FormConfig } from '../../models/form.config';
import { FieldFormGroup, FormGroupDefinition } from '../../models/form.group.definition';
import { FormHelper } from '../../components/FormHelper';
import { ValueKeyHtmlSelectOptions } from '../../models/value.key.html.select.options';

import { DynamicFormLayoutComponent } from './dynamic-form-layout.component';
import { ErrorMessageComponent } from './error-message.compoent';
import { ButtonModule } from '@openng/optimus-ui/button';
import { DynamicFieldDirective } from '../../components/dynamic-field/dynamic-field.directive';

/**
 * The form with its label, input fields and buttons.
 */
@Component({
  exportAs: 'dynamicForm',
  selector: 'dynamic-form',
  template: `
    @let rev = notifier.revision();
    <div #actualTarget class="container-fluid dynamic-form-container">
      <form [formGroup]="form" (ngSubmit)="handleSubmit($event)">
        @if (showWithFieldset) {
          @for (fieldsetConfig of fieldsetConfigs; track fieldsetConfig) {
            <fieldset [class]="fieldsetConfig.fieldsetName ? 'out-border' : ''">
              <legend [class]="fieldsetConfig.fieldsetName ? 'out-border-legend' : ''">
                {{ fieldsetConfig.fieldsetName | translate }}
              </legend>
              <div class="row">
                @for (field of fieldsetConfig.fieldConfig; track field) {
                  <dynamic-form-layout [config]="field" [formConfig]="formConfig()" [group]="form" />
                }
              </div>
            </fieldset>
          }
        } @else {
          <div class="row">
            @for (field of controlsWithGroups; track field) {
              @if ('inputType' in field) {
                <dynamic-form-layout [config]="field" [formConfig]="formConfig()" [group]="form" />
              } @else {
                <ng-container [formGroupName]="field.formGroupName">
                  @for (childField of field.fieldConfig; track childField) {
                    <dynamic-form-layout
                      [config]="childField"
                      [formConfig]="formConfig()"
                      [group]="field.formControl" />
                  }
                </ng-container>
                <div class="col-12">
                  <error-message [baseFieldFieldgroupConfig]="field" />
                </div>
              }
            }
          </div>
        }

        <!-- Buttons -->
        <div class="d-flex align-items-center" [class.ui-widget-content]="!formConfig().nonModal">
          @if (formConfig().helpLinkFN) {
            <p-button [rounded]="true" (click)="helpLink($event)">
              <i class="pi pi-question" pButtonIcon></i>
            </p-button>
          }
          <div class="ms-auto">
            @for (field of buttons; track field) {
              @if (!field.invisible) {
                <dynamicField [config]="field" [formConfig]="formConfig()" [group]="form" />
              }
            }
          </div>
        </div>
      </form>
    </div>
  `,
  imports: [
    ReactiveFormsModule,
    TranslateModule,
    DynamicFormLayoutComponent,
    ErrorMessageComponent,
    ButtonModule,
    DynamicFieldDirective
  ]
})
export class DynamicFormComponent implements OnDestroy {
  private _formBuilder = inject(UntypedFormBuilder);

  readonly formConfig = input<FormConfig>(undefined);
  readonly config = input<FieldFormGroup[]>([]);
  readonly formGroupDefinition = input<FormGroupDefinition>(undefined);
  readonly translateService = input<TranslateService>(undefined);

  readonly submitBt = output<any>();

  form: UntypedFormGroup;

  fieldsetConfigs: FieldsetConfig[];
  showWithFieldset: boolean;

  /**
   * Change notification of this form, shared with every FieldConfig of its configuration. Every template of the form
   * reads its revision, so the form re-renders on a configuration or control change even below OnPush components.
   */
  notifier = new FormNotifier();

  /** Events of the current group and its controls; replaced whenever a new group is built. */
  private controlEventsSubscription: Subscription;

  constructor() {
    effect(() => {
      this.config();
      this.formConfig();
      this.formGroupDefinition();
      this.translateService();
      untracked(() => {
        this.notifier = FormNotifier.instrument(this.config(), this.notifier);
        this.buildForm();
      });
    });
  }

  get formGroups(): FormGroupDefinition[] {
    return this.config().filter((fieldFormGroup: any) => !!fieldFormGroup.formGroupName) as FormGroupDefinition[];
  }

  get controlsWithGroups(): FieldFormGroup[] {
    return this.config().filter(
      (fieldFormGroup: any) =>
        fieldFormGroup.formGroupName ||
        fieldFormGroup.buttonInForm ||
        !(fieldFormGroup.inputType === InputType.Button || fieldFormGroup.inputType === InputType.Pbutton)
    );
  }

  get controlsFlatten(): FieldConfig[] {
    return FormHelper.flattenConfigMap(this.config()).filter(
      ({ inputType }) => inputType !== InputType.Button && inputType !== InputType.Pbutton
    );
  }

  get controls(): FieldConfig[] {
    return FormHelper.getFieldConfigs(this.config()).filter(
      ({ inputType }) => inputType !== InputType.Button && inputType !== InputType.Pbutton
    );
  }

  get buttons(): FieldConfig[] {
    return FormHelper.getFieldConfigs(this.config()).filter(
      (config) =>
        !config.buttonInForm && (config.inputType === InputType.Button || config.inputType === InputType.Pbutton)
    );
  }

  get submitButton(): FieldConfig {
    return FormHelper.getFieldConfigs(this.config()).find(
      (config) => (config.inputType === InputType.Button || config.inputType === InputType.Pbutton) && !config.buttonFN
    );
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

  ngOnDestroy(): void {
    this.controlEventsSubscription?.unsubscribe();
  }

  /**
   * Builds the group of the current configuration and observes it. Every event of the group or of one of its
   * controls notifies the form; the controls are observed one by one, because the events of a group do not report
   * every transition of its children, for example a second child becoming touched.
   */
  private buildForm(): void {
    this.form = this.createGroupAndControl();
    this.fieldsetConfigs = this.createFieldsetConfigs();
    this.showWithFieldset = this.hasFieldset();
    this.controlEventsSubscription?.unsubscribe();
    const controls: AbstractControl[] = [
      this.form,
      ...this.formGroups.map((formGroupDefinition) => formGroupDefinition.formControl),
      ...this.controlsFlatten.map((fieldConfig) => fieldConfig.formControl)
    ].filter((control) => !!control);
    this.controlEventsSubscription = merge(...controls.map((control) => control.events)).subscribe(() =>
      this.notifier.notify()
    );
    this.notifier.notify();
  }

  /**
   * Buttons are not created here!
   */
  createGroupAndControl(): UntypedFormGroup {
    const childGroups = {};

    this.formGroups.forEach((formGroupDefinition) => {
      formGroupDefinition.formControl = this._formBuilder.group({});

      childGroups[formGroupDefinition.formGroupName] = formGroupDefinition.formControl;
      formGroupDefinition.fieldConfig.forEach((control) =>
        childGroups[formGroupDefinition.formGroupName].addControl(control.field, this.createControl(control))
      );
      formGroupDefinition.formControl.setValidators(formGroupDefinition.validation);
    });

    const group = this._formBuilder.group(childGroups);
    this.controls.forEach((control) => group.addControl(control.field, this.createControl(control)));
    return group;
  }

  createControl(config: FieldConfig): AbstractControl {
    if (!config.formControl) {
      const { disabled, validation, defaultValue: value } = config;
      config.formControl = this._formBuilder.control({ disabled, value }, validation);
    }
    return config.formControl;
  }

  transferBusinessObjectToForm(sourceObject: any): void {
    this.controlsFlatten.forEach((config) => {
      let value = config.defaultValue;

      if (config.dataproperty) {
        // field can not be used to access the input value
        value = Helper.getValueByPath(sourceObject, config.dataproperty);
      } else if (config.field in sourceObject) {
        if (
          config.dataType === DataType.DateNumeric ||
          config.dataType === DataType.DateTimeNumeric ||
          config.dataType === DataType.DateString
        ) {
          if (sourceObject[config.field]) {
            value = Helper.parseDateOnlyAsLocal(sourceObject[config.field]);
          }
        } else if (
          config.inputType === InputType.InputNumber &&
          sourceObject[config.field] === 0 &&
          config.inputNumberSettings.treatZeroAsNull
        ) {
          value = null;
        } else {
          value = sourceObject[config.field];
        }
      }
      if (config.inputType === InputType.Input && config.valueKeyHtmlOptions) {
        // Mapping value to a shown value, this mapping is happened with valueKeyHtmlOptions
        let valueKeyHtmlOption: ValueKeyHtmlSelectOptions;
        if (!value) {
          valueKeyHtmlOption = config.valueKeyHtmlOptions.find((valueKeyHtmlOptions) => '' === valueKeyHtmlOptions.key);
        } else {
          valueKeyHtmlOption = config.valueKeyHtmlOptions.find(
            (valueKeyHtmlOptions) => value === valueKeyHtmlOptions.key
          );
        }
        value = valueKeyHtmlOption.value;
      }

      config.formControl.setValue(value);
    });
    this.notifier.notify();
  }

  cleanMaskAndTransferValuesToBusinessObject(targetObject: any, createProperty: boolean = false): void {
    this.controlsFlatten.forEach((fieldConfig) =>
      Helper.copyFormSingleFormConfigToBusinessObject(this.formConfig(), fieldConfig, targetObject, createProperty)
    );
  }

  setDefaultValuesAndEnableSubmit() {
    if (this.submitButton !== undefined) {
      this.submitButton.disabled = false;
    }
    this.setDefaultValues();
    this.notifier.notify();
  }

  setDefaultValues() {
    this.form.reset();
    this.controlsFlatten.forEach((config) => {
      if (config.defaultValue !== null && config.defaultValue !== undefined) {
        config.formControl.setValue(config.defaultValue);
      }
    });
  }

  handleSubmit(event: Event) {
    if (this.submitButton) {
      this.submitButton.disabled = true;
    }

    event.preventDefault();
    event.stopPropagation();

    this.submitBt.emit(this.form.value);
    this.notifier.notify();
  }

  setDisableAll(disable: boolean) {
    const method = disable ? 'disable' : 'enable';
    FormHelper.flattenConfigMap(this.config()).forEach((fieldConfig) => {
      if (fieldConfig.inputType !== InputType.Button && fieldConfig.inputType !== InputType.Pbutton) {
        fieldConfig.formControl[method]();
      }
      fieldConfig.disabled = disable;
    });
    this.notifier.notify();
  }

  setDisabled(name: string, disable: boolean) {
    if (this.form.controls[name]) {
      const method = disable ? 'disable' : 'enable';
      this.form.controls[name][method]();
    } else {
      FormHelper.getFieldConfigs(this.config()).forEach((item) => {
        if (item.field === name) {
          item.disabled = disable;
        }
      });
    }
    this.notifier.notify();
  }

  setValue(name: string, value: any) {
    this.form.controls[name].setValue(value, { emitEvent: true });
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
    fieldConfigs.forEach((fieldConfig) => {
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
    this.formConfig().helpLinkFN();
  }
}

class FieldsetConfig {
  fieldConfig: FieldConfig[] = [];

  constructor(public fieldsetName: string) {}
}
