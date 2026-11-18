import { Component, DestroyRef, inject, OnInit, signal, viewChild } from '@angular/core';
import { takeUntilDestroyed } from '@angular/core/rxjs-interop';
import { SchemaPathTree, pattern, validate } from '@angular/forms/signals';
import { TranslateModule, TranslateService } from '@ngx-translate/core';
import { NavigationEnd, Router } from '@angular/router';
import { combineLatest, filter } from 'rxjs';
import { FormBase } from '../../edit/form.base';
import { SignalDynamicFormComponent } from '../../dynamic-form/signal/signal-dynamic-form.component';
import { SignalFormModel } from '../../dynamic-form/signal/signal-form.model';
import { DynamicFieldHelper } from '../../helper/dynamic.field.helper';
import { DynamicFieldModelHelper } from '../../helper/dynamic.field.model.helper';
import { TranslateHelper } from '../../helper/translate.helper';
import { GlobalparameterService } from '../../services/globalparameter.service';
import { LoginService } from '../service/log-in.service';
import { FieldDescriptorInputAndShow } from '../../dynamicfield/field.descriptor.input.and.show';
import { BaseSettings } from '../../base.settings';

interface ResetFormModel {
  email: string;
  token: string;
  passwordNew: string;
  passwordConfirm: string;
}

/** Public recovery form shared by both desktop hosts. Secrets stay in the form and are cleared on completion. */
@Component({
  imports: [SignalDynamicFormComponent, TranslateModule],
  template: `
    <div class="container">
      <div class="login jumbotron-replacement mx-auto">
        <h2>{{ 'FORGOT_PASSWORD' | translate }}</h2>
        @if (message()) {
          <div class="alert alert-info" role="status">{{ message() }}</div>
        }
        @if (formReady() && !finished()) {
          <signal-dynamic-form
            [config]="config"
            [formConfig]="formConfig"
            [rules]="rules"
            #form="signalDynamicForm"
            (submitBt)="submit($event)" />
        }
        <button type="button" class="btn btn-link" (click)="backToLogin()">{{ 'SIGN_IN' | translate }}</button>
      </div>
    </div>
  `
})
export class PasswordResetComponent extends FormBase implements OnInit {
  private readonly gps = inject(GlobalparameterService);
  private readonly login = inject(LoginService);
  private readonly translate = inject(TranslateService);
  private readonly router = inject(Router);
  private readonly destroyRef = inject(DestroyRef);
  readonly form = viewChild(SignalDynamicFormComponent<ResetFormModel>);
  readonly message = signal('');
  readonly formReady = signal(false);
  readonly finished = signal(false);
  private confirming = false;
  private descriptors: FieldDescriptorInputAndShow[] = [];
  private passwordPattern = /(?:)/;
  private passwordPatternMessage = '';

  readonly rules = (p: SchemaPathTree<ResetFormModel>): void => {
    if (this.confirming) {
      pattern(p.passwordNew, this.passwordPattern, { message: this.passwordPatternMessage });
      validate(p.passwordConfirm, (ctx) =>
        ctx.value() === ctx.valueOf(p.passwordNew)
          ? null
          : { kind: 'equalTo', message: this.translate.instant('equalToPassword') }
      );
    }
  };

  ngOnInit(): void {
    // The fragment must disappear before any asynchronous loading or navigation can expose it.
    const token = this.consumeMailboxToken();
    this.router.events
      .pipe(
        filter((event) => event instanceof NavigationEnd),
        takeUntilDestroyed(this.destroyRef)
      )
      .subscribe(() => this.receiveResetLink());
    combineLatest([this.gps.getUserFormDefinitions(), this.gps.getPasswordRegexProperties()])
      .pipe(takeUntilDestroyed(this.destroyRef))
      .subscribe(([descriptors, policy]) => {
        this.descriptors = descriptors;
        this.passwordPattern = new RegExp(policy.regex);
        this.passwordPatternMessage = policy.languageErrorMsgMap[this.gps.getUserLang()] ?? '';
        this.buildForm(this.consumeMailboxToken() ?? token);
        this.clearRouterFragment();
      });
  }

  submit(value: SignalFormModel): void {
    const request = this.confirming
      ? this.login.confirmPasswordReset(String(value.token), String(value.passwordNew))
      : this.login.requestPasswordReset(String(value.email));
    request.pipe(takeUntilDestroyed(this.destroyRef)).subscribe({
      next: (result) => {
        this.message.set(result.message);
        if (this.confirming) {
          this.configObject.token.defaultValue = '';
          this.form()?.setDefaultValuesAndEnableSubmit();
          this.finished.set(true);
        } else {
          this.buildForm('');
        }
      },
      error: () => this.form()?.enableSubmit()
    });
  }

  /** A mail link opened in a tab already on this route is a same-document fragment navigation. */
  receiveResetLink(): void {
    if (!this.formReady()) return;
    const token = this.consumeMailboxToken();
    if (token !== null) {
      this.message.set('');
      this.finished.set(false);
      this.buildForm(token);
    }
    this.clearRouterFragment();
  }

  private clearRouterFragment(): void {
    const fragmentStart = this.router.url.indexOf('#');
    if (fragmentStart >= 0) {
      this.router.navigateByUrl(this.router.url.slice(0, fragmentStart), { replaceUrl: true });
    }
  }

  private consumeMailboxToken(): string | null {
    if (!location.hash) return null;
    const token = new URLSearchParams(location.hash.slice(1)).get('token');
    history.replaceState(history.state, '', location.pathname + location.search);
    return token;
  }

  backToLogin(): void {
    this.router.navigate(['/' + BaseSettings.LOGIN_KEY]);
  }

  private buildForm(token: string | null): void {
    this.confirming = token !== null;
    this.formConfig = { nonModal: true, labelColumns: 4 };
    this.config = this.confirming
      ? [
          DynamicFieldHelper.createFieldInputString('token', 'RESET_TOKEN', 100, true, {
            defaultValue: token ?? '',
            invisible: !!token
          }),
          DynamicFieldModelHelper.ccWithFieldsFromDescriptor(
            this.translate,
            'password',
            'PASSWORD_NEW',
            this.descriptors,
            { targetField: 'passwordNew' }
          ),
          {
            ...DynamicFieldModelHelper.ccWithFieldsFromDescriptor(
              this.translate,
              'password',
              'PASSWORD_NEW_CONFIRM',
              this.descriptors,
              { targetField: 'passwordConfirm' }
            ),
            labelKey: 'PASSWORD_NEW_CONFIRM'
          }
        ]
      : [DynamicFieldModelHelper.ccWithFieldsFromDescriptorHeqF(this.translate, 'email', this.descriptors)];
    this.config.push(
      DynamicFieldHelper.createSubmitButton(this.confirming ? 'RESET_PASSWORD' : 'REQUEST_PASSWORD_RESET')
    );
    this.configObject = TranslateHelper.prepareFieldsAndErrors(this.translate, this.config, false);
    this.formReady.set(true);
  }
}
