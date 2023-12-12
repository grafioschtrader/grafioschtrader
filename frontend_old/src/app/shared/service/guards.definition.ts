import {GlobalSessionNames} from '../global.session.names';
import {AppSettings} from '../app.settings';
import {CanActivateFn, Router} from '@angular/router';
import {inject} from '@angular/core';


export const authGuard: CanActivateFn = () => {
  const router = inject(Router);
  const roles: string = sessionStorage.getItem(GlobalSessionNames.ROLES);
  if (roles) {
    const roleSplit = roles.split(',');
    if (roles.indexOf(AppSettings.ROLE_LIMIT_EDIT) >= 0 || roles.indexOf(AppSettings.ROLE_USER) >= 0) {
      return true;
    }
  }
  router.navigate(['/' + AppSettings.LOGIN_KEY]);
  return false;
};

export const allEditGuard: CanActivateFn = () => {
  const roles: string = sessionStorage.getItem(GlobalSessionNames.ROLES);
  if (roles && roles.split(',').indexOf(AppSettings.ROLE_ALL_EDIT) >= 0) {
    return true;
  }
  return false;
};

export const adminGuard: CanActivateFn = () => {
  const roles: string = sessionStorage.getItem(GlobalSessionNames.ROLES);
  if (roles && roles.split(',').indexOf(AppSettings.ROLE_ADMIN) >= 0) {
    return true;
  }
  return false;
};
