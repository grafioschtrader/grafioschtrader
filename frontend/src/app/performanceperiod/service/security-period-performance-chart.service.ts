import { Service, computed, signal } from '@angular/core';
import { Subject } from 'rxjs';
import type { SecurityPeriodPerformance } from '../model/security-period-performance';

/** A calculated period together with the scope it belongs to; null as idPortfolio means the whole client. */
interface ScopedResult {
  idPortfolio: number | null;
  result: SecurityPeriodPerformance;
}

/**
 * Couples the view "Performance by security" with its charts in the lower display area. The view publishes every
 * calculated period, the chart component draws the chart type the user selected. The latest result outlives the view:
 * the chart keeps showing it while the user works in another tab, and the view shows it again when it is opened anew
 * for the same scope. A click on a security in the chart travels back as an event, so the view can expand its row.
 */
@Service()
export class SecurityPeriodPerformanceChartService {
  private readonly state = signal<ScopedResult | null>(null);
  private readonly securityClicked = new Subject<{ result: SecurityPeriodPerformance; idSecuritycurrency: number }>();

  /** Latest calculated period; null before the first calculation and after a calculation for another scope began. */
  readonly result = computed(() => this.state()?.result ?? null);

  /** A security clicked in the chart, together with the result the chart showed. */
  readonly securityClicked$ = this.securityClicked.asObservable();

  /**
   * Publishes a calculated period.
   *
   * @param idPortfolio - Portfolio of the calculation, null for the whole client
   * @param result - The result shown in the table
   */
  publish(idPortfolio: number | null, result: SecurityPeriodPerformance): void {
    this.state.set({ idPortfolio, result });
  }

  /**
   * Returns the kept result when it belongs to the given scope. A result of another scope is removed, because a chart
   * of one portfolio beside the view of another would be misread.
   *
   * @param idPortfolio - Portfolio of the view, null for the whole client
   * @returns The result to show again, or null
   */
  takeOverForScope(idPortfolio: number | null): SecurityPeriodPerformance | null {
    const kept = this.state();
    if (kept && kept.idPortfolio !== idPortfolio) {
      this.state.set(null);
      return null;
    }
    return kept?.result ?? null;
  }

  /**
   * Reports a click on a security of the chart.
   *
   * @param result - The result the chart was drawn from
   * @param idSecuritycurrency - The clicked security
   */
  clickSecurity(result: SecurityPeriodPerformance, idSecuritycurrency: number): void {
    this.securityClicked.next({ result, idSecuritycurrency });
  }
}
