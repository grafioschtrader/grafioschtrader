import {Component, Input, OnInit} from '@angular/core';
import {MainDialogService} from '../../mainmenubar/service/main.dialog.service';
import {TranslateService} from '@ngx-translate/core';
import {LoginService} from '../service/log-in.service';
import {ChangePasswordDTO} from '../model/change.password.dto';
import {InfoLevelType} from '../../../lib/message/info.leve.type';
import {MessageToastService} from '../../../lib/message/message.toast.service';
import {PasswordBaseComponent} from './password.base.component';
import {AppHelper} from '../../../lib/helper/app.helper';
import {GlobalparameterService} from '../../service/globalparameter.service';
import {DynamicFieldHelper} from '../../../lib/helper/dynamic.field.helper';
import {UserSettingsDialogs} from '../../mainmenubar/component/main.dialog.component';
import {TranslateHelper} from '../../../lib/helper/translate.helper';
import {FieldDescriptorInputAndShow} from '../../dynamicfield/field.descriptor.input.and.show';
import {GlobalSessionNames} from '../../global.session.names';
import {DynamicFieldModelHelper} from '../../../lib/helper/dynamic.field.model.helper';

/**
 * Change the password with a dialog.
 */
@Component({
  selector: 'password-edit',
  template: `
    <p-dialog header="{{'PASSWORD_CHANGE' | translate}}" [(visible)]="visibleDialog"
              [style]="{width: '450px'}"
              [closeOnEscape]="!forcePasswordChange" [closable]="!forcePasswordChange"
              (onShow)="onShow($event)" (onHide)="onHide($event)" [modal]="true">
      <dynamic-form [config]="config" [formConfig]="formConfig" [translateService]="translateService"
                    #form="dynamicForm"
                    (submitBt)="submit($event)">
      </dynamic-form>
    </p-dialog>`,
  standalone: false
})
export class PasswordEditComponent extends PasswordBaseComponent implements OnInit {
  @Input() forcePasswordChange: boolean;
  @Input() visibleDialog: boolean;

  private static readonly passwordOld = 'passwordOld';

  constructor(private mainDialogService: MainDialogService,
    private messageToastService: MessageToastService,
    private loginService: LoginService,
    gps: GlobalparameterService,
    translateService: TranslateService) {
    super(gps, translateService);
    this.changePasswdFormDefinition(JSON.parse(sessionStorage.getItem(GlobalSessionNames.USER_FORM_DEFINITION)));
  }

  ngOnInit(): void {
    this.gps.getPasswordRegexProperties().subscribe(prp => {
        this.passwordRegexProperties = prp;
        this.form.setDefaultValuesAndEnableSubmit();
        super.preparePasswordFields();
        this.configObject[PasswordEditComponent.passwordOld].elementRef.nativeElement.focus();
      }
    );
  }

  submit(value: { [name: string]: any }): void {
    const changePassword = new ChangePasswordDTO();
    this.form.cleanMaskAndTransferValuesToBusinessObject(changePassword);
    changePassword.passwordNew = value.password;
    this.loginService.updatePassword(changePassword).subscribe(successfullyChanged => {
      this.messageToastService.showMessage(InfoLevelType.INFO, successfullyChanged.message);
      this.loginService.logoutWithLoginView();
    });
    if (!this.forcePasswordChange) {
      this.mainDialogService.visibleDialog(false, UserSettingsDialogs.Password);
    } else {
      this.visibleDialog = false;
    }
  }

  public onShow(event) {
  }

  public onHide(event) {
    this.mainDialogService.visibleDialog(false, UserSettingsDialogs.Password);
  }

  private changePasswdFormDefinition(fdias: FieldDescriptorInputAndShow[]): void {
    super.init(fdias, true);
    this.formConfig = AppHelper.getDefaultFormConfig(this.gps,
      5);
    this.config = [
      DynamicFieldModelHelper.ccWithFieldsFromDescriptorHeqF('password', fdias,
        {targetField: PasswordEditComponent.passwordOld}),
      ...this.configPassword,
      DynamicFieldHelper.createSubmitButton()
    ];
    this.configObject = TranslateHelper.prepareFieldsAndErrors(this.translateService, this.config);
  }


}
