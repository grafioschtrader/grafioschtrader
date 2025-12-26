import {Component, Input, OnInit} from '@angular/core';
import {SimpleEntityEditBase} from '../../lib/edit/simple.entity.edit.base';
import {
  getResponseCodesForRequest,
  GTNetMessageAnswer,
  GTNetMessageAnswerCallParam,
  REQUEST_CODES_FOR_AUTO_RESPONSE
} from '../model/gtnet.message.answer';
import {TranslateModule, TranslateService} from '@ngx-translate/core';
import {GlobalparameterService} from '../../lib/services/globalparameter.service';
import {MessageToastService} from '../../lib/message/message.toast.service';
import {HelpIds} from '../../lib/help/help.ids';
import {AppSettings} from '../../shared/app.settings';
import {GTNetMessageAnswerService} from '../service/gtnet.message.answer.service';
import {AppHelper} from '../../lib/helper/app.helper';
import {DynamicFieldHelper} from '../../lib/helper/dynamic.field.helper';
import {Subscription} from 'rxjs';
import {TranslateHelper} from '../../lib/helper/translate.helper';
import {SelectOptionsHelper} from '../../lib/helper/select.options.helper';
import {DataType} from '../../lib/dynamic-form/models/data.type';
import {DialogModule} from 'primeng/dialog';
import {DynamicFormComponent} from '../../lib/dynamic-form/containers/dynamic-form/dynamic-form.component';
import {GTNetMessageCodeType} from '../model/gtnet.message';

/**
 * Edit component for GTNetMessageAnswer entities.
 * Allows configuration of automatic response rules for incoming GTNet messages.
 */
@Component({
  selector: 'gtnet-message-answer-edit',
  standalone: true,
  imports: [
    DialogModule,
    DynamicFormComponent,
    TranslateModule
  ],
  template: `
    <p-dialog header="{{'GT_NET_MESSAGE_ANSWER' | translate}}" [(visible)]="visibleDialog"
              [style]="{width: '600px'}"
              (onShow)="onShow($event)" (onHide)="onHide($event)" [modal]="true">

      <dynamic-form [config]="config" [formConfig]="formConfig" [translateService]="translateService"
                    #form="dynamicForm"
                    (submitBt)="submit($event)">
      </dynamic-form>
    </p-dialog>
  `
})
export class GTNetMessageAnswerEditComponent extends SimpleEntityEditBase<GTNetMessageAnswer> implements OnInit {
  @Input() callParam: GTNetMessageAnswerCallParam;

  private requestMsgCodeSubscription: Subscription;

  constructor(translateService: TranslateService,
    gps: GlobalparameterService,
    messageToastService: MessageToastService,
    gtNetMessageAnswerService: GTNetMessageAnswerService) {
    super(HelpIds.HELP_GT_NET, AppSettings.GT_NET_MESSAGE_ANSWER.toUpperCase(), translateService, gps,
      messageToastService, gtNetMessageAnswerService);
  }

  ngOnInit(): void {
    this.formConfig = AppHelper.getDefaultFormConfig(this.gps, 4, this.helpLink.bind(this));
    this.config = [
      DynamicFieldHelper.createFieldSelectStringHeqF('requestMsgCode', true),
      DynamicFieldHelper.createFieldSelectStringHeqF('responseMsgCode', true),
      DynamicFieldHelper.createFieldMinMaxNumberHeqF(DataType.NumericInteger, 'priority', true, 1, 99,
        {defaultValue: 1}),
      DynamicFieldHelper.createFieldTextareaInputStringHeqF('responseMsgConditional', 256, false,
        {inputWidth: 500}),
      DynamicFieldHelper.createFieldTextareaInputStringHeqF('responseMsgMessage', 1000, false,
        {inputWidth: 500}),
      DynamicFieldHelper.createFieldMinMaxNumberHeqF(DataType.NumericInteger, 'waitDaysApply', true, 0, 365,
        {defaultValue: 0}),
      DynamicFieldHelper.createSubmitButton()
    ];
    this.configObject = TranslateHelper.prepareFieldsAndErrors(this.translateService, this.config);
  }

  protected override initialize(): void {
    // Setup request code options (only RR codes that support auto-response)
    this.configObject.requestMsgCode.valueKeyHtmlOptions = SelectOptionsHelper.createHtmlOptionsFromEnum(
      this.translateService, GTNetMessageCodeType,
      REQUEST_CODES_FOR_AUTO_RESPONSE.map(code => GTNetMessageCodeType[code]), false);

    // Setup response code options (empty initially, updated when request code changes)
    this.configObject.responseMsgCode.valueKeyHtmlOptions = [];

    // Subscribe to request code changes to update response code options
    this.requestMsgCodeSubscription = this.configObject.requestMsgCode.formControl.valueChanges.subscribe(
      (requestCode: string) => {
        this.updateResponseCodeOptions(requestCode);
      }
    );

    // Load existing entity or set defaults
    const entity = this.callParam?.gtNetMessageAnswer ?? new GTNetMessageAnswer();
    if (entity.requestMsgCode) {
      // Convert numeric code to string name for form
      const requestCodeName = typeof entity.requestMsgCode === 'number'
        ? GTNetMessageCodeType[entity.requestMsgCode]
        : entity.requestMsgCode;
      this.updateResponseCodeOptions(requestCodeName as string);
    }
    this.form.transferBusinessObjectToForm(entity);
  }

  protected override getNewOrExistingInstanceBeforeSave(value: { [name: string]: any }): GTNetMessageAnswer {
    const entity = new GTNetMessageAnswer();
    if (this.callParam?.gtNetMessageAnswer) {
      Object.assign(entity, this.callParam.gtNetMessageAnswer);
    }

    // Convert string enum names back to numeric values
    entity.requestMsgCode = GTNetMessageCodeType[value['requestMsgCode'] as keyof typeof GTNetMessageCodeType];
    entity.responseMsgCode = GTNetMessageCodeType[value['responseMsgCode'] as keyof typeof GTNetMessageCodeType];
    entity.priority = value['priority'];
    entity.responseMsgConditional = value['responseMsgConditional'] || null;
    entity.responseMsgMessage = value['responseMsgMessage'] || null;
    entity.waitDaysApply = value['waitDaysApply'];

    return entity;
  }

  override onHide(event): void {
    this.requestMsgCodeSubscription?.unsubscribe();
    super.onHide(event);
  }

  /**
   * Updates the response code options based on the selected request code.
   */
  private updateResponseCodeOptions(requestCodeName: string): void {
    if (!requestCodeName) {
      this.configObject.responseMsgCode.valueKeyHtmlOptions = [];
      return;
    }

    const requestCode = GTNetMessageCodeType[requestCodeName as keyof typeof GTNetMessageCodeType];
    const responseCodes = getResponseCodesForRequest(requestCode);

    this.configObject.responseMsgCode.valueKeyHtmlOptions = SelectOptionsHelper.createHtmlOptionsFromEnum(
      this.translateService, GTNetMessageCodeType,
      responseCodes.map(code => GTNetMessageCodeType[code]), false);
  }
}
