import { Injectable } from '@angular/core';

import { Historyquote, HistoryquoteCreateType } from '../../entities/historyquote';

import { AppSettings } from '../../shared/app.settings';
import { MessageToastService } from '../../lib/message/message.toast.service';
import { Observable } from 'rxjs';
import { DeleteService } from '../../lib/datashowbase/delete.service';
import { AuthServiceWithLogout } from '../../lib/login/service/base.auth.service.with.logout';
import { HttpClient, HttpParams } from '@angular/common/http';
import { ServiceEntityUpdate } from '../../lib/edit/service.entity.update';
import { catchError } from 'rxjs/operators';
import { LoginService } from '../../lib/login/service/log-in.service';
import { TaFormDefinition, TaTraceIndicatorData } from '../component/indicator.definitions';
import { HistoryquoteChartResponse } from '../../entities/projection/historyquote.chart.response';
import { HistoryquoteDateClose } from '../../entities/projection/historyquote.date.close';
import { IHistoryquoteQuality } from '../../entities/view/ihistoryquote.quality';
import { SupportedCSVFormats, UploadServiceFunction } from '../../lib/generaldialog/model/file.upload.param';
import { ISecuritycurrencyIdDateClose } from '../../entities/projection/i.securitycurrency.id.date.close';
import { BaseSettings } from '../../lib/base.settings';
import { HistoryquoteDeleteBounds } from '../../securitycurrency/model/historyquote.quality.group';

