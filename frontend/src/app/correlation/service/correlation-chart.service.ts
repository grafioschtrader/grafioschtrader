import { Service, signal } from '@angular/core';
import { CorrelationRollingResult } from '../../entities/correlation.set';

/** Data shared by the matrix and its two chart views. */
export interface CorrelationChartInput {
  results: CorrelationRollingResult[];
  title: string;
  includeDividends: boolean;
  adjustCurrency: boolean;
}

/** Keeps the latest matrix selection available when the lower chart outlet opens. */
@Service()
export class CorrelationChartService {
  private readonly inputState = signal<CorrelationChartInput | null>(null);
  readonly input = this.inputState.asReadonly();
  shown = false;

  /** Publishes results, or clears stale results when the set or selection changes. */
  publish(input: CorrelationChartInput): void {
    this.inputState.set(input);
  }
}
