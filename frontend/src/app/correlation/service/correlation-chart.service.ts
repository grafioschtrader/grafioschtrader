import { Injectable } from '@angular/core';
import { BehaviorSubject } from 'rxjs';
import { CorrelationRollingResult } from '../../entities/correlation.set';

/** Data shared by the matrix and its two chart views. */
export interface CorrelationChartInput {
  results: CorrelationRollingResult[];
  title: string;
  includeDividends: boolean;
  adjustCurrency: boolean;
}

/** Keeps the latest matrix selection available when the lower chart outlet opens. */
@Injectable({ providedIn: 'root' })
export class CorrelationChartService {
  private input = new BehaviorSubject<CorrelationChartInput>(null);
  readonly input$ = this.input.asObservable();
  shown = false;

  /** Publishes results, or clears stale results when the set or selection changes. */
  publish(input: CorrelationChartInput): void {
    this.input.next(input);
  }
}
