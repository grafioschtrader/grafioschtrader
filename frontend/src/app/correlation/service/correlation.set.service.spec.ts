import { describe, expect, it } from 'vitest';
import { CorrelationSetService } from './correlation.set.service';
import { Injector, runInInjectionContext } from '@angular/core';
import { HttpClient } from '@angular/common/http';
import { LoginService } from '../../lib/login/service/log-in.service';
import { MessageToastService } from '../../lib/message/message.toast.service';

/** The correlation matrix is read through an `httpResource`; its request must stay an idempotent GET of one set. */
describe('CorrelationSetService.getCalculationByCorrelationSetRequest', () => {
  it('requests the calculation of the given correlation set', () => {
    const injector = Injector.create({
      providers: [
        { provide: HttpClient, useValue: null },
        { provide: LoginService, useValue: null },
        { provide: MessageToastService, useValue: null }
      ]
    });
    const request = runInInjectionContext(
      injector,
      () => new CorrelationSetService()
    ).getCalculationByCorrelationSetRequest(7);

    expect(request.url).toBe('/api/correlationset/calculation/7');
    expect(request.method).toBeUndefined();
    expect(request.body).toBeUndefined();
  });
});
