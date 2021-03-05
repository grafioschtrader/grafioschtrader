import {CanActivate} from '@angular/router';
import {AppSettings} from '../app.settings';
import {GlobalSessionNames} from '../global.session.names';
import {Injectable} from '@angular/core';

@Injectable()
export class AllEditGuard implements CanActivate {
  canActivate() {
    const roles: string = sessionStorage.getItem(GlobalSessionNames.ROLES);
    if (roles && roles.split(',').indexOf(AppSettings.ROLE_ALL_EDIT) >= 0) {
      return true;
    }

    return false;
  }
}
