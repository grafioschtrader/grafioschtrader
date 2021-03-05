import {Injectable} from '@angular/core';
import {CanActivate} from '@angular/router';
import {GlobalSessionNames} from '../global.session.names';
import {AppSettings} from '../app.settings';

@Injectable()
export class AdminGuard implements CanActivate {
  canActivate() {
    const roles: string = sessionStorage.getItem(GlobalSessionNames.ROLES);
    if (roles && roles.split(',').indexOf(AppSettings.ROLE_ADMIN) >= 0) {
      return true;
    }
    return false;
  }
}
