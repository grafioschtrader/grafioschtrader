import { signal, Service } from '@angular/core';
import { UserSettingsDialogs } from '../component/user-settings-dialogs';

/**
 * The referencing of dialogs and their usage is implemented in different components, therefore this service exists.
 * These dialogs are usually available from GT menu bar. Therefore, these dialogs can be activated from any component.
 */
@Service()
export class MainDialogService {
  /** Visibility per {@link UserSettingsDialogs}; a signal, so the dialog host follows a menu command or a close */
  readonly visibleDialogs = signal<boolean[]>([false, false]);

  visibleDialog(visible: boolean, userSettingsDialogs: UserSettingsDialogs) {
    this.visibleDialogs.update((dialogs) => dialogs.map((v, i) => (i === userSettingsDialogs ? visible : v)));
  }
}
