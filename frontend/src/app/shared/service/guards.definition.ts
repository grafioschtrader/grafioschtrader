import {GlobalSessionNames} from '../../lib/global.session.names';
import {AppSettings} from '../app.settings';
import {CanActivateFn, Router} from '@angular/router';
import {inject} from '@angular/core';
import {BaseSettings} from '../../lib/base.settings';


export const authGuard: CanActivateFn = () => {
  const router = inject(Router);
  const roles: string = sessionStorage.getItem(GlobalSessionNames.ROLES);
  if (roles) {
    const roleSplit = roles.split(',');
    if (roles.indexOf(BaseSettings.ROLE_LIMIT_EDIT) >= 0 || roles.indexOf(BaseSettings.ROLE_USER) >= 0) {
      return true;
    }
  }
  router.navigate(['/' + BaseSettings.LOGIN_KEY]);
  return false;
};

export const allEditGuard: CanActivateFn = () => {
  const roles: string = sessionStorage.getItem(GlobalSessionNames.ROLES);
  if (roles && roles.split(',').indexOf(BaseSettings.ROLE_ALL_EDIT) >= 0) {
    return true;
  }
  return false;
};

export const adminGuard: CanActivateFn = () => {
  const roles: string = sessionStorage.getItem(GlobalSessionNames.ROLES);
  if (roles && roles.split(',').indexOf(BaseSettings.ROLE_ADMIN) >= 0) {
    return true;
  }
  return false;
};
