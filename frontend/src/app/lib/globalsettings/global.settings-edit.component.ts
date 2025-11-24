/**
 * Component for editing the portfolio.
 */
import {Component, Input, OnInit} from '@angular/core';
import {CommonModule} from '@angular/common';
import {SimpleEntityEditBase} from '../edit/simple.entity.edit.base';
import {Globalparameters} from '../entities/globalparameters';
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

@Component({
  selector: 'globalsettings-edit',
  template: `
    <p-dialog header="{{'GLOBAL_SETTINGS' | translate}}" [(visible)]="visibleDialog"
              [style]="{width: '800px'}"
              (onShow)="onShow($event)" (onHide)="onHide($event)" [modal]="true">

      <h5>{{ globalparameters.propertyName }}</h5>
      <dynamic-form [config]="config" [formConfig]="formConfig" [translateService]="translateService"
                    #form="dynamicForm"
                    (submitBt)="submit($event)">
      </dynamic-form>
    </p-dialog>`,
  standalone: true,
  imports: [CommonModule, DialogModule, DynamicFormModule, TranslateModule]
})
export class GlobalSettingsEditComponent extends SimpleEntityEditBase<Globalparameters> implements OnInit {

  @Input() globalparameters: Globalparameters;
  private selectedPropertyField: string;

  constructor(translateService: TranslateService,
    gps: GlobalparameterService,
    messageToastService: MessageToastService) {
    super(HelpIds.HELP_GLOBAL_SETTINGS, 'GLOBAL_SETTINGS', translateService, gps, messageToastService,
      gps);
  }

  ngOnInit(): void {
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
    if (globalparameters.propertyBlobAsText) {
      this.selectedPropertyField = 'propertyBlobAsText';
      return DynamicFieldHelper.createFieldTextareaInputStringHeqF(this.selectedPropertyField, BaseSettings.FID_MAX_LETTERS, true,
        {textareaRows: 10});
    } else if (globalparameters.propertyDate) {
      this.selectedPropertyField = 'propertyDate';
      return DynamicFieldHelper.createFieldPcalendar(DataType.DateString, this.selectedPropertyField,
        globalparameters.propertyName, true);
    } else if (globalparameters.propertyInt !== null) {
      this.selectedPropertyField = 'propertyInt';
      return DynamicFieldHelper.createFieldMinMaxNumber(DataType.Numeric,
        this.selectedPropertyField, globalparameters.propertyName, true, 0, 9999);
    } else {
      this.selectedPropertyField = 'propertyString';
      return DynamicFieldHelper.createFieldInputString(this.selectedPropertyField, globalparameters.propertyName,
        30, true);
    }
  }

}
