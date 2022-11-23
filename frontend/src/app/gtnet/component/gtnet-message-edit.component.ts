import {Component, Input, OnInit} from '@angular/core';
import {SimpleEntityEditBase} from '../../shared/edit/simple.entity.edit.base';
import {GTNetMessage, GTNetMessageCodeType, MsgCallParam, SendReceivedType} from '../model/gtnet.message';
import {TranslateService} from '@ngx-translate/core';
import {GlobalparameterService} from '../../shared/service/globalparameter.service';
import {MessageToastService} from '../../shared/message/message.toast.service';
import {HelpIds} from '../../shared/help/help.ids';
import {AppSettings} from '../../shared/app.settings';
import {AppHelper} from '../../shared/helper/app.helper';
import {GTNetMessageService} from '../service/gtnet.message.service';
import {ClassDescriptorInputAndShow} from '../../shared/dynamicfield/field.descriptor.input.and.show';
import {DynamicFieldHelper} from '../../shared/helper/dynamic.field.helper';
import {TranslateHelper} from '../../shared/helper/translate.helper';
import {Subscription} from 'rxjs';
import {FieldConfig} from '../../dynamic-form/models/field.config';
import {SelectOptionsHelper} from '../../shared/helper/select.options.helper';
import {DynamicFieldModelHelper} from '../../shared/helper/dynamic.field.model.helper';
import {FieldFormGroup} from '../../dynamic-form/models/form.group.definition';
import {BaseParam} from '../../entities/view/base.param';
import {Helper} from '../../helper/helper';

/**
 * Crate a new GTNet message. A message can not be changed.
 */
@Component({
  selector: 'gtnet-message-edit',
  template: `
    <p-dialog header="{{i18nRecord | translate}}" [(visible)]="visibleDialog"
              [responsive]="true" [style]="{width: '500px'}"
              (onShow)="onShow($event)" (onHide)="onHide($event)" [modal]="true">

      <dynamic-form [config]="config" [formConfig]="formConfig" [translateService]="translateService"
                    #form="dynamicForm"
                    (submitBt)="submit($event)">
      </dynamic-form>
    </p-dialog>
  `
})
export class GTNetMessageEditComponent extends SimpleEntityEditBase<GTNetMessage> implements OnInit {
  @Input() msgCallParam: MsgCallParam;
  private readonly MESSAGE_CODE = 'messageCode';
  private classDescriptorInputAndShows: ClassDescriptorInputAndShow;
  messageCodeSubscription: Subscription;

  constructor(translateService: TranslateService,
              gps: GlobalparameterService,
              messageToastService: MessageToastService,
              gtNetMessageService: GTNetMessageService) {
    super(HelpIds.HELP_GTNET, AppSettings.GTNETMESSAGE.toUpperCase(), translateService, gps,
      messageToastService, gtNetMessageService);
  }

  ngOnInit(): void {
    this.formConfig = AppHelper.getDefaultFormConfig(this.gps,
      4, this.helpLink.bind(this));
    this.config = [
      DynamicFieldHelper.createFieldSelectStringHeqF(this.MESSAGE_CODE, true),
      DynamicFieldHelper.createFieldTextareaInputStringHeqF('message',  AppSettings.FID_MAX_LETTERS, false),
      DynamicFieldHelper.createSubmitButton()
    ];
    this.configObject = TranslateHelper.prepareFieldsAndErrors(this.translateService, this.config);
  }

  protected override initialize(): void {
    this.configObject[this.MESSAGE_CODE].formControl.enable();
    this.valueChangedOnMessageCode();
    if (this.msgCallParam.gtNetMessage) {
      this.configObject[this.MESSAGE_CODE].valueKeyHtmlOptions = SelectOptionsHelper.createHtmlOptionsFromEnum(this.translateService,
        GTNetMessageCodeType, [GTNetMessageCodeType[this.msgCallParam.gtNetMessage.messageCode]], false);
      this.configObject[this.MESSAGE_CODE].formControl.setValue(this.msgCallParam.gtNetMessage[this.MESSAGE_CODE]);
    } else {
      this.configObject[this.MESSAGE_CODE].valueKeyHtmlOptions = SelectOptionsHelper.createHtmlOptionsFromEnum(this.translateService,
        GTNetMessageCodeType, Object.keys(GTNetMessageCodeType).filter(key => !isNaN(Number(GTNetMessageCodeType[key]))
          && key.endsWith('_C')).map(k => GTNetMessageCodeType[k]), false);
    }
  }

  private valueChangedOnMessageCode(): void {
    this.messageCodeSubscription = this.configObject[this.MESSAGE_CODE].formControl.valueChanges.subscribe((messageCode: GTNetMessageCodeType) => {
      this.createViewFromSelectedEnum(messageCode);
    });
  }

  private createViewFromSelectedEnum(gtNetMessageCodeType: string | GTNetMessageCodeType): void {
    this.classDescriptorInputAndShows = this.msgCallParam.formDefinitions[gtNetMessageCodeType];
    this.createDynamicInputFields();
  }


  private createDynamicInputFields(): void {
    const fieldConfig: FieldConfig[] = <FieldConfig[]> DynamicFieldModelHelper.createFieldsFromClassDescriptorInputAndShow(
      this.classDescriptorInputAndShows,
      '', false);

    this.config = [this.config[0], ...fieldConfig, ...this.config.slice(-2)];
    this.configObject = TranslateHelper.prepareFieldsAndErrors(this.translateService, this.config);

    if (this.msgCallParam.gtNetMessage) {
      setTimeout(() => this.setExistingModel());
    }
  }


  private setExistingModel(): void {
    const dynamicModel = DynamicFieldModelHelper.createAndSetValuesInDynamicModel(
      this.msgCallParam.formDefinitions,
      this.MESSAGE_CODE,
      this.msgCallParam.gtNetMessage.gtNetMessageParamMap,
      this.classDescriptorInputAndShows.fieldDescriptorInputAndShows, true);
    this.form.transferBusinessObjectToForm(dynamicModel);
  }

  protected override getNewOrExistingInstanceBeforeSave(value: { [name: string]: any }): GTNetMessage {
    const gtNetMessage: GTNetMessage = new GTNetMessage();
    this.form.cleanMaskAndTransferValuesToBusinessObject(gtNetMessage);
    gtNetMessage.idGtNet = this.msgCallParam.idsGTNet[0];
    gtNetMessage.sendRecv = SendReceivedType.SEND;
    gtNetMessage.gtNetMessageParamMap = {};
    const valuesFlatten = Helper.flattenObject(value);
    this.classDescriptorInputAndShows.fieldDescriptorInputAndShows.forEach(fDIAS =>
      gtNetMessage.gtNetMessageParamMap[fDIAS.fieldName] = new BaseParam(valuesFlatten[fDIAS.fieldName]));
    return gtNetMessage;
  }

  override onHide(event): void {
    this.messageCodeSubscription && this.messageCodeSubscription.unsubscribe();
    super.onHide(event);
  }
}
