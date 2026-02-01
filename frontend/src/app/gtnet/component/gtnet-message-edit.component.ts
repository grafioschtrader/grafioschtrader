import {Component, Input, OnInit} from '@angular/core';
import {GTNetMessageCodeType, MessageVisibility, MsgCallParam} from '../model/gtnet.message';
import {TranslateModule, TranslateService} from '@ngx-translate/core';
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
import {DialogModule} from 'primeng/dialog';
import {DynamicFormComponent} from '../../lib/dynamic-form/containers/dynamic-form/dynamic-form.component';

/**
 * Crate a new GTNet message. A message can not be changed.
 */
@Component({
  selector: 'gtnet-message-edit',
  standalone: true,
  imports: [
    DialogModule,
    DynamicFormComponent,
    TranslateModule
  ],
  template: `
    <p-dialog header="{{'GT_NET_MESSAGE_SEND' | translate}}" [(visible)]="visibleDialog"
              [style]="{width: '500px'}"
              (onShow)="onShow($event)" (onHide)="onHide($event)" [modal]="true">

      <dynamic-form [config]="config" [formConfig]="formConfig" [translateService]="translateService"
                    #form="dynamicForm"
                    (submitBt)="submit($event)">
      </dynamic-form>
    </p-dialog>
  `
})
export class GTNetMessageEditComponent extends SimpleEditBase implements OnInit {
  @Input() msgCallParam: MsgCallParam;
  private readonly MESSAGE_CODE = 'messageCode';
  private readonly VISIBILITY = 'visibility';
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
      DynamicFieldHelper.createFieldSelectStringHeqF(this.VISIBILITY, false, {invisible: true}),
      DynamicFieldHelper.createFieldInputNumberHeqF('waitDaysApply', false, 4, 0, false, {invisible: true}),
      DynamicFieldHelper.createFieldTextareaInputStringHeqF('message', BaseSettings.FID_MAX_LETTERS, false),
      DynamicFieldHelper.createSubmitButton('SEND')
    ];
    this.configObject = TranslateHelper.prepareFieldsAndErrors(this.translateService, this.config);
  }

  protected override initialize(): void {
    this.configObject[this.MESSAGE_CODE].formControl.enable();
    this.valueChangedOnMessageCode();
    this.initializeVisibilityField();
    if (this.msgCallParam.validResponseCodes?.length) {
      // Response mode: only show valid response codes for the request
      this.configObject[this.MESSAGE_CODE].valueKeyHtmlOptions = SelectOptionsHelper.createHtmlOptionsFromEnum(
        this.translateService, GTNetMessageCodeType,
        this.msgCallParam.validResponseCodes.map(code => GTNetMessageCodeType[code]), false);
      // Show waitDaysApply field in response mode
      this.configObject.waitDaysApply.invisible = false;
    } else if (this.msgCallParam.gtNetMessage) {
      this.configObject[this.MESSAGE_CODE].valueKeyHtmlOptions = SelectOptionsHelper.createHtmlOptionsFromEnum(this.translateService,
        GTNetMessageCodeType, [GTNetMessageCodeType[this.msgCallParam.gtNetMessage.messageCode]], false);
      this.configObject[this.MESSAGE_CODE].formControl.setValue(this.msgCallParam.gtNetMessage[this.MESSAGE_CODE]);
    } else {
      // Filter message codes: ALL messages for broadcast mode, SEL messages for single target mode
      const filterSuffixes = this.msgCallParam.isAllMessage ? ['_ALL_C']: ['_SEL_RR_C', 'SEL_C', '_ALL_C'];
      // Exclude discontinued option if one is already open
      const excludeCodes: string[] = this.msgCallParam.idOpenDiscontinuedMessage != null
        ? ['GT_NET_OPERATION_DISCONTINUED_ALL_C'] : [];
      // Exclude admin messages when called from GTNetSetupTableComponent
      if (this.msgCallParam.excludeAdminMessages) {
        excludeCodes.push('GT_NET_ADMIN_MESSAGE_SEL_C', 'GT_NET_ADMIN_MESSAGE_ALL_C');
      }
      this.configObject[this.MESSAGE_CODE].valueKeyHtmlOptions =
        SelectOptionsHelper.createHtmlOptionsFromEnum(this.translateService, GTNetMessageCodeType,
          Object.keys(GTNetMessageCodeType).filter(
              key => !isNaN(Number(GTNetMessageCodeType[key])) &&
                filterSuffixes.some(suffix => key.endsWith(suffix)) &&
                !excludeCodes.includes(key)
            ).map(k => GTNetMessageCodeType[k]),false);

      // Pre-select message code if provided (e.g., from admin messages component)
      if (this.msgCallParam.preselectedMessageCode != null) {
        const codeKey = GTNetMessageCodeType[this.msgCallParam.preselectedMessageCode];
        this.configObject[this.MESSAGE_CODE].formControl.setValue(codeKey);
      }
    }
  }

  /**
   * Initializes the visibility field based on context:
   * - New thread (no replyTo): Show visibility dropdown with ALL_USERS and ADMIN_ONLY options
   * - Reply to ADMIN_ONLY thread: Force to ADMIN_ONLY, field disabled
   * - Reply to ALL_USERS thread: Show dropdown, default to ALL_USERS
   */
  private initializeVisibilityField(): void {
    // Set up visibility options
    this.configObject[this.VISIBILITY].valueKeyHtmlOptions = SelectOptionsHelper.createHtmlOptionsFromEnum(
      this.translateService, MessageVisibility,
      [MessageVisibility[MessageVisibility.ALL_USERS], MessageVisibility[MessageVisibility.ADMIN_ONLY]], false);

    if (this.msgCallParam.replyTo != null && this.msgCallParam.parentVisibility != null) {
      // Reply mode - check parent visibility
      if (this.msgCallParam.parentVisibility === MessageVisibility.ADMIN_ONLY) {
        // Parent is ADMIN_ONLY: force reply to ADMIN_ONLY, disable field
        this.configObject[this.VISIBILITY].formControl.setValue(MessageVisibility[MessageVisibility.ADMIN_ONLY]);
        this.configObject[this.VISIBILITY].formControl.disable();
        this.configObject[this.VISIBILITY].invisible = false;
      } else {
        // Parent is ALL_USERS: allow choice, default to ALL_USERS
        this.configObject[this.VISIBILITY].formControl.setValue(MessageVisibility[MessageVisibility.ALL_USERS]);
        this.configObject[this.VISIBILITY].formControl.enable();
        this.configObject[this.VISIBILITY].invisible = false;
      }
    } else {
      // New thread - default to ADMIN_ONLY for admin messages, visibility shown when admin message selected
      this.configObject[this.VISIBILITY].formControl.setValue(MessageVisibility[MessageVisibility.ADMIN_ONLY]);
      this.configObject[this.VISIBILITY].formControl.enable();
      // Visibility will be shown/hidden based on message code selection
    }
  }

  private valueChangedOnMessageCode(): void {
    this.messageCodeSubscription = this.configObject[this.MESSAGE_CODE].formControl.valueChanges.subscribe(
      (messageCode: GTNetMessageCodeType) => {
        this.createViewFromSelectedEnum(messageCode);
        this.updateVisibilityFieldDisplay(messageCode);
      });
  }

  /**
   * Shows the visibility field for admin message codes, hides it otherwise.
   * Admin message codes: GT_NET_ADMIN_MESSAGE_SEL_C, GT_NET_ADMIN_MESSAGE_ALL_C
   */
  private updateVisibilityFieldDisplay(messageCode: GTNetMessageCodeType | string): void {
    // Don't override visibility display if we're in reply mode (already handled)
    if (this.msgCallParam.replyTo != null && this.msgCallParam.parentVisibility != null) {
      return;
    }
    const codeName = typeof messageCode === 'string' ? messageCode : GTNetMessageCodeType[messageCode];
    const isAdminMessage = codeName?.startsWith('GT_NET_ADMIN_MESSAGE') ?? false;
    this.configObject[this.VISIBILITY].invisible = !isAdminMessage;
  }

  private createViewFromSelectedEnum(gtNetMessageCodeType: string | GTNetMessageCodeType): void {
    this.classDescriptorInputAndShows = this.msgCallParam.formDefinitions[gtNetMessageCodeType];
    this.createDynamicInputFields();
  }

  private createDynamicInputFields(): void {
    const fieldConfig: FieldConfig[] = <FieldConfig[]>DynamicFieldModelHelper.createFieldsFromClassDescriptorInputAndShow(
      this.translateService, this.classDescriptorInputAndShows,
      '', false);

    // Keep messageCode, visibility, waitDaysApply (first 3), add dynamic fields, then message and submit (last 2)
    this.config = [...this.config.slice(0, 3), ...fieldConfig, ...this.config.slice(-2)];
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
    if (value.waitDaysApply != null) {
      msgRequest.waitDaysApply = value.waitDaysApply;
    }
    // Include visibility if field is visible (admin messages)
    if (value[this.VISIBILITY] != null) {
      msgRequest.visibility = value[this.VISIBILITY];
    }
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
    this.classDescriptorInputAndShows && this.classDescriptorInputAndShows.fieldDescriptorInputAndShows.forEach(fDIAS => {
      let paramValue = valuesFlatten[fDIAS.fieldName];
      // Convert arrays (from MultiSelect/EnumSet) to comma-separated string for backend storage
      if (Array.isArray(paramValue)) {
        paramValue = paramValue.join(',');
      }
      gtNetMessageParamMap[fDIAS.fieldName] = new BaseParam(paramValue);
    });
    return gtNetMessageParamMap;
  }

  override onHide(event): void {
    this.messageCodeSubscription && this.messageCodeSubscription.unsubscribe();
    super.onHide(event);
  }
}
