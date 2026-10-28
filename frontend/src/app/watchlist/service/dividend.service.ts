import { AuthServiceWithLogout } from '../../lib/login/service/base.auth.service.with.logout';
import { Service } from '@angular/core';
import { Observable } from 'rxjs';
import { AppSettings } from '../../shared/app.settings';
import { catchError } from 'rxjs/operators';
import { Dividend } from '../../entities/dividend.split';
import { BaseSettings } from '../../lib/base.settings';

@Service()
export class DividendService extends AuthServiceWithLogout<Dividend> {
  getDividendsByIdSecuritycurrency(idSecuritycurrency: number): Observable<Dividend[]> {
    return <Observable<Dividend[]>>(
      this.httpClient
        .get(
          `${BaseSettings.API_ENDPOINT}${AppSettings.SECURITY_DIVIDEND_KEY}` + `/${idSecuritycurrency}`,
          this.getHeaders()
        )
        .pipe(catchError(this.handleError.bind(this)))
    );
  }
}
