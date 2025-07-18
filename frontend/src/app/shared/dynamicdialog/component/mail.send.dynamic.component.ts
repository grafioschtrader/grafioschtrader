import {Component, OnInit, ViewChild} from '@angular/core';
import {FormBase} from '../../../lib/edit/form.base';
import {AppHelper} from '../../../lib/helper/app.helper';
import {DynamicFieldHelper} from '../../../lib/helper/dynamic.field.helper';
import {InfoLevelType} from '../../../lib/message/info.leve.type';
import {BusinessHelper} from '../../helper/business.helper';
import {HelpIds} from '../../help/help.ids';
import {TranslateService} from '@ngx-translate/core';
import {GlobalparameterService} from '../../service/globalparameter.service';
import {MessageToastService} from '../../../lib/message/message.toast.service';
import {MailSendRecvService} from '../../../lib/mail/service/mail.send.recv.service';
import {TranslateHelper} from '../../../lib/helper/translate.helper';
import {FieldConfig} from '../../../dynamic-form/models/field.config';
import {SelectOptionsHelper} from '../../../lib/helper/select.options.helper';
import {AppSettings} from '../../app.settings';
import {DynamicFormComponent} from '../../../dynamic-form/containers/dynamic-form/dynamic-form.component';
import {DialogService, DynamicDialogConfig, DynamicDialogRef} from 'primeng/dynamicdialog';
import {MailSendRecv, ReplyToRolePrivateType} from '../../../lib/mail/model/mail.send.recv';
import {ValueKeyHtmlSelectOptions} from '../../../dynamic-form/models/value.key.html.select.options';
import {UserAdminService} from '../../../user/service/user.admin.service';

/**
 * This input form can be used to compose and send a message. It is a dynamic dialog, as it can be used in different places in the GUI.
 */
@Component({
    template: `
    <dynamic-form [config]="config" [formConfig]="formConfig" [translateService]="translateService" #form="dynamicForm"
                  (submitBt)="submit($event)">
    </dynamic-form>`,
    standalone: false
})
export class MailSendDynamicComponent extends FormBase implements OnInit {
  @ViewChild(DynamicFormComponent) form: DynamicFormComponent;

  private readonly NEW_LINE = '\n';
  private readonly USER_TO_ANY_USER_PARAM = -1;
  private readonly SUBJECT = 'subject';
  private readonly MESSAGE = 'message';
  private readonly ID_USER_TO = 'idUserTo';
  private readonly ID_USER_FROM = 'idUserFrom';
  private readonly ROLE_NAME_TO = 'roleNameTo';
  private mailSendParam: MailSendParam;
  private userToAnyUserOptions: ValueKeyHtmlSelectOptions[];

  constructor(public translateService: TranslateService,
              public userAdminService: UserAdminService,
              public sendRecvService: MailSendRecvService,
              public gps: GlobalparameterService,
              private messageToastService: MessageToastService,
              private dialogService: DialogService,
              private dynamicDialogRef: DynamicDialogRef,
              private dynamicDialogConfig: DynamicDialogConfig) {
    super();
  }

  ngOnInit(): void {
    this.formConfig = AppHelper.getDefaultFormConfig(this.gps,
      3, this.helpLink.bind(this));
    this.mailSendParam = this.dynamicDialogConfig.data.mailSendParam;
    if (this.mailSendParam?.idUserTo === this.USER_TO_ANY_USER_PARAM) {
      this.userAdminService.getIdUserAndNicknameExcludeMe().subscribe((vkhso: ValueKeyHtmlSelectOptions[]) => {
        this.userToAnyUserOptions = vkhso;
        this.initializeFields();
      });
    } else {
      this.initializeFields();
    }
  }

  private initializeFields(): void {
    this.config = [
      this.getUserRoleOrUserField(),
      DynamicFieldHelper.createFieldTextareaInputStringHeqF(this.SUBJECT, 96, true),
      DynamicFieldHelper.createFieldTextareaInputStringHeqF(this.MESSAGE, 4096, true,
        {textareaRows: 30}),
      ...this.getMarkAsPrivateWhenRoleMessage(),
      DynamicFieldHelper.createSubmitButton('SEND')
    ];
    this.configObject = TranslateHelper.prepareFieldsAndErrors(this.translateService, this.config);
    setTimeout(() => this.updateViewData());
  }


  submit(value: { [name: string]: any }): void {
    const mailSendRecv = new MailSendRecv();
    this.form.cleanMaskAndTransferValuesToBusinessObject(mailSendRecv, true);
    mailSendRecv.replyToRolePrivate = value.replyToRolePrivate ? ReplyToRolePrivateType.REPLY_IS_PRIVATE :
      ReplyToRolePrivateType.REPLY_NORMAL;
    mailSendRecv.idUserFrom = this.gps.getIdUser();
    mailSendRecv.idReplyToLocal = this.mailSendParam.mailSendRecv?.idReplyToLocal ? this.mailSendParam.mailSendRecv.idReplyToLocal :
      this.mailSendParam.mailSendRecv ? this.mailSendParam.mailSendRecv.idMailSendRecv : null;
    this.sendRecvService.sendMessage(mailSendRecv).subscribe({
      next: (mailSendRecvRc: MailSendRecv) => {
        this.messageToastService.showMessageI18n(InfoLevelType.SUCCESS, 'MSG_PROPOSE_SAVED');
        this.dynamicDialogRef.close(mailSendRecvRc);
      }, error: () => this.configObject.submit.disabled = false
    });
  }

