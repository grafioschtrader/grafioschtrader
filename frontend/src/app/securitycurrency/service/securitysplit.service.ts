import {Injectable} from '@angular/core';
import {Observable} from 'rxjs';
import {AppSettings} from '../../shared/app.settings';
import {MessageToastService} from '../../lib/message/message.toast.service';
import {AuthServiceWithLogout} from '../../lib/login/service/base.auth.service.with.logout';
import {HttpClient} from '@angular/common/http';
import {catchError} from 'rxjs/operators';
import {LoginService} from '../../lib/login/service/log-in.service';
import {DeleteCreateMultiple} from './delete.create.multiple';
import {Securitysplit} from '../../entities/dividend.split';
import {BaseSettings} from '../../lib/base.settings';

@Injectable()
export class SecuritysplitService extends AuthServiceWithLogout<Securitysplit> implements DeleteCreateMultiple<Securitysplit> {

  constructor(loginService: LoginService, httpClient: HttpClient, messageToastService: MessageToastService) {
    super(loginService, httpClient, messageToastService);
  }

  getSecuritysplitsByIdSecuritycurrency(idSecuritycurrency: number): Observable<Securitysplit[]> {
    return <Observable<Securitysplit[]>>this.httpClient.get(`${BaseSettings.API_ENDPOINT}${AppSettings.SECURITY_SPLIT_KEY}`
      + `/${idSecuritycurrency}/${AppSettings.SECURITY_KEY}`, this.getHeaders()).pipe(catchError(this.handleError.bind(this)));
  }

  deleteAndCreateMultiple(idSecuritycurrency: number, securitysplits: Securitysplit[], noteRequest: string): Observable<Securitysplit[]> {
    return <Observable<Securitysplit[]>>this.httpClient.post(`${BaseSettings.API_ENDPOINT}${AppSettings.SECURITY_SPLIT_KEY}`,
      {idSecuritycurrency, securitysplits, noteRequest},
      this.getHeaders()).pipe(catchError(this.handleError.bind(this)));
  }


}
