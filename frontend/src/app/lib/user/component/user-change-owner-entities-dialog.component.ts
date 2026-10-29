import { requireFormControl } from '../../dynamic-form/models/form-control-access';
import { Component, OnInit, input, inject } from '@angular/core';

import { SimpleFormEditBase } from '../../edit/simple.form.edit.base';
import { TranslateModule, TranslateService } from '@ngx-translate/core';
import { DialogModule } from '@openng/optimus-ui/dialog';
import { DYNAMIC_FORM_IMPORTS } from '../../dynamic-form/dynamic-form.imports';
import { MessageToastService } from '../../message/message.toast.service';
import { HelpIds } from '../../help/help.ids';
import { AppHelper } from '../../helper/app.helper';
import { DynamicFieldHelper } from '../../helper/dynamic.field.helper';
import { TranslateHelper } from '../../helper/translate.helper';
import { User } from '../../entities/user';
import { SelectOptionsHelper } from '../../helper/select.options.helper';
import { UserAdminService } from '../service/user.admin.service';
import { InfoLevelType } from '../../message/info.leve.type';
import { ProcessedActionData } from '../../types/processed.action.data';
import { ProcessedAction } from '../../types/processed.action';

/**
 * Dialog for changing the owner of entities.
 */
@Component({
  selector: 'user-change-owner-entities',
  template: ` <p-dialog
    header="{{ 'USER_CHANGE_OWNER_ENTITIES' | translate }}"
    [visible]="visibleDialog()"
    [style]="{ width: '500px' }"
    (onShow)="onShow($event)"
    (onHide)="onHide($event)"
    [modal]="true">
    <dynamic-form
      [config]="config"
      [formConfig]="formConfig"
      [translateService]="translateService"
      #form="dynamicForm"
      (submitBt)="submit($event)" />
  </p-dialog>`,
  imports: [DialogModule, ...DYNAMIC_FORM_IMPORTS, TranslateModule]
})
export class UserChangeOwnerEntitiesDialogComponent extends SimpleFormEditBase implements OnInit {
  public translateService: TranslateService = inject(TranslateService);
  private userAdminService: UserAdminService = inject(UserAdminService);
  private messageToastService: MessageToastService = inject(MessageToastService);

  readonly fromUser = input<User>(undefined);
  readonly allUsers = input<User[]>(undefined);

  constructor() {
    super(HelpIds.HELP_USER);
  }

  ngOnInit(): void {
    this.formConfig = AppHelper.getDefaultFormConfig(this.gps, 5, this.helpLink.bind(this));
    this.config = [
      DynamicFieldHelper.createFieldInputString('fromIdUser', 'USER_CURRENT_OWNER', 30, true, {
        minLength: 2,
        readonly: true
      }),
      DynamicFieldHelper.createFieldSelectNumber('toIdUser', 'USER_NEW_OWNER', true),
      DynamicFieldHelper.createSubmitButton()
    ];
    this.configObject = TranslateHelper.prepareFieldsAndErrors(this.translateService, this.config);
  }

  submit(values: { [name: string]: any }): void {
    this.userAdminService
      .moveCreatedByUserToOtherUser(this.fromUser().idUser, values.toIdUser)
      .subscribe((numberOfChangedEntities) => {
        this.messageToastService.showMessageI18nEnableHtml(InfoLevelType.SUCCESS, 'CHANGED_OWNER_OF_ENTITIES', {
          numberOfChangedEntities
        });
        this.closeDialog.emit(new ProcessedActionData(ProcessedAction.NO_CHANGE, null));
      });
  }

  protected initialize(): void {
    requireFormControl(this.configObject.fromIdUser).setValue(this.fromUser().nickname);
    this.configObject.toIdUser.valueKeyHtmlOptions = SelectOptionsHelper.createValueKeyHtmlSelectOptionsFromArray(
      'idUser',
      'nickname',
      this.allUsers().filter((u) => u.nickname !== this.fromUser().nickname && u.enabled),
      false
    );
  }
}
