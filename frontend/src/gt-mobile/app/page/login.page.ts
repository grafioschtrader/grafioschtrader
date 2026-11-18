import { Component, inject, signal } from '@angular/core';
import { Router } from '@angular/router';
import {
  IonButton,
  IonContent,
  IonHeader,
  IonInput,
  IonInputPasswordToggle,
  IonItem,
  IonList,
  IonTitle,
  IonToolbar
} from '@ionic/angular';
import { TranslatePipe } from '@ngx-translate/core';
import { MOBILE_HOME } from '../mobile.routes';
import { MobileLoginService } from '../service/mobile.login.service';
import { TransformedError } from '../../../app/lib/login/service/transformed.error';

/** Value of an Ionic input event. */
interface InputValueEvent {
  detail: { value?: string | null };
}

/**
 * Sign-in with the email address and password of the GT account. A failed attempt is reported by the toast of the
 * shell with the text the server sent; the browser's password manager may fill in both fields.
 *
 * For an account with a second factor the server answers the first attempt with SecondFactorRequiredError. The page
 * then keeps e-mail and password, shows a code input and repeats the request with the code. The kept values are
 * discarded on success, on cancel and when the e-mail is edited; a sign-in without second factor stays one step.
 */
@Component({
  selector: 'mobile-login-page',
  imports: [
    IonHeader,
    IonToolbar,
    IonTitle,
    IonContent,
    IonList,
    IonItem,
    IonInput,
    IonInputPasswordToggle,
    IonButton,
    TranslatePipe
  ],
  template: `
    <ion-header>
      <ion-toolbar color="primary">
        <ion-title>Grafioschtrader</ion-title>
      </ion-toolbar>
    </ion-header>
    <ion-content class="ion-padding">
      <form (submit)="signIn($event)">
        <ion-list lines="full">
          <ion-item>
            <ion-input
              type="email"
              name="email"
              autocomplete="username"
              inputmode="email"
              labelPlacement="stacked"
              [label]="'EMAIL' | translate"
              [value]="email()"
              (ionInput)="changeEmail(valueOf($event))" />
          </ion-item>
          @if (!secondFactorRequired()) {
            <ion-item>
              <ion-input
                type="password"
                name="password"
                autocomplete="current-password"
                labelPlacement="stacked"
                [label]="'PASSWORD' | translate"
                [value]="password()"
                (ionInput)="password.set(valueOf($event))">
                <ion-input-password-toggle slot="end" />
              </ion-input>
            </ion-item>
          } @else {
            <ion-item>
              <ion-input
                type="text"
                name="secondFactorCode"
                autocomplete="one-time-code"
                labelPlacement="stacked"
                [maxlength]="16"
                [label]="'SECOND_FACTOR_CODE' | translate"
                [value]="secondFactorCode()"
                (ionInput)="secondFactorCode.set(valueOf($event))" />
            </ion-item>
          }
        </ion-list>
        <ion-button
          type="submit"
          expand="block"
          class="ion-margin-top"
          [disabled]="!email() || !password() || (secondFactorRequired() && !secondFactorCode().trim()) || busy()">
          {{ 'SIGN_IN' | translate }}
        </ion-button>
        @if (secondFactorRequired()) {
          <ion-button type="button" expand="block" fill="clear" (click)="discardSecondFactor()">
            {{ 'CANCEL' | translate }}
          </ion-button>
        }
      </form>
      <p class="widget-note ion-text-center ion-margin-top">
        <a href="../">{{ 'MOBILE_DESKTOP_VIEW' | translate }}</a>
      </p>
    </ion-content>
  `
})
export class LoginPage {
  private readonly loginService = inject(MobileLoginService);
  private readonly router = inject(Router);

  protected readonly email = signal('');
  protected readonly password = signal('');
  protected readonly busy = signal(false);
  /** Set after the server asked for the code of the second factor; e-mail and password are kept meanwhile. */
  protected readonly secondFactorRequired = signal(false);
  protected readonly secondFactorCode = signal('');

  protected valueOf(event: InputValueEvent): string {
    return event.detail.value ?? '';
  }

  /** Editing the e-mail starts over: a kept password and the code prompt belong to the previous address. */
  protected changeEmail(email: string): void {
    this.email.set(email);
    if (this.secondFactorRequired()) {
      this.discardSecondFactor();
    }
  }

  protected discardSecondFactor(): void {
    this.secondFactorRequired.set(false);
    this.secondFactorCode.set('');
    this.password.set('');
  }

  protected signIn(event: Event): void {
    event.preventDefault();
    const code = this.secondFactorRequired() ? this.secondFactorCode().trim() : '';
    if (!this.email() || !this.password() || (this.secondFactorRequired() && !code) || this.busy()) {
      return;
    }
    this.busy.set(true);
    this.loginService.login(this.email(), this.password(), code || undefined).subscribe({
      next: (response) => {
        const token = response.headers.get('x-auth-token');
        if (token && response.body) {
          this.loginService.afterSuccessfulLogin(token, response.body);
          this.discardSecondFactor();
          this.router.navigate([MOBILE_HOME], { replaceUrl: true });
        }
        this.busy.set(false);
      },
      error: (error: TransformedError) => {
        if (error.secondFactorRequired) {
          // Keep e-mail and password for the repeated request; a refused code is entered anew
          this.secondFactorRequired.set(true);
          this.secondFactorCode.set('');
        } else {
          this.discardSecondFactor();
        }
        this.busy.set(false);
      }
    });
  }
}
