import { requireFormControl } from '../../dynamic-form/models/form-control-access';
import { Component, DestroyRef, inject, OnInit, signal, viewChild, input } from '@angular/core';
import { takeUntilDestroyed } from '@angular/core/rxjs-interop';
import { Router } from '@angular/router';
import { LoginService } from '../service/log-in.service';
import { TranslateModule, TranslateService } from '@ngx-translate/core';
import { DynamicFormComponent } from '../../dynamic-form/containers/dynamic-form/dynamic-form.component';
import { GlobalparameterService } from '../../services/globalparameter.service';
import { DynamicFieldHelper } from '../../helper/dynamic.field.helper';
import { TranslateHelper } from '../../helper/translate.helper';
import { FormBase } from '../../edit/form.base';
import { DialogService } from '@openng/optimus-ui/dynamicdialog';
import { ActuatorService, ApplicationInfo } from '../../services/actuator.service';
import { combineLatest } from 'rxjs';
import { FieldDescriptorInputAndShow } from '../../dynamicfield/field.descriptor.input.and.show';
import { GlobalSessionNames } from '../../global.session.names';
import { DynamicFieldModelHelper } from '../../helper/dynamic.field.model.helper';
import { AppHelper } from '../../helper/app.helper';
import { DynamicDialogs } from '../../dynamicdialog/component/dynamic.dialogs';
import { ReleaseNote, ReleaseNoteService } from '../service/release.note.service';
import { HelpIds } from '../../help/help.ids';
import { BaseSettings } from '../../base.settings';
import { DYNAMIC_FORM_IMPORTS } from '../../dynamic-form/dynamic-form.imports';
import { CardModule } from '@openng/optimus-ui/card';
import { ApplicationInfoComponent } from './application-info.component';
import { PasswordEditComponent } from './password-edit.component';

/**
 * Shows the login form
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
        @if (formConfig) {
          <h2>{{ 'SIGN_IN' | translate }}</h2>
          <dynamic-form
            [config]="config"
            [formConfig]="formConfig"
            [translateService]="translateService"
            #form="dynamicForm"
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
  imports: [...DYNAMIC_FORM_IMPORTS, TranslateModule, CardModule, ApplicationInfoComponent, PasswordEditComponent]
})
export class LoginComponent extends FormBase implements OnInit {
  private router: Router = inject(Router);
  private releaseNoteService: ReleaseNoteService = inject(ReleaseNoteService);
  private loginService: LoginService = inject(LoginService);
  private actuatorService: ActuatorService = inject(ActuatorService);
  public translateService: TranslateService = inject(TranslateService);
  private dialogService: DialogService = inject(DialogService);
  private gps: GlobalparameterService = inject(GlobalparameterService);

  readonly form = viewChild(DynamicFormComponent);
  readonly applicationInfo = signal<ApplicationInfo | null | undefined>(undefined);
  readonly releaseNotes = signal<ReleaseNote[]>([]);
  readonly successLastRegistration = input<string | undefined>(undefined, { alias: 'success' });
  readonly visiblePasswordDialog = signal(false);
  private readonly destroyRef = inject(DestroyRef);

  constructor() {
    super();
  }

  ngOnInit(): void {
    combineLatest([
      this.actuatorService.applicationInfo(),
      this.gps.getUserFormDefinitions(),
      this.releaseNoteService.getTopReleaseNotes()
    ])
      .pipe(takeUntilDestroyed(this.destroyRef))
      .subscribe({
        next: (
          data: [applicationInfo: ApplicationInfo, fdias: FieldDescriptorInputAndShow[], releaseNotes: ReleaseNote[]]
        ) => {
          this.applicationInfo.set(data[0]);
          sessionStorage.setItem(GlobalSessionNames.USER_FORM_DEFINITION, JSON.stringify(data[1]));
          this.loginFormDefinition(data[1]);
          this.releaseNotes.set(data[2] ?? []);
        },
        error: () => this.applicationInfo.set(null)
      });
  }

  submit(value: { [name: string]: any }): void {
    this.loginService.login(value.email, value.password).subscribe({
      next: (response) => {
        const token = response.headers.get('x-auth-token');
        const configuration = response.body;
        if (!token || !configuration) {
          this.configObject.submit.disabled = false;
          return;
        }
        const passwordRegexOk = this.loginService.afterSuccessfulLogin(token, configuration);
        if (this.gps.getIdTenant()) {
          this.navigateToMainView(passwordRegexOk);
        } else {
          // It is a new tenant -> setup is required
          this.router.navigate([`/${BaseSettings.TENANT_KEY}`]);
        }
      },
      error: (errorBackend) => {
        this.configObject.submit.disabled = false;

        if (errorBackend.bringUpAdminSelfReleaseDialog) {
          DynamicDialogs.getOpenedAdminSelfReleaseDynamicComponent(
            this.translateService,
            this.dialogService,
            this.loginService,
            this.gps,
            this.router,
            value.email,
            value.password
          );
        } else if (errorBackend.bringUpDialog) {
          DynamicDialogs.getOpenedLogoutReleaseRequestDynamicComponent(
            this.translateService,
            this.dialogService,
            value.email,
            value.password
          );
        }
      }
    });
  }

  private navigateToMainView(passwordRegexOk: boolean): void {
    if (!passwordRegexOk) {
      this.visiblePasswordDialog.set(true);
      this.configObject.submit.disabled = false;
      requireFormControl(this.configObject.password).setValue('');
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

  private loginFormDefinition(fdias: FieldDescriptorInputAndShow[]): void {
    this.formConfig = {
      labelColumns: 2,
      helpLinkFN: this.helpLink.bind(this),
      nonModal: true,
      language: AppHelper.getNonUserDefinedLanguage(this.translateService.currentLang)
    };
    this.config = [];
    this.config.push(DynamicFieldModelHelper.ccWithFieldsFromDescriptorHeqF(this.translateService, 'email', fdias));
    this.config.push(DynamicFieldModelHelper.ccWithFieldsFromDescriptorHeqF(this.translateService, 'password', fdias));

    const users = this.applicationInfo()?.users;
    if (users && users.active < users.allowed) {
      this.config.push(
        DynamicFieldHelper.createFunctionButton('REGISTRATION', (e) =>
          this.router.navigate([`/${BaseSettings.REGISTER_KEY}`])
        )
      );
    }
    this.config.push(DynamicFieldHelper.createSubmitButton('SIGN_IN'));
    this.configObject = TranslateHelper.prepareFieldsAndErrors(this.translateService, this.config);
  }
}
