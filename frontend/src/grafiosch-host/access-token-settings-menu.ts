import { BaseSettings } from '../app/lib/base.settings';
import { SettingsMenuExtension } from '../app/lib/mainmenubar/service/settings-menu-extensions';

/** The reference host enables g.use.accesstoken and exposes the portable dialog without an MCP endpoint. */
export const accessTokenSettingsMenu: SettingsMenuExtension = (context) =>
  context.accountOperationsAvailable
    ? [
        {
          label: 'ACCESS_TOKEN_PERSONAL' + BaseSettings.DIALOG_MENU_SUFFIX,
          command: () =>
            import('../app/lib/accesstoken/component/user-access-token-dialog.component').then((module) =>
              context.dialogService.open(module.UserAccessTokenDialogComponent, {
                header: context.translateService.instant('ACCESS_TOKEN_PERSONAL'),
                width: '900px',
                modal: true,
                closable: true,
                closeOnEscape: true
              })
            )
        }
      ]
    : [];
