import { requireFormControl } from '../../dynamic-form/models/form-control-access';
import { afterNextRender, Component, DestroyRef, inject, Injector, OnInit, viewChild } from '@angular/core';
import { takeUntilDestroyed } from '@angular/core/rxjs-interop';
import { FormBase } from '../../edit/form.base';
import { AppHelper } from '../../helper/app.helper';
import { DynamicFieldHelper } from '../../helper/dynamic.field.helper';
import { InfoLevelType } from '../../message/info.leve.type';
import { HelpIds } from '../../help/help.ids';
import { TranslateService } from '@ngx-translate/core';
import { GlobalparameterService } from '../../services/globalparameter.service';
import { MessageToastService } from '../../message/message.toast.service';
import { MailSendRecvService } from '../../mail/service/mail.send.recv.service';
import { TranslateHelper } from '../../helper/translate.helper';
import { FieldConfig } from '../../dynamic-form/models/field.config';
import { SelectOptionsHelper } from '../../helper/select.options.helper';
import { DynamicFormComponent } from '../../dynamic-form/containers/dynamic-form/dynamic-form.component';
import { DialogService, DynamicDialogConfig, DynamicDialogRef } from '@openng/optimus-ui/dynamicdialog';
import { MailSendRecv, ReplyToRolePrivateType } from '../../mail/model/mail.send.recv';
import { ValueKeyHtmlSelectOptions } from '../../dynamic-form/models/value.key.html.select.options';
import { UserAdminService } from '../../user/service/user.admin.service';
import { BaseSettings } from '../../base.settings';
import { DYNAMIC_FORM_IMPORTS } from '../../dynamic-form/dynamic-form.imports';

/**
 * This input form can be used to compose and send a message. It is a dynamic dialog, as it can be used in different places in the GUI.
 */
@Component({
  template: ` <dynamic-form
    [config]="config"
    [formConfig]="formConfig"
    [translateService]="translateService"
    #form="dynamicForm"
    (submitBt)="submit($event)" />`,
  imports: [...DYNAMIC_FORM_IMPORTS]
})
export class MailSendDynamicDialogComponent extends FormBase implements OnInit {
  public translateService: TranslateService = inject(TranslateService);
  public userAdminService: UserAdminService = inject(UserAdminService);
  public sendRecvService: MailSendRecvService = inject(MailSendRecvService);
  public gps: GlobalparameterService = inject(GlobalparameterService);
  private messageToastService: MessageToastService = inject(MessageToastService);
  private dialogService: DialogService = inject(DialogService);
  private dynamicDialogRef: DynamicDialogRef = inject(DynamicDialogRef);
  private dynamicDialogConfig: DynamicDialogConfig = inject(DynamicDialogConfig);

  readonly form = viewChild.required(DynamicFormComponent);

  private readonly NEW_LINE = '\n';
  private readonly USER_TO_ANY_USER_PARAM = -1;
  private readonly SUBJECT = 'subject';
  private readonly MESSAGE = 'message';
  private readonly ID_USER_TO = 'idUserTo';
  private readonly ID_USER_FROM = 'idUserFrom';
  private readonly ROLE_NAME_TO = 'roleNameTo';
  private readonly mailSendParam: MailSendParam = this.dynamicDialogConfig.data.mailSendParam;
  private readonly destroyRef = inject(DestroyRef);
  private readonly injector = inject(Injector);

  constructor() {
    super();
  }

  ngOnInit(): void {
    this.formConfig = AppHelper.getDefaultFormConfig(this.gps, 3, this.helpLink.bind(this));
    // Build the form before its child renders. Only the select options depend on the asynchronous user lookup;
    // assigning those FieldConfig options notifies the existing form through FormNotifier.
    this.initializeFields();
    if (this.mailSendParam?.idUserTo === this.USER_TO_ANY_USER_PARAM) {
      this.userAdminService
        .getIdUserAndNicknameExcludeMe()
        .pipe(takeUntilDestroyed(this.destroyRef))
        .subscribe((vkhso: ValueKeyHtmlSelectOptions[]) => {
          this.configObject[this.ID_USER_TO].valueKeyHtmlOptions = vkhso;
        });
    }
  }

  private initializeFields(): void {
    this.config = [
      this.getUserRoleOrUserField(),
      DynamicFieldHelper.createFieldTextareaInputStringHeqF(this.SUBJECT, 96, true),
      DynamicFieldHelper.createFieldTextareaInputStringHeqF(this.MESSAGE, 4096, true, { textareaRows: 30 }),
      ...this.getMarkAsPrivateWhenRoleMessage(),
      DynamicFieldHelper.createSubmitButton('SEND')
    ];
    this.configObject = TranslateHelper.prepareFieldsAndErrors(this.translateService, this.config);
    afterNextRender(() => this.updateViewData(), { injector: this.injector });
  }

