import { describe, expect, it } from 'vitest';
import { CorrelationSetService } from './correlation.set.service';

/** The correlation matrix is read through an `httpResource`; its request must stay an idempotent GET of one set. */
describe('CorrelationSetService.getCalculationByCorrelationSetRequest', () => {
  it('requests the calculation of the given correlation set', () => {
    const request = new CorrelationSetService(null, null, null).getCalculationByCorrelationSetRequest(7);

    expect(request.url).toBe('/api/correlationset/calculation/7');
    expect(request.method).toBeUndefined();
    expect(request.body).toBeUndefined();
  });
});
