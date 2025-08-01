/**
 * Component for editing the portfolio.
 */
import {Component, Input, OnInit} from '@angular/core';
import {SimpleEntityEditBase} from '../../lib/edit/simple.entity.edit.base';
import {Globalparameters} from '../../lib/entities/globalparameters';
import {FieldConfig} from '../../lib/dynamic-form/models/field.config';
import {TranslateService} from '@ngx-translate/core';
import {GlobalparameterService} from '../service/globalparameter.service';
import {MessageToastService} from '../../lib/message/message.toast.service';
import {HelpIds} from '../help/help.ids';
import {DynamicFieldHelper} from '../../lib/helper/dynamic.field.helper';
import {DataType} from '../../lib/dynamic-form/models/data.type';
import {TranslateHelper} from '../../lib/helper/translate.helper';
import {AppHelper} from '../../lib/helper/app.helper';
import {AppSettings} from '../app.settings';

@Component({
    selector: 'globalsettings-edit',
    template: `
    <p-dialog header="{{'GLOBAL_SETTINGS' | translate}}" [(visible)]="visibleDialog"
              [style]="{width: '800px'}"
              (onShow)="onShow($event)" (onHide)="onHide($event)" [modal]="true">

      <h5>{{globalparameters.propertyName}}</h5>
      <dynamic-form [config]="config" [formConfig]="formConfig" [translateService]="translateService"
                    #form="dynamicForm"
                    (submitBt)="submit($event)">
      </dynamic-form>
    </p-dialog>`,
    standalone: false
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
      return DynamicFieldHelper.createFieldTextareaInputStringHeqF(this.selectedPropertyField, AppSettings.FID_MAX_LETTERS, true,
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
