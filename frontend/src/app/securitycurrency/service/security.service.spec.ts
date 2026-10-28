import { describe, expect, it } from 'vitest';
import { HttpParams } from '@angular/common/http';
import { SecurityService } from './security.service';
import { SeasonalPeriodType } from '../../entities/seasonal.returns.result';

/**
 * The seasonal return matrix is read through an `httpResource`, which turns the plain params object into HttpParams
 * itself. The backend binds the two flags as booleans, so they must arrive as "true" / "false".
 */
describe('SecurityService.getSeasonalReturnsRequest', () => {
  it('addresses the instrument and passes all three inputs as query parameters', () => {
    const request = new SecurityService(null, null, null).getSeasonalReturnsRequest(
      42,
      SeasonalPeriodType.QUARTERLY,
      true,
      false
    );

    expect(request.url).toBe('/api/security/42/seasonalreturns');
    expect(request.method).toBeUndefined();
    expect(new HttpParams({ fromObject: request.params as Record<string, string | boolean> }).toString()).toBe(
      'periodType=QUARTERLY&includeDividends=true&inTenantCurrency=false'
    );
  });
});
