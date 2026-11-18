import { Component, inject, input, OnInit } from '@angular/core';
import { TranslateModule, TranslateService } from '@ngx-translate/core';
import { DialogModule } from '@openng/optimus-ui/dialog';
import { from, of, switchMap } from 'rxjs';
import { SimpleEditBase } from '../../edit/simple.edit.base';
import { SignalDynamicFormComponent } from '../../dynamic-form/signal/signal-dynamic-form.component';
import { SignalFormModel } from '../../dynamic-form/signal/signal-form.model';
import { FieldConfig } from '../../dynamic-form/models/field.config';
import { DynamicFieldHelper } from '../../helper/dynamic.field.helper';
import { DynamicFieldModelHelper } from '../../helper/dynamic.field.model.helper';
import { TranslateHelper } from '../../helper/translate.helper';
import { AppHelper } from '../../helper/app.helper';
import { HelpIds } from '../../help/help.ids';
import { GlobalSessionNames } from '../../global.session.names';
import { FieldDescriptorInputAndShow } from '../../dynamicfield/field.descriptor.input.and.show';
import { MessageToastService } from '../../message/message.toast.service';
import { InfoLevelType } from '../../message/info.leve.type';
import { ProcessedActionData } from '../../types/processed.action.data';
import { ProcessedAction } from '../../types/processed.action';
import { UserPasskey } from '../model/sign-in-security';
import { UserPasskeyService } from '../service/user-passkey.service';
import { PasskeyBrowser } from '../service/passkey-browser';

/** Fields of the passkey dialog. */
interface PasskeyFormModel {
  password: string;
  secondFactorCode: string;
  label: string;
}

/** Longest name of a passkey, as the server accepts it. */
const LABEL_MAX_LENGTH = 40;

/**
 * Adds a passkey or renames one. Adding asks for the proof of a new sign-in method - the password and, while an
 * authenticator app is enabled, a code - and a name; then the browser asks for the device. A cancelled browser dialog
 * leaves the form open without a message. Renaming needs no proof.
 */
@Component({
  selector: 'user-passkey-edit-dialog',
  imports: [DialogModule, SignalDynamicFormComponent, TranslateModule],
  template: `<p-dialog
    [header]="(passkey() ? 'USER_PASSKEY' : 'PASSKEY_ADD') | translate"
    [visible]="visibleDialog()"
    [style]="{ width: '520px' }"
    [modal]="true"
    appendTo="body"
    (onShow)="onShow($event)"
    (onHide)="onHide($event)">
    @if (!passkey()) {
      <p>{{ 'PASSKEY_ADD_PROOF' | translate }}</p>
    }
    <signal-dynamic-form
      [config]="config"
      [formConfig]="formConfig"
      [values]="startValues"
      #form="signalDynamicForm"
      (submitBt)="submit($event)" />
  </p-dialog>`
})
export class UserPasskeyEditDialogComponent
  extends SimpleEditBase<SignalDynamicFormComponent<PasskeyFormModel>>
  implements OnInit
{
  private readonly translateService = inject(TranslateService);
  private readonly service = inject(UserPasskeyService);
  private readonly messageToastService = inject(MessageToastService);

  /** The passkey to rename, null for adding one. */
  readonly passkey = input<UserPasskey | null>(null);
  /** While an authenticator app is enabled, adding needs one of its codes as well. */
  readonly totpEnabled = input(false);
  startValues: Partial<PasskeyFormModel> = {};

  constructor() {
    super(HelpIds.HELP_INTRO_SETTINGS);
  }

  ngOnInit(): void {
    const existing = this.passkey();
    this.formConfig = AppHelper.getDefaultFormConfig(this.gps, 5, this.helpLink.bind(this));
    const label = DynamicFieldHelper.createFieldInputString('label', 'PASSKEY_LABEL', LABEL_MAX_LENGTH, true);
    const fields: FieldConfig[] = [];
    if (!existing) {
      const descriptors: FieldDescriptorInputAndShow[] =
        JSON.parse(sessionStorage.getItem(GlobalSessionNames.USER_FORM_DEFINITION) ?? 'null') ?? [];
      fields.push(
        DynamicFieldModelHelper.ccWithFieldsFromDescriptorHeqF(this.translateService, 'password', descriptors)
      );
      if (this.totpEnabled()) {
        fields.push(DynamicFieldHelper.createFieldInputStringHeqF('secondFactorCode', 16, true));
      }
    }
    fields.push(label, DynamicFieldHelper.createSubmitButton(existing ? 'SAVE' : 'PASSKEY_ADD'));
    this.config = fields;
    this.startValues = { label: existing?.label ?? '' };
    this.configObject = TranslateHelper.prepareFieldsAndErrors(this.translateService, this.config, false);
  }

  protected override initialize(): void {}

  submit(value: SignalFormModel): void {
    const label = String(value['label'] ?? '').trim();
    const existing = this.passkey();
    if (existing?.idUserPasskey != null) {
      this.service
        .rename(existing.idUserPasskey, label)
        .pipe(this.untilDialogClosed())
        .subscribe({
          next: () => this.closeDialog.emit(new ProcessedActionData(ProcessedAction.UPDATED)),
          error: () => this.form()?.enableSubmit()
        });
      return;
    }
    const code = String(value['secondFactorCode'] ?? '').trim();
    this.service
      .registrationOptions(String(value['password'] ?? ''), code || undefined)
      .pipe(
        switchMap((options) =>
          from(PasskeyBrowser.create(options.publicKey)).pipe(
            switchMap((credential) =>
              credential ? this.service.register(options.challengeId, label, credential) : of(null)
            )
          )
        ),
        this.untilDialogClosed()
      )
      .subscribe({
        next: (created) => {
          if (created) {
            this.messageToastService.showMessageI18n(InfoLevelType.SUCCESS, 'PASSKEY_ADDED');
            this.closeDialog.emit(new ProcessedActionData(ProcessedAction.CREATED));
          } else {
            // Cancelled in the browser; the proof was used, so a new attempt starts with the password again
            this.form()?.setValue('password', '');
            this.form()?.enableSubmit();
          }
        },
        error: (error: unknown) => {
          if (error instanceof DOMException) {
            this.messageToastService.showMessage(InfoLevelType.ERROR, error.message);
          }
          this.form()?.enableSubmit();
        }
      });
  }
}
