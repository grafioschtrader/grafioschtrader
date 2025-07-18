import {Injectable} from '@angular/core';
import {AuthServiceWithLogout} from '../../shared/login/service/base.auth.service.with.logout';
import {ImportTransactionTemplate} from '../../entities/import.transaction.template';
import {ServiceEntityUpdate} from '../../lib/edit/service.entity.update';
import {AppSettings} from '../../shared/app.settings';
import {lastValueFrom, Observable} from 'rxjs';
import {HttpClient, HttpParams} from '@angular/common/http';
import {MessageToastService} from '../../lib/message/message.toast.service';
import {DeleteService} from '../../lib/datashowbase/delete.service';
import {FormTemplateCheck} from '../component/form.template.check';
import {catchError} from 'rxjs/operators';
import {LoginService} from '../../shared/login/service/log-in.service';
import {ValueKeyHtmlSelectOptions} from '../../dynamic-form/models/value.key.html.select.options';
import {ImportTransactionPos} from '../../entities/import.transaction.pos';


@Injectable()
export class ImportTransactionTemplateService extends AuthServiceWithLogout<ImportTransactionTemplate>
  implements DeleteService, ServiceEntityUpdate<ImportTransactionTemplate> {

  constructor(loginService: LoginService, httpClient: HttpClient, messageToastService: MessageToastService) {
    super(loginService, httpClient, messageToastService);
  }

  getImportTransactionPlatformByPlatform(idTransactionImportPlatform: number, excludeTemplate: boolean):
    Observable<ImportTransactionTemplate[]> {
    return <Observable<ImportTransactionTemplate[]>>this.httpClient.get(`${AppSettings.API_ENDPOINT}`
      + `${AppSettings.IMP_TRANS_TEMPLATE_KEY}/${AppSettings.IMP_TRANS_PLATFORM_KEY}/${idTransactionImportPlatform}`,
      this.getOptionsWithExcludeTemplate(excludeTemplate)).pipe(catchError(this.handleError.bind(this)));
  }

  getImportTransactionPlatformByTradingPlatformPlan(idTradingPlatformPlan: number, excludeTemplate: boolean):
    Observable<ImportTransactionTemplate[]> {
    return <Observable<ImportTransactionTemplate[]>>this.httpClient.get(`${AppSettings.API_ENDPOINT}`
      + `${AppSettings.IMP_TRANS_TEMPLATE_KEY}/${AppSettings.IMP_TRANS_PLATFORM_KEY}/`
      + `${AppSettings.TRADING_PLATFORM_PLAN_KEY}/${idTradingPlatformPlan}`,
      this.getOptionsWithExcludeTemplate(excludeTemplate)).pipe(catchError(this.handleError.bind(this)));
  }

  getCSVTemplateIdsAsValueKeyHtmlSelectOptions(idTradingPlatformPlan: number): Observable<ValueKeyHtmlSelectOptions[]> {
    return <Observable<ValueKeyHtmlSelectOptions[]>>this.httpClient.get(`${AppSettings.API_ENDPOINT}`
      + `${AppSettings.IMP_TRANS_TEMPLATE_KEY}/${AppSettings.IMP_TRANS_PLATFORM_KEY}/`
      + `${AppSettings.TRADING_PLATFORM_PLAN_KEY}/csv/${idTradingPlatformPlan}`,
      this.getHeaders()).pipe(catchError(this.handleError.bind(this)));
  }

  setIdTransactionMayBe(idTransactionMayBe: number, idTransactionPosList: number[]): Observable<ImportTransactionPos[]> {
    return <Observable<ImportTransactionPos[]>>this.httpClient.patch(`${AppSettings.API_ENDPOINT}`
      + `${AppSettings.IMPORT_TRANSACTION_POS_KEY}/setidtransactionmaybe`,
      {idTransactionMayBe, idTransactionPosList},
      {headers: this.prepareHeaders()}).pipe(catchError(this.handleError.bind(this)));
  }

  getPossibleLanguagesForTemplate(): Observable<ValueKeyHtmlSelectOptions[]> {
    return <Observable<ValueKeyHtmlSelectOptions[]>>this.httpClient.get(`${AppSettings.API_ENDPOINT}`
      + `${AppSettings.IMP_TRANS_TEMPLATE_KEY}/languages`,
      this.getHeaders()).pipe(catchError(this.handleError.bind(this)));
  }

  public deleteEntity(idTransactionImportTemplate: number): Observable<any> {
    return this.httpClient.delete(`${AppSettings.API_ENDPOINT}${AppSettings.IMP_TRANS_TEMPLATE_KEY}/${idTransactionImportTemplate}`,
      this.getHeaders()).pipe(catchError(this.handleError.bind(this)));
  }

  update(importTransactionTemplate: ImportTransactionTemplate): Observable<ImportTransactionTemplate> {
    return this.updateEntity(importTransactionTemplate, importTransactionTemplate.idTransactionImportTemplate,
      AppSettings.IMP_TRANS_TEMPLATE_KEY);
  }

  checkFormAgainstTemplate(formTemplateCheck: FormTemplateCheck): Observable<FormTemplateCheck> {
    return <Observable<FormTemplateCheck>>this.httpClient.post(`${AppSettings.API_ENDPOINT}`
      + `${AppSettings.IMP_TRANS_TEMPLATE_KEY}/checkformagainsttemplate`,
      formTemplateCheck, this.getHeaders()).pipe(catchError(this.handleError.bind(this)));
  }

  public async getTemplatesByPlatformPlanAsZip(idTradingPlatformPlan: number): Promise<Blob> {
    const blob$ = this.httpClient.get<Blob>(
      `${AppSettings.API_ENDPOINT}${AppSettings.IMP_TRANS_TEMPLATE_KEY}/exportalltemplates/${idTradingPlatformPlan}`,
      {headers: this.prepareHeaders('application/zip'), responseType: 'blob' as 'json'});
    return await lastValueFrom(blob$);
  }

  public uploadImportTemplateFiles(idTransactionImportPlatform: number, formData: FormData):
    Observable<SuccessFailedImportTransactionTemplate> {
    return this.httpClient.post(`${AppSettings.API_ENDPOINT}${AppSettings.IMP_TRANS_TEMPLATE_KEY}`
      + `/uploadtemplatefiles/${idTransactionImportPlatform}`,
      formData, this.getMultipartHeaders()).pipe(catchError(this.handleError.bind(this)));
  }

  private getOptionsWithExcludeTemplate(excludeTemplate: boolean) {
    const httpParams = new HttpParams().set('excludeTemplate', excludeTemplate.toString());
    return {headers: this.prepareHeaders(), params: httpParams};
  }
}

export interface SuccessFailedImportTransactionTemplate {
  successNew: number;
  successUpdated: number;
  notOwner: number;
  fileNameError: number;
  contentError: number;
}
