/**
 * Component for editing global parameter values.
 */
import {Component, Input, OnInit} from '@angular/core';

import {SimpleEntityEditBase} from '../edit/simple.entity.edit.base';
import {Globalparameters} from '../entities/globalparameters';
import {InputRule} from '../entities/input-rule';
import {FieldConfig} from '../dynamic-form/models/field.config';
import {TranslateModule, TranslateService} from '@ngx-translate/core';
import {DialogModule} from 'primeng/dialog';
import {DynamicFormModule} from '../dynamic-form/dynamic-form.module';
import {GlobalparameterService} from '../services/globalparameter.service';
import {MessageToastService} from '../message/message.toast.service';
import {DynamicFieldHelper} from '../helper/dynamic.field.helper';
import {DataType} from '../dynamic-form/models/data.type';
import {TranslateHelper} from '../helper/translate.helper';
import {AppHelper} from '../helper/app.helper';
import {HelpIds} from '../help/help.ids';
import {BaseSettings} from '../base.settings';
import {ValidatorFn} from '@angular/forms';

@Component({
  selector: 'globalsettings-edit',
  template: `
    <p-dialog header="{{'GLOBAL_SETTINGS' | translate}}" [(visible)]="visibleDialog"
              [style]="{width: '800px'}"
              (onShow)="onShow($event)" (onHide)="onHide($event)" [modal]="true">

      <h5>{{ globalparameters.propertyName }}</h5>
      @if (inputRuleDescription) {
        <p class="text-secondary"><small>{{ 'INPUT_RULE' | translate }}: {{ inputRuleDescription }}</small></p>
      }
      <dynamic-form [config]="config" [formConfig]="formConfig" [translateService]="translateService"
                    #form="dynamicForm"
                    (submitBt)="submit($event)">
      </dynamic-form>
    </p-dialog>`,
  standalone: true,
  imports: [DialogModule, DynamicFormModule, TranslateModule]
})
export class GlobalSettingsEditComponent extends SimpleEntityEditBase<Globalparameters> implements OnInit {

  @Input() globalparameters: Globalparameters;
  inputRuleDescription: string = null;
  private selectedPropertyField: string;
  private inputRule: InputRule = null;

  constructor(translateService: TranslateService,
    gps: GlobalparameterService,
    messageToastService: MessageToastService) {
    super(HelpIds.HELP_GLOBAL_SETTINGS, 'GLOBAL_SETTINGS', translateService, gps, messageToastService,
      gps);
  }

  ngOnInit(): void {
    // Parse input rule if present
    if (this.globalparameters.inputRule) {
      this.inputRule = InputRule.parse(this.globalparameters.inputRule);
      if (this.inputRule) {
        this.inputRuleDescription = this.inputRule.getDescription();
      }
    }

    this.formConfig = AppHelper.getDefaultFormConfig(this.gps,
      3, this.helpLink.bind(this));
    this.config = [
      this.getPropertyDefinition(this.globalparameters),
      DynamicFieldHelper.createSubmitButton()
    ];
    this.configObject = TranslateHelper.prepareFieldsAndErrors(this.translateService, this.config);
  }

  protected override initialize(): void {
    this.form.transferBusinessObjectToForm(this.globalparameters);
  }

  protected override getNewOrExistingInstanceBeforeSave(value: { [name: string]: any }): Globalparameters {
    return this.copyFormToPrivateBusinessObject(new Globalparameters(), <Globalparameters>this.globalparameters);
  }

  private getPropertyDefinition(globalparameters: Globalparameters): FieldConfig {
    let fieldConfig: FieldConfig;

    if (globalparameters.propertyBlobAsText) {
      this.selectedPropertyField = 'propertyBlobAsText';
      fieldConfig = DynamicFieldHelper.createFieldTextareaInputStringHeqF(this.selectedPropertyField, BaseSettings.FID_MAX_LETTERS, true,
        {textareaRows: 10});
    } else if (globalparameters.propertyDateTime) {
      this.selectedPropertyField = 'propertyDateTime';
      // Use string input for datetime values (ISO 8601 format)
      fieldConfig = DynamicFieldHelper.createFieldInputString(this.selectedPropertyField, globalparameters.propertyName,
        30, true);
    } else if (globalparameters.propertyDate) {
      this.selectedPropertyField = 'propertyDate';
      fieldConfig = DynamicFieldHelper.createFieldPcalendar(DataType.DateString, this.selectedPropertyField,
        globalparameters.propertyName, true);
    } else if (globalparameters.propertyInt !== null) {
      this.selectedPropertyField = 'propertyInt';
      // Use inputRule min/max if available, otherwise use defaults
      const min = this.inputRule?.min ?? 0;
      const max = this.inputRule?.max ?? 9999;
      fieldConfig = DynamicFieldHelper.createFieldMinMaxNumber(DataType.Numeric,
        this.selectedPropertyField, globalparameters.propertyName, true, min, max);
      // Add additional validators from inputRule (enum validator)
      if (this.inputRule) {
        const additionalValidators = this.inputRule.getValidators();
        // Filter out min/max validators since they are already applied
        const enumValidators = additionalValidators.filter(v => v !== null);
        if (fieldConfig.validation && enumValidators.length > 0) {
          fieldConfig.validation = [...fieldConfig.validation, ...enumValidators];
        }
      }
    } else {
      this.selectedPropertyField = 'propertyString';
      fieldConfig = DynamicFieldHelper.createFieldInputString(this.selectedPropertyField, globalparameters.propertyName,
        30, true);
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
