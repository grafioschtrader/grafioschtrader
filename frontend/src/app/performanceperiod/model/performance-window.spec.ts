import { describe, expect, it } from 'vitest';
import { performanceWindowFromDraft, type PerformanceWindowDef } from './performance-window';

describe('period performance request window', () => {
  it('keeps incomplete or invalid form values out of the REST path', () => {
    expect(performanceWindowFromDraft({ idPortfolio: null })).toBeUndefined();
    expect(performanceWindowFromDraft({ dateFrom: '2026-01-01', dateTo: '', periodSplit: 'WM_YEAR' })).toBeUndefined();
    expect(
      performanceWindowFromDraft({ dateFrom: '2026-01-01', dateTo: '2026-10-30', periodSplit: 'YEAR' })
    ).toBeUndefined();
  });

  it('captures the displayed calculation independently of later form edits and keeps tenant scope', () => {
    const draft = { idPortfolio: null, dateFrom: '2026-01-01', dateTo: '2026-10-30', periodSplit: 'WM_WEEK' };
    const window = performanceWindowFromDraft(draft);
    draft.dateTo = '2026-10-31';
    expect(window).toEqual({ idPortfolio: null, dateFrom: '2026-01-01', dateTo: '2026-10-30', periodSplit: 'WM_WEEK' });
  });

  it('keeps the portfolio scope and annual split', () => {
    const draft: Partial<PerformanceWindowDef> = {
      idPortfolio: 17,
      dateFrom: '2025-01-01',
      dateTo: '2026-10-30',
      periodSplit: 'WM_YEAR'
    };
    expect(performanceWindowFromDraft(draft)?.idPortfolio).toBe(17);
    expect(performanceWindowFromDraft(draft)?.periodSplit).toBe('WM_YEAR');
  });
});
