import { SettingsMenuExtension } from '../lib/mainmenubar/service/settings-menu-extensions';
import { BaseSettings } from '../lib/base.settings';

/**
 * Access of AI assistants through the MCP server. Offered in the home-tenant context of export and account deletion,
 * because a token always reads the home tenant, and only when the instance runs the MCP server.
 */
export const mcpSettingsMenu: SettingsMenuExtension = (context) =>
  context.accountOperationsAvailable && context.useMcp
    ? [
        {
          label: 'MCP_ACCESS' + BaseSettings.DIALOG_MENU_SUFFIX,
          // Loaded on first use, so the dialog stays out of the bundle of the application shell
          command: () =>
            import('../lib/accesstoken/component/user-access-token-dialog.component').then((m) =>
              context.dialogService.open(m.UserAccessTokenDialogComponent, {
                header: context.translateService.instant('MCP_ACCESS'),
                width: '900px',
                data: { introKey: 'MCP_ACCESS_INTRO' },
                modal: true,
                closable: true,
                closeOnEscape: true
              })
            )
        }
      ]
    : [];