@Injectable()
export class HistoryquoteService
  extends AuthServiceWithLogout<Historyquote>
  implements ServiceEntityUpdate<Historyquote>, DeleteService, UploadServiceFunction
{
  constructor(loginService: LoginService, httpClient: HttpClient, messageToastService: MessageToastService) {
    super(loginService, httpClient, messageToastService);
  }

  getHistoryqoutesByIdSecuritycurrencyWithMissing(
    idSecuritycurrency: number,
    isCurrency: boolean
  ): Observable<IHistoryquoteQuality> {
    return <Observable<IHistoryquoteQuality>>this.httpClient
      .get(`${BaseSettings.API_ENDPOINT}${AppSettings.HISTORYQUOTE_KEY}/securitycurrency/${idSecuritycurrency}`, {
        headers: this.prepareHeaders(),
        params: new HttpParams().set('isCurrencypair', isCurrency.toString())
      })
      .pipe(catchError(this.handleError.bind(this)));
  }

  getDateCloseByIdSecuritycurrency(idSecuritycurrency: number): Observable<HistoryquoteDateClose[]> {
    return <Observable<HistoryquoteDateClose[]>>(
      this.httpClient
        .get(
          `${BaseSettings.API_ENDPOINT}${AppSettings.HISTORYQUOTE_KEY}/securitycurrency/${idSecuritycurrency}/dateclose`,
          { headers: this.prepareHeaders() }
        )
        .pipe(catchError(this.handleError.bind(this)))
    );
  }

  /**
   * Retrieves chart data for a security or currency pair.
   * Returns either OHLC data for candlestick/OHLC charts or simple date/close data for line charts.
   *
   * @param idSecuritycurrency The ID of the security or currency pair
   * @returns Observable of HistoryquoteChartResponse containing ohlcAvailable flag and data
   */
  getHistoryquoteForChart(idSecuritycurrency: number): Observable<HistoryquoteChartResponse> {
    return <Observable<HistoryquoteChartResponse>>(
      this.httpClient
        .get(
          `${BaseSettings.API_ENDPOINT}${AppSettings.HISTORYQUOTE_KEY}/securitycurrency/${idSecuritycurrency}/forchart`,
          { headers: this.prepareHeaders() }
        )
        .pipe(catchError(this.handleError.bind(this)))
    );
  }

  getCertainOrOlderDayInHistoryquoteByIdSecuritycurrency(
    idSecuritycurrency: number,
    dateString: string,
    asTraded: boolean
  ): Observable<ISecuritycurrencyIdDateClose> {
    return <Observable<ISecuritycurrencyIdDateClose>>(
      this.httpClient
        .get(
          `${BaseSettings.API_ENDPOINT}${AppSettings.HISTORYQUOTE_KEY}/` +
            `${idSecuritycurrency}/${dateString}/${asTraded}`,
          this.getHeaders()
        )
        .pipe(catchError(this.handleError.bind(this)))
    );
  }

  getAllTaForms(): Observable<{ [key: string]: TaFormDefinition }> {
    return <Observable<{ [key: string]: TaFormDefinition }>>(
      this.httpClient
        .get(`${BaseSettings.API_ENDPOINT}` + `${AppSettings.HISTORYQUOTE_KEY}/alltaforms`, this.getHeaders())
        .pipe(catchError(this.handleError.bind(this)))
    );
  }

  getTaWithShortMediumLongInputPeriod(
    taIndicator: string,
    idSecuritycurrency: number,
    dynamicDataModel: any
  ): Observable<TaTraceIndicatorData[]> {
    return <Observable<TaTraceIndicatorData[]>>(
      this.httpClient
        .post(
          `${BaseSettings.API_ENDPOINT}` +
            `${AppSettings.HISTORYQUOTE_KEY}/${idSecuritycurrency}/taindicator/${taIndicator}`,
          dynamicDataModel,
          this.getHeaders()
        )
        .pipe(catchError(this.handleError.bind(this)))
    );
  }

  update(historyquote: Historyquote): Observable<Historyquote> {
    return this.updateEntity(historyquote, historyquote.idHistoryQuote, AppSettings.HISTORYQUOTE_KEY);
  }

  uploadFiles(idSecuritycurrency: number, formData: FormData): Observable<any> {
    return this.httpClient
      .post(
        `${BaseSettings.API_ENDPOINT}${AppSettings.HISTORYQUOTE_KEY}/${idSecuritycurrency}/uploadhistoryquotes`,
        formData,
        this.getMultipartHeaders()
      )
      .pipe(catchError(this.handleError.bind(this)));
  }

  public deleteEntity(idHistoryQuote: number): Observable<any> {
    return this.httpClient
      .delete(`${BaseSettings.API_ENDPOINT}${AppSettings.HISTORYQUOTE_KEY}/${idHistoryQuote}`, this.getHeaders())
      .pipe(catchError(this.handleError.bind(this)));
  }

  public getPossibleCSVFormats(): Observable<SupportedCSVFormats> {
    return <Observable<SupportedCSVFormats>>(
      this.httpClient
        .get(`${BaseSettings.API_ENDPOINT}` + `${AppSettings.HISTORYQUOTE_KEY}/supportedcsvformat`, this.getHeaders())
        .pipe(catchError(this.handleError.bind(this)))
    );
  }

  /**
   * Loads the period the deletion offers, the oldest and the most recent stored price of the instrument. It is read
   * before the dialog is created, because its date fields must be configured with the selectable range while the
   * dynamic form is built.
   *
   * @param idSecuritycurrency - Security or currency pair whose stored period is asked for
   */
  public getDeleteBounds(idSecuritycurrency: number): Observable<HistoryquoteDeleteBounds> {
    return <Observable<HistoryquoteDeleteBounds>>(
      this.httpClient
        .get(
          `${BaseSettings.API_ENDPOINT}` + `${AppSettings.HISTORYQUOTE_KEY}/deletebounds/${idSecuritycurrency}`,
          this.getHeaders()
        )
        .pipe(catchError(this.handleError.bind(this)))
    );
  }

  /**
   * Deletes the linear filled and/or manually imported quotes of an instrument inside a period. Both boundary dates
   * belong to the period, so only that part of a linear filling is withdrawn.
   *
   * @param idSecuritycurrency - Security or currency pair whose quotes are deleted
   * @param hct - Create types to delete, only manually imported and linear filled are accepted by the backend
   * @param dateFrom - First date of the deleted period as yyyy-MM-dd, inclusive
   * @param dateTo - Last date of the deleted period as yyyy-MM-dd, inclusive
   */
  public deleteHistoryquotesByCreateTypes(
    idSecuritycurrency: number,
    hct: HistoryquoteCreateType[],
    dateFrom: string,
    dateTo: string
  ) {
    return this.httpClient
      .delete(
        `${BaseSettings.API_ENDPOINT}${AppSettings.HISTORYQUOTE_KEY}/delete/${idSecuritycurrency}`,
        this.getOptionsCreateType(hct, dateFrom, dateTo)
      )
      .pipe(catchError(this.handleError.bind(this)));
  }

  private getOptionsCreateType(hct: HistoryquoteCreateType[], dateFrom: string, dateTo: string) {
    let httpParams = new HttpParams();
    hct.forEach((id: number) => (httpParams = httpParams.append(`createTypes`, id.toString())));
    httpParams = httpParams.append(`dateFrom`, dateFrom).append(`dateTo`, dateTo);
    return { headers: this.prepareHeaders(), params: httpParams };
  }
}
