import {AfterViewInit, Component, Input, OnInit} from '@angular/core';
import {MainDialogService} from '../../mainmenubar/service/main.dialog.service';
import {TranslateService} from '@ngx-translate/core';
import {LoginService} from '../service/log-in.service';
import {ChangePasswordDTO} from '../../../entities/backend/change.password.dto';
import {InfoLevelType} from '../../message/info.leve.type';
import {MessageToastService} from '../../message/message.toast.service';
import {PasswordBaseComponent} from './password.base.component';
import {AppHelper} from '../../helper/app.helper';
import {GlobalparameterService} from '../../service/globalparameter.service';
import {DynamicFieldHelper} from '../../helper/dynamic.field.helper';
import {UserSettingsDialogs} from '../../mainmenubar/component/main.dialog.component';
import {TranslateHelper} from '../../helper/translate.helper';
import {FieldDescriptorInputAndShow} from '../../dynamicfield/field.descriptor.input.and.show';
import {GlobalSessionNames} from '../../global.session.names';
import {DynamicFieldModelHelper} from '../../helper/dynamic.field.model.helper';

/**
 * Change the password with a dialog.
 */
@Component({
  selector: 'password-edit',
  template: `
    <p-dialog header="{{'PASSWORD_CHANGE' | translate}}" [(visible)]="visibleDialog"
              [responsive]="true" [style]="{width: '450px'}"
              (onShow)="onShow($event)" (onHide)="onHide($event)" [modal]="true">
      <dynamic-form [config]="config" [formConfig]="formConfig" [translateService]="translateService"
                    #form="dynamicForm"
                    (submitBt)="submit($event)">
      </dynamic-form>
    </p-dialog>`
})
export class PasswordEditComponent extends PasswordBaseComponent implements OnInit, AfterViewInit {
  @Input() visibleDialog: boolean;

  constructor(private mainDialogService: MainDialogService,
              private messageToastService: MessageToastService,
              private loginService: LoginService,
              private gps: GlobalparameterService,
              translateService: TranslateService) {
    super(translateService);
  }

  ngOnInit(): void {
    this.changePasswdFormDefinition(JSON.parse(sessionStorage.getItem(GlobalSessionNames.USER_FORM_DEFINITION)));
  }

  submit(value: { [name: string]: any }): void {
    const changePassword = new ChangePasswordDTO();
    this.form.cleanMaskAndTransferValuesToBusinessObject(changePassword);
    changePassword.passwordNew = value.password;
    this.loginService.updatePassword(changePassword).subscribe(successfullyChanged => {
      this.messageToastService.showMessage(InfoLevelType.INFO, successfullyChanged.message);
      this.loginService.logoutWithLoginView();
    });
    this.mainDialogService.visibleDialog(false, UserSettingsDialogs.Password);
  }

  ngAfterViewInit(): void {
    this.form.setDefaultValuesAndEnableSubmit();
    super.afterViewInit();
    this.configObject.oldPassword.elementRef.nativeElement.focus();
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
        {targetField: 'passwordOld'}),
      ...this.configPassword,
      DynamicFieldHelper.createSubmitButton()
    ];
    this.configObject = TranslateHelper.prepareFieldsAndErrors(this.translateService, this.config);
  }


}
