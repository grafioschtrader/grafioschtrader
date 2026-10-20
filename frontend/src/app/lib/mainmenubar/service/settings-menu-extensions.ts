import { InjectionToken } from '@angular/core';
import { MenuItem } from '@openng/optimus-ui/api';
import { DialogService } from '@openng/optimus-ui/dynamicdialog';
import { TranslateService } from '@ngx-translate/core';

/** Application-owned settings entries; the library never imports an application's dialogs. */
export interface SettingsMenuContext {
  accountOperationsAvailable: boolean;
  /** True when the administrator has opened the MCP server for AI assistants (gt.mcp.use). */
  useMcp: boolean;
  dialogService: DialogService;
  translateService: TranslateService;
}
export type SettingsMenuExtension = (context: SettingsMenuContext) => MenuItem[];
export const SETTINGS_MENU_EXTENSIONS = new InjectionToken<SettingsMenuExtension[]>('SETTINGS_MENU_EXTENSIONS');
