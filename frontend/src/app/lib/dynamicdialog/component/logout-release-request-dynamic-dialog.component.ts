import { FormBase } from '../../edit/form.base';
import { Component, OnInit, inject } from '@angular/core';
import { AppHelper } from '../../helper/app.helper';
import { DynamicFieldHelper } from '../../helper/dynamic.field.helper';
import { TranslateHelper } from '../../helper/translate.helper';
import { HelpIds } from '../../help/help.ids';
import { TranslateModule, TranslateService } from '@ngx-translate/core';
import { GlobalparameterService } from '../../services/globalparameter.service';
import { LoginService } from '../../login/service/log-in.service';
import { InfoLevelType } from '../../message/info.leve.type';
import { MessageToastService } from '../../message/message.toast.service';
import { DialogService, DynamicDialogConfig, DynamicDialogRef } from '@openng/optimus-ui/dynamicdialog';
import { BaseSettings } from '../../base.settings';
import { DYNAMIC_FORM_IMPORTS } from '../../dynamic-form/dynamic-form.imports';

/**
 * Apply for admin to release security breaches or the too often to many serve requests.
 */
@Component({
  template: ` {{ 'RESET_USER_MISUSED_QUESTION' | translate }}
    <dynamic-form
      [config]="config"
      [formConfig]="formConfig"
      [translateService]="translateService"
      #form="dynamicForm"
      (submitBt)="submit($event)" />`,
  imports: [...DYNAMIC_FORM_IMPORTS, TranslateModule]
})
export class LogoutReleaseRequestDynamicDialogComponent extends FormBase implements OnInit {
  public translateService: TranslateService = inject(TranslateService);
  public gps: GlobalparameterService = inject(GlobalparameterService);
  private messageToastService: MessageToastService = inject(MessageToastService);
  private loginService: LoginService = inject(LoginService);
  private dialogService: DialogService = inject(DialogService);
  private dynamicDialogRef: DynamicDialogRef = inject(DynamicDialogRef);
  private dynamicDialogConfig: DynamicDialogConfig = inject(DynamicDialogConfig);

  readonly NOTE = 'note';

  constructor() {
    super();
  }

  ngOnInit(): void {
    this.formConfig = AppHelper.getDefaultFormConfig(this.gps, 4, this.helpLink.bind(this));
    this.config = [
      DynamicFieldHelper.createFieldTextareaInputString(this.NOTE, 'REASON', BaseSettings.FID_MAX_LETTERS, true, {
        textareaRows: 10
      }),
      DynamicFieldHelper.createSubmitButton('SEND')
    ];

    this.configObject = TranslateHelper.prepareFieldsAndErrors(this.translateService, this.config);
  }

  submit(value: { [name: string]: any }): void {
    this.configObject.submit.groupItemUseOrLoading = true;
    this.loginService
      .login(this.dynamicDialogConfig.data.email, this.dynamicDialogConfig.data.password, value[this.NOTE])
      .subscribe({
        next: (response) => {
          this.messageToastService.showMessageI18n(InfoLevelType.SUCCESS, 'MSG_PROPOSE_SAVED');
          this.dynamicDialogRef.close();
        },
        error: () => (this.configObject.submit.groupItemUseOrLoading = false)
      });
  }

  helpLink(): void {
    this.gps.toExternalHelpWebpage(
      AppHelper.getNonUserDefinedLanguage(this.translateService.currentLang),
      HelpIds.HELP_USER
    );
  }
}
