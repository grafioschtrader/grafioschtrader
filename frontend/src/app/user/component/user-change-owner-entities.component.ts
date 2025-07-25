import {Component, Input, OnInit} from '@angular/core';
import {SimpleEditBase} from '../../lib/edit/simple.edit.base';
import {TranslateService} from '@ngx-translate/core';
import {MessageToastService} from '../../lib/message/message.toast.service';
import {MainDialogService} from '../../shared/mainmenubar/service/main.dialog.service';
import {GlobalparameterService} from '../../shared/service/globalparameter.service';
import {HelpIds} from '../../shared/help/help.ids';
import {UserSettingsDialogs} from '../../shared/mainmenubar/component/main.dialog.component';
import {AppHelper} from '../../lib/helper/app.helper';
import {DynamicFieldHelper} from '../../lib/helper/dynamic.field.helper';
import {TranslateHelper} from '../../lib/helper/translate.helper';
import {User} from '../../lib/entities/user';
import {SelectOptionsHelper} from '../../lib/helper/select.options.helper';
import {UserAdminService} from '../service/user.admin.service';
import {InfoLevelType} from '../../lib/message/info.leve.type';
import {ProcessedActionData} from '../../lib/types/processed.action.data';
import {ProcessedAction} from '../../lib/types/processed.action';

/**
 * Dialog for changing the owner of entities.
 */
@Component({
    selector: 'user-change-owner-entities',
    template: `
    <p-dialog header="{{'USER_CHANGE_OWNER_ENTITIES' | translate}}" [(visible)]="visibleDialog"
              [style]="{width: '500px'}"
              (onShow)="onShow($event)" (onHide)="onHide($event)" [modal]="true">

      <dynamic-form [config]="config" [formConfig]="formConfig" [translateService]="translateService"
                    #form="dynamicForm"
                    (submitBt)="submit($event)">
      </dynamic-form>
    </p-dialog>`,
    standalone: false
})

export class UserChangeOwnerEntitiesComponent extends SimpleEditBase implements OnInit {
  @Input() fromUser: User;
  @Input() allUsers: User[];

  constructor(public translateService: TranslateService,
              private userAdminService: UserAdminService,
              private messageToastService: MessageToastService,
              private mainDialogService: MainDialogService,
              gps: GlobalparameterService) {
    super(HelpIds.HELP_USER, gps);
  }

  ngOnInit(): void {
    this.formConfig = AppHelper.getDefaultFormConfig(this.gps,
      5, this.helpLink.bind(this));
    this.config = [
      DynamicFieldHelper.createFieldInputString('fromIdUser', 'USER_CURRENT_OWNER', 30, true,
        {minLength: 2, readonly: true}),
      DynamicFieldHelper.createFieldSelectNumber('toIdUser', 'USER_NEW_OWNER', true),
      DynamicFieldHelper.createSubmitButton()
    ];
    this.configObject = TranslateHelper.prepareFieldsAndErrors(this.translateService, this.config);
  }

  override onHide(event) {
    this.mainDialogService.visibleDialog(false, UserSettingsDialogs.NicknameLocale);
  }

  submit(values: { [name: string]: any }): void {
    this.userAdminService.moveCreatedByUserToOtherUser(this.fromUser.idUser, values.toIdUser).subscribe(
      numberOfChangedEntities => {
        this.messageToastService.showMessageI18nEnableHtml(InfoLevelType.SUCCESS,
          'CHANGED_OWNER_OF_ENTITIES', {numberOfChangedEntities});
        this.closeDialog.emit(new ProcessedActionData(ProcessedAction.NO_CHANGE, null));
      });
  }

  protected initialize(): void {
    this.configObject.fromIdUser.formControl.setValue(this.fromUser.nickname);
    this.configObject.toIdUser.valueKeyHtmlOptions = SelectOptionsHelper.createValueKeyHtmlSelectOptionsFromArray('idUser', 'nickname',
      this.allUsers.filter(u => u.nickname !== this.fromUser.nickname && u.enabled), false);
  }
}

