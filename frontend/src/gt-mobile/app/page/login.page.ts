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
import { MobileLoginService } from '../service/mobile.login.service';

/** Value of an Ionic input event. */
interface InputValueEvent {
  detail: { value?: string | null };
}

/**
 * Sign-in with the email address and password of the GT account. A failed attempt is reported by the toast of the
 * shell with the text the server sent; the browser's password manager may fill in both fields.
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
              (ionInput)="email.set(valueOf($event))" />
          </ion-item>
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
        </ion-list>
        <ion-button type="submit" expand="block" class="ion-margin-top" [disabled]="!email() || !password() || busy()">
          {{ 'SIGN_IN' | translate }}
        </ion-button>
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

  protected valueOf(event: InputValueEvent): string {
    return event.detail.value ?? '';
  }

  protected signIn(event: Event): void {
    event.preventDefault();
    if (!this.email() || !this.password() || this.busy()) {
      return;
    }
    this.busy.set(true);
    this.loginService.login(this.email(), this.password()).subscribe({
      next: (response) => {
        const token = response.headers.get('x-auth-token');
        if (token && response.body) {
          this.loginService.afterSuccessfulLogin(token, response.body);
          this.password.set('');
          this.router.navigate(['/dashboard'], { replaceUrl: true });
        }
        this.busy.set(false);
      },
      error: () => {
        this.password.set('');
        this.busy.set(false);
      }
    });
  }
}
