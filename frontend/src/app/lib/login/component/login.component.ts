import {
  afterNextRender,
  Component,
  DestroyRef,
  inject,
  Injector,
  OnInit,
  signal,
  viewChild,
  input
} from '@angular/core';
import { takeUntilDestroyed } from '@angular/core/rxjs-interop';
import { HttpResponse } from '@angular/common/http';
import { Router } from '@angular/router';
import { hidden, SchemaPathTree } from '@angular/forms/signals';
import { LoginService } from '../service/log-in.service';
import { TranslateModule, TranslateService } from '@ngx-translate/core';
import { SignalDynamicFormComponent } from '../../dynamic-form/signal/signal-dynamic-form.component';
import { SignalFormModel } from '../../dynamic-form/signal/signal-form.model';
import { GlobalparameterService } from '../../services/globalparameter.service';
import { DynamicFieldHelper } from '../../helper/dynamic.field.helper';
import { TranslateHelper } from '../../helper/translate.helper';
import { FormBase } from '../../edit/form.base';
import { DialogService } from '@openng/optimus-ui/dynamicdialog';
import { ActuatorService, ApplicationInfo } from '../../services/actuator.service';
import { catchError, combineLatest, EMPTY, from, of, switchMap } from 'rxjs';
import { FieldDescriptorInputAndShow } from '../../dynamicfield/field.descriptor.input.and.show';
import { GlobalSessionNames } from '../../global.session.names';
import { DynamicFieldModelHelper } from '../../helper/dynamic.field.model.helper';
import { AppHelper } from '../../helper/app.helper';
import { DynamicDialogs } from '../../dynamicdialog/component/dynamic.dialogs';
import { ReleaseNote, ReleaseNoteService } from '../service/release.note.service';
import { HelpIds } from '../../help/help.ids';
import { BaseSettings } from '../../base.settings';
import { CardModule } from '@openng/optimus-ui/card';
import { ApplicationInfoComponent } from './application-info.component';
import { PasswordEditComponent } from './password-edit.component';
import { ConfigurationWithLogin } from '../model';
import { TransformedError } from '../service/transformed.error';
import { SecondFactorRequiredError } from '../service/second.factor.required.error';
import { PasskeyBrowser } from '../../signinsecurity/service/passkey-browser';
import { LoginMethods } from '../../signinsecurity/model/sign-in-security';
import { MessageToastService } from '../../message/message.toast.service';
import { InfoLevelType } from '../../message/info.leve.type';

/** Fields of the login form its rules refer to. */
interface LoginFormModel {
  email: string;
  password: string;
  secondFactorCode: string;
}

/**
 * Shows the login form. For an account with an enabled second factor the server answers the first attempt with
 * SecondFactorRequiredError; the form then shows the code field and the user repeats the sign-in with e-mail, password
 * and code. The field accepts six digits of an authenticator app and recovery codes.
 *
 * Where the server offers passkeys for the origin of this page and the browser supports them, a button signs in with a
 * passkey instead: no e-mail address is typed, the browser offers the passkeys of this site. Both ways end in the same
 * completion, which also returns to the address the sign-in interrupted (the OAuth authorize page depends on it).
 */
@Component({
  template: `
    <div class="container">
      <div class="login jumbotron-replacement mx-auto">
        @if (successLastRegistration()) {
          <div class="alert alert-success" role="alert">
            {{ successLastRegistration() | translate }}
          </div>
        }
        <application-info [applicationInfo]="applicationInfo()" />
        @if (formReady()) {
          <h2>{{ 'SIGN_IN' | translate }}</h2>
          <signal-dynamic-form
            [config]="config"
            [formConfig]="formConfig"
            [rules]="rules"
            #form="signalDynamicForm"
            (submitBt)="submit($event)" />
        }
        @if (releaseNotes().length > 0) {
          <p-card class="mt-2 d-block" header="{{ 'RELEASE_NOTE' | translate }}">
            @for (note of releaseNotes(); track note.idReleaseNote) {
              <div class="mb-3">
                <h4>{{ note.version }}</h4>
                {{ note.note }}
              </div>
            }
          </p-card>
        } @else {
          <p-card class="mt-2 d-block" header="{{ 'RELEASE_NOTE' | translate }}">
            <div class="text-center text-muted">
              <i class="pi pi-info-circle me-2"></i>
              {{ 'NO_RELEASE_NOTES_AVAILABLE' | translate }}
            </div>
          </p-card>
        }
      </div>
    </div>

    @if (visiblePasswordDialog()) {
      <password-edit [forcePasswordChange]="true" [visibleDialog]="visiblePasswordDialog()" />
    }
  `,
  providers: [DialogService],
  imports: [SignalDynamicFormComponent, TranslateModule, CardModule, ApplicationInfoComponent, PasswordEditComponent]
})
export class LoginComponent extends FormBase implements OnInit {
  private router: Router = inject(Router);
  private releaseNoteService: ReleaseNoteService = inject(ReleaseNoteService);
  private loginService: LoginService = inject(LoginService);
  private actuatorService: ActuatorService = inject(ActuatorService);
  public translateService: TranslateService = inject(TranslateService);
  private dialogService: DialogService = inject(DialogService);
  private gps: GlobalparameterService = inject(GlobalparameterService);
  private messageToastService: MessageToastService = inject(MessageToastService);
  private readonly injector = inject(Injector);

