import { Component, inject, signal } from '@angular/core';
import { TranslateModule, TranslateService } from '@ngx-translate/core';
import { DialogModule } from '@openng/optimus-ui/dialog';
import { ButtonModule } from '@openng/optimus-ui/button';
import { MessageModule } from '@openng/optimus-ui/message';
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
import { MainDialogService } from '../../mainmenubar/service/main.dialog.service';
import { UserSettingsDialogs } from '../../mainmenubar/component/user-settings-dialogs';
import { SignInSecurityService } from '../service/sign-in-security.service';
import { SecondFactorState, TotpSetup } from '../model/sign-in-security';
import { LoginService } from '../../login/service/log-in.service';
import { PasskeyBrowser } from '../service/passkey-browser';
import { UserPasskeyTableComponent } from './user-passkey-table.component';

/** Fields of the forms of this dialog. */
interface SignInSecurityModel {
  password: string;
  secondFactorCode: string;
  totpCode: string;
}

/** The step the dialog shows: the overview or one of the forms of an action. */
type SignInSecurityStep = 'overview' | 'setupProof' | 'setupScan' | 'renew' | 'disable' | 'codes';

/**
 * Management of the user's own sign-in methods, opened from the settings menu. It holds the table of the passkeys,
 * replaced by a hint where passkeys are not available for this page, and the authenticator app (TOTP): whether it is
 * enabled, how many recovery codes are left, its setup (proof, QR code and secret, confirming code, recovery codes shown
 * once), the renewal of the recovery codes and the disabling.
 *
 * Passwords, codes and the secret are held in the form state of the open dialog and nowhere else; the secret and the
 * recovery codes are dropped when the dialog closes. Every change of the credentials ends the other sessions of the
 * user, and the replacement token of the answer keeps this one. The backend refuses demo accounts.
 */
@Component({
  selector: 'sign-in-security-dialog',
  imports: [
    DialogModule,
    SignalDynamicFormComponent,
    TranslateModule,
    ButtonModule,
    MessageModule,
    UserPasskeyTableComponent
  ],
  template: `<p-dialog
    header="{{ 'SIGN_IN_SECURITY' | translate }}"
    [visible]="visibleDialog()"
    [style]="{ width: '760px' }"
    [modal]="true"
    (onShow)="onShow($event)"
    (onHide)="onHide($event)">
    @if (step() === 'overview') {
      <h4>{{ 'PASSKEYS' | translate }}</h4>
      <p>{{ 'PASSKEY_INTRO' | translate }}</p>
      @let available = passkeyAvailable();
      @if (available === true) {
        <user-passkey-table [totpEnabled]="!!state()?.totpEnabled" (changed)="loadState()" />
      } @else if (available === false) {
        <p-message severity="info" [text]="'PASSKEY_UNAVAILABLE_HINT' | translate" />
      }
    }
    <h4 class="mt-4">{{ 'TOTP_AUTHENTICATOR_APP' | translate }}</h4>
    <p>{{ 'TOTP_INTRO' | translate }}</p>
    @if (state(); as current) {
      <p>
        <b>{{ (current.totpEnabled ? 'TOTP_STATE_ENABLED' : 'TOTP_STATE_DISABLED') | translate }}</b>
        @if (current.totpEnabled) {
          — {{ 'RECOVERY_CODES_LEFT' | translate }}: {{ current.recoveryCodesLeft }}
        }
      </p>
      @switch (step()) {
        @case ('overview') {
          <div class="button-row">
            <p-button [label]="'TOTP_SETUP' | translate" (onClick)="showStep('setupProof')" />
            @if (current.totpEnabled) {
              <p-button [label]="'RECOVERY_CODES_RENEW' | translate" (onClick)="showStep('renew')" />
              <p-button [label]="'TOTP_DISABLE' | translate" severity="danger" (onClick)="showStep('disable')" />
            }
          </div>
        }
        @case ('codes') {
          <p-message severity="warn" [text]="'RECOVERY_CODES_SHOWN_ONCE' | translate" />
          <div class="recovery-codes" aria-label="recovery codes">
            @for (code of recoveryCodes(); track code) {
              <div>{{ code }}</div>
            }
          </div>
          <div class="button-row">
            <p-button [label]="'RECOVERY_CODES_COPY' | translate" (onClick)="copyCodes()" />
            <p-button [label]="'OK' | translate" severity="secondary" (onClick)="showStep('overview')" />
          </div>
        }
        @default {
          @if (step() === 'setupScan') {
            @if (setup(); as created) {
              <p>{{ 'TOTP_SCAN_QR' | translate }}</p>
              <img
                [src]="'data:image/png;base64,' + created.qrCodePng"
                width="240"
                height="240"
                alt="otpauth QR code" />
              <p>
                {{ 'TOTP_SECRET' | translate }}: <code class="totp-secret">{{ created.secretBase32 }}</code>
              </p>
            }
          } @else {
            <p>{{ stepIntroKey() | translate }}</p>
          }
          <signal-dynamic-form
            [config]="config"
            [formConfig]="formConfig"
            #form="signalDynamicForm"
            (submitBt)="submit($event)" />
          <p-button [label]="'CANCEL' | translate" [text]="true" (onClick)="showStep('overview')" />
        }
      }
    }
  </p-dialog>`,
  styles: [
    `
      .button-row {
        display: flex;
        flex-wrap: wrap;
        gap: 0.5rem;
      }
      .recovery-codes {
        font-family: monospace;
        font-size: 1.1rem;
        line-height: 1.6;
        margin: 0.75rem 0;
      }
      .totp-secret {
        word-break: break-all;
      }
    `
  ]
})
export class SignInSecurityDialogComponent extends SimpleEditBase<SignalDynamicFormComponent<SignInSecurityModel>> {
  private readonly translateService = inject(TranslateService);
  private readonly service = inject(SignInSecurityService);
  private readonly messageToastService = inject(MessageToastService);
  private readonly mainDialogService = inject(MainDialogService);
  private readonly loginService = inject(LoginService);

