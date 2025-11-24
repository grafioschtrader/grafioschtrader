import {Injectable} from '@angular/core';
import {UserSettingsDialogs} from '../component/user-settings-dialogs';

/**
 * The referencing of dialogs and their usage is implemented in different components, therefore this service exists.
 * These dialogs are usually available from GT menu bar. Therefore, these dialogs can be activated from any component.
 */
@Injectable()
export class MainDialogService {

  visibleDialogs: boolean[] = [false, false];

  visibleDialog(visible: boolean, userSettingsDialogs: UserSettingsDialogs) {
    this.visibleDialogs[userSettingsDialogs] = visible;
  }

}