  readonly form = viewChild(SignalDynamicFormComponent<LoginFormModel>);
  readonly applicationInfo = signal<ApplicationInfo | null | undefined>(undefined);
  readonly releaseNotes = signal<ReleaseNote[]>([]);
  readonly successLastRegistration = input<string | undefined>(undefined, { alias: 'success' });
  readonly visiblePasswordDialog = signal(false);
  readonly formReady = signal(false);
  /** Set once the server asked for the code of the second factor; shows the code field. */
  readonly secondFactorRequired = signal(false);
  private readonly destroyRef = inject(DestroyRef);

  readonly rules = (p: SchemaPathTree<LoginFormModel>): void => {
    hidden(p.secondFactorCode, { when: () => !this.secondFactorRequired() });
  };

  constructor() {
    super();
  }

  ngOnInit(): void {
    combineLatest([
      this.actuatorService.applicationInfo(),
      this.gps.getUserFormDefinitions(),
      this.releaseNoteService.getTopReleaseNotes(),
      // A failure leaves the password sign-in alone
      this.loginService.getLoginMethods().pipe(catchError(() => of<LoginMethods>({ passkeyOrigins: [] })))
    ])
      .pipe(takeUntilDestroyed(this.destroyRef))
      .subscribe({
        next: (
          data: [
            applicationInfo: ApplicationInfo,
            fdias: FieldDescriptorInputAndShow[],
            releaseNotes: ReleaseNote[],
            loginMethods: LoginMethods
          ]
        ) => {
          this.applicationInfo.set(data[0]);
          sessionStorage.setItem(GlobalSessionNames.USER_FORM_DEFINITION, JSON.stringify(data[1]));
          this.loginFormDefinition(data[1], PasskeyBrowser.isAvailable(data[3].passkeyOrigins));
          this.releaseNotes.set(data[2] ?? []);
        },
        error: () => this.applicationInfo.set(null)
      });
  }

  submit(value: SignalFormModel): void {
    const email = String(value['email']);
    const password = String(value['password']);
    const code = this.secondFactorRequired() ? String(value['secondFactorCode'] ?? '').trim() : '';
    this.loginService.login(email, password, undefined, code || undefined).subscribe({
      next: (response) => this.completeSignIn(response),
      error: (errorBackend: TransformedError) => {
        this.form()?.enableSubmit();
        if (errorBackend.secondFactorRequired) {
          this.askForSecondFactor(errorBackend);
        } else if (errorBackend.bringUpAdminSelfReleaseDialog) {
          DynamicDialogs.getOpenedAdminSelfReleaseDynamicComponent(
            this.translateService,
            this.dialogService,
            this.loginService,
            this.gps,
            this.router,
            email,
            password,
            !!code
          );
        } else if (errorBackend.bringUpDialog) {
          DynamicDialogs.getOpenedLogoutReleaseRequestDynamicComponent(
            this.translateService,
            this.dialogService,
            email,
            password,
            !!code
          );
        }
      }
    });
  }

  /**
   * Signs in with a passkey: options of the server, the browser dialog, then the verification. A cancelled browser
   * dialog ends quietly; server errors are reported by the service.
   */
  private signInWithPasskey(): void {
    this.loginService
      .passkeySignInOptions()
      .pipe(
        switchMap((options) =>
          from(PasskeyBrowser.get(options.publicKey)).pipe(
            switchMap((credential) =>
              credential ? this.loginService.passkeySignIn(options.challengeId, credential) : EMPTY
            )
          )
        ),
        takeUntilDestroyed(this.destroyRef)
      )
      .subscribe({
        next: (response) => this.completeSignIn(response),
        error: (error: unknown) => {
          if (error instanceof DOMException) {
            this.messageToastService.showMessage(InfoLevelType.ERROR, error.message);
          }
        }
      });
  }

