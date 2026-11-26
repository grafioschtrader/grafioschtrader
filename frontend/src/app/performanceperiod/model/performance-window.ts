/**
 * Complete REST path and owner of a period-performance calculation. Dates are serialized form values. The benchmark is
 * the request-local choice: undefined uses the stored benchmark, 0 compares none, any other value that instrument.
 */
export class PerformanceWindowDef {
  constructor(
    public readonly idPortfolio: number | null | undefined,
    public readonly dateFrom: string,
    public readonly dateTo: string,
    public readonly periodSplit: string,
    public readonly idBenchmark?: number
  ) {}
}

export enum WeekYear {
  WM_WEEK = 0,
  WM_YEAR = 1
}

/** An incomplete form stays a draft; only a complete window may become an HTTP path or report scope. */
export function performanceWindowFromDraft(draft: Partial<PerformanceWindowDef>): PerformanceWindowDef | undefined {
  const { idPortfolio, dateFrom, dateTo, periodSplit, idBenchmark } = draft;
  if (
    typeof dateFrom !== 'string' ||
    !dateFrom ||
    typeof dateTo !== 'string' ||
    !dateTo ||
    (periodSplit !== 'WM_WEEK' && periodSplit !== 'WM_YEAR')
  )
    return undefined;
  return new PerformanceWindowDef(idPortfolio, dateFrom, dateTo, periodSplit, idBenchmark);
}
