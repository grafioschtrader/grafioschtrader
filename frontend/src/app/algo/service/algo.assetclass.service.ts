import {AuthServiceWithLogout} from '../../lib/login/service/base.auth.service.with.logout';
import {DeleteService} from '../../lib/datashowbase/delete.service';
import {ServiceEntityUpdate} from '../../lib/edit/service.entity.update';
import {AlgoAssetclass} from '../model/algo.assetclass';
import {LoginService} from '../../lib/login/service/log-in.service';
import {HttpClient} from '@angular/common/http';
import {MessageToastService} from '../../lib/message/message.toast.service';
import {Observable} from 'rxjs';
import {AppSettings} from '../../shared/app.settings';
import {catchError} from 'rxjs/operators';
import {Injectable} from '@angular/core';
import {BaseSettings} from '../../lib/base.settings';

@Injectable()
export class AlgoAssetclassService extends AuthServiceWithLogout<AlgoAssetclass> implements DeleteService,
  ServiceEntityUpdate<AlgoAssetclass> {

  constructor(loginService: LoginService, httpClient: HttpClient, messageToastService: MessageToastService) {
    super(loginService, httpClient, messageToastService);
  }

  getAlgoAssetclassByIdTenantAndIdAlgoAssetclassParent(idAlgoAssetclassSecurity: number): Observable<AlgoAssetclass[]> {
    return <Observable<AlgoAssetclass[]>>this.httpClient.get(`${BaseSettings.API_ENDPOINT}${AppSettings.ALGO_ASSETCLASS_KEY}`
      + `/${idAlgoAssetclassSecurity}`, this.getHeaders()).pipe(catchError(this.handleError.bind(this)));
  }

  public update(algoAssetclass: AlgoAssetclass): Observable<AlgoAssetclass> {
    return this.updateEntity(algoAssetclass, algoAssetclass.idAlgoAssetclassSecurity, AppSettings.ALGO_ASSETCLASS_KEY);
  }

  public deleteEntity(idAlgoAssetclassSecurity: number): Observable<any> {
    return this.httpClient.delete(`${BaseSettings.API_ENDPOINT}${AppSettings.ALGO_ASSETCLASS_KEY}/${idAlgoAssetclassSecurity}`,
      this.getHeaders()).pipe(catchError(this.handleError.bind(this)));
  }

}
