import {FormBase} from '../../edit/form.base';
import {Component, OnInit} from '@angular/core';
import {AppHelper} from '../../helper/app.helper';
import {DynamicFieldHelper} from '../../helper/dynamic.field.helper';
import {TranslateHelper} from '../../helper/translate.helper';
import {BusinessHelper} from '../../helper/business.helper';
import {HelpIds} from '../../help/help.ids';
import {TranslateService} from '@ngx-translate/core';
import {GlobalparameterService} from '../../service/globalparameter.service';
import {LoginService} from '../../login/service/log-in.service';
import {InfoLevelType} from '../../message/info.leve.type';
import {MessageToastService} from '../../message/message.toast.service';
import {DialogService, DynamicDialogConfig, DynamicDialogRef} from 'primeng/dynamicdialog';
import {AppSettings} from '../../app.settings';

/**
 * Apply for admin to release security breaches or the too often to many serve requests.
 */
@Component({
  template: `
      {{'RESET_USER_MISUSED_QUESTiON' | translate}}
      <dynamic-form [config]="config" [formConfig]="formConfig" [translateService]="translateService" #form="dynamicForm"
                    (submitBt)="submit($event)">
      </dynamic-form>`
})
export class LogoutReleaseRequestDynamicComponent extends FormBase implements OnInit {
  readonly NOTE = 'note';

  constructor(public translateService: TranslateService,
              public gps: GlobalparameterService,
              private messageToastService: MessageToastService,
              private loginService: LoginService,
              private dialogService: DialogService,
              private dynamicDialogRef: DynamicDialogRef,
              private dynamicDialogConfig: DynamicDialogConfig) {
    super();
  }

  ngOnInit(): void {
    this.formConfig = AppHelper.getDefaultFormConfig(this.gps,
      4, this.helpLink.bind(this));
    this.config = [
      DynamicFieldHelper.createFieldTextareaInputString(this.NOTE, 'REASON', AppSettings.FID_MAX_LETTERS, true,
        {textareaRows: 10}),
      DynamicFieldHelper.createSubmitButton('SEND')
    ];

    this.configObject = TranslateHelper.prepareFieldsAndErrors(this.translateService, this.config);
  }

  submit(value: { [name: string]: any }): void {
    this.loginService.login(this.dynamicDialogConfig.data.email, this.dynamicDialogConfig.data.password, value[this.NOTE])
      .subscribe((response: Response) => {
        this.messageToastService.showMessageI18n(InfoLevelType.SUCCESS, 'MSG_PROPOSE_SAVED');
        this.dynamicDialogRef.close();
      });
  }

  helpLink(): void {
    BusinessHelper.toExternalHelpWebpage(this.gps.getUserLang(), HelpIds.HELP_USER);
  }
}
