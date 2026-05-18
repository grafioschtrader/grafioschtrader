import {Injectable} from '@angular/core';
import {HttpClient} from '@angular/common/http';
import {Observable} from 'rxjs';
import {catchError} from 'rxjs/operators';

import {RiskFreeInstrumentOption, RiskFreeRateMapping} from '../../../entities/risk.free.rate.mapping';
import {AuthServiceWithLogout} from '../../../lib/login/service/base.auth.service.with.logout';
import {LoginService} from '../../../lib/login/service/log-in.service';
import {MessageToastService} from '../../../lib/message/message.toast.service';
import {BaseSettings} from '../../../lib/base.settings';
import {DeleteService} from '../../../lib/datashowbase/delete.service';
import {ServiceEntityUpdate} from '../../../lib/edit/service.entity.update';
import {AppSettings} from '../../app.settings';

/**
 * CRUD service for {@link RiskFreeRateMapping}. Backed by {@code /api/riskfreeratemapping}. Update/create routes via
 * the shared {@link AuthServiceWithLogout#updateEntity} helper, which picks PUT vs POST based on whether the entity
 * already has an id.
 */
@Injectable()
export class RiskFreeRateMappingService extends AuthServiceWithLogout<RiskFreeRateMapping>
  implements DeleteService, ServiceEntityUpdate<RiskFreeRateMapping> {

  constructor(loginService: LoginService, httpClient: HttpClient, messageToastService: MessageToastService) {
    super(loginService, httpClient, messageToastService);
  }

  getAll(): Observable<RiskFreeRateMapping[]> {
    return <Observable<RiskFreeRateMapping[]>>this.httpClient.get(
      `${BaseSettings.API_ENDPOINT}${AppSettings.RISK_FREE_RATE_MAPPING_KEY}`,
      this.getHeaders()).pipe(catchError(this.handleError.bind(this)));
  }

  getAllInstruments(): Observable<RiskFreeInstrumentOption[]> {
    return <Observable<RiskFreeInstrumentOption[]>>this.httpClient.get(
      `${BaseSettings.API_ENDPOINT}${AppSettings.RISK_FREE_RATE_MAPPING_KEY}/instruments`,
      this.getHeaders()).pipe(catchError(this.handleError.bind(this)));
  }

  update(entity: RiskFreeRateMapping): Observable<RiskFreeRateMapping> {
    return this.updateEntity(entity, entity.idRiskFreeRateMapping, AppSettings.RISK_FREE_RATE_MAPPING_KEY);
  }

  public deleteEntity(idRiskFreeRateMapping: number): Observable<any> {
    return this.httpClient.delete(
      `${BaseSettings.API_ENDPOINT}${AppSettings.RISK_FREE_RATE_MAPPING_KEY}/${idRiskFreeRateMapping}`,
      this.getHeaders()).pipe(catchError(this.handleError.bind(this)));
  }
}
