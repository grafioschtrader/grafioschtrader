import {AuthServiceWithLogout} from '../../shared/login/service/base.auth.service.with.logout';
import {AlgoSecurity} from '../model/algo.security';
import {DeleteService} from '../../shared/datashowbase/delete.service';
import {ServiceEntityUpdate} from '../../shared/edit/service.entity.update';
import {LoginService} from '../../shared/login/service/log-in.service';
import {HttpClient} from '@angular/common/http';
import {MessageToastService} from '../../shared/message/message.toast.service';
import {Observable} from 'rxjs';
import {AppSettings} from '../../shared/app.settings';
import {catchError} from 'rxjs/operators';
import {Injectable} from '@angular/core';


@Injectable()
export class AlgoSecurityService extends AuthServiceWithLogout<AlgoSecurity> implements DeleteService,
  ServiceEntityUpdate<AlgoSecurity> {

  constructor(loginService: LoginService, httpClient: HttpClient, messageToastService: MessageToastService) {
    super(loginService, httpClient, messageToastService);
  }

  public update(algoSecurity: AlgoSecurity): Observable<AlgoSecurity> {
    return this.updateEntity(algoSecurity, algoSecurity.idAlgoAssetclassSecurity, AppSettings.ALGO_SECURITY_KEY);
  }

  public deleteEntity(idAlgoAssetclassSecurity: number): Observable<any> {
    return this.httpClient.delete(`${AppSettings.API_ENDPOINT}${AppSettings.ALGO_SECURITY_KEY}/${idAlgoAssetclassSecurity}`,
      this.getHeaders()).pipe(catchError(this.handleError.bind(this)));
  }

}
