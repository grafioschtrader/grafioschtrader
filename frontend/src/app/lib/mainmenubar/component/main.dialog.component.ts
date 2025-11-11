import {Component} from '@angular/core';
import {MainDialogService} from '../service/main.dialog.service';

/**
 * This component references global dialogs that are normally accessed via the Global Menu Bar.
 */
@Component({
    selector: 'main-dialog',
  template: `
    @if (mainDialogService.visibleDialogs[UserSettingsDialogs.Password]) {
      <password-edit [forcePasswordChange]="false"
                     [visibleDialog]="mainDialogService.visibleDialogs[UserSettingsDialogs.Password]">
      </password-edit>
    }
    @if (mainDialogService.visibleDialogs[UserSettingsDialogs.NicknameLocale]) {
      <nickname-lang-edit [visibleDialog]="mainDialogService.visibleDialogs[UserSettingsDialogs.NicknameLocale]">
      </nickname-lang-edit>
    }
  `,
    standalone: false
})
export class MainDialogComponent {
  // Otherwise enum DialogVisible can't be used in a html template
  UserSettingsDialogs: typeof UserSettingsDialogs = UserSettingsDialogs;

  constructor(public mainDialogService: MainDialogService) {
  }
}

export enum UserSettingsDialogs {
  Password = 0,
  NicknameLocale = 1
}
