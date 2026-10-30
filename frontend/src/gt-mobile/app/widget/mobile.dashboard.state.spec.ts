import { describe, expect, it } from 'vitest';
import { DashboardResult } from '../../../app/lib/dashboard/dashboard.types';
import { mergeMobileResults, mobileCardErrorKey, mobileCardState } from './mobile.dashboard.state';

const result = (overrides: Partial<DashboardResult>): DashboardResult => ({
  instanceId: 'w1',
  type: 'PERFORMANCE_LAST_SESSIONS',
  status: 'OK',
  payload: { lists: [], custom: { sessions: [1] } },
  dataAsOf: '2026-10-29T18:00:00Z',
  ...overrides
});

describe('mobile dashboard state', () => {
  it('keeps the figures of an earlier read when a refresh fails, marked stale', () => {
    const first = mergeMobileResults({}, [result({})]);
    const failed = mergeMobileResults(first, [result({ status: 'ERROR', payload: null, errorCode: 'X' })]);
    expect(failed['w1'].payload).toEqual(first['w1'].payload);
    expect(failed['w1'].stale).toBe(true);
    expect(mobileCardState(failed['w1'], false)).toBe('data');
  });

  it('shows a missing result as pending while groups load and as failed afterwards', () => {
    expect(mobileCardState(undefined, true)).toBe('loading');
    expect(mobileCardState(undefined, false)).toBe('error');
    expect(mobileCardErrorKey(undefined)).toBe('DASHBOARD_LOAD_ERROR');
  });

  it('reports the error code of the server for a failed widget without figures', () => {
    const failed = result({ status: 'ERROR', payload: null, errorCode: 'DASHBOARD_ALGO_NONE' });
    expect(mobileCardState(failed, false)).toBe('error');
    expect(mobileCardErrorKey(failed)).toBe('DASHBOARD_ALGO_NONE');
    expect(mobileCardState(result({ status: 'EMPTY', payload: null }), false)).toBe('empty');
  });
});
