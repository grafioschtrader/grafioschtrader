import { inject } from '@angular/core';
import { CanActivateFn, Router } from '@angular/router';
import { MobileLoginService } from './mobile.login.service';

/** Sends a visitor without a session to the sign-in page. */
export const mobileAuthGuard: CanActivateFn = () =>
  inject(MobileLoginService).isSignedIn() || inject(Router).createUrlTree(['/login']);
