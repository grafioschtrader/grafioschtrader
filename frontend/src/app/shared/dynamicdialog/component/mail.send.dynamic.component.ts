import {AfterViewInit, Component, OnInit, ViewChild} from '@angular/core';
import {FormBase} from '../../edit/form.base';
import {AppHelper} from '../../helper/app.helper';
import {DynamicFieldHelper} from '../../helper/dynamic.field.helper';
import {InfoLevelType} from '../../message/info.leve.type';
import {BusinessHelper} from '../../helper/business.helper';
import {HelpIds} from '../../help/help.ids';
import {TranslateService} from '@ngx-translate/core';
import {GlobalparameterService} from '../../service/globalparameter.service';
import {MessageToastService} from '../../message/message.toast.service';
import {MailSendboxService} from '../../../mail/service/mail.sendbox.service';
import {MailSendbox} from '../../../mail/model/mail.sendbox';
import {MailInbox} from '../../../mail/model/mail.inbox';
import {TranslateHelper} from '../../helper/translate.helper';
import {FieldConfig} from '../../../dynamic-form/models/field.config';
import {SelectOptionsHelper} from '../../helper/select.options.helper';
import {AppSettings} from '../../app.settings';
import {DynamicFormComponent} from '../../../dynamic-form/containers/dynamic-form/dynamic-form.component';
import {DialogService, DynamicDialogConfig, DynamicDialogRef} from 'primeng/dynamicdialog';

@Component({
  template: `
      <dynamic-form [config]="config" [formConfig]="formConfig" [translateService]="translateService" #form="dynamicForm"
                    (submitBt)="submit($event)">
      </dynamic-form>`
})
export class MailSendDynamicComponent extends FormBase implements OnInit, AfterViewInit {
  @ViewChild(DynamicFormComponent) form: DynamicFormComponent;

  private readonly NEW_LINE = '\n';
  private readonly SUBJECT = 'subject';
  private readonly MESSAGE = 'message';
  private readonly ID_USER_TO = 'idUserTo';
  private readonly ID_USER_FROM = 'idUserFrom';
  private readonly ROLE_NAME_TO = 'roleNameTo';
  private mailSendParam: MailSendParam;

  constructor(public translateService: TranslateService,
              public mailSendboxService: MailSendboxService,
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
    this.config = [
      this.getGroupOrUserField(),
      DynamicFieldHelper.createFieldTextareaInputStringHeqF(this.SUBJECT, 64, true),
      DynamicFieldHelper.createFieldTextareaInputStringHeqF(this.MESSAGE, 1024, true,
        {textareaRows: 30}),
      DynamicFieldHelper.createSubmitButton('SEND')
    ];
    this.configObject = TranslateHelper.prepareFieldsAndErrors(this.translateService, this.config);
  }

  ngAfterViewInit(): void {
    setTimeout(() => this.updateView());
  }

  submit(value: { [name: string]: any }): void {
    const mailSendbox = new MailSendbox();
    this.form.cleanMaskAndTransferValuesToBusinessObject(mailSendbox, true);
    mailSendbox.idUserFrom = this.gps.getIdUser();
    this.mailSendboxService.replyMessage(mailSendbox).subscribe((sendMailSendbox: MailSendbox) => {
      this.messageToastService.showMessageI18n(InfoLevelType.SUCCESS, 'MSG_PROPOSE_SAVED');
      this.dynamicDialogRef.close();
    });
  }

  helpLink(): void {
    BusinessHelper.toExternalHelpWebpage(this.gps.getUserLang(), HelpIds.HELP_USER);
  }

  private getGroupOrUserField(): FieldConfig {
    if (this.mailSendParam.idUserTo) {
      return DynamicFieldHelper.createFieldInputStringHeqF(this.ID_USER_TO, 10, true);
    } else {
      return DynamicFieldHelper.createFieldSelectStringHeqF(this.ROLE_NAME_TO, true);
    }
  }

  private updateView(): void {
    if (this.mailSendParam.idUserTo) {
      this.configObject[this.ID_USER_TO].formControl.setValue(this.mailSendParam.idUserTo);
      this.configObject[this.ID_USER_TO].formControl.disable();
    } else {
      this.configObject[this.ROLE_NAME_TO].valueKeyHtmlOptions = SelectOptionsHelper.translateArrayKeyEqualValue(this.translateService,
        [AppSettings.ROLE_ALL_EDIT, AppSettings.ROLE_ADMIN]);
    }
    const mailReplayLine = 'MAIL_REPLY_LINE';
    const idUserFrom = AppHelper.convertPropertyForLabelOrHeaderKey(this.ID_USER_FROM);
    const subject = AppHelper.convertPropertyForLabelOrHeaderKey(this.SUBJECT);

    if (this.mailSendParam.mailInbox) {
      this.translateService.get([mailReplayLine, idUserFrom, subject]).subscribe(
        replyLines => {
          this.configObject[this.SUBJECT].formControl.setValue(this.mailSendParam.mailInbox.subject);
          const replayText = this.NEW_LINE + replyLines[mailReplayLine] + this.NEW_LINE
            + replyLines[idUserFrom] + ': ' + this.mailSendParam.idUserTo + this.NEW_LINE
            + replyLines[subject] + ': ' + this.mailSendParam.mailInbox.subject + this.NEW_LINE
            + this.mailSendParam.mailInbox.message;
          this.configObject[this.MESSAGE].formControl.setValue(replayText);
          this.configObject[this.MESSAGE].elementRef.nativeElement.focus();
          this.configObject[this.MESSAGE].elementRef.nativeElement.selectionEnd = 0;
          this.configObject[this.SUBJECT].elementRef.nativeElement.focus();
        });
    } else if (this.mailSendParam.subject) {
      this.configObject[this.SUBJECT].formControl.setValue(this.mailSendParam.subject);
    }
  }

}

export class MailSendParam {
  constructor(public idUserTo: number, public mailInbox?: MailInbox, public subject?: string) {
  }
}
