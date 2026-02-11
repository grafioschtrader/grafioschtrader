import {Component, Input, OnInit} from '@angular/core';
import {GTNetMessageCodeType, MessageVisibility, MsgCallParam, shouldShowWaitDaysApply} from '../model/gtnet.message';
import {TranslateModule, TranslateService} from '@ngx-translate/core';
import {GlobalparameterService} from '../../services/globalparameter.service';
import {MessageToastService} from '../../message/message.toast.service';
import {HelpIds} from '../../help/help.ids';
import {AppHelper} from '../../helper/app.helper';
import {ClassDescriptorInputAndShow} from '../../dynamicfield/field.descriptor.input.and.show';
import {DynamicFieldHelper} from '../../helper/dynamic.field.helper';
import {TranslateHelper} from '../../helper/translate.helper';
import {Subscription} from 'rxjs';
import {FieldConfig} from '../../dynamic-form/models/field.config';
import {SelectOptionsHelper} from '../../helper/select.options.helper';
import {DynamicFieldModelHelper} from '../../helper/dynamic.field.model.helper';
import {BaseParam} from '../../entities/base.param';
import {Helper} from '../../helper/helper';
import {SimpleEditBase} from '../../edit/simple.edit.base';
import {InfoLevelType} from '../../message/info.leve.type';
import {ProcessedActionData} from '../../types/processed.action.data';
import {ProcessedAction} from '../../types/processed.action';
import {GTNetService} from '../service/gtnet.service';
import {GTNetWithMessages, MsgRequest} from '../model/gtnet';
import {MultiTargetMsgRequest} from '../model/multi-target-msg-request';
import {BaseSettings} from '../../base.settings';
import {DialogModule} from 'primeng/dialog';
import {DynamicFormComponent} from '../../dynamic-form/containers/dynamic-form/dynamic-form.component';

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
    <p-dialog header="{{'GT_NET_MESSAGE_SEND' | translate}}" [visible]="visibleDialog"
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

      // If only one response code available, auto-select it and disable the dropdown
      if (this.msgCallParam.validResponseCodes.length === 1) {
        const singleCode = this.msgCallParam.validResponseCodes[0];
        const codeKey = GTNetMessageCodeType[singleCode];
        this.configObject[this.MESSAGE_CODE].formControl.setValue(codeKey);
        this.configObject[this.MESSAGE_CODE].formControl.disable();
        // Show waitDaysApply only if configured for this message type
        this.configObject.waitDaysApply.invisible = !shouldShowWaitDaysApply(singleCode);
      } else {
        // Multiple response codes - waitDaysApply visibility will be updated when code is selected
        this.configObject.waitDaysApply.invisible = true;
      }
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
        this.updateWaitDaysApplyDisplay(messageCode);
      });
  }

  /**
   * Shows/hides the waitDaysApply field based on message code.
   * Only shown in response mode for message types that support delayed application.
   */
  private updateWaitDaysApplyDisplay(messageCode: GTNetMessageCodeType | string): void {
    // Only relevant in response mode
    if (!this.msgCallParam.validResponseCodes?.length) {
      return;
    }
    this.configObject.waitDaysApply.invisible = !shouldShowWaitDaysApply(messageCode);
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

    // Preserve current visibility state before recreating form controls
    const currentVisibility = this.configObject?.[this.VISIBILITY]?.formControl?.value;
    const visibilityWasDisabled = this.configObject?.[this.VISIBILITY]?.formControl?.disabled ?? false;
    const visibilityWasInvisible = this.configObject?.[this.VISIBILITY]?.invisible ?? true;
    const visibilityOptions = this.configObject?.[this.VISIBILITY]?.valueKeyHtmlOptions;

    // Keep messageCode, visibility, waitDaysApply (first 3), add dynamic fields, then message and submit (last 2)
    this.config = [...this.config.slice(0, 3), ...fieldConfig, ...this.config.slice(-2)];
    this.configObject = TranslateHelper.prepareFieldsAndErrors(this.translateService, this.config);

    // Restore visibility field state after form recreation
    if (visibilityOptions) {
      this.configObject[this.VISIBILITY].valueKeyHtmlOptions = visibilityOptions;
    }
    if (currentVisibility != null) {
      this.configObject[this.VISIBILITY].formControl.setValue(currentVisibility);
    }
    if (visibilityWasDisabled) {
      this.configObject[this.VISIBILITY].formControl.disable();
    }
    this.configObject[this.VISIBILITY].invisible = visibilityWasInvisible;

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
    // Get messageCode from form control directly (disabled controls are excluded from value)
    const messageCode = value[this.MESSAGE_CODE] ?? this.configObject[this.MESSAGE_CODE].formControl.value;

    // Include visibility - get from form control if disabled (disabled controls are excluded from value)
    const visibility = value[this.VISIBILITY] ?? this.configObject[this.VISIBILITY].formControl.value;
    const gtNetMessageParamMap = this.getMessageParam(value);

    // Multi-target mode: use submitMsgToMultiple for batch delivery
    if (this.msgCallParam.targetIds?.length > 0) {
      const multiRequest = new MultiTargetMsgRequest(this.msgCallParam.targetIds, value.message, visibility);
      multiRequest.gtNetMessageParamMap = gtNetMessageParamMap;

      this.gtNetService.submitMsgToMultiple(multiRequest).subscribe({
        next: (gtNetWithMessages: GTNetWithMessages) => {
          // Single target: sent synchronously, multiple targets: queued for background delivery
          if (this.msgCallParam.targetIds.length === 1) {
            this.messageToastService.showMessageI18n(InfoLevelType.SUCCESS, 'GT_NET_ADMIN_MESSAGE_SENT');
          } else {
            this.messageToastService.showMessageI18n(InfoLevelType.SUCCESS, 'GT_NET_ADMIN_MESSAGE_QUEUED',
              {count: this.msgCallParam.targetIds.length});
          }
          this.closeDialog.emit(new ProcessedActionData(ProcessedAction.CREATED, gtNetWithMessages));
        }, error: () => this.configObject.submit.disabled = false
      });
    } else {
      // Single-target or reply mode: use standard submitMsg
      const msgRequest = new MsgRequest(this.msgCallParam.idGTNet, this.msgCallParam.replyTo,
        messageCode, value.message);
      msgRequest.gtNetMessageParamMap = gtNetMessageParamMap;
      if (value.waitDaysApply != null) {
        msgRequest.waitDaysApply = value.waitDaysApply;
      }
      if (visibility != null && !this.configObject[this.VISIBILITY].invisible) {
        msgRequest.visibility = visibility;
      }
      this.gtNetService.submitMsg(msgRequest).subscribe({
        next: (gtNetWithMessages: GTNetWithMessages) => {
          this.messageToastService.showMessageI18n(InfoLevelType.SUCCESS, 'MSG_RECORD_SAVED', {i18nRecord: 'GT_NET_MESSAGE'});
          this.closeDialog.emit(new ProcessedActionData(ProcessedAction.CREATED, gtNetWithMessages));
        }, error: () => this.configObject.submit.disabled = false
      });
    }
  }

  private getMessageParam(value: { [name: string]: any }): { [key: string]: BaseParam } {
    const gtNetMessageParamMap: { [key: string]: BaseParam } = {};
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
