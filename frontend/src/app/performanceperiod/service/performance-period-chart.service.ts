import { Service, computed, signal } from '@angular/core';
import type { PerformancePeriod } from '../model/performance.period';

/** A calculated period together with the scope it belongs to; null as idPortfolio means the whole client. */
interface ScopedPeriod {
  idPortfolio: number | null;
  period: PerformancePeriod;
}

/**
 * Couples the view "Period performance" with its chart in the lower display area. The view publishes every calculated
 * period, the chart draws the mode the user selected. The latest period outlives the view, so the chart keeps showing
 * it while the user works in another tab; a calculation for another scope replaces it.
 */
@Service()
export class PerformancePeriodChartService {
  private readonly state = signal<ScopedPeriod | null>(null);

  /** Latest calculated period; null before the first calculation. */
  readonly period = computed(() => this.state()?.period ?? null);

  /**
   * Publishes a calculated period.
   *
   * @param idPortfolio - Portfolio of the calculation, null for the whole client
   * @param period - The period shown in the view
   */
  publish(idPortfolio: number | null, period: PerformancePeriod): void {
    this.state.set({ idPortfolio, period });
  }

  /**
   * Removes a kept period of another scope, because a chart of one portfolio beside the view of another would be
   * misread.
   *
   * @param idPortfolio - Portfolio of the view, null for the whole client
   */
  clearOtherScope(idPortfolio: number | null): void {
    const kept = this.state();
    if (kept && kept.idPortfolio !== idPortfolio) {
      this.state.set(null);
    }
  }
}
