import { afterNextRender, Component, OnInit, signal, inject, input } from '@angular/core';
import { takeUntilDestroyed } from '@angular/core/rxjs-interop';
import { Router } from '@angular/router';
import { LoginService } from '../service/log-in.service';
import { TranslateModule } from '@ngx-translate/core';
import { MessageToastService } from '../../message/message.toast.service';
import { PasswordRegexProperties } from '../../services/globalparameter.service';
import { User } from '../../entities/user';
import { InfoLevelType } from '../../message/info.leve.type';
import { PasswordBaseComponent } from './password.base.component';
import { DynamicFieldHelper } from '../../helper/dynamic.field.helper';
import { TranslateHelper } from '../../helper/translate.helper';
import { ActuatorService, ApplicationInfo } from '../../services/actuator.service';
import { combineLatest } from 'rxjs';
import { FieldDescriptorInputAndShow } from '../../dynamicfield/field.descriptor.input.and.show';
import { GlobalSessionNames } from '../../global.session.names';
import { DynamicFieldModelHelper } from '../../helper/dynamic.field.model.helper';
import { HelpIds } from '../../help/help.ids';
import { BaseSettings } from '../../base.settings';
import { DYNAMIC_FORM_IMPORTS } from '../../dynamic-form/dynamic-form.imports';
import { ProgressBarModule } from '@openng/optimus-ui/progressbar';
import { ApplicationInfoComponent } from './application-info.component';

/**
 * Shows the user register form.
 */
@Component({
  template: `
    <div class="container">
      <div class="login jumbotron-replacement mx-auto">
        @if (errorLastRegistration()) {
          <div class="alert alert-danger" role="alert">
            {{ errorLastRegistration() | translate }}
          </div>
        }

        <application-info [applicationInfo]="applicationInfo()" />

        @if (applicationInfo()) {
          <h2>{{ 'REGISTRATION' | translate }}</h2>
          <dynamic-form
            [config]="config"
            [formConfig]="formConfig"
            [translateService]="translateService"
            #form="dynamicForm"
            (submitBt)="submit($event)" />

          @if (progressValue()) {
            <div>
              <h4>{{ 'REGISTER_WAIT' | translate }}</h4>
              <p-progressBar [value]="progressValue()" />
            </div>
          }

          @if (confirmEmail()) {
            <div class="alert alert-info" role="alert">
              {{ 'REGISTRATION_EMAIL' | translate }}
            </div>
          }
        }
      </div>
    </div>
  `,
  imports: [...DYNAMIC_FORM_IMPORTS, TranslateModule, ProgressBarModule, ApplicationInfoComponent]
})
export class RegisterComponent extends PasswordBaseComponent implements OnInit {
  private messageToastService = inject(MessageToastService);
  private actuatorService = inject(ActuatorService);
  private router = inject(Router);
  private loginService = inject(LoginService);

  readonly progressValue = signal<number>(undefined);
  readonly errorLastRegistration = input<string>(undefined, { alias: 'failure' });
  readonly confirmEmail = signal(false);
  readonly applicationInfo = signal<ApplicationInfo>(null);

  ngOnInit(): void {
    combineLatest([
      this.actuatorService.applicationInfo(),
      this.gps.getUserFormDefinitions(),
      this.gps.getPasswordRegexProperties()
    ])
      .pipe(takeUntilDestroyed(this.destroyRef))
      .subscribe({
        next: (
          data: [applicationInfo: ApplicationInfo, fdias: FieldDescriptorInputAndShow[], prp: PasswordRegexProperties]
        ) => {
          sessionStorage.setItem(GlobalSessionNames.USER_FORM_DEFINITION, JSON.stringify(data[1]));
          this.loginFormDefinition(data[1]);
          this.passwordRegexProperties = data[2];
          this.applicationInfo.set(data[0]);
          // The @if around <dynamic-form> renders the form only now; its controls exist after that render
          afterNextRender(
            () => {
              this.preparePasswordFields();
              this.configObject.nickname.elementRef?.nativeElement.focus();
            },
            { injector: this.injector }
          );
        },
        error: () => this.applicationInfo.set(null)
      });
  }

  submit(value: { [name: string]: any }): void {
    const user = new User();
    this.form().cleanMaskAndTransferValuesToBusinessObject(user);
    const date = new Date();
    user.timezoneOffset = date.getTimezoneOffset();
    this.loginService.logout();
    this.form().setDisableAll(true);
    this.showProgressIndicator();
    this.loginService.update(user).subscribe({
      next: (newUser) => {
        this.messageToastService.showMessageI18n(InfoLevelType.SUCCESS, 'USER_NEW_SAVED');
        this.progressValue.set(undefined);
        this.confirmEmail.set(true);
      },
      error: () => {
        this.progressValue.set(undefined);
        this.form().setDisableAll(false);
        this.configObject.submit.disabled = false;
      }
    });
  }

  showProgressIndicator() {
    this.progressValue.set(0);
    const interval = setInterval(() => {
      if (this.progressValue() === undefined) {
        clearInterval(interval);
        return;
      }
      this.progressValue.update((value) => Math.min(100, value + Math.floor(Math.random() * 10) + 1));
      if (this.progressValue() >= 100) {
        clearInterval(interval);
      }
    }, 1000);
    this.destroyRef.onDestroy(() => clearInterval(interval));
  }

  helpLink(): void {
    this.gps.toExternalHelpWebpage(this.translateService.currentLang, HelpIds.HELP_INTRO_REGISTER);
  }

  private loginFormDefinition(fdias: FieldDescriptorInputAndShow[]): void {
    super.init(fdias, false);
    this.formConfig = {
      labelColumns: 3,
      helpLinkFN: this.helpLink.bind(this),
      nonModal: true,
      language: this.translateService.currentLang
    };

    this.config = [
      DynamicFieldModelHelper.ccWithFieldsFromDescriptorHeqF(this.translateService, 'nickname', fdias),
      DynamicFieldModelHelper.ccWithFieldsFromDescriptorHeqF(this.translateService, 'email', fdias),
      { formGroupName: 'passwordGroup', fieldConfig: this.configPassword },
      DynamicFieldModelHelper.ccWithFieldsFromDescriptorHeqF(this.translateService, 'localeStr', fdias),
      DynamicFieldHelper.createFunctionButton('SIGN_IN', (e) => this.router.navigate([`/${BaseSettings.LOGIN_KEY}`])),
      DynamicFieldHelper.createSubmitButton('REGISTRATION')
    ];
    this.configObject = TranslateHelper.prepareFieldsAndErrors(this.translateService, this.config);
    this.prepareData();
  }

  private prepareData() {
    this.gps
      .getSupportedLocales()
      .pipe(takeUntilDestroyed(this.destroyRef))
      .subscribe((data) => (this.configObject.localeStr.valueKeyHtmlOptions = data));
  }
}
