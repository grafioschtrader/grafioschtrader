import { Injectable } from '@angular/core';
import { HttpClient } from '@angular/common/http';
import { Observable } from 'rxjs';
import { catchError } from 'rxjs/operators';
import { BankruptSecurity, BankruptSecurityRow } from '../../entities/bankrupt.security';
import { AppSettings } from '../../shared/app.settings';
import { BaseSettings } from '../../lib/base.settings';
import { AuthServiceWithLogout } from '../../lib/login/service/base.auth.service.with.logout';
import { DeleteService } from '../../lib/datashowbase/delete.service';
import { ServiceEntityUpdate } from '../../lib/edit/service.entity.update';
import { LoginService } from '../../lib/login/service/log-in.service';
import { MessageToastService } from '../../lib/message/message.toast.service';

/** REST client for the instruments marked as no longer receiving price data. */
@Injectable()
export class BankruptSecurityService
  extends AuthServiceWithLogout<BankruptSecurity>
  implements DeleteService, ServiceEntityUpdate<BankruptSecurity>
{
  constructor(loginService: LoginService, httpClient: HttpClient, messageToastService: MessageToastService) {
    super(loginService, httpClient, messageToastService);
  }

  /** Every marker with the instrument behind it and the two newest closing dates. */
  public getAllWithSecurityName(): Observable<BankruptSecurityRow[]> {
    return <Observable<BankruptSecurityRow[]>>(
      this.httpClient
        .get(`${BaseSettings.API_ENDPOINT}${AppSettings.BANKRUPT_SECURITY_KEY}`, this.getHeaders())
        .pipe(catchError(this.handleError.bind(this)))
    );
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
