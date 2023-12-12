import {Injectable} from '@angular/core';

import {Historyquote, HistoryquoteCreateType} from '../../entities/historyquote';

import {AppSettings} from '../../shared/app.settings';
import {MessageToastService} from '../../shared/message/message.toast.service';
import {Observable} from 'rxjs';
import {DeleteService} from '../../shared/datashowbase/delete.service';
import {AuthServiceWithLogout} from '../../shared/login/service/base.auth.service.with.logout';
import {HttpClient, HttpParams} from '@angular/common/http';
import {ServiceEntityUpdate} from '../../shared/edit/service.entity.update';
import {catchError} from 'rxjs/operators';
import {LoginService} from '../../shared/login/service/log-in.service';
import {TaFormDefinition, TaTraceIndicatorData} from '../component/indicator.definitions';
import {HistoryquoteDateClose} from '../../entities/projection/historyquote.date.close';
import {IHistoryquoteQuality} from '../../entities/view/ihistoryquote.quality';
import {SupportedCSVFormats, UploadServiceFunction} from '../../shared/generaldialog/model/file.upload.param';
import {ISecuritycurrencyIdDateClose} from '../../entities/projection/i.securitycurrency.id.date.close';


@Injectable()
export class HistoryquoteService extends AuthServiceWithLogout<Historyquote> implements ServiceEntityUpdate<Historyquote>,
  DeleteService, UploadServiceFunction {

  constructor(loginService: LoginService, httpClient: HttpClient, messageToastService: MessageToastService) {
    super(loginService, httpClient, messageToastService);
  }

  getHistoryqoutesByIdSecuritycurrencyWithMissing(idSecuritycurrency: number, isCurrency: boolean): Observable<IHistoryquoteQuality> {
    return <Observable<IHistoryquoteQuality>>this.httpClient.get(
      `${AppSettings.API_ENDPOINT}${AppSettings.HISTORYQUOTE_KEY}/securitycurrency/${idSecuritycurrency}`,
      {headers: this.prepareHeaders(), params: new HttpParams().set('isCurrencypair', isCurrency.toString())})
      .pipe(catchError(this.handleError.bind(this)));
  }

  getDateCloseByIdSecuritycurrency(idSecuritycurrency: number): Observable<HistoryquoteDateClose[]> {
    return <Observable<HistoryquoteDateClose[]>>this.httpClient.get(
      `${AppSettings.API_ENDPOINT}${AppSettings.HISTORYQUOTE_KEY}/securitycurrency/${idSecuritycurrency}/dateclose`,
      {headers: this.prepareHeaders()}).pipe(catchError(this.handleError.bind(this)));
  }

  getCertainOrOlderDayInHistoryquoteByIdSecuritycurrency(idSecuritycurrency: number, dateString: string,
                                                         asTraded: boolean): Observable<ISecuritycurrencyIdDateClose> {
    return <Observable<ISecuritycurrencyIdDateClose>>this.httpClient.get(`${AppSettings.API_ENDPOINT}${AppSettings.HISTORYQUOTE_KEY}/`
      + `${idSecuritycurrency}/${dateString}/${asTraded}`, this.getHeaders()).pipe(catchError(this.handleError.bind(this)));
  }

  getAllTaForms(): Observable<{ [key: string]: TaFormDefinition }> {
    return <Observable<{ [key: string]: TaFormDefinition }>>this.httpClient.get(`${AppSettings.API_ENDPOINT}`
      + `${AppSettings.HISTORYQUOTE_KEY}/alltaforms`, this.getHeaders()).pipe(catchError(this.handleError.bind(this)));
  }

  getTaWithShortMediumLongInputPeriod(taIndicator: string, idSecuritycurrency:
    number, dynamicDataModel: any): Observable<TaTraceIndicatorData[]> {
    return <Observable<TaTraceIndicatorData[]>>this.httpClient.post(`${AppSettings.API_ENDPOINT}`
      + `${AppSettings.HISTORYQUOTE_KEY}/${idSecuritycurrency}/taindicator/${taIndicator}`, dynamicDataModel,
      this.getHeaders()).pipe(catchError(this.handleError.bind(this)));
  }

  update(historyquote: Historyquote): Observable<Historyquote> {
    return this.updateEntity(historyquote, historyquote.idHistoryQuote, AppSettings.HISTORYQUOTE_KEY);
  }

  uploadFiles(idSecuritycurrency: number, formData: FormData): Observable<any> {
    return this.httpClient
      .post(`${AppSettings.API_ENDPOINT}${AppSettings.HISTORYQUOTE_KEY}/${idSecuritycurrency}/uploadhistoryquotes`,
        formData, this.getMultipartHeaders())
      .pipe(catchError(this.handleError.bind(this)));
  }

  public deleteEntity(idHistoryQuote: number): Observable<any> {
    return this.httpClient.delete(`${AppSettings.API_ENDPOINT}${AppSettings.HISTORYQUOTE_KEY}/${idHistoryQuote}`, this.getHeaders())
      .pipe(catchError(this.handleError.bind(this)));
  }

  public getPossibleCSVFormats(): Observable<SupportedCSVFormats> {
    return <Observable<SupportedCSVFormats>>this.httpClient.get(`${AppSettings.API_ENDPOINT}`
      + `${AppSettings.HISTORYQUOTE_KEY}/supportedcsvformat`,
      this.getHeaders()).pipe(catchError(this.handleError.bind(this)));
  }

  public deleteHistoryquotesByCreateTypes(idSecuritycurrency: number, hct: HistoryquoteCreateType[]) {
    return this.httpClient.delete(`${AppSettings.API_ENDPOINT}${AppSettings.HISTORYQUOTE_KEY}/delete/${idSecuritycurrency}`,
      this.getOptionsCreateType(hct)).pipe(catchError(this.handleError.bind(this)));
  }

  private getOptionsCreateType(hct: HistoryquoteCreateType[]) {
    let httpParams = new HttpParams();
    hct.forEach((id: number) => httpParams = httpParams.append(`createTypes`, id.toString()));
    return {headers: this.prepareHeaders(), params: httpParams};
  }


}
