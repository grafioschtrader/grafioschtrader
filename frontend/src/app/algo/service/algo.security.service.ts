import {AuthServiceWithLogout} from '../../lib/login/service/base.auth.service.with.logout';
import {AlgoSecurity, AlgoSecurityStrategyImplType} from '../model/algo.security';
import {DeleteService} from '../../lib/datashowbase/delete.service';
import {ServiceEntityUpdate} from '../../lib/edit/service.entity.update';
import {LoginService} from '../../lib/login/service/log-in.service';
import {HttpClient} from '@angular/common/http';
import {MessageToastService} from '../../lib/message/message.toast.service';
import {Observable} from 'rxjs';
import {AppSettings} from '../../shared/app.settings';
import {catchError} from 'rxjs/operators';
import {Injectable} from '@angular/core';
import {BaseSettings} from '../../lib/base.settings';


@Injectable()
export class AlgoSecurityService extends AuthServiceWithLogout<AlgoSecurity> implements DeleteService,
  ServiceEntityUpdate<AlgoSecurity> {

  constructor(loginService: LoginService, httpClient: HttpClient, messageToastService: MessageToastService) {
    super(loginService, httpClient, messageToastService);
  }

  getAlgoSecurityStrategyImplTypeByIdSecuritycurrency(idSecuritycurrency: number): Observable<AlgoSecurityStrategyImplType> {
    return <Observable<AlgoSecurityStrategyImplType>>
      this.httpClient.get(`${BaseSettings.API_ENDPOINT}${AppSettings.ALGO_SECURITY_KEY}/security/${idSecuritycurrency}`,
        this.getHeaders()).pipe(catchError(this.handleError.bind(this)));
  }

  public update(algoSecurity: AlgoSecurity): Observable<AlgoSecurity> {
    return this.updateEntity(algoSecurity, algoSecurity.idAlgoAssetclassSecurity, AppSettings.ALGO_SECURITY_KEY);
  }

  public deleteEntity(idAlgoAssetclassSecurity: number): Observable<any> {
    return this.httpClient.delete(`${BaseSettings.API_ENDPOINT}${AppSettings.ALGO_SECURITY_KEY}/${idAlgoAssetclassSecurity}`,
      this.getHeaders()).pipe(catchError(this.handleError.bind(this)));
  }

}
