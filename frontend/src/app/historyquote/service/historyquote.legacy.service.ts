import {Injectable} from '@angular/core';
import {HttpClient} from '@angular/common/http';
import {Observable} from 'rxjs';
import {catchError} from 'rxjs/operators';

import {HistoryquoteLegacy} from '../../entities/historyquote.legacy';
import {AppSettings} from '../../shared/app.settings';
import {BaseSettings} from '../../lib/base.settings';
import {MessageToastService} from '../../lib/message/message.toast.service';
import {DeleteService} from '../../lib/datashowbase/delete.service';
import {AuthServiceWithLogout} from '../../lib/login/service/base.auth.service.with.logout';
import {ServiceEntityUpdate} from '../../lib/edit/service.entity.update';
import {LoginService} from '../../lib/login/service/log-in.service';
import {LegacySplitRequest} from '../model/legacy.split.request';

/**
 * Backend access for the {@code historyquote_legacy} shadow archive. Single rows are edited (via the propose-change
 * flow) and deleted through the inherited entity endpoints; the security-scoped bulk operations (list, forgotten split,
 * delete-all, CSV import) target {@code /historyquotelegacy/security/{id}}.
 */
@Injectable()
export class HistoryquoteLegacyService extends AuthServiceWithLogout<HistoryquoteLegacy>
  implements ServiceEntityUpdate<HistoryquoteLegacy>, DeleteService {

  constructor(loginService: LoginService, httpClient: HttpClient, messageToastService: MessageToastService) {
    super(loginService, httpClient, messageToastService);
  }

  update(historyquoteLegacy: HistoryquoteLegacy): Observable<HistoryquoteLegacy> {
    return this.updateEntity(historyquoteLegacy, historyquoteLegacy.idHistoryquoteLegacy,
      AppSettings.HISTORYQUOTE_LEGACY_KEY);
  }

  public deleteEntity(idHistoryquoteLegacy: number): Observable<any> {
    return this.httpClient.delete(
      `${BaseSettings.API_ENDPOINT}${AppSettings.HISTORYQUOTE_LEGACY_KEY}/${idHistoryquoteLegacy}`, this.getHeaders())
      .pipe(catchError(this.handleError.bind(this)));
  }

  getLegacyForSecurity(idSecuritycurrency: number): Observable<HistoryquoteLegacy[]> {
    return <Observable<HistoryquoteLegacy[]>>this.httpClient.get(
      `${BaseSettings.API_ENDPOINT}${AppSettings.HISTORYQUOTE_LEGACY_KEY}/security/${idSecuritycurrency}`,
      this.getHeaders()).pipe(catchError(this.handleError.bind(this)));
  }

  applySplitToLegacy(idSecuritycurrency: number, body: LegacySplitRequest): Observable<number> {
    return <Observable<number>>this.httpClient.post(
      `${BaseSettings.API_ENDPOINT}${AppSettings.HISTORYQUOTE_LEGACY_KEY}/security/${idSecuritycurrency}/split`,
      body, this.getHeaders()).pipe(catchError(this.handleError.bind(this)));
  }

  deleteAllLegacyForSecurity(idSecuritycurrency: number): Observable<any> {
    return this.httpClient.delete(
      `${BaseSettings.API_ENDPOINT}${AppSettings.HISTORYQUOTE_LEGACY_KEY}/security/${idSecuritycurrency}`,
      this.getHeaders()).pipe(catchError(this.handleError.bind(this)));
  }

  uploadFilesLegacy(idSecuritycurrency: number, formData: FormData): Observable<any> {
    return this.httpClient.post(
      `${BaseSettings.API_ENDPOINT}${AppSettings.HISTORYQUOTE_LEGACY_KEY}/security/${idSecuritycurrency}/uploadhistoryquotes`,
      formData, this.getMultipartHeaders()).pipe(catchError(this.handleError.bind(this)));
  }
}
