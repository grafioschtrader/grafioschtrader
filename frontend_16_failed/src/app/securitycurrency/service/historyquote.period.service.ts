import {Injectable} from '@angular/core';
import {AuthServiceWithLogout} from '../../shared/login/service/base.auth.service.with.logout';
import {HistoryquotePeriod} from '../../entities/historyquote.period';
import {DeleteCreateMultiple} from './delete.create.multiple';
import {LoginService} from '../../shared/login/service/log-in.service';
import {HttpClient} from '@angular/common/http';
import {MessageToastService} from '../../shared/message/message.toast.service';
import {Observable} from 'rxjs';
import {AppSettings} from '../../shared/app.settings';
import {catchError} from 'rxjs/operators';

@Injectable()
export class HistoryquotePeriodService extends AuthServiceWithLogout<HistoryquotePeriod>
  implements DeleteCreateMultiple<HistoryquotePeriod> {

  constructor(loginService: LoginService, httpClient: HttpClient, messageToastService: MessageToastService) {
    super(loginService, httpClient, messageToastService);
  }

  getHistoryquotePeriodByIdSecuritycurrency(idSecuritycurrency: number): Observable<HistoryquotePeriod[]> {
    return <Observable<HistoryquotePeriod[]>>this.httpClient.get(`${AppSettings.API_ENDPOINT}${AppSettings.HISTORYQUOTE_PERIOD_KEY}`
      + `/${idSecuritycurrency}/${AppSettings.SECURITY_KEY}`, this.getHeaders()).pipe(catchError(this.handleError.bind(this)));
  }

  deleteAndCreateMultiple(idSecuritycurrency: number, historyquotePeriods: HistoryquotePeriod[],
                          noteRequest: string): Observable<HistoryquotePeriod[]> {
    return <Observable<HistoryquotePeriod[]>>this.httpClient.post(`${AppSettings.API_ENDPOINT}${AppSettings.HISTORYQUOTE_PERIOD_KEY}`,
      {idSecuritycurrency, historyquotePeriods, noteRequest},
      this.getHeaders()).pipe(catchError(this.handleError.bind(this)));
  }
}
