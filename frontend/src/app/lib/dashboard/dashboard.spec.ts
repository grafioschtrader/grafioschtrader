import { describe, expect, it } from 'vitest';
import {
  DashboardDescriptor,
  DashboardResult,
  DashboardWidget,
  availableDescriptors,
  insertDashboardWidget,
  mergeDashboardResult,
  moveDashboardWidget,
  widgetFromDescriptor
} from './dashboard.types';

const widgets: DashboardWidget[] = ['a', 'b', 'c'].map((instanceId) => ({
  instanceId,
  type: 'UNREAD_MAIL',
  width: 'HALF',
  config: { maxRows: 5 }
}));
describe('Dashboard layout and refresh', () => {
  it('moves cards without mutating the saved layout', () => {
    expect(moveDashboardWidget(widgets, 0, 2).map((w) => w.instanceId)).toEqual(['b', 'c', 'a']);
    expect(widgets.map((w) => w.instanceId)).toEqual(['a', 'b', 'c']);
    expect(moveDashboardWidget(widgets, 0, -1)).toBe(widgets);
  });
  it('retains successful data on refresh failure and clears the stale marker after recovery', () => {
    const previous: DashboardResult = {
      instanceId: 'a',
      type: 'UNREAD_MAIL',
      status: 'OK',
      dataAsOf: '2026-09-06 12:00:00',
      payload: { lists: [] }
    };
    const failed: DashboardResult = { ...previous, status: 'ERROR', payload: null, errorCode: 'DASHBOARD_LOAD_ERROR' };
    expect(mergeDashboardResult(previous, failed)).toMatchObject({
      payload: previous.payload,
      dataAsOf: previous.dataAsOf,
      stale: true
    });
    expect(mergeDashboardResult(mergeDashboardResult(previous, failed), previous)).toBe(previous);
  });
  it('removes earlier data when authorization is withdrawn', () => {
    const previous: DashboardResult = {
      instanceId: 'a',
      type: 'USER_LIMIT_REQUESTS',
      status: 'OK',
      dataAsOf: '',
      payload: { lists: [] }
    };
    const denied: DashboardResult = { ...previous, status: 'UNAVAILABLE', payload: null };
    expect(mergeDashboardResult(previous, denied).payload).toBeNull();
  });
});

const descriptor = (type: string): DashboardDescriptor => ({
  type,
  titleKey: 'DASHBOARD_' + type,
  descriptionKey: 'DASHBOARD_' + type + '_HELP',
  defaultWidth: 'HALF',
  defaultConfig: { maxRows: 5 },
  formDefinition: null
});
describe('Available widgets and their transfer', () => {
  const catalogue = ['UNREAD_MAIL', 'PROPOSE_CHANGE_OPEN', 'USER_LIMIT_REQUESTS'].map(descriptor);
  it('offers only the types the dashboard does not carry', () => {
    expect(availableDescriptors(catalogue, widgets).map((d) => d.type)).toEqual([
      'PROPOSE_CHANGE_OPEN',
      'USER_LIMIT_REQUESTS'
    ]);
    expect(availableDescriptors(catalogue, [])).toHaveLength(3);
  });
  it('inserts at the drop position and appends outside the list', () => {
    const added = widgetFromDescriptor(catalogue[1]);
    expect(insertDashboardWidget(widgets, added, 1)[1]).toBe(added);
    expect(insertDashboardWidget(widgets, added, 99).at(-1)).toBe(added);
    expect(widgets).toHaveLength(3);
  });
  it('restores the settings a removed widget had instead of the descriptor defaults', () => {
    const removed: DashboardWidget = {
      instanceId: 'kept',
      type: 'PROPOSE_CHANGE_OPEN',
      width: 'FULL',
      config: { maxRows: 20 }
    };
    expect(widgetFromDescriptor(catalogue[1], removed)).toBe(removed);
    expect(widgetFromDescriptor(catalogue[1])).toMatchObject({ width: 'HALF', config: { maxRows: 5 } });
  });
});
