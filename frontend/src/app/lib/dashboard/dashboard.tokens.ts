import { InjectionToken } from '@angular/core';
import { ColumnConfig } from '../datashowbase/column.config';
import { DialogComponentLoader } from '../maintree/handler/lazy-dialog';

const loadDashboardSummary: DialogComponentLoader = () =>
  import('./dashboard-summary.component').then((m) => m.DashboardSummaryComponent);

/**
 * Applications may provide a renderer map extending these built-in library renderers by stable backend type code.
 * Each entry is a dynamic import, so a widget and what it pulls in - a chart library, for instance - is loaded with
 * the dashboard and not with the application shell.
 */
export const DASHBOARD_RENDERERS = new InjectionToken<Record<string, DialogComponentLoader>>('DASHBOARD_RENDERERS', {
  providedIn: 'root',
  factory: () => ({
    UNREAD_MAIL: loadDashboardSummary,
    PROPOSE_CHANGE_OPEN: loadDashboardSummary,
    USER_LIMIT_REQUESTS: loadDashboardSummary
  })
});

/** Applications can replace the default maxRows summary with the setting appropriate to each widget type. */
export const DASHBOARD_CONFIG_SUMMARIES = new InjectionToken<Record<string, ColumnConfig>>(
  'DASHBOARD_CONFIG_SUMMARIES',
  {
    providedIn: 'root',
    factory: () => ({})
  }
);

/**
 * Widget types whose card offers a maximize button, letting it take over the whole dashboard area for a while. Only a
 * card that gains from the space - a chart, for instance - is worth listing; a renderer of a listed type receives a
 * `maximized` input next to `result`. None of the library widgets is maximizable.
 */
export const DASHBOARD_MAXIMIZABLE = new InjectionToken<ReadonlySet<string>>('DASHBOARD_MAXIMIZABLE', {
  providedIn: 'root',
  factory: () => new Set<string>()
});
