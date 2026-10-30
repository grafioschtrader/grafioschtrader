import { DashboardResult, mergeDashboardResult } from '../../../app/lib/dashboard/dashboard.types';

/** What a card shows: its loading placeholder, its figures, a text that nothing is there, or a failure. */
export type MobileCardState = 'loading' | 'data' | 'empty' | 'error';

/** Results by widget instance. */
export type MobileResults = Readonly<Record<string, DashboardResult>>;

/**
 * Derives the state of a card from the result of its widget. As on the desktop, a result with a payload is shown
 * whatever its status: a widget explains an empty payload itself, and a failed read that kept the figures of an
 * earlier one shows those figures, marked stale.
 *
 * @param result Result of the widget, undefined while its load group is outstanding or after it failed
 * @param loading Whether the dashboard is still reading load groups
 * @returns State of the card
 */
export function mobileCardState(result: DashboardResult | undefined, loading: boolean): MobileCardState {
  if (!result) {
    return loading ? 'loading' : 'error';
  }
  if (result.payload) {
    return 'data';
  }
  return result.status === 'ERROR' || result.status === 'UNAVAILABLE' ? 'error' : 'empty';
}

/**
 * Translation key of the failure text of a card: the error code of the server when it sent one.
 *
 * @param result Failed result, undefined when the load group of the widget could not be read
 * @returns Translation key
 */
export function mobileCardErrorKey(result: DashboardResult | undefined): string {
  if (!result) {
    return 'DASHBOARD_LOAD_ERROR';
  }
  return result.errorCode ?? (result.status === 'UNAVAILABLE' ? 'DASHBOARD_UNAVAILABLE' : 'DASHBOARD_LOAD_ERROR');
}

/**
 * Adds results to those already shown. A failed read of a widget keeps its earlier figures, as on the desktop.
 *
 * @param current Results shown so far
 * @param results Newly read results
 * @returns A new object with the merged results
 */
export function mergeMobileResults(current: MobileResults, results: DashboardResult[]): MobileResults {
  const merged: Record<string, DashboardResult> = { ...current };
  for (const result of results) {
    merged[result.instanceId] = mergeDashboardResult(current[result.instanceId], result);
  }
  return merged;
}

/** Translation key of the title of a widget type; the desktop dashboard uses the same keys. */
export function mobileCardTitleKey(type: string): string {
  return 'DASHBOARD_' + type;
}
