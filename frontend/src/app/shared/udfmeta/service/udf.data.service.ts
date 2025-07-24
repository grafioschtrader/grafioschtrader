import {Injectable} from '@angular/core';
import {AuthServiceWithLogout} from '../../../lib/login/service/base.auth.service.with.logout';
import {UDFData} from '../model/udf.metadata';
import {LoginService} from '../../../lib/login/service/log-in.service';
import {HttpClient} from '@angular/common/http';
import {MessageToastService} from '../../../lib/message/message.toast.service';
import {Observable} from 'rxjs';
import {AppSettings} from '../../app.settings';
import {catchError} from 'rxjs/operators';
import {BaseSettings} from '../../../lib/base.settings';

@Injectable()
export class UDFDataService extends AuthServiceWithLogout<UDFData> {
  constructor(loginService: LoginService, httpClient: HttpClient, messageToastService: MessageToastService) {
    super(loginService, httpClient, messageToastService);
  }

  public getUDFDataByEntityAndIdEntity(entity: string, idEntity: number): Observable<UDFData> {
    return <Observable<UDFData>>this.httpClient.get(`${BaseSettings.API_ENDPOINT}${AppSettings.UDF_DATA_KEY}`
      + `/${entity}/${idEntity}`,
      this.getHeaders()).pipe(catchError(this.handleError.bind(this)));
  }

  public update(udfData: UDFData, idUser: number): Observable<UDFData> {
    return this.updateEntity(udfData, idUser, AppSettings.UDF_DATA_KEY);
  }

  public deleteEntity(idUDFData: number): Observable<any> {
    return this.httpClient.delete(`${BaseSettings.API_ENDPOINT}${AppSettings.UDF_DATA_KEY}/${idUDFData}`, this.getHeaders())
      .pipe(catchError(this.handleError.bind(this)));
  }
}
