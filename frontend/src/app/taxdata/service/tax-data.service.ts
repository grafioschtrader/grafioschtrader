import {Injectable} from '@angular/core';
import {Observable} from 'rxjs';
import {catchError} from 'rxjs/operators';
import {HttpClient} from '@angular/common/http';
import {LoginService} from '../../lib/login/service/log-in.service';
import {MessageToastService} from '../../lib/message/message.toast.service';
import {AuthServiceWithLogout} from '../../lib/login/service/base.auth.service.with.logout';
import {BaseSettings} from '../../lib/base.settings';
import {UploadServiceFunction} from '../../lib/generaldialog/model/file.upload.param';
import {TaxCountry, TaxUpload, TaxYear} from '../model/tax-data.model';
import {ValueKeyHtmlSelectOptions} from '../../lib/dynamic-form/models/value.key.html.select.options';

@Injectable()
export class TaxDataService extends AuthServiceWithLogout<TaxCountry> implements UploadServiceFunction {

  private static readonly BASE_URL = BaseSettings.API_ENDPOINT + 'taxdata';

  constructor(loginService: LoginService, httpClient: HttpClient, messageToastService: MessageToastService) {
    super(loginService, httpClient, messageToastService);
  }

  getCantons(): Observable<ValueKeyHtmlSelectOptions[]> {
    return <Observable<ValueKeyHtmlSelectOptions[]>>this.httpClient.get(`${TaxDataService.BASE_URL}/cantons`,
      this.getHeaders()).pipe(catchError(this.handleError.bind(this)));
  }

  getTree(): Observable<TaxCountry[]> {
    return <Observable<TaxCountry[]>>this.httpClient.get(`${TaxDataService.BASE_URL}/tree`,
      this.getHeaders()).pipe(catchError(this.handleError.bind(this)));
  }

  createCountry(taxCountry: TaxCountry): Observable<TaxCountry> {
    return <Observable<TaxCountry>>this.httpClient.post(`${TaxDataService.BASE_URL}/country`,
      taxCountry, this.getHeaders()).pipe(catchError(this.handleError.bind(this)));
  }

  deleteCountry(id: number): Observable<any> {
    return this.httpClient.delete(`${TaxDataService.BASE_URL}/country/${id}`,
      this.getHeaders()).pipe(catchError(this.handleError.bind(this)));
  }

  createYear(taxYear: TaxYear): Observable<TaxYear> {
    return <Observable<TaxYear>>this.httpClient.post(`${TaxDataService.BASE_URL}/year`,
      taxYear, this.getHeaders()).pipe(catchError(this.handleError.bind(this)));
  }

  deleteYear(id: number): Observable<any> {
    return this.httpClient.delete(`${TaxDataService.BASE_URL}/year/${id}`,
      this.getHeaders()).pipe(catchError(this.handleError.bind(this)));
  }

  uploadFiles(idTaxYear: number, formData: FormData): Observable<TaxUpload[]> {
    return <Observable<TaxUpload[]>>this.httpClient.post(
      `${TaxDataService.BASE_URL}/year/${idTaxYear}/upload`,
      formData, this.getMultipartHeaders()).pipe(catchError(this.handleError.bind(this)));
  }

  reimport(idTaxUpload: number): Observable<TaxUpload> {
    return <Observable<TaxUpload>>this.httpClient.post(
      `${TaxDataService.BASE_URL}/upload/${idTaxUpload}/reimport`,
      null, this.getHeaders()).pipe(catchError(this.handleError.bind(this)));
  }

  deleteUpload(id: number): Observable<any> {
    return this.httpClient.delete(`${TaxDataService.BASE_URL}/upload/${id}`,
      this.getHeaders()).pipe(catchError(this.handleError.bind(this)));
  }

  toggleSecurityExclusion(taxYear: number, idSecuritycurrency: number): Observable<{excluded: boolean}> {
    return this.httpClient.post<{excluded: boolean}>(
      `${TaxDataService.BASE_URL}/security-exclusion/toggle`,
      {taxYear, idSecuritycurrency},
      this.getHeaders()).pipe(catchError(this.handleError.bind(this)));
  }

  exportEch0196(request: TaxStatementExportRequest): Observable<Blob> {
    return this.httpClient.post(`${TaxDataService.BASE_URL}/export/ech0196`, request,
      {...this.getHeaders(), responseType: 'blob' as 'json'}).pipe(catchError(this.handleError.bind(this))) as Observable<Blob>;
  }
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
