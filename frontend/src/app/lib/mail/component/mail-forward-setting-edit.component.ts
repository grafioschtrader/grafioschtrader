import {Component, Input, OnInit} from '@angular/core';
import {CommonModule} from '@angular/common';
import {SimpleEntityEditBase} from '../../edit/simple.entity.edit.base';
import {
  MailSendForwardDefaultConfig,
  MailSettingForward,
  MailSettingForwardParam,
  MailSettingForwardVar,
  MessageComType,
  MessageTargetType
} from '../model/mail.send.recv';
import {TranslateModule, TranslateService} from '@ngx-translate/core';
import {DialogModule} from 'primeng/dialog';
import {DynamicFormModule} from '../../dynamic-form/dynamic-form.module';
import {GlobalparameterService} from '../../services/globalparameter.service';
import {MessageToastService} from '../../message/message.toast.service';
import {MailSettingForwardService} from '../service/mail.setting.forward.service';
import {AppHelper} from '../../helper/app.helper';
import {TranslateHelper} from '../../helper/translate.helper';
import {DynamicFieldHelper} from '../../helper/dynamic.field.helper';
import {Subscription} from 'rxjs';
import {SelectOptionsHelper} from '../../helper/select.options.helper';
import {FormHelper} from '../../dynamic-form/components/FormHelper';
import {ValueKeyHtmlSelectOptions} from '../../dynamic-form/models/value.key.html.select.options';
import {BaseSettings} from '../../base.settings';
import {HelpIds} from '../../help/help.ids';

/**
 * This component contains a form with which the message settings can be edited.
 */
@Component({
  selector: 'mail-forward-setting-edit',
  template: `
    <p-dialog header="{{i18nRecord | translate}}" [(visible)]="visibleDialog"
              [style]="{width: '500px'}"
              (onShow)="onShow($event)" (onHide)="onHide($event)" [modal]="true">

      <dynamic-form [config]="config" [formConfig]="formConfig" [translateService]="translateService"
                    #form="dynamicForm"
                    (submitBt)="submit($event)">
      </dynamic-form>
    </p-dialog>
  `,
  standalone: true,
  imports: [CommonModule, DialogModule, DynamicFormModule, TranslateModule]
})
export class MailForwardSettingEditComponent extends SimpleEntityEditBase<MailSettingForward> implements OnInit {
  @Input() callParam: MailSettingForwardParam;
  private messageComTypeSubscribe: Subscription;

  constructor(translateService: TranslateService,
    gps: GlobalparameterService,
    messageToastService: MessageToastService,
    mailSettingForwardService: MailSettingForwardService) {
    super(HelpIds.HELP_MESSAGE_SYSTEM, AppHelper.toUpperCaseWithUnderscore(BaseSettings.MAIL_SETTING_FORWARD),
      translateService, gps, messageToastService, mailSettingForwardService);
  }

  valueChangedOnMessageComType(): void {
    this.messageComTypeSubscribe = this.configObject[MailSettingForwardVar.MESSAGE_COM_TYPE].formControl.valueChanges.subscribe(mct => {
      const msfdc: MailSendForwardDefaultConfig = this.callParam.mailSendForwardDefault.mailSendForwardDefaultMapForUser[mct];
      this.configObject[MailSettingForwardVar.MESSAGE_TARGET_TYPE].valueKeyHtmlOptions =
        SelectOptionsHelper.createHtmlOptionsFromEnum(this.translateService, MessageTargetType, msfdc.mttPossibleTypeSet, false);

      if (!this.configObject[MailSettingForwardVar.ID_USER_DIRECT].invisible) {
        const disableIdUserRedirect = !(this.callParam.mailSendForwardDefault.canRedirectToUsers && msfdc.canRedirect);
        FormHelper.disableEnableFieldConfigs(disableIdUserRedirect, [this.configObject[MailSettingForwardVar.ID_USER_DIRECT]]);
        if (!disableIdUserRedirect) {
          this.configObject[MailSettingForwardVar.ID_USER_DIRECT].valueKeyHtmlOptions =
            [new ValueKeyHtmlSelectOptions(null, '')].concat(this.callParam.mailSendForwardDefault.canRedirectToUsers);
        }
      }
    });
  }

  ngOnInit(): void {
    this.formConfig = AppHelper.getDefaultFormConfig(this.gps,
      4, this.helpLink.bind(this));

    this.config = [
      DynamicFieldHelper.createFieldSelectStringHeqF(MailSettingForwardVar.MESSAGE_COM_TYPE, true),
      DynamicFieldHelper.createFieldSelectStringHeqF(MailSettingForwardVar.MESSAGE_TARGET_TYPE, true),
      DynamicFieldHelper.createFieldSelectStringHeqF(MailSettingForwardVar.ID_USER_DIRECT, false,
        {invisible: !this.gps.hasRole(BaseSettings.ROLE_ALL_EDIT)}),
      DynamicFieldHelper.createSubmitButton()
    ];
    this.configObject = TranslateHelper.prepareFieldsAndErrors(this.translateService, this.config);
  }

  protected override initialize(): void {
    this.valueChangedOnMessageComType();
    if (this.callParam.mailSettingForward) {
      this.form.transferBusinessObjectToForm(this.callParam.mailSettingForward);
    }
    this.configObject[MailSettingForwardVar.MESSAGE_COM_TYPE].valueKeyHtmlOptions = SelectOptionsHelper.createHtmlOptionsFromEnum(
      this.translateService, MessageComType, this.callParam.possibleMsgComType, false);
  }

  override onHide(event): void {
    this.messageComTypeSubscribe && this.messageComTypeSubscribe.unsubscribe();
    super.onHide(event);
  }

  protected override getNewOrExistingInstanceBeforeSave(value: { [name: string]: any }): MailSettingForward {
    const mailSettingForward = this.copyFormToPrivateBusinessObject(new MailSettingForward(), this.callParam.mailSettingForward);
    mailSettingForward.idUser = this.gps.getIdUser();
    return mailSettingForward;
  }

}