  helpLink(): void {
    BusinessHelper.toExternalHelpWebpage(this.gps.getUserLang(), HelpIds.HELP_USER);
  }

  private getUserRoleOrUserField(): FieldConfig {
    if (this.mailSendParam.idUserTo) {
      if (this.mailSendParam.idUserTo === this.USER_TO_ANY_USER_PARAM) {
        return DynamicFieldHelper.createFieldSelectStringHeqF(this.ID_USER_TO, true);
      } else {
        return DynamicFieldHelper.createFieldInputStringHeqF(this.ID_USER_TO, 10, true);
      }
    } else {
      return DynamicFieldHelper.createFieldSelectStringHeqF(this.ROLE_NAME_TO, true);
    }
  }

  private getMarkAsPrivateWhenRoleMessage(): FieldConfig[] {
    return this.isPrivateReply(this.mailSendParam.mailSendRecv?.replyToRolePrivate)
    || this.mailSendParam.mailSendRecv?.idRoleTo ?
      [DynamicFieldHelper.createFieldCheckboxHeqF('replyToRolePrivate')] : [];
  }

  private isPrivateReply(replyToRolePrivate: ReplyToRolePrivateType | string | number ): boolean {
    if(replyToRolePrivate) {
      return typeof replyToRolePrivate === 'number' && replyToRolePrivate === ReplyToRolePrivateType.REPLY_IS_PRIVATE ||
        replyToRolePrivate === ReplyToRolePrivateType[ReplyToRolePrivateType.REPLY_IS_PRIVATE];
    }
    return false;
  }

  private updateViewData(): void {
    if (this.mailSendParam.idUserTo) {
      if (this.mailSendParam.idUserTo === this.USER_TO_ANY_USER_PARAM) {
        this.configObject[this.ID_USER_TO].valueKeyHtmlOptions = this.userToAnyUserOptions;
      } else {
        this.configObject[this.ID_USER_TO].formControl.setValue(this.mailSendParam.idUserTo);
        this.configObject[this.ID_USER_TO].formControl.disable();
      }
    } else {
      this.configObject[this.ROLE_NAME_TO].valueKeyHtmlOptions = SelectOptionsHelper.translateArrayKeyEqualValue(this.translateService,
        [AppSettings.ROLE_ALL_EDIT, AppSettings.ROLE_ADMIN].concat(
          this.gps.hasRole(AppSettings.ROLE_ADMIN) ? [AppSettings.ROLE_USER, AppSettings.ROLE_LIMIT_EDIT,
            AppSettings.ROLE_EVERY_USER] : []));
    }
    if (this.mailSendParam.mailSendRecv) {
      this.sendResponseToMessage();
    } else if (this.mailSendParam.subject) {
      this.configObject[this.SUBJECT].formControl.setValue(this.mailSendParam.subject);
    }
  }

  private sendResponseToMessage(): void {
    const mailReplayLine = 'MAIL_REPLY_LINE';
    const idUserFrom = AppHelper.removeSomeStringAndToUpperCaseWithUnderscore(this.ID_USER_FROM);
    const subject = AppHelper.removeSomeStringAndToUpperCaseWithUnderscore(this.SUBJECT);

    this.translateService.get([mailReplayLine, idUserFrom, subject]).subscribe(
      replyLines => {
        this.configObject[this.SUBJECT].formControl.setValue(this.mailSendParam.mailSendRecv.subject);
        const replayText = this.NEW_LINE + replyLines[mailReplayLine] + this.NEW_LINE
          + replyLines[idUserFrom] + ': ' + this.mailSendParam.idUserTo + this.NEW_LINE
          + replyLines[subject] + ': ' + this.mailSendParam.mailSendRecv.subject + this.NEW_LINE
          + this.mailSendParam.mailSendRecv.message;
        this.configObject[this.MESSAGE].formControl.setValue(replayText);
        this.configObject[this.MESSAGE].elementRef.nativeElement.focus();
        this.configObject[this.MESSAGE].elementRef.nativeElement.selectionEnd = 0;
        this.configObject[this.SUBJECT].elementRef.nativeElement.focus();
      });
  }

}

export class MailSendParam {
  /**
   * A new independent message can be sent to only one role, in this case idUserTo is set to zero.
   * In certain cases a message should be sent to a specific user, for example the creator of a security.
   * In such a case the idUserTo and the subject is set. In the 3rd case, a reply is sent to an existing message.
   * In this case the idUserTo and mailSendRecv is set.
   *
   * @param idUserTo A message can be a response for a specific user. If it is -1, the user can be selected.
   * @param mailSendRecv A message can be a specific response to an existing message.
   * @param subject The subject can be predefined for a message.
   */
  constructor(public idUserTo: number, public mailSendRecv?: MailSendRecv, public subject?: string) {
  }
}
