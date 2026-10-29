import { Component, inject, effect, input, untracked } from '@angular/core';
import { Router } from '@angular/router';
import { LoginService } from '../service/log-in.service';
import { BaseSettings } from '../../base.settings';
import { TranslateModule } from '@ngx-translate/core';

@Component({
  template: `
    <div class="container">
      <h1>{{ 'VERIFYING_TOKEN' | translate }}</h1>
    </div>
  `,
  imports: [TranslateModule]
})
export class RegistrationTokenVerifyComponent {
  private router = inject(Router);
  private loginService = inject(LoginService);

  readonly token = input.required<string>();

  constructor() {
    effect((onCleanup) => {
      const token = this.token();
      const request = untracked(() =>
        this.loginService.getTokenVerified(token).subscribe((redirect: string) => {
          switch (redirect) {
            case 'TOKEN_INVALID':
            case 'TOKEN_EXPIRED':
              this.router.navigate([BaseSettings.REGISTER_KEY, { failure: redirect }]);
              break;
            case 'TOKEN_SUCCESS':
              this.router.navigate([BaseSettings.LOGIN_KEY, { success: redirect }]);
              break;
          }
        })
      );
      onCleanup(() => request.unsubscribe());
    });
  }
}
