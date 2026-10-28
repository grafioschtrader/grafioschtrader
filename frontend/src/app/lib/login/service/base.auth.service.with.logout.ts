import { inject } from '@angular/core';
import { LoginService } from './log-in.service';
import { BaseAuthService } from './base.auth.service';

/**
 * Delivers functionality for a logout. For example when user misused this application.
 */
export abstract class AuthServiceWithLogout<T> extends BaseAuthService<T> {
  protected loginService: LoginService = inject(LoginService);

  protected override toLogout(): void {
    this.loginService.logoutWithLoginView();
  }
}
