import { ChangeDetectionStrategy, Component, Input, OnInit } from '@angular/core';
import { TranslateModule, TranslateService } from '@ngx-translate/core';
import { DialogModule } from '@openng/optimus-ui/dialog';
import { ButtonModule } from '@openng/optimus-ui/button';
import { MessageModule } from '@openng/optimus-ui/message';
import { SimpleEditBase } from '../../edit/simple.edit.base';
import { DynamicFormModule } from '../../dynamic-form/dynamic-form.module';
import { DynamicFieldModelHelper } from '../../helper/dynamic.field.model.helper';
import { ClassDescriptorInputAndShow } from '../../dynamicfield/field.descriptor.input.and.show';
import { FieldConfig } from '../../dynamic-form/models/field.config';
import { AppHelper } from '../../helper/app.helper';
import { TranslateHelper } from '../../helper/translate.helper';
import { GlobalparameterService } from '../../services/globalparameter.service';
import { MessageToastService } from '../../message/message.toast.service';
import { InfoLevelType } from '../../message/info.leve.type';
import { HelpIds } from '../../help/help.ids';
import { ProcessedActionData } from '../../types/processed.action.data';
import { ProcessedAction } from '../../types/processed.action';
import { UserAccessTokenService } from '../service/user-access-token.service';
import { UserAccessTokenCreated } from '../model/user-access-token';

/**
 * Creates a personal access token. The form comes from the backend definition of the creation request. After a
 * successful creation the form gives way to the token itself, which is shown this one time only and can be copied.
 * Closing the dialog then reports the new token to the table.
 */
@Component({
  selector: 'user-access-token-create-dialog',
  standalone: true,
  changeDetection: ChangeDetectionStrategy.Eager,
  imports: [DialogModule, DynamicFormModule, TranslateModule, ButtonModule, MessageModule],
  template: `<p-dialog
    [header]="'ACCESS_TOKEN_CREATE' | translate"
    [visible]="visibleDialog"
    [style]="{ width: '560px' }"
    [modal]="true"
    appendTo="body"
    (onShow)="onShow($event)"
    (onHide)="onHide($event)">
    @if (!created) {
      <dynamic-form
        [config]="config"
        [formConfig]="formConfig"
        [translateService]="translateService"
        #form="dynamicForm"
        (submitBt)="submit($event)"></dynamic-form>
    } @else {
      <p-message severity="warn" [text]="'ACCESS_TOKEN_SHOWN_ONCE' | translate" />
      <div style="display: flex; gap: 0.5rem; align-items: center; margin-top: 0.75rem">
        <input
          type="text"
          readonly
          [value]="created.plainToken"
          style="flex: 1; font-family: monospace"
          aria-label="token" />
        <p-button [label]="'ACCESS_TOKEN_COPY' | translate" (onClick)="copyToken()" />
      </div>
    }
  </p-dialog>`
})
export class UserAccessTokenCreateDialogComponent extends SimpleEditBase implements OnInit {
  @Input() formDefinition: ClassDescriptorInputAndShow;
  /** Set once the server created the token; it is held only as long as this dialog is open. */
  created: UserAccessTokenCreated;

  constructor(
    public translateService: TranslateService,
    gps: GlobalparameterService,
    private messageToastService: MessageToastService,
    private userAccessTokenService: UserAccessTokenService
  ) {
    super(HelpIds.HELP_INTRO_SETTINGS_AI_ASSISTANT, gps);
  }

  ngOnInit(): void {
    this.formConfig = AppHelper.getDefaultFormConfig(this.gps, 5, this.helpLink.bind(this));
    this.config = DynamicFieldModelHelper.createFieldsFromClassDescriptorInputAndShow(
      this.translateService,
      this.formDefinition,
      '',
      true
    ) as FieldConfig[];
    this.configObject = TranslateHelper.prepareFieldsAndErrors(this.translateService, this.config);
  }

  protected override initialize(): void {
    this.created = null;
    this.form.setDefaultValuesAndEnableSubmit();
    this.form.setValue('expiresInDays', 90);
  }

  submit(value: { [name: string]: any }): void {
    this.userAccessTokenService
      .create({ tokenName: value['tokenName'], scope: 'READ', expiresInDays: value['expiresInDays'] })
      .subscribe({
        next: (created) => (this.created = created),
        error: () => (this.configObject.submit.disabled = false)
      });
  }

  async copyToken(): Promise<void> {
    await navigator.clipboard.writeText(this.created.plainToken);
    this.messageToastService.showMessageI18n(InfoLevelType.SUCCESS, 'ACCESS_TOKEN_COPIED');
  }

  override onHide(event: any): void {
    if (this.visibleDialog) {
      // The clear text must not outlive the dialog
      const action = this.created ? ProcessedAction.CREATED : ProcessedAction.NO_CHANGE;
      this.created = null;
      this.closeDialog.emit(new ProcessedActionData(action));
    }
  }
}
