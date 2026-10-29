import { Service } from '@angular/core';
import { Observable } from 'rxjs';
import { AppSettings } from '../../shared/app.settings';
import { AuthServiceWithLogout } from '../../lib/login/service/base.auth.service.with.logout';
import { catchError } from 'rxjs/operators';
import { DeleteCreateMultiple } from './delete.create.multiple';
import { Securitysplit } from '../../entities/dividend.split';
import { BaseSettings } from '../../lib/base.settings';

@Service()
export class SecuritysplitService
  extends AuthServiceWithLogout<Securitysplit>
  implements DeleteCreateMultiple<Securitysplit>
{
  getSecuritysplitsByIdSecuritycurrency(idSecuritycurrency: number): Observable<Securitysplit[]> {
    return this.httpClient
      .get<Securitysplit[]>(
        `${BaseSettings.API_ENDPOINT}${AppSettings.SECURITY_SPLIT_KEY}` +
          `/${idSecuritycurrency}/${AppSettings.SECURITY_KEY}`,
        this.getHeaders()
      )
      .pipe(catchError(this.handleError.bind(this)));
  }

  deleteAndCreateMultiple(
    idSecuritycurrency: number,
    securitysplits: Securitysplit[],
    noteRequest: string
  ): Observable<Securitysplit[]> {
    return this.httpClient
      .post<Securitysplit[]>(
        `${BaseSettings.API_ENDPOINT}${AppSettings.SECURITY_SPLIT_KEY}`,
        { idSecuritycurrency, securitysplits, noteRequest },
        this.getHeaders()
      )
      .pipe(catchError(this.handleError.bind(this)));
  }
}
