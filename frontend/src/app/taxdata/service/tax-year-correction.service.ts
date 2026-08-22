import { Injectable } from '@angular/core';
import { HttpClient } from '@angular/common/http';
import { Observable } from 'rxjs';
import { catchError } from 'rxjs/operators';

import { TaxYearCorrection, TaxYearCorrectionSecurityInfo } from '../../entities/tax.year.correction';
import { AuthServiceWithLogout } from '../../lib/login/service/base.auth.service.with.logout';
import { LoginService } from '../../lib/login/service/log-in.service';
import { MessageToastService } from '../../lib/message/message.toast.service';
import { BaseSettings } from '../../lib/base.settings';
import { DeleteService } from '../../lib/datashowbase/delete.service';
import { ServiceEntityUpdate } from '../../lib/edit/service.entity.update';
import { AppSettings } from '../../shared/app.settings';

/**
 * CRUD service for {@link TaxYearCorrection}. Backed by {@code /api/taxyearcorrection}. Update/create routes via
 * the shared {@link AuthServiceWithLogout#updateEntity} helper, which picks PUT vs POST based on whether the
 * entity already has an id.
 */
@Injectable()
export class TaxYearCorrectionService
  extends AuthServiceWithLogout<TaxYearCorrection>
  implements DeleteService, ServiceEntityUpdate<TaxYearCorrection>
{
  constructor(loginService: LoginService, httpClient: HttpClient, messageToastService: MessageToastService) {
    super(loginService, httpClient, messageToastService);
  }

  /**
   * Loads the maintenance dialog payload for one security: all corrections of the tenant across all tax years
   * plus the tax years for which ICTax data exists for the security's ISIN.
   *
   * @param idSecuritycurrency the security identifier
   * @returns the corrections and the tax years with ICTax data
   */
  getBySecurity(idSecuritycurrency: number): Observable<TaxYearCorrectionSecurityInfo> {
    return <Observable<TaxYearCorrectionSecurityInfo>>(
      this.httpClient
        .get(
          `${BaseSettings.API_ENDPOINT}${AppSettings.TAX_YEAR_CORRECTION_KEY}/security/${idSecuritycurrency}`,
          this.getHeaders()
        )
        .pipe(catchError(this.handleError.bind(this)))
    );
  }

  update(entity: TaxYearCorrection): Observable<TaxYearCorrection> {
    return this.updateEntity(entity, entity.idTaxYearCorrection, AppSettings.TAX_YEAR_CORRECTION_KEY);
  }

  public deleteEntity(idTaxYearCorrection: number): Observable<any> {
    return this.httpClient
      .delete(
        `${BaseSettings.API_ENDPOINT}${AppSettings.TAX_YEAR_CORRECTION_KEY}/${idTaxYearCorrection}`,
        this.getHeaders()
      )
      .pipe(catchError(this.handleError.bind(this)));
  }
}
