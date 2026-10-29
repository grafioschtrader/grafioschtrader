import { HttpResourceRequest } from '@angular/common/http';
import { Service } from '@angular/core';
import { Observable } from 'rxjs';
import { catchError } from 'rxjs/operators';
import { BaseSettings } from '../../lib/base.settings';
import { ValueKeyHtmlSelectOptions } from '../../lib/dynamic-form/models/value.key.html.select.options';
import { UploadServiceFunction } from '../../lib/generaldialog/model/file.upload.param';
import { AuthServiceWithLogout } from '../../lib/login/service/base.auth.service.with.logout';
import { IctaxExchangeRate, TaxCountry, TaxUpload, TaxYear } from '../model/tax-data.model';

@Service()
export class TaxDataService extends AuthServiceWithLogout<TaxCountry> implements UploadServiceFunction {
  /** Builds the cancellable read used by signal-driven views and observable callers. */
  getExchangeRatesRequest(idTaxYear: number) {
    return {
      url: `${TaxDataService.BASE_URL}/year/${idTaxYear}/exchangerate`,
      headers: this.prepareHeaders()
    } satisfies HttpResourceRequest;
  }

  private static readonly BASE_URL = BaseSettings.API_ENDPOINT + 'taxdata';

  getCantons(): Observable<ValueKeyHtmlSelectOptions[]> {
    return this.httpClient
      .get<ValueKeyHtmlSelectOptions[]>(`${TaxDataService.BASE_URL}/cantons`, this.getHeaders())
      .pipe(catchError(this.handleError.bind(this)));
  }

  getTaxModel(id: number): Observable<{ yaml: string }> {
    return this.httpClient
      .get<{ yaml: string }>(`${TaxDataService.BASE_URL}/country/${id}/taxmodelyaml`, this.getHeaders())
      .pipe(catchError(this.handleError.bind(this)));
  }

  saveTaxModel(id: number, yaml: string): Observable<{ yaml: string }> {
    return this.httpClient
      .put<{ yaml: string }>(`${TaxDataService.BASE_URL}/country/${id}/taxmodelyaml`, { yaml }, this.getHeaders())
      .pipe(catchError(this.handleError.bind(this)));
  }

  validateTaxModel(yaml: string): Observable<string[]> {
    return this.httpClient
      .post<string[]>(`${TaxDataService.BASE_URL}/taxmodelyaml/validate`, { yaml }, this.getHeaders())
      .pipe(catchError(this.handleError.bind(this)));
  }

  estimateTax(request: object): Observable<any> {
    return this.httpClient
      .post(`${TaxDataService.BASE_URL}/estimatetaxyaml`, request, this.getHeaders())
      .pipe(catchError(this.handleError.bind(this)));
  }

  taxOptions(kind: 'eventkinds' | 'coupondaycounts'): Observable<ValueKeyHtmlSelectOptions[]> {
    return this.httpClient.get<ValueKeyHtmlSelectOptions[]>(`${TaxDataService.BASE_URL}/${kind}`, this.getHeaders());
  }

  /**
   * Loads the usual coupon day-count convention per bond currency, which the bond terms of a security propose.
   *
   * @returns the fallback convention and the currencies deviating from it
   */
  getCouponDayCountDefaults(): Observable<CouponDayCountDefaults> {
    return this.httpClient
      .get<CouponDayCountDefaults>(`${TaxDataService.BASE_URL}/coupondaycounts/defaults`, this.getHeaders())
      .pipe(catchError(this.handleError.bind(this)));
  }

  getTree(): Observable<TaxCountry[]> {
    return this.httpClient
      .get<TaxCountry[]>(`${TaxDataService.BASE_URL}/tree`, this.getHeaders())
      .pipe(catchError(this.handleError.bind(this)));
  }

  createCountry(taxCountry: TaxCountry): Observable<TaxCountry> {
    return this.httpClient
      .post<TaxCountry>(`${TaxDataService.BASE_URL}/country`, taxCountry, this.getHeaders())
      .pipe(catchError(this.handleError.bind(this)));
  }

  deleteCountry(id: number): Observable<any> {
    return this.httpClient
      .delete(`${TaxDataService.BASE_URL}/country/${id}`, this.getHeaders())
      .pipe(catchError(this.handleError.bind(this)));
  }

  createYear(taxYear: TaxYear): Observable<TaxYear> {
    return this.httpClient
      .post<TaxYear>(`${TaxDataService.BASE_URL}/year`, taxYear, this.getHeaders())
      .pipe(catchError(this.handleError.bind(this)));
  }

  deleteYear(id: number): Observable<any> {
    return this.httpClient
      .delete(`${TaxDataService.BASE_URL}/year/${id}`, this.getHeaders())
      .pipe(catchError(this.handleError.bind(this)));
  }

  uploadFiles(idTaxYear: number, formData: FormData): Observable<TaxUpload[]> {
    return this.httpClient
      .post<TaxUpload[]>(`${TaxDataService.BASE_URL}/year/${idTaxYear}/upload`, formData, this.getMultipartHeaders())
      .pipe(catchError(this.handleError.bind(this)));
  }

  reimport(idTaxUpload: number): Observable<TaxUpload> {
    return this.httpClient
      .post<TaxUpload>(`${TaxDataService.BASE_URL}/upload/${idTaxUpload}/reimport`, null, this.getHeaders())
      .pipe(catchError(this.handleError.bind(this)));
  }

  deleteUpload(id: number): Observable<any> {
    return this.httpClient
      .delete(`${TaxDataService.BASE_URL}/upload/${id}`, this.getHeaders())
      .pipe(catchError(this.handleError.bind(this)));
  }

  getExchangeRates(idTaxYear: number): Observable<IctaxExchangeRate[]> {
    const { url, ...options } = this.getExchangeRatesRequest(idTaxYear);
    return this.httpClient.get<IctaxExchangeRate[]>(url, options).pipe(catchError(this.handleError.bind(this)));
  }

  updateExchangeRate(exchangeRate: IctaxExchangeRate): Observable<IctaxExchangeRate> {
    return this.httpClient
      .put<IctaxExchangeRate>(`${TaxDataService.BASE_URL}/exchangerate`, exchangeRate, this.getHeaders())
      .pipe(catchError(this.handleError.bind(this)));
  }

  toggleSecurityExclusion(taxYear: number, idSecuritycurrency: number): Observable<{ excluded: boolean }> {
    return this.httpClient
      .post<{ excluded: boolean }>(
        `${TaxDataService.BASE_URL}/security-exclusion/toggle`,
        { taxYear, idSecuritycurrency },
        this.getHeaders()
      )
      .pipe(catchError(this.handleError.bind(this)));
  }

  exportEch0196(request: TaxStatementExportRequest): Observable<Blob> {
    return this.httpClient
      .post(`${TaxDataService.BASE_URL}/export/ech0196`, request, {
        ...this.getHeaders(),
        responseType: 'blob'
      })
      .pipe(catchError(this.handleError.bind(this)));
  }
}

/** Usual coupon day-count convention per bond currency, served by the backend. */
export interface CouponDayCountDefaults {
  /** Convention for every currency not listed in byCurrency. */
  fallback: string;
  /** Deviating conventions keyed by ISO 4217 currency code. */
  byCurrency: { [currency: string]: string };
}

export interface TaxStatementExportRequest {
  taxYear: number;
  canton: string;
  institutionName: string;
  institutionLei?: string;
  clientNumber: string;
  clientFirstName?: string;
  clientLastName?: string;
  clientTin?: string;
  idsSecurityaccount?: number[];
}