  /**
   * Finishes a successful sign-in: stores the session, sends a user without tenant to the tenant setup and otherwise
   * returns to the address the sign-in interrupted. Shared by every sign-in method of this page.
   *
   * @param response - Answer of the sign-in with the token header and the configuration body
   */
  private completeSignIn(response: HttpResponse<ConfigurationWithLogin>): void {
    const token = response.headers.get('x-auth-token');
    const configuration = response.body;
    if (!token || !configuration) {
      this.form()?.enableSubmit();
      return;
    }
    const passwordRegexOk = this.loginService.afterSuccessfulLogin(token, configuration);
    this.secondFactorRequired.set(false);
    if (this.gps.getIdTenant()) {
      this.navigateToMainView(passwordRegexOk);
    } else {
      // It is a new tenant -> setup is required
      this.router.navigate([`/${BaseSettings.TENANT_KEY}`]);
    }
  }

  /** Shows the code field, empties a refused code and puts the cursor there. */
  private askForSecondFactor(errorBackend: TransformedError): void {
    const errorClass = errorBackend.errorClass;
    if (this.secondFactorRequired() && errorClass instanceof SecondFactorRequiredError && errorClass.invalidCode) {
      this.form()?.setValue('secondFactorCode', '');
    }
    this.secondFactorRequired.set(true);
    afterNextRender(() => this.configObject['secondFactorCode']?.baseInputComponent?.focus(), {
      injector: this.injector
    });
  }

  private navigateToMainView(passwordRegexOk: boolean): void {
    if (!passwordRegexOk) {
      this.visiblePasswordDialog.set(true);
      this.form()?.enableSubmit();
      this.form()?.setValue('password', '');
    } else {
      const returnUrl = sessionStorage.getItem(GlobalSessionNames.RETURN_AFTER_LOGIN);
      sessionStorage.removeItem(GlobalSessionNames.RETURN_AFTER_LOGIN);
      // Only an in-app route is accepted, never an absolute address
      if (returnUrl?.startsWith('/') && !returnUrl.startsWith('//')) {
        this.router.navigateByUrl(returnUrl);
      } else {
        this.router.navigate([`/${BaseSettings.MAINVIEW_KEY}`]);
      }
    }
  }

  helpLink(): void {
    this.gps.toExternalHelpWebpage(this.translateService.currentLang, HelpIds.HELP_INTRO);
  }

  private loginFormDefinition(fdias: FieldDescriptorInputAndShow[], passkeyAvailable: boolean): void {
    this.formConfig = {
      labelColumns: 2,
      helpLinkFN: this.helpLink.bind(this),
      nonModal: true,
      language: AppHelper.getNonUserDefinedLanguage(this.translateService.currentLang)
    };
    this.config = [];
    this.config.push(DynamicFieldModelHelper.ccWithFieldsFromDescriptorHeqF(this.translateService, 'email', fdias));
    this.config.push(DynamicFieldModelHelper.ccWithFieldsFromDescriptorHeqF(this.translateService, 'password', fdias));
    // Six digits of an authenticator app or a recovery code XXXXX-XXXXX; hidden until the server asks for it
    this.config.push(DynamicFieldHelper.createFieldInputStringHeqF('secondFactorCode', 16, true));

    const users = this.applicationInfo()?.users;
    if (users && users.active < users.allowed) {
      this.config.push(
        DynamicFieldHelper.createFunctionButton('REGISTRATION', () =>
          this.router.navigate([`/${BaseSettings.REGISTER_KEY}`])
        )
      );
    }
    this.config.push(
      DynamicFieldHelper.createFunctionButton('FORGOT_PASSWORD', () => this.router.navigate(['/passwordreset']))
    );
    if (passkeyAvailable) {
      this.config.push(
        DynamicFieldHelper.createFunctionButtonFieldName('signInWithPasskey', 'SIGN_IN_WITH_PASSKEY', () =>
          this.signInWithPasskey()
        )
      );
    }
    this.config.push(DynamicFieldHelper.createSubmitButton('SIGN_IN'));
    this.configObject = TranslateHelper.prepareFieldsAndErrors(this.translateService, this.config, false);
    this.formReady.set(true);
  }
}
