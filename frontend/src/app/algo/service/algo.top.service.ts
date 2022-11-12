import {AuthServiceWithLogout} from '../../shared/login/service/base.auth.service.with.logout';
import {AlgoTop} from '../model/algo.top';
import {Injectable} from '@angular/core';
import {DeleteService} from '../../shared/datashowbase/delete.service';
import {ServiceEntityUpdate} from '../../shared/edit/service.entity.update';
import {Observable} from 'rxjs';
import {AppSettings} from '../../shared/app.settings';
import {catchError} from 'rxjs/operators';
import {LoginService} from '../../shared/login/service/log-in.service';
import {HttpClient} from '@angular/common/http';
import {MessageToastService} from '../../shared/message/message.toast.service';
import {AlgoTopCreate} from '../../entities/backend/algo.top.create';

@Injectable()
export class AlgoTopService extends AuthServiceWithLogout<AlgoTop> implements DeleteService, ServiceEntityUpdate<AlgoTop> {

  constructor(loginService: LoginService, httpClient: HttpClient, messageToastService: MessageToastService) {
    super(loginService, httpClient, messageToastService);
  }

  getAlgoTopByIdTenantOrderByName(): Observable<AlgoTop[]> {
    return <Observable<AlgoTop[]>>this.httpClient.get(`${AppSettings.API_ENDPOINT}${AppSettings.ALGO_TOP_KEY}/${AppSettings.TENANT_KEY}`,
      this.getHeaders()).pipe(catchError(this.handleError.bind(this)));
  }

  getAlgoTopByIdAlgoAssetclassSecurity(idAlgoAssetclassSecurity: number): Observable<AlgoTop> {
    return <Observable<AlgoTop>>this.httpClient.get(`${AppSettings.API_ENDPOINT}${AppSettings.ALGO_TOP_KEY}/${idAlgoAssetclassSecurity}`,
      this.getHeaders()).pipe(catchError(this.handleError.bind(this)));
  }

  public create(algoTopCreate: AlgoTopCreate): Observable<AlgoTop> {
    return <Observable<AlgoTop>>this.httpClient.post(`${AppSettings.API_ENDPOINT}${AppSettings.ALGO_TOP_KEY}/create`, algoTopCreate,
      {headers: this.prepareHeaders()}).pipe(catchError(this.handleError.bind(this)));
  }

  public update(algoTop: AlgoTop): Observable<AlgoTop> {
    return this.updateEntity(algoTop, algoTop.idAlgoAssetclassSecurity, AppSettings.ALGO_TOP_KEY);
  }

  public deleteEntity(idAlgoAssetclassSecurity: number): Observable<any> {
    return this.httpClient.delete(`${AppSettings.API_ENDPOINT}${AppSettings.ALGO_TOP_KEY}/${idAlgoAssetclassSecurity}`,
      this.getHeaders()).pipe(catchError(this.handleError.bind(this)));
  }
}
