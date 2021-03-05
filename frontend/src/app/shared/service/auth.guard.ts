import {Injectable} from '@angular/core';
import {CanActivate, Router} from '@angular/router';
import {AppSettings} from '../app.settings';
import {GlobalSessionNames} from '../global.session.names';

@Injectable()
export class AuthGuard implements CanActivate {
  constructor(private router: Router) {
  }

  canActivate() {
    const roles: string = sessionStorage.getItem(GlobalSessionNames.ROLES);
    if (roles) {
      const roleSplit = roles.split(',');
      if (roles.indexOf(AppSettings.ROLE_LIMIT_EDIT) >= 0 || roles.indexOf(AppSettings.ROLE_USER) >= 0) {
        return true;
      }
    }

    this.router.navigate(['/' + AppSettings.LOGIN_KEY]);
    return false;
  }
}