  submit(value: { [name: string]: any }): void {
    const mailSendRecv = new MailSendRecv();
    this.form().cleanMaskAndTransferValuesToBusinessObject(mailSendRecv, true);
    mailSendRecv.replyToRolePrivate = value.replyToRolePrivate
      ? ReplyToRolePrivateType.REPLY_IS_PRIVATE
      : ReplyToRolePrivateType.REPLY_NORMAL;
    mailSendRecv.idUserFrom = this.gps.getIdUser();
    mailSendRecv.idReplyToLocal = this.mailSendParam.mailSendRecv?.idReplyToLocal
      ? this.mailSendParam.mailSendRecv.idReplyToLocal
      : this.mailSendParam.mailSendRecv
        ? this.mailSendParam.mailSendRecv.idMailSendRecv
        : null;
    mailSendRecv.contextEntity = this.mailSendParam.contextEntity;
    mailSendRecv.idEntityContext = this.mailSendParam.idEntityContext;
    this.sendRecvService.sendMessage(mailSendRecv).subscribe({
      next: (mailSendRecvRc: MailSendRecv) => {
        this.messageToastService.showMessageI18n(InfoLevelType.SUCCESS, 'MSG_PROPOSE_SAVED');
        this.dynamicDialogRef.close(mailSendRecvRc);
      },
      error: () => (this.configObject.submit.disabled = false)
    });
  }

  helpLink(): void {
    this.gps.toExternalHelpWebpage(this.gps.getUserLang(), HelpIds.HELP_USER);
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
    return this.isPrivateReply(this.mailSendParam.mailSendRecv?.replyToRolePrivate) ||
      this.mailSendParam.mailSendRecv?.idRoleTo
      ? [DynamicFieldHelper.createFieldCheckboxHeqF('replyToRolePrivate')]
      : [];
  }

  private isPrivateReply(replyToRolePrivate: ReplyToRolePrivateType | string | number | null | undefined): boolean {
    if (replyToRolePrivate) {
      return (
        (typeof replyToRolePrivate === 'number' && replyToRolePrivate === ReplyToRolePrivateType.REPLY_IS_PRIVATE) ||
        replyToRolePrivate === ReplyToRolePrivateType[ReplyToRolePrivateType.REPLY_IS_PRIVATE]
      );
    }
    return false;
  }

  private updateViewData(): void {
    if (this.mailSendParam.idUserTo) {
      if (this.mailSendParam.idUserTo !== this.USER_TO_ANY_USER_PARAM) {
        requireFormControl(this.configObject[this.ID_USER_TO]).setValue(this.mailSendParam.idUserTo);
        requireFormControl(this.configObject[this.ID_USER_TO]).disable();
      }
    } else {
      this.configObject[this.ROLE_NAME_TO].valueKeyHtmlOptions = SelectOptionsHelper.translateArrayKeyEqualValue(
        this.translateService,
        [BaseSettings.ROLE_ALL_EDIT, BaseSettings.ROLE_ADMIN].concat(
          this.gps.hasRole(BaseSettings.ROLE_ADMIN)
            ? [BaseSettings.ROLE_USER, BaseSettings.ROLE_LIMIT_EDIT, BaseSettings.ROLE_EVERY_USER]
            : []
        )
      );
      if (this.mailSendParam.roleName) {
        requireFormControl(this.configObject[this.ROLE_NAME_TO]).setValue(this.mailSendParam.roleName);
        requireFormControl(this.configObject[this.ROLE_NAME_TO]).disable();
      }
    }
    if (this.mailSendParam.mailSendRecv) {
      this.sendResponseToMessage();
    } else if (this.mailSendParam.subject) {
      requireFormControl(this.configObject[this.SUBJECT]).setValue(this.mailSendParam.subject);
    }
  }

  private sendResponseToMessage(): void {
    const reply = this.mailSendParam.mailSendRecv;
    if (!reply) return;
    const mailReplayLine = 'MAIL_REPLY_LINE';
    const idUserFrom = AppHelper.removeSomeStringAndToUpperCaseWithUnderscore(this.ID_USER_FROM);
    const subject = AppHelper.removeSomeStringAndToUpperCaseWithUnderscore(this.SUBJECT);

    this.translateService.get([mailReplayLine, idUserFrom, subject]).subscribe((replyLines) => {
      requireFormControl(this.configObject[this.SUBJECT]).setValue(reply.subject);
      const replayText =
        this.NEW_LINE +
        replyLines[mailReplayLine] +
        this.NEW_LINE +
        replyLines[idUserFrom] +
        ': ' +
        this.mailSendParam.idUserTo +
        this.NEW_LINE +
        replyLines[subject] +
        ': ' +
        reply.subject +
        this.NEW_LINE +
        reply.message;
      requireFormControl(this.configObject[this.MESSAGE]).setValue(replayText);
      this.configObject[this.MESSAGE].elementRef?.nativeElement?.focus();
      const message = this.configObject[this.MESSAGE].elementRef?.nativeElement;
      if (message) message.selectionEnd = 0;
      this.configObject[this.SUBJECT].elementRef?.nativeElement?.focus();
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
   * @param roleName Optional role name to pre-select and lock the role dropdown (e.g. BaseSettings.ROLE_ADMIN).
   * @param contextEntity Optional context entity type (e.g. 'Securitycurrency') that justifies an initial
   *                      user-to-user message to a non-admin recipient; verified by the backend.
   * @param idEntityContext Optional id of the context entity referenced by contextEntity.
   */
  constructor(
    public idUserTo: number | undefined,
    public mailSendRecv?: MailSendRecv | null,
    public subject?: string | null,
    public roleName?: string,
    public contextEntity?: string,
    public idEntityContext?: number | null
  ) {}
}
