import { Service } from '@angular/core';
import { Observable } from 'rxjs';
import { catchError } from 'rxjs/operators';
import { BankruptSecurity, BankruptSecurityRow } from '../../entities/bankrupt.security';
import { AppSettings } from '../../shared/app.settings';
import { BaseSettings } from '../../lib/base.settings';
import { AuthServiceWithLogout } from '../../lib/login/service/base.auth.service.with.logout';
import { DeleteService } from '../../lib/datashowbase/delete.service';
import { ServiceEntityUpdate } from '../../lib/edit/service.entity.update';

/** REST client for the instruments marked as no longer receiving price data. */
@Service()
export class BankruptSecurityService
  extends AuthServiceWithLogout<BankruptSecurity>
  implements DeleteService, ServiceEntityUpdate<BankruptSecurity>
{
  /** Every marker with the instrument behind it and the two newest closing dates. */
  public getAllWithSecurityName(): Observable<BankruptSecurityRow[]> {
    return this.httpClient
      .get<BankruptSecurityRow[]>(`${BaseSettings.API_ENDPOINT}${AppSettings.BANKRUPT_SECURITY_KEY}`, this.getHeaders())
      .pipe(catchError(this.handleError.bind(this)));
  }

  public update(bankruptSecurity: BankruptSecurity): Observable<BankruptSecurity> {
    return this.updateEntity(bankruptSecurity, bankruptSecurity.idBankruptSecurity, AppSettings.BANKRUPT_SECURITY_KEY);
  }

  public deleteEntity(idBankruptSecurity: number): Observable<any> {
    return this.httpClient
      .delete(
        `${BaseSettings.API_ENDPOINT}${AppSettings.BANKRUPT_SECURITY_KEY}/${idBankruptSecurity}`,
        this.getHeaders()
      )
      .pipe(catchError(this.handleError.bind(this)));
  }
}
