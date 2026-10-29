import { HttpResourceRequest } from '@angular/common/http';
import { Injectable } from '@angular/core';
import { Observable } from 'rxjs';
import { catchError } from 'rxjs/operators';

import { TaxYearCorrection, TaxYearCorrectionSecurityInfo } from '../../entities/tax.year.correction';
import { BaseSettings } from '../../lib/base.settings';
import { DeleteService } from '../../lib/datashowbase/delete.service';
import { ServiceEntityUpdate } from '../../lib/edit/service.entity.update';
import { AuthServiceWithLogout } from '../../lib/login/service/base.auth.service.with.logout';
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
  /** Builds the cancellable read used by signal-driven views and observable callers. */
  getBySecurityRequest(idSecuritycurrency: number) {
    return {
      url: `${BaseSettings.API_ENDPOINT}${AppSettings.TAX_YEAR_CORRECTION_KEY}/security/${idSecuritycurrency}`,
      headers: this.prepareHeaders()
    } satisfies HttpResourceRequest;
  }

  /**
   * Loads the maintenance dialog payload for one security: all corrections of the tenant across all tax years
   * plus the tax years for which ICTax data exists for the security's ISIN.
   *
   * @param idSecuritycurrency the security identifier
   * @returns the corrections and the tax years with ICTax data
   */
  getBySecurity(idSecuritycurrency: number): Observable<TaxYearCorrectionSecurityInfo> {
    const { url, ...options } = this.getBySecurityRequest(idSecuritycurrency);
    return this.httpClient
      .get<TaxYearCorrectionSecurityInfo>(url, options)
      .pipe(catchError(this.handleError.bind(this)));
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
