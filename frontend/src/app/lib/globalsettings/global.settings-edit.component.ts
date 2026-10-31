/**
 * Component for editing global parameter values.
 */
import { Component, OnInit, input, inject } from '@angular/core';

import { SimpleEntityEditBase } from '../edit/simple.entity.edit.base';
import { Globalparameters } from '../entities/globalparameters';
import { InputRule } from '../entities/input-rule';
import { FieldConfig } from '../dynamic-form/models/field.config';
import { TranslateModule } from '@ngx-translate/core';
import { DialogModule } from '@openng/optimus-ui/dialog';
import { DYNAMIC_FORM_IMPORTS } from '../dynamic-form/dynamic-form.imports';
import { GlobalparameterService } from '../services/globalparameter.service';
import { DynamicFieldHelper } from '../helper/dynamic.field.helper';
import { DataType } from '../dynamic-form/models/data.type';
import { TranslateHelper } from '../helper/translate.helper';
import { AppHelper } from '../helper/app.helper';
import { HelpIds } from '../help/help.ids';
import { BaseSettings } from '../base.settings';

@Component({
  selector: 'globalsettings-edit',
  template: ` <p-dialog
    header="{{ 'GLOBAL_SETTINGS' | translate }}"
    [visible]="visibleDialog()"
    [style]="{ width: '800px' }"
    (onShow)="onShow($event)"
    (onHide)="onHide($event)"
    [modal]="true">
    <h5>{{ globalparameters().propertyName }}</h5>
    @if (inputRuleDescription) {
      <p class="text-secondary">
        <small>{{ 'INPUT_RULE' | translate }}: {{ inputRuleDescription }}</small>
      </p>
    }
    <dynamic-form
      [config]="config"
      [formConfig]="formConfig"
      [translateService]="translateService"
      #form="dynamicForm"
      (submitBt)="submit($event)" />
  </p-dialog>`,
  imports: [DialogModule, ...DYNAMIC_FORM_IMPORTS, TranslateModule]
})
export class GlobalSettingsEditComponent extends SimpleEntityEditBase<Globalparameters> implements OnInit {
  readonly globalparameters = input.required<Globalparameters>();
  inputRuleDescription: string | null = null;
  private inputRule: InputRule | null = null;

  constructor() {
    super(HelpIds.HELP_GLOBAL_SETTINGS, 'GLOBAL_SETTINGS', inject(GlobalparameterService));
  }

  ngOnInit(): void {
    // Parse input rule if present
    const globalparameters = this.globalparameters();
    if (globalparameters.inputRule) {
      this.inputRule = InputRule.parse(globalparameters.inputRule);
      if (this.inputRule) {
        this.inputRuleDescription = this.inputRule.getDescription();
      }
    }

    this.formConfig = AppHelper.getDefaultFormConfig(this.gps, 3, this.helpLink.bind(this));
    this.config = [this.getPropertyDefinition(globalparameters), DynamicFieldHelper.createSubmitButton()];
    this.configObject = TranslateHelper.prepareFieldsAndErrors(this.translateService, this.config);
  }

  protected override initialize(): void {
    this.form().transferBusinessObjectToForm(this.globalparameters());
  }

  protected override getNewOrExistingInstanceBeforeSave(value: { [name: string]: any }): Globalparameters {
    return this.copyFormToPrivateBusinessObject(new Globalparameters(), <Globalparameters>this.globalparameters());
  }

  private getPropertyDefinition(globalparameters: Globalparameters): FieldConfig {
    let selectedPropertyField: string;
    const propertyName = globalparameters.propertyName;
    if (propertyName == null) throw new Error('A global parameter requires its property name before editing.');
    let fieldConfig: FieldConfig;

    if (globalparameters.propertyBlobAsText) {
      selectedPropertyField = 'propertyBlobAsText';
      fieldConfig = DynamicFieldHelper.createFieldTextareaInputStringHeqF(
        selectedPropertyField,
        BaseSettings.FID_MAX_LETTERS,
        true,
        { textareaRows: 10 }
      );
    } else if (globalparameters.propertyDateTime) {
      selectedPropertyField = 'propertyDateTime';
      // Use string input for datetime values (ISO 8601 format)
      fieldConfig = DynamicFieldHelper.createFieldInputString(selectedPropertyField, propertyName, 30, true);
    } else if (globalparameters.propertyDate) {
      selectedPropertyField = 'propertyDate';
      fieldConfig = DynamicFieldHelper.createFieldPcalendar(
        DataType.DateString,
        selectedPropertyField,
        propertyName,
        true
      );
    } else if (
      globalparameters.propertyInt !== null ||
      (globalparameters.propertyString == null && this.inputRule?.isNumeric())
    ) {
      // A row without any value has no type of its own; a numeric input rule makes it an integer parameter
      selectedPropertyField = 'propertyInt';
      // Use inputRule min/max if available, otherwise use defaults
      const min = this.inputRule?.min ?? 0;
      const max = this.inputRule?.max ?? 9999;
      fieldConfig = DynamicFieldHelper.createFieldMinMaxNumber(
        DataType.Numeric,
        selectedPropertyField,
        propertyName,
        true,
        min,
        max
      );
      // Add additional validators from inputRule (enum validator)
      if (this.inputRule) {
        const additionalValidators = this.inputRule.getValidators();
        // Filter out min/max validators since they are already applied
        const enumValidators = additionalValidators.filter((v) => v !== null);
        if (fieldConfig.validation && enumValidators.length > 0) {
          fieldConfig.validation = [...fieldConfig.validation, ...enumValidators];
        }
      }
    } else {
      selectedPropertyField = 'propertyString';
      // Matches the length of the column property_string
      fieldConfig = DynamicFieldHelper.createFieldInputString(selectedPropertyField, propertyName, 100, true);
      // Add pattern validator from inputRule if present
      if (this.inputRule) {
        const additionalValidators = this.inputRule.getValidators();
        if (fieldConfig.validation && additionalValidators.length > 0) {
          fieldConfig.validation = [...fieldConfig.validation, ...additionalValidators];
        }
      }
    }

    return fieldConfig;
  }
}
