import {Component, Input, OnInit} from '@angular/core';
import {SimpleEntityEditBase} from '../../lib/edit/simple.entity.edit.base';
import {UserEntityChangeLimit} from '../../lib/entities/user.entity.change.limit';
import {AppHelper} from '../../lib/helper/app.helper';
import {TranslateService} from '@ngx-translate/core';
import {GlobalparameterService} from '../../shared/service/globalparameter.service';
import {MessageToastService} from '../../lib/message/message.toast.service';
import {HelpIds} from '../../shared/help/help.ids';
import {UserEntityChangeLimitService} from '../service/user.entity.change.limit.service';
import {DynamicFieldHelper} from '../../lib/helper/dynamic.field.helper';
import {DataType} from '../../lib/dynamic-form/models/data.type';
import {ValueKeyHtmlSelectOptions} from '../../lib/dynamic-form/models/value.key.html.select.options';
import {SelectOptionsHelper} from '../../lib/helper/select.options.helper';
import {TranslateHelper} from '../../lib/helper/translate.helper';
import {User} from '../../lib/entities/user';
import {AuditHelper} from '../../lib/helper/audit.helper';
import {ProposeChangeEntityWithEntity} from '../../lib/proposechange/model/propose.change.entity.whit.entity';

/**
 * Edit the limit of a information class.
 */
@Component({
    selector: 'user-entity-change-limit-edit',
    template: `
    <p-dialog header="{{'USER_ENTITY_CHANGE_LIMIT' | translate}}" [(visible)]="visibleDialog"
              [style]="{width: '500px'}"
              (onShow)="onShow($event)" (onHide)="onHide($event)" [modal]="true">

      <dynamic-form [config]="config" [formConfig]="formConfig" [translateService]="translateService"
                    #form="dynamicForm"
                    (submitBt)="submit($event)">
      </dynamic-form>
    </p-dialog>`,
    standalone: false
})
export class UserEntityChangeLimitEditComponent extends SimpleEntityEditBase<UserEntityChangeLimit> implements OnInit {
  @Input() user: User;
  @Input() existingUserEntityChangeLimit: UserEntityChangeLimit;
  @Input() proposeChangeEntityWithEntity: ProposeChangeEntityWithEntity;

  constructor(translateService: TranslateService,
    gps: GlobalparameterService,
    messageToastService: MessageToastService,
    userEntityChangeLimitService: UserEntityChangeLimitService) {
    super(HelpIds.HELP_USER, 'USER_ENTITY_CHANGE_LIMIT', translateService, gps, messageToastService,
      userEntityChangeLimitService);
  }

  ngOnInit(): void {
    this.formConfig = AppHelper.getDefaultFormConfig(this.gps,
      6, this.helpLink.bind(this));
    this.config = [
      DynamicFieldHelper.createFieldSelectStringHeqF('entityName', true),
      DynamicFieldHelper.createFieldPcalendarHeqF(DataType.DateNumeric, 'untilDate', true),
      DynamicFieldHelper.createFieldMinMaxNumberHeqF(DataType.NumericInteger, 'dayLimit', true, 0, 999),
      ...AuditHelper.getFullNoteRequestInputDefinition(this.closeDialog, this, true)
      // DynamicFieldHelper.createSubmitButton(),
    ];
    this.configObject = TranslateHelper.prepareFieldsAndErrors(this.translateService, this.config);
  }

  protected override initialize(): void {
    (<UserEntityChangeLimitService>this.serviceEntityUpdate).getPublicEntitiesAsHtmlSelectOptions(this.user.idUser,
      this.existingUserEntityChangeLimit ? this.existingUserEntityChangeLimit.idUserEntityChangeLimit : undefined)
      .subscribe((valueKeyHtmlSelectOptions: ValueKeyHtmlSelectOptions[]) => {
        this.configObject.entityName.valueKeyHtmlOptions = SelectOptionsHelper.translateExistingValueKeyHtmlSelectOptions(
          this.translateService, valueKeyHtmlSelectOptions);
        if (this.existingUserEntityChangeLimit) {
          this.configObject.entityName.formControl.disable();
          this.form.transferBusinessObjectToForm(this.existingUserEntityChangeLimit);
        }
        AuditHelper.transferToFormAndChangeButtonForProposaleEdit(this.translateService, this.gps,
          (this.proposeChangeEntityWithEntity) ? this.proposeChangeEntityWithEntity.proposedEntity : this.existingUserEntityChangeLimit,
          this.form, this.configObject, this.proposeChangeEntityWithEntity);
        this.configObject.entityName.elementRef.nativeElement.focus();
      });
  }

  protected override getNewOrExistingInstanceBeforeSave(value: { [name: string]: any }): UserEntityChangeLimit {
    const userEntityChangeLimit = new UserEntityChangeLimit();
    userEntityChangeLimit.idUser = this.user.idUser;
    this.copyFormToPublicBusinessObject(userEntityChangeLimit, this.existingUserEntityChangeLimit, this.proposeChangeEntityWithEntity);
    return userEntityChangeLimit;
  }
}
