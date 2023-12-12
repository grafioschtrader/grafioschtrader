import {AuthServiceWithLogout} from '../../shared/login/service/base.auth.service.with.logout';
import {Injectable} from '@angular/core';
import {LoginService} from '../../shared/login/service/log-in.service';
import {HttpClient} from '@angular/common/http';
import {MessageToastService} from '../../shared/message/message.toast.service';
import {Observable} from 'rxjs';
import {AppSettings} from '../../shared/app.settings';
import {catchError} from 'rxjs/operators';
import {Dividend} from '../../entities/dividend.split';

@Injectable()
export class DividendService extends AuthServiceWithLogout<Dividend> {
  constructor(loginService: LoginService, httpClient: HttpClient, messageToastService: MessageToastService) {
    super(loginService, httpClient, messageToastService);
  }

  getDividendsByIdSecuritycurrency(idSecuritycurrency: number): Observable<Dividend[]> {
    return <Observable<Dividend[]>>this.httpClient.get(`${AppSettings.API_ENDPOINT}${AppSettings.SECURITY_DIVIDEND_KEY}`
      + `/${idSecuritycurrency}`, this.getHeaders()).pipe(catchError(this.handleError.bind(this)));
  }
}
