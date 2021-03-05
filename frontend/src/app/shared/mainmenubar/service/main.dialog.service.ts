import {Injectable} from '@angular/core';
import {UserSettingsDialogs} from '../component/main.dialog.component';

@Injectable()
export class MainDialogService {

  visibleDialogs: boolean[] = [false, false];

  visibleDialog(visible: boolean, userSettingsDialogs: UserSettingsDialogs) {
    this.visibleDialogs[userSettingsDialogs] = visible;
  }

}



