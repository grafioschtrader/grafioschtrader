import {Component} from '@angular/core';
import {MainDialogService} from '../service/main.dialog.service';

@Component({
  selector: 'main-dialog',
  template: `
    <password-edit *ngIf="mainDialogService.visibleDialogs[UserSettingsDialogs.Password]"
                   [visibleDialog]="mainDialogService.visibleDialogs[UserSettingsDialogs.Password]">
    </password-edit>
    <nickname-lang-edit *ngIf="mainDialogService.visibleDialogs[UserSettingsDialogs.NicknameLocale]"
                        [visibleDialog]="mainDialogService.visibleDialogs[UserSettingsDialogs.NicknameLocale]">
    </nickname-lang-edit>
  `
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
