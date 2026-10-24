import { SettingsMenuExtension } from '../lib/mainmenubar/service/settings-menu-extensions';
import { BaseSettings } from '../lib/base.settings';

/**
 * Offered in the same home-tenant context as export and account deletion. The dialog is loaded on first use, so it stays
 * out of the bundle of the application shell.
 */
export const aiSettingsMenu: SettingsMenuExtension = (context) =>
  context.accountOperationsAvailable
    ? [
        {
          label: 'AI_PROVIDERS' + BaseSettings.DIALOG_MENU_SUFFIX,
          command: () =>
            import('./component/tenant-ai-provider-dialog.component').then((m) =>
              context.dialogService.open(m.TenantAIProviderDialogComponent, {
                header: context.translateService.instant('AI_PROVIDERS'),
                width: '850px',
                modal: true,
                closable: true,
                closeOnEscape: true
              })
            )
        }
      ]
    : [];