  readonly state = signal<SecondFactorState | null>(null);
  /** Whether this page may use passkeys; null until the login methods of the server arrived. */
  readonly passkeyAvailable = signal<boolean | null>(null);
  readonly step = signal<SignInSecurityStep>('overview');
  /** The secret of the running setup; only held while this dialog is open. */
  readonly setup = signal<TotpSetup | null>(null);
  /** Recovery codes of the last answer; only held while they are shown. */
  readonly recoveryCodes = signal<string[]>([]);
  private readonly descriptors: FieldDescriptorInputAndShow[] =
    JSON.parse(sessionStorage.getItem(GlobalSessionNames.USER_FORM_DEFINITION) ?? 'null') ?? [];

  constructor() {
    super(HelpIds.HELP_INTRO_SETTINGS);
    this.destroyRef.onDestroy(() => this.forgetSecrets());
  }

  protected override initialize(): void {
    this.showStep('overview');
    this.loadState();
    this.loginService
      .getLoginMethods()
      .pipe(this.untilDialogClosed())
      .subscribe((methods) => this.passkeyAvailable.set(PasskeyBrowser.isAvailable(methods.passkeyOrigins)));
  }

  override onHide(event: unknown): void {
    super.onHide(event);
    this.forgetSecrets();
    this.mainDialogService.visibleDialog(false, UserSettingsDialogs.SignInSecurity);
  }

  /** The explanation above the form of the current step. */
  stepIntroKey(): string {
    switch (this.step()) {
      case 'renew':
        return 'RECOVERY_CODES_RENEW_PROOF';
      case 'disable':
        return 'TOTP_DISABLE_PROOF';
      default:
        return 'TOTP_PROOF';
    }
  }

