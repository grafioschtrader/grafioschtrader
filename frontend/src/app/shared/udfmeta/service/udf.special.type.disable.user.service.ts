import {Injectable} from '@angular/core';
import {AuthServiceWithLogout} from '../../login/service/base.auth.service.with.logout';
import {UDFSpecialType, UDFSpecialTypeDisableUser} from '../model/udf.metadata';
import {Observable} from 'rxjs';
import {LoginService} from '../../login/service/log-in.service';
import {HttpClient} from '@angular/common/http';
import {MessageToastService} from '../../../lib/message/message.toast.service';
import {AppSettings} from '../../app.settings';
import {catchError} from 'rxjs/operators';

@Injectable()
export class UDFSpecialTypeDisableUserService extends AuthServiceWithLogout<UDFSpecialTypeDisableUser> {
  constructor(loginService: LoginService, httpClient: HttpClient, messageToastService: MessageToastService) {
    super(loginService, httpClient, messageToastService);
  }

  public getDisabledSpecialTypes(): Observable<UDFSpecialType[]> {
    return  <Observable<UDFSpecialType[]>> this.httpClient.get(`${AppSettings.API_ENDPOINT}${AppSettings.UDF_SPECIAL_TYPE_DISABLE_USER}`,
      this.getHeaders()).pipe(catchError(this.handleError.bind(this)));
  }

  public create(udfSpecialType: string): Observable<any> {
    return this.httpClient.post(`${AppSettings.API_ENDPOINT}${AppSettings.UDF_SPECIAL_TYPE_DISABLE_USER}`,
      udfSpecialType, this.getHeaders()).pipe(catchError(this.handleError.bind(this)));
  }

  public delete(udfSpecialType: string): Observable<any> {
    return this.httpClient.delete(`${AppSettings.API_ENDPOINT}${AppSettings.UDF_SPECIAL_TYPE_DISABLE_USER}/${udfSpecialType}`,
      this.getHeaders()).pipe(catchError(this.handleError.bind(this)));
  }
}
