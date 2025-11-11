import {Component, Input, OnInit} from '@angular/core';
import {GTNetMessageCodeType, MsgCallParam} from '../model/gtnet.message';
import {TranslateService} from '@ngx-translate/core';
import {GlobalparameterService} from '../../lib/services/globalparameter.service';
import {MessageToastService} from '../../lib/message/message.toast.service';
import {HelpIds} from '../../lib/help/help.ids';
import {AppHelper} from '../../lib/helper/app.helper';
import {ClassDescriptorInputAndShow} from '../../lib/dynamicfield/field.descriptor.input.and.show';
import {DynamicFieldHelper} from '../../lib/helper/dynamic.field.helper';
import {TranslateHelper} from '../../lib/helper/translate.helper';
import {Subscription} from 'rxjs';
import {FieldConfig} from '../../lib/dynamic-form/models/field.config';
import {SelectOptionsHelper} from '../../lib/helper/select.options.helper';
import {DynamicFieldModelHelper} from '../../lib/helper/dynamic.field.model.helper';
import {BaseParam} from '../../lib/entities/base.param';
import {Helper} from '../../lib/helper/helper';
import {SimpleEditBase} from '../../lib/edit/simple.edit.base';
import {InfoLevelType} from '../../lib/message/info.leve.type';
import {ProcessedActionData} from '../../lib/types/processed.action.data';
import {ProcessedAction} from '../../lib/types/processed.action';
import {GTNetService} from '../service/gtnet.service';
import {GTNetWithMessages, MsgRequest} from '../model/gtnet';
import {BaseSettings} from '../../lib/base.settings';

/**
 * Crate a new GTNet message. A message can not be changed.
 */
@Component({
  selector: 'gtnet-message-edit',
  template: `
    <p-dialog header="{{'GT_NET_MESSAGE_SEND' | translate}}" [(visible)]="visibleDialog"
              [style]="{width: '500px'}"
              (onShow)="onShow($event)" (onHide)="onHide($event)" [modal]="true">

      <dynamic-form [config]="config" [formConfig]="formConfig" [translateService]="translateService"
                    #form="dynamicForm"
                    (submitBt)="submit($event)">
      </dynamic-form>
    </p-dialog>
  `,
  standalone: false
})
export class GTNetMessageEditComponent extends SimpleEditBase implements OnInit {
  @Input() msgCallParam: MsgCallParam;
  private readonly MESSAGE_CODE = 'messageCode';
  private classDescriptorInputAndShows: ClassDescriptorInputAndShow;
  messageCodeSubscription: Subscription;

  constructor(public translateService: TranslateService,
    private messageToastService: MessageToastService,
    private gtNetService: GTNetService,
    gps: GlobalparameterService) {
    super(HelpIds.HELP_GT_NET, gps);
  }

  ngOnInit(): void {
    this.formConfig = AppHelper.getDefaultFormConfig(this.gps,
      4, this.helpLink.bind(this));
    this.config = [
      DynamicFieldHelper.createFieldSelectStringHeqF(this.MESSAGE_CODE, true),
      DynamicFieldHelper.createFieldTextareaInputStringHeqF('message', BaseSettings.FID_MAX_LETTERS, false),
      DynamicFieldHelper.createSubmitButton('SEND')
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
    this.messageCodeSubscription = this.configObject[this.MESSAGE_CODE].formControl.valueChanges.subscribe(
      (messageCode: GTNetMessageCodeType) => {
        this.createViewFromSelectedEnum(messageCode);
      });
  }

  private createViewFromSelectedEnum(gtNetMessageCodeType: string | GTNetMessageCodeType): void {
    this.classDescriptorInputAndShows = this.msgCallParam.formDefinitions[gtNetMessageCodeType];
    this.createDynamicInputFields();
  }

  private createDynamicInputFields(): void {
    const fieldConfig: FieldConfig[] = <FieldConfig[]>DynamicFieldModelHelper.createFieldsFromClassDescriptorInputAndShow(
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

  submit(value: { [name: string]: any }): void {
    const msgRequest = new MsgRequest(this.msgCallParam.idGTNet, this.msgCallParam.replyTo,
      value[this.MESSAGE_CODE], value.message,);
    msgRequest.gtNetMessageParamMap = this.getMessageParam(value);
    this.gtNetService.submitMsg(msgRequest).subscribe({
      next: (gtNetWithMessages: GTNetWithMessages) => {
        this.messageToastService.showMessageI18n(InfoLevelType.SUCCESS, 'MSG_RECORD_SAVED', {i18nRecord: 'GT_NET_MESSAGE'});
        this.closeDialog.emit(new ProcessedActionData(ProcessedAction.CREATED, gtNetWithMessages));
      }, error: () => this.configObject.submit.disabled = false
    });
  }

  private getMessageParam(value: { [name: string]: any }): Map<string, BaseParam> | { [key: string]: BaseParam } {
    const gtNetMessageParamMap: Map<string, BaseParam> | { [key: string]: BaseParam } = {};
    const valuesFlatten = Helper.flattenObject(value);
    this.classDescriptorInputAndShows && this.classDescriptorInputAndShows.fieldDescriptorInputAndShows.forEach(fDIAS =>
      gtNetMessageParamMap[fDIAS.fieldName] = new BaseParam(valuesFlatten[fDIAS.fieldName]));
    return gtNetMessageParamMap;
  }

  override onHide(event): void {
    this.messageCodeSubscription && this.messageCodeSubscription.unsubscribe();
    super.onHide(event);
  }
}