  /** Switches to a step and builds its form; leaving a step forgets what was entered there. */
  showStep(step: SignInSecurityStep): void {
    if (step === 'overview') {
      this.forgetSecrets();
    }
    const fields = this.fieldsOf(step);
    if (fields.length > 0) {
      this.formConfig = AppHelper.getDefaultFormConfig(this.gps, 5, this.helpLink.bind(this));
      this.config = fields;
      this.configObject = TranslateHelper.prepareFieldsAndErrors(this.translateService, this.config, false);
    }
    this.step.set(step);
  }

  submit(value: SignalFormModel): void {
    const password = String(value['password'] ?? '');
    const code = String(value['secondFactorCode'] ?? value['totpCode'] ?? '').trim();
    switch (this.step()) {
      case 'setupProof':
        this.service
          .startTotpSetup(password, code || undefined)
          .pipe(this.untilDialogClosed())
          .subscribe({
            next: (setup) => {
              this.setup.set(setup);
              this.showStep('setupScan');
            },
            error: () => this.form()?.enableSubmit()
          });
        break;
      case 'setupScan':
        this.service
          .enableTotp(code)
          .pipe(this.untilDialogClosed())
          .subscribe({
            next: (codes) => {
              this.messageToastService.showMessageI18n(InfoLevelType.SUCCESS, 'TOTP_ENABLED_SUCCESS');
              this.showCodes(codes.codes);
            },
            error: () => this.form()?.enableSubmit()
          });
        break;
      case 'renew':
        this.service
          .renewRecoveryCodes(code)
          .pipe(this.untilDialogClosed())
          .subscribe({ next: (codes) => this.showCodes(codes.codes), error: () => this.form()?.enableSubmit() });
        break;
      case 'disable':
        this.service
          .disableTotp(password, code)
          .pipe(this.untilDialogClosed())
          .subscribe({
            next: (result) => {
              this.messageToastService.showMessage(InfoLevelType.SUCCESS, result.message);
              this.showStep('overview');
              this.loadState();
            },
            error: () => this.form()?.enableSubmit()
          });
        break;
    }
  }

  async copyCodes(): Promise<void> {
    const codes = this.recoveryCodes();
    if (codes.length === 0) return;
    await navigator.clipboard.writeText(codes.join('\n'));
    if (!this.destroyRef.destroyed) {
      this.messageToastService.showMessageI18n(InfoLevelType.SUCCESS, 'RECOVERY_CODES_COPIED');
    }
  }

  private showCodes(codes: string[]): void {
    this.setup.set(null);
    this.recoveryCodes.set(codes);
    this.step.set('codes');
    this.loadState();
  }

  protected loadState(): void {
    this.service
      .getState()
      .pipe(this.untilDialogClosed())
      .subscribe((state) => this.state.set(state));
  }

  private forgetSecrets(): void {
    this.setup.set(null);
    this.recoveryCodes.set([]);
  }

  /** The fields of a step; a step without form returns none. */
  private fieldsOf(step: SignInSecurityStep): FieldConfig[] {
    const totpEnabled = !!this.state()?.totpEnabled;
    const password = () =>
      DynamicFieldModelHelper.ccWithFieldsFromDescriptorHeqF(this.translateService, 'password', this.descriptors);
    const secondFactorCode = () => DynamicFieldHelper.createFieldInputStringHeqF('secondFactorCode', 16, true);
    const totpCode = () => DynamicFieldHelper.createFieldInputStringHeqF('totpCode', 6, true);
    switch (step) {
      case 'setupProof':
        return [
          password(),
          ...(totpEnabled ? [secondFactorCode()] : []),
          DynamicFieldHelper.createSubmitButton('TOTP_SETUP')
        ];
      case 'setupScan':
        return [totpCode(), DynamicFieldHelper.createSubmitButton('TOTP_ENABLE')];
      case 'renew':
        return [totpCode(), DynamicFieldHelper.createSubmitButton('RECOVERY_CODES_RENEW')];
      case 'disable':
        return [password(), secondFactorCode(), DynamicFieldHelper.createSubmitButton('TOTP_DISABLE')];
      default:
        return [];
    }
  }
}
