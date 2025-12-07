import {Component, OnInit} from '@angular/core';
import {SimpleEditBase} from '../../edit/simple.edit.base';
import {TranslateModule, TranslateService} from '@ngx-translate/core';
import {GlobalparameterService} from '../../services/globalparameter.service';
import {HelpIds} from '../../help/help.ids';
import {AppHelper} from '../../helper/app.helper';
import {DynamicFieldHelper} from '../../helper/dynamic.field.helper';
import {LoginService} from '../service/log-in.service';
import {UserSettingsDialogs} from '../../mainmenubar/component/user-settings-dialogs';
import {MainDialogService} from '../../mainmenubar/service/main.dialog.service';
import {combineLatest} from 'rxjs';
import {UserOwnProjection} from '../../entities/projection/user.own.projection';
import {SuccessfullyChanged} from '../model/successfully.changed';
import {InfoLevelType} from '../../message/info.leve.type';
import {MessageToastService} from '../../message/message.toast.service';
import {TranslateHelper} from '../../helper/translate.helper';
import {ValueKeyHtmlSelectOptions} from '../../dynamic-form/models/value.key.html.select.options';

import {DialogModule} from 'primeng/dialog';
import {DynamicFormModule} from '../../dynamic-form/dynamic-form.module';

/**
 * Change nickname and locale of a user
 */
@Component({
    selector: 'nickname-lang-edit',
    template: `
    <p-dialog header="{{'NICKNAME_LOCALE_CHANGE' | translate}}" [(visible)]="visibleDialog"
              [style]="{width: '450px'}"
              (onShow)="onShow($event)" (onHide)="onHide($event)" [modal]="true">
      <dynamic-form [config]="config" [formConfig]="formConfig" [translateService]="translateService" #form="dynamicForm"
                    (submitBt)="submit($event)">
      </dynamic-form>
    </p-dialog>`,
    standalone: true,
    imports: [DialogModule, DynamicFormModule, TranslateModule]
})
export class NicknameLangEditComponent extends SimpleEditBase implements OnInit {

  constructor(public translateService: TranslateService,
              private messageToastService: MessageToastService,
              private loginService: LoginService,
              private mainDialogService: MainDialogService,
              gps: GlobalparameterService) {
    super(HelpIds.HELP_USER, gps);
  }

  ngOnInit(): void {
    this.formConfig = AppHelper.getDefaultFormConfig(this.gps,
      5, this.helpLink.bind(this));
    this.config = [
      DynamicFieldHelper.createFieldInputStringHeqF('nickname', 30, true, {minLength: 2}),
      DynamicFieldHelper.createFieldSelectStringHeqF('localeStr', true, {inputWidth: 10}),
      DynamicFieldHelper.createFieldCheckboxHeqF('uiShowMyProperty'),
      DynamicFieldHelper.createSubmitButton()
    ];
    this.configObject = TranslateHelper.prepareFieldsAndErrors(this.translateService, this.config);
  }

  submit(value: { [name: string]: any }): void {
    const userOwnProjection = new UserOwnProjection();
    this.form.cleanMaskAndTransferValuesToBusinessObject(userOwnProjection);
    this.loginService.updateNicknameLocale(userOwnProjection).subscribe({next:(successfullyChanged: SuccessfullyChanged) => {
      this.messageToastService.showMessage(InfoLevelType.INFO, successfullyChanged.message);
      this.loginService.logoutWithLoginView();
      this.mainDialogService.visibleDialog(false, UserSettingsDialogs.NicknameLocale);
    }, error: () => this.configObject.submit.disabled = false});
  }

  override onHide(event): void {
    this.mainDialogService.visibleDialog(false, UserSettingsDialogs.NicknameLocale);
  }

  protected override initialize(): void {
    combineLatest([this.gps.getSupportedLocales(), this.loginService.getOwnUser()])
      .subscribe(([locales, user]: [ValueKeyHtmlSelectOptions[], UserOwnProjection]) => {
        this.configObject.localeStr.valueKeyHtmlOptions = locales;
        this.form.transferBusinessObjectToForm(user);
        this.configObject.nickname.elementRef.nativeElement.focus();
      });
  }

}
