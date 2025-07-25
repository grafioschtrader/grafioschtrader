import {Component, Input, OnInit} from '@angular/core';
import {SimpleEntityEditBase} from '../../lib/edit/simple.entity.edit.base';
import {TranslateService} from '@ngx-translate/core';
import {GlobalparameterService} from '../../shared/service/globalparameter.service';
import {MessageToastService} from '../../lib/message/message.toast.service';
import {HelpIds} from '../../shared/help/help.ids';
import {AppHelper} from '../../lib/helper/app.helper';
import {DataType} from '../../lib/dynamic-form/models/data.type';
import {UserAdminService} from '../service/user.admin.service';
import {User} from '../../lib/entities/user';
import {DynamicFieldHelper} from '../../lib/helper/dynamic.field.helper';
import {TranslateHelper} from '../../lib/helper/translate.helper';
import {SelectOptionsHelper} from '../../lib/helper/select.options.helper';
import {AppSettings} from '../../shared/app.settings';
import {AuditHelper} from '../../lib/helper/audit.helper';
import {ProposeChangeEntityWithEntity} from '../../lib/proposechange/model/propose.change.entity.whit.entity';

/**
 * Component for editing the users properties.
 */
@Component({
    selector: 'user-edit',
    template: `
    <p-dialog header="{{'USER_SETTINGS' | translate}}" [(visible)]="visibleDialog"
              [style]="{width: '500px'}"
              (onShow)="onShow($event)" (onHide)="onHide($event)" [modal]="true">

      <dynamic-form [config]="config" [formConfig]="formConfig" [translateService]="translateService"
                    #form="dynamicForm"
                    (submitBt)="submit($event)">
      </dynamic-form>
    </p-dialog>`,
    standalone: false
})
export class UserEditComponent extends SimpleEntityEditBase<User> implements OnInit {
  @Input() callParam: User;

  constructor(translateService: TranslateService,
              gps: GlobalparameterService,
              messageToastService: MessageToastService,
              userService: UserAdminService) {
    super(HelpIds.HELP_USER, AppSettings.USER.toUpperCase(), translateService, gps, messageToastService, userService);
  }

  ngOnInit(): void {
    this.formConfig = AppHelper.getDefaultFormConfig(this.gps,
      6, this.helpLink.bind(this));

    this.config = [
      DynamicFieldHelper.createFieldInputStringHeqF('nickname', 30, true,
        {minLength: 2}),
      DynamicFieldHelper.createFieldSelectStringHeqF('mostPrivilegedRole', true),
      DynamicFieldHelper.createFieldCheckboxHeqF('enabled'),
      DynamicFieldHelper.createFieldMinMaxNumberHeqF(DataType.NumericInteger, 'timezoneOffset',
        true, -720, 720, {fieldSuffix: 'MINUTES'}),
      DynamicFieldHelper.createFieldMinMaxNumberHeqF(DataType.NumericInteger, 'securityBreachCount',
        true, 0, 99, null),
      DynamicFieldHelper.createFieldMinMaxNumberHeqF(DataType.NumericInteger, 'limitRequestExceedCount',
        true, 0, 99, null),
      ...AuditHelper.getFullNoteRequestInputDefinition(this.closeDialog, this, true)
      // DynamicFieldHelper.createSubmitButton()
    ];
    this.configObject = TranslateHelper.prepareFieldsAndErrors(this.translateService, this.config);
  }

  protected initialize(): void {
    this.form.setDefaultValuesAndEnableSubmit();
    const proposeChangeEntityWithEntity: ProposeChangeEntityWithEntity = AuditHelper.convertToProposeChangeEntityWithEntity(
      this.callParam, this.callParam.userChangePropose);
    this.configObject.mostPrivilegedRole.valueKeyHtmlOptions = SelectOptionsHelper.translateArrayKeyEqualValue(this.translateService,
      [AppSettings.ROLE_LIMIT_EDIT, AppSettings.ROLE_USER, AppSettings.ROLE_ALL_EDIT, AppSettings.ROLE_ADMIN]);
    this.callParam != null && this.form.transferBusinessObjectToForm(this.callParam);
    AuditHelper.transferToFormAndChangeButtonForProposaleEdit(this.translateService, this.gps,
      (proposeChangeEntityWithEntity) ? proposeChangeEntityWithEntity.proposedEntity : this.callParam,
      this.form, this.configObject, proposeChangeEntityWithEntity);
  }

  protected override getNewOrExistingInstanceBeforeSave(value: { [name: string]: any }): User {
    const user = this.copyFormToPrivateBusinessObject(new User(), this.callParam);
    AuditHelper.copyNoteAcceptRejectToEntity(this, user);
    return user;
  }

  protected override activateWaitStateInButton(): void {
    if (AuditHelper.isProposeVisible(this)) {
      this.configObject[AuditHelper.SUBMIT_FIELD_BUTTON].groupItemUseOrLoading = true;
    }
  }

  protected override deactivateWaitStateInButton(): void {
    if (AuditHelper.isProposeVisible(this)) {
      this.configObject[AuditHelper.SUBMIT_FIELD_BUTTON].groupItemUseOrLoading = false;
    }
  }

}

