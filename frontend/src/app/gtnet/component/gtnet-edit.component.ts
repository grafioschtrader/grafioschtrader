import {Component, Input, OnInit} from '@angular/core';
import {SimpleEntityEditBase} from '../../lib/edit/simple.entity.edit.base';
import {GTNet, GTNetCallParam, GTNetServerOnlineStatusTypes, GTNetServerStateTypes} from '../model/gtnet';
import {TranslateModule, TranslateService} from '@ngx-translate/core';
import {GlobalparameterService} from '../../lib/services/globalparameter.service';
import {MessageToastService} from '../../lib/message/message.toast.service';
import {HelpIds} from '../../lib/help/help.ids';
import {AppSettings} from '../../shared/app.settings';
import {GTNetService} from '../service/gtnet.service';
import {AppHelper} from '../../lib/helper/app.helper';
import {ValueKeyHtmlSelectOptions} from '../../lib/dynamic-form/models/value.key.html.select.options';
import {DynamicFieldHelper} from '../../lib/helper/dynamic.field.helper';
import {Subscription} from 'rxjs';
import {TranslateHelper} from '../../lib/helper/translate.helper';
import {SelectOptionsHelper} from '../../lib/helper/select.options.helper';
import {DataType} from '../../lib/dynamic-form/models/data.type';
import {DialogModule} from 'primeng/dialog';
import {DynamicFormComponent} from '../../lib/dynamic-form/containers/dynamic-form/dynamic-form.component';
import {FieldConfig} from '../../lib/dynamic-form/models/field.config';

/**
 * Add ar modify a GTNet entity.
 */
@Component({
  selector: 'gtnet-edit',
  standalone: true,
  imports: [
    DialogModule,
    DynamicFormComponent,
    TranslateModule
  ],
  template: `
    <p-dialog header="{{'GT_NET_NET_AND_MESSAGE' | translate}}" [(visible)]="visibleDialog"
              [style]="{width: '500px'}"
              (onShow)="onShow($event)" (onHide)="onHide($event)" [modal]="true">

      <dynamic-form [config]="config" [formConfig]="formConfig" [translateService]="translateService"
                    #form="dynamicForm"
                    (submitBt)="submit($event)">
      </dynamic-form>
    </p-dialog>
  `
})
export class GTNetEditComponent extends SimpleEntityEditBase<GTNet> implements OnInit {
  @Input() callParam: GTNetCallParam;
  private readonly BASE_SETTING = 'BASE_SETTING';
  private readonly ENTITY = 'ENTITY';
  private readonly LAST_PRICE = 'LAST_PRICE';
  private domainRemoteNameSubscribe: Subscription;


  constructor(translateService: TranslateService,
              gps: GlobalparameterService,
              messageToastService: MessageToastService,
              gtNetService: GTNetService) {
    super(HelpIds.HELP_GT_NET, AppSettings.GT_NET.toUpperCase(), translateService, gps,
      messageToastService, gtNetService);
  }

  ngOnInit(): void {
    const isUpdate = this.callParam.gtNet && !!this.callParam?.gtNet.idGtNet;
    this.formConfig = AppHelper.getDefaultFormConfig(this.gps,
      5, this.helpLink.bind(this));
    this.config = [
      DynamicFieldHelper.createFieldTextareaInputStringHeqF('domainRemoteName', 128, false, {
        fieldsetName: this.BASE_SETTING,
        disabled: isUpdate
      }),
      ...this.editOwnInstance(isUpdate),
      DynamicFieldHelper.createSubmitButton()
    ];
    this.configObject = TranslateHelper.prepareFieldsAndErrors(this.translateService, this.config);
  }

  private editOwnInstance(isUpdate: boolean): FieldConfig[] {
    return isUpdate? [
    DynamicFieldHelper.createFieldSelectStringHeqF('timeZone', true, {fieldsetName: this.BASE_SETTING}),
      DynamicFieldHelper.createFieldCheckboxHeqF('spreadCapability', {
        defaultValue: true,
        fieldsetName: this.BASE_SETTING
      }),
      DynamicFieldHelper.createFieldMinMaxNumberHeqF(DataType.NumericInteger, 'dailyRequestLimit', true, 0, 9999,
        {defaultValue: 1000, fieldsetName: this.BASE_SETTING}),
      DynamicFieldHelper.createFieldCheckboxHeqF('serverBusy', {fieldsetName: this.BASE_SETTING}),
      DynamicFieldHelper.createFieldSelectStringHeqF('serverOnline', true,
        {defaultValue: GTNetServerOnlineStatusTypes[GTNetServerOnlineStatusTypes.SOS_UNKNOWN], fieldsetName: this.BASE_SETTING, disabled: isUpdate}),

      DynamicFieldHelper.createFieldCheckboxHeqF('acceptEntityRequest', {
        defaultValue: true,
        fieldsetName: this.ENTITY,
        disabled: isUpdate
      }),
      DynamicFieldHelper.createFieldSelectStringHeqF('entityServerState', true,
        {defaultValue: GTNetServerStateTypes[GTNetServerStateTypes.SS_OPEN], fieldsetName: this.ENTITY, disabled: isUpdate}),
      DynamicFieldHelper.createFieldSelectStringHeqF('lastpriceServerState', true,
        {defaultValue: GTNetServerStateTypes[GTNetServerStateTypes.SS_NONE], fieldsetName: this.LAST_PRICE, disabled: isUpdate})
      ]: [];
  }

  protected override initialize(): void {
    this.gps.getTimezones().subscribe((timezones: ValueKeyHtmlSelectOptions[]) => {
      this.configObject.timeZone.valueKeyHtmlOptions = timezones;
      this.configObject.entityServerState.valueKeyHtmlOptions = SelectOptionsHelper.createHtmlOptionsFromEnum(this.translateService,
        GTNetServerStateTypes);
      this.configObject.lastpriceServerState.valueKeyHtmlOptions = this.configObject.entityServerState.valueKeyHtmlOptions;
      this.configObject.serverOnline.valueKeyHtmlOptions = SelectOptionsHelper.createHtmlOptionsFromEnum(this.translateService,
        GTNetServerOnlineStatusTypes);
      if(this.callParam) {
        this.form.transferBusinessObjectToForm(this.callParam.gtNet);
      }
    });
  }

  protected override getNewOrExistingInstanceBeforeSave(value: { [name: string]: any }): GTNet {
    const gtNet: GTNet = this.copyFormToPrivateBusinessObject(new GTNet(), this.callParam.gtNet);
    return gtNet;
  }

  override onHide(event): void {
    this.domainRemoteNameSubscribe && this.domainRemoteNameSubscribe.unsubscribe();
    super.onHide(event);
  }


}
