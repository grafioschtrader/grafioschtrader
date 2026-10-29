import { AuthServiceWithLogout } from '../../lib/login/service/base.auth.service.with.logout';
import { AlgoSecurity, AlgoSecurityStrategyImplType } from '../model/algo.security';
import { DeleteService } from '../../lib/datashowbase/delete.service';
import { ServiceEntityUpdate } from '../../lib/edit/service.entity.update';
import { Observable } from 'rxjs';
import { AppSettings } from '../../shared/app.settings';
import { catchError } from 'rxjs/operators';
import { Service } from '@angular/core';
import { BaseSettings } from '../../lib/base.settings';

@Service()
export class AlgoSecurityService
  extends AuthServiceWithLogout<AlgoSecurity>
  implements DeleteService, ServiceEntityUpdate<AlgoSecurity>
{
  getAllForTenant(): Observable<AlgoSecurity[]> {
    return this.httpClient
      .get<AlgoSecurity[]>(`${BaseSettings.API_ENDPOINT}${AppSettings.ALGO_SECURITY_KEY}/tenant`, this.getHeaders())
      .pipe(catchError(this.handleError.bind(this)));
  }

  getAlgoSecurityStrategyImplTypeByIdSecuritycurrency(
    idSecuritycurrency: number
  ): Observable<AlgoSecurityStrategyImplType> {
    return this.httpClient
      .get<AlgoSecurityStrategyImplType>(
        `${BaseSettings.API_ENDPOINT}${AppSettings.ALGO_SECURITY_KEY}/security/${idSecuritycurrency}`,
        this.getHeaders()
      )
      .pipe(catchError(this.handleError.bind(this)));
  }

  public update(algoSecurity: AlgoSecurity): Observable<AlgoSecurity> {
    return this.updateEntity(algoSecurity, algoSecurity.idAlgoAssetclassSecurity, AppSettings.ALGO_SECURITY_KEY);
  }

  public deleteEntity(idAlgoAssetclassSecurity: number): Observable<any> {
    return this.httpClient
      .delete(
        `${BaseSettings.API_ENDPOINT}${AppSettings.ALGO_SECURITY_KEY}/${idAlgoAssetclassSecurity}`,
        this.getHeaders()
      )
      .pipe(catchError(this.handleError.bind(this)));
  }
}
