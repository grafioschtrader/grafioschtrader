import { describe, expect, it } from 'vitest';
import { HttpParams } from '@angular/common/http';
import { SecurityService } from './security.service';
import { SeasonalPeriodType } from '../../entities/seasonal.returns.result';
import { Injector, runInInjectionContext } from '@angular/core';
import { HttpClient } from '@angular/common/http';
import { LoginService } from '../../lib/login/service/log-in.service';
import { MessageToastService } from '../../lib/message/message.toast.service';

/**
 * The seasonal return matrix is read through an `httpResource`, which turns the plain params object into HttpParams
 * itself. The backend binds the two flags as booleans, so they must arrive as "true" / "false".
 */
describe('SecurityService.getSeasonalReturnsRequest', () => {
  it('addresses the instrument and passes all three inputs as query parameters', () => {
    const injector = Injector.create({
      providers: [
        { provide: HttpClient, useValue: null },
        { provide: LoginService, useValue: null },
        { provide: MessageToastService, useValue: null }
      ]
    });
    const request = runInInjectionContext(injector, () => new SecurityService()).getSeasonalReturnsRequest(
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
