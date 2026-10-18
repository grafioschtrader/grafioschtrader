import { SettingsMenuExtension } from '../lib/mainmenubar/service/settings-menu-extensions';
import { BaseSettings } from '../lib/base.settings';
import { TenantAIProviderDialogComponent } from './component/tenant-ai-provider-dialog.component';

/** Offered in the same home-tenant context as export and account deletion. */
export const aiSettingsMenu: SettingsMenuExtension = (context) =>
  context.accountOperationsAvailable
    ? [
        {
          label: 'AI_PROVIDERS' + BaseSettings.DIALOG_MENU_SUFFIX,
          command: () =>
            context.dialogService.open(TenantAIProviderDialogComponent, {
              header: context.translateService.instant('AI_PROVIDERS'),
              width: '850px',
              modal: true,
              closable: true,
              closeOnEscape: true
            })
        }
      ]
    : [];
