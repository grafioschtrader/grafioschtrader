import {Injectable} from '@angular/core';
import {AppSettings} from '../../shared/app.settings';
import {Currencypair} from '../../entities/currencypair';
import {MessageToastService} from '../../shared/message/message.toast.service';
import {CurrencypairWithHistoryquote} from '../../entities/view/currencypair.with.historyquote';
import {CurrencypairWithTransaction} from '../../entities/view/currencypair.with.transaction';
import {Observable} from 'rxjs';
import {IFeedConnector} from '../component/ifeed.connector';
import {HttpClient} from '@angular/common/http';
import {AuthServiceWithLogout} from '../../shared/login/service/base.auth.service.with.logout';
import {catchError} from 'rxjs/operators';
import {LoginService} from '../../shared/login/service/log-in.service';
import {SecuritycurrencySearch} from '../../entities/search/securitycurrency.search';
import {AppHelper} from '../../shared/helper/app.helper';
import {HistoryquoteDateClose} from '../../entities/projection/historyquote.date.close';
import {SecurityCurrencyService} from './security.currency.service';


@Injectable()
export class CurrencypairService extends SecurityCurrencyService<Currencypair> {

  constructor(loginService: LoginService, httpClient: HttpClient, messageToastService: MessageToastService) {
    super(loginService, httpClient, messageToastService);
  }

  getCurrencypairByIdSecuritycurrency(idSecuritycurrency: number): Observable<Currencypair> {
    return <Observable<Currencypair>>this.httpClient.get(`${AppSettings.API_ENDPOINT}${AppSettings.CURRENCYPAIR_KEY}/`
      + `${idSecuritycurrency}`, this.getHeaders()).pipe(catchError(this.handleError.bind(this)));
  }


  getAllCurrencypairs(): Observable<Currencypair[]> {
    return <Observable<Currencypair[]>>this.httpClient.get(`${AppSettings.API_ENDPOINT}${AppSettings.CURRENCYPAIR_KEY}`,
      this.getHeaders()).pipe(catchError(this.handleError.bind(this)));
  }

  getCurrencypairInTransactionByTenant(): Observable<Currencypair[]> {
    return <Observable<Currencypair[]>>this.httpClient.get(`${AppSettings.API_ENDPOINT}${AppSettings.CURRENCYPAIR_KEY}/`
      + `${AppSettings.TENANT_KEY}`, this.getHeaders()).pipe(catchError(this.handleError.bind(this)));
  }


  getCurrencypairByPortfolioId(idPortfolio: number): Observable<Currencypair[]> {
    return <Observable<Currencypair[]>>this.httpClient.get(`${AppSettings.API_ENDPOINT}${AppSettings.CURRENCYPAIR_KEY}/`
      + `${idPortfolio}/${AppSettings.PORTFOLIO_KEY}`, this.getHeaders()).pipe(catchError(this.handleError.bind(this)));
  }

  findOrCreateCurrencypairByFromAndToCurrency(fromCurrency: string, toCurrency: string): Observable<Currencypair> {
    return <Observable<Currencypair>>this.httpClient.get(`${AppSettings.API_ENDPOINT}${AppSettings.CURRENCYPAIR_KEY}/`
      + `${fromCurrency}/${toCurrency}`, this.getHeaders()).pipe(catchError(this.handleError.bind(this)));
  }

  getCurrencypairWithHistoryquoteByIdSecuritycurrencyAndDate(currencypair: Currencypair,
                                                             dateString: string): Observable<CurrencypairWithHistoryquote> {
    return <Observable<CurrencypairWithHistoryquote>>this.httpClient.get(
      `${AppSettings.API_ENDPOINT}${AppSettings.CURRENCYPAIR_KEY}/${currencypair.fromCurrency}/${currencypair.toCurrency}/`
      + `${dateString}`, this.getHeaders()).pipe(catchError(this.handleError.bind(this)));
  }

  getTransactionForCurrencyPair(idCurrencypair: number, forChart: boolean): Observable<CurrencypairWithTransaction> {
    return <Observable<CurrencypairWithTransaction>>this.httpClient.get(
      `${AppSettings.API_ENDPOINT}${AppSettings.CURRENCYPAIR_KEY}/tenant/${idCurrencypair}`,
      this.getOptionsWithIncludeForChart(forChart)).pipe(catchError(this.handleError.bind(this)));
  }

  getFeedConnectors(): Observable<IFeedConnector[]> {
    return <Observable<IFeedConnector[]>>this.httpClient.get(`${AppSettings.API_ENDPOINT}${AppSettings.CURRENCYPAIR_KEY}/feedConnectors`,
      this.getHeaders()).pipe(catchError(this.handleError.bind(this)));
  }

  searchByCriteria(securitycurrencySearch: SecuritycurrencySearch): Observable<Currencypair[]> {
    return <Observable<Currencypair[]>>this.httpClient.get(`${AppSettings.API_ENDPOINT}${AppSettings.CURRENCYPAIR_KEY}/search`,
      {
        headers: this.prepareHeaders(),
        params: AppHelper.getHttpParamsOfObject(securitycurrencySearch)
      }).pipe(catchError(this.handleError.bind(this)));
  }

  getCurrencypairForCrossRate(crossRateRequest: CrossRateRequest): Observable<CrossRateResponse[]> {
    return <Observable<CrossRateResponse[]>>this.httpClient.get(`${AppSettings.API_ENDPOINT}${AppSettings.CURRENCYPAIR_KEY}/crossrate`,
      {
        headers: this.prepareHeaders(),
        params: AppHelper.getHttpParamsOfObject(crossRateRequest)
      }).pipe(catchError(this.handleError.bind(this)));
  }

  /**
   * Update the passed currency pair.
   */
  update(currencypair: Currencypair): Observable<Currencypair> {
    return this.updateEntity(currencypair, currencypair.idSecuritycurrency, AppSettings.CURRENCYPAIR_KEY);
  }
}

export class CrossRateRequest {
  constructor(public securityCurrencyList: string[], public existingCurrencies: string[]) {
  }
}

export interface CurrenciesAndClosePrice {
  currencypair: Currencypair;
  closeAndDateList: HistoryquoteDateClose[];
}

export interface CrossRateResponse {
  mainCurrency: string;
  currenciesAndClosePrice: CurrenciesAndClosePrice[];
}
