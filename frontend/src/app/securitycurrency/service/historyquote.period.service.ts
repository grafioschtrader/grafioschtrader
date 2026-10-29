import { Service } from '@angular/core';
import { AuthServiceWithLogout } from '../../lib/login/service/base.auth.service.with.logout';
import { HistoryquotePeriod } from '../../entities/historyquote.period';
import { DeleteCreateMultiple } from './delete.create.multiple';
import { Observable } from 'rxjs';
import { AppSettings } from '../../shared/app.settings';
import { catchError } from 'rxjs/operators';
import { BaseSettings } from '../../lib/base.settings';

@Service()
export class HistoryquotePeriodService
  extends AuthServiceWithLogout<HistoryquotePeriod>
  implements DeleteCreateMultiple<HistoryquotePeriod>
{
  getHistoryquotePeriodByIdSecuritycurrency(idSecuritycurrency: number): Observable<HistoryquotePeriod[]> {
    return this.httpClient
      .get<HistoryquotePeriod[]>(
        `${BaseSettings.API_ENDPOINT}${AppSettings.HISTORYQUOTE_PERIOD_KEY}` +
          `/${idSecuritycurrency}/${AppSettings.SECURITY_KEY}`,
        this.getHeaders()
      )
      .pipe(catchError(this.handleError.bind(this)));
  }

  deleteAndCreateMultiple(
    idSecuritycurrency: number,
    historyquotePeriods: HistoryquotePeriod[],
    noteRequest: string
  ): Observable<HistoryquotePeriod[]> {
    return this.httpClient
      .post<HistoryquotePeriod[]>(
        `${BaseSettings.API_ENDPOINT}${AppSettings.HISTORYQUOTE_PERIOD_KEY}`,
        { idSecuritycurrency, historyquotePeriods, noteRequest },
        this.getHeaders()
      )
      .pipe(catchError(this.handleError.bind(this)));
  }
}
