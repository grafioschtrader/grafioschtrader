import { GlobalSessionNames } from '../global.session.names';
import { CanActivateFn, Router } from '@angular/router';
import { inject } from '@angular/core';
import { BaseSettings } from '../base.settings';

/**
 * Route guards shared by every application built on this library. They read the roles that
 * {@link LoginService#afterSuccessfulLogin} put into the session storage, so they work for any host without knowing
 * anything about its domain.
 */

/** Allows any signed-in user; sends everybody else back to the login route. */
export const authGuard: CanActivateFn = () => {
  const router = inject(Router);
  const roles: string = sessionStorage.getItem(GlobalSessionNames.ROLES);
  if (roles) {
    if (roles.indexOf(BaseSettings.ROLE_LIMIT_EDIT) >= 0 || roles.indexOf(BaseSettings.ROLE_USER) >= 0) {
      return true;
    }
  }
  router.navigate(['/' + BaseSettings.LOGIN_KEY]);
  return false;
};

/** Allows users that may edit shared data. Blocks silently, because the route is not offered to others anyway. */
export const allEditGuard: CanActivateFn = () => {
  const roles: string = sessionStorage.getItem(GlobalSessionNames.ROLES);
  if (roles && roles.split(',').indexOf(BaseSettings.ROLE_ALL_EDIT) >= 0) {
    return true;
  }
  return false;
};

/** Allows administrators only. Blocks silently, because the route is not offered to others anyway. */
export const adminGuard: CanActivateFn = () => {
  const roles: string = sessionStorage.getItem(GlobalSessionNames.ROLES);
  if (roles && roles.split(',').indexOf(BaseSettings.ROLE_ADMIN) >= 0) {
    return true;
  }
  return false;
};
