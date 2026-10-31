import { Service } from '@angular/core';
import { AppSettings } from '../../shared/app.settings';
import { Currencypair } from '../../entities/currencypair';
import { CurrencypairWithHistoryquote } from '../../entities/view/currencypair.with.historyquote';
import type { CurrencypairTransactionData } from '../../entities/view/currencypair.with.transaction';
import { Observable } from 'rxjs';
import { IFeedConnector } from '../../shared/securitycurrency/ifeed.connector';
import { HttpParams } from '@angular/common/http';
import { catchError } from 'rxjs/operators';
import { SecuritycurrencySearch } from '../../entities/search/securitycurrency.search';
import { AppHelper } from '../../lib/helper/app.helper';
import { HistoryquoteDateClose } from '../../entities/projection/historyquote.date.close';
import { SecurityCurrencyService } from './security.currency.service';
import { BaseSettings } from '../../lib/base.settings';

@Service()
export class CurrencypairService extends SecurityCurrencyService<Currencypair> {
  getCurrencypairByIdSecuritycurrency(idSecuritycurrency: number): Observable<Currencypair> {
    return this.httpClient
      .get<Currencypair>(
        `${BaseSettings.API_ENDPOINT}${AppSettings.CURRENCYPAIR_KEY}/` + `${idSecuritycurrency}`,
        this.getHeaders()
      )
      .pipe(catchError(this.handleError.bind(this)));
  }

  getAllCurrencypairs(): Observable<Currencypair[]> {
    return this.httpClient
      .get<Currencypair[]>(`${BaseSettings.API_ENDPOINT}${AppSettings.CURRENCYPAIR_KEY}`, this.getHeaders())
      .pipe(catchError(this.handleError.bind(this)));
  }

  getCurrencypairInTransactionByTenant(): Observable<Currencypair[]> {
    return this.httpClient
      .get<Currencypair[]>(
        `${BaseSettings.API_ENDPOINT}${AppSettings.CURRENCYPAIR_KEY}/` + `${BaseSettings.TENANT_KEY}`,
        this.getHeaders()
      )
      .pipe(catchError(this.handleError.bind(this)));
  }

  getTransactionCurrencypairsByPortfolioId(idPortfolio: number): Observable<Currencypair[]> {
    return this.httpClient
      .get<Currencypair[]>(
        `${BaseSettings.API_ENDPOINT}${AppSettings.CURRENCYPAIR_KEY}/` + `${idPortfolio}/${AppSettings.PORTFOLIO_KEY}`,
        this.getHeaders()
      )
      .pipe(catchError(this.handleError.bind(this)));
  }

  findOrCreateCurrencypairByFromAndToCurrency(fromCurrency: string, toCurrency: string): Observable<Currencypair> {
    return this.httpClient
      .get<Currencypair>(
        `${BaseSettings.API_ENDPOINT}${AppSettings.CURRENCYPAIR_KEY}/` + `${fromCurrency}/${toCurrency}`,
        this.getHeaders()
      )
      .pipe(catchError(this.handleError.bind(this)));
  }

  getCurrencypairWithHistoryquoteByIdSecuritycurrencyAndDate(
    currencypair: Currencypair,
    dateString: string
  ): Observable<CurrencypairWithHistoryquote> {
    return this.httpClient
      .get<CurrencypairWithHistoryquote>(
        `${BaseSettings.API_ENDPOINT}${AppSettings.CURRENCYPAIR_KEY}/${currencypair.fromCurrency}/${currencypair.toCurrency}/` +
          `${dateString}`,
        this.getHeaders()
      )
      .pipe(catchError(this.handleError.bind(this)));
  }

  getTransactionForCurrencyPair(idCurrencypair: number, forChart: boolean): Observable<CurrencypairTransactionData> {
    return this.httpClient
      .get<CurrencypairTransactionData>(
        `${BaseSettings.API_ENDPOINT}${AppSettings.CURRENCYPAIR_KEY}/tenant/${idCurrencypair}`,
        this.getOptionsWithIncludeForChart(forChart)
      )
      .pipe(catchError(this.handleError.bind(this)));
  }

  getFeedConnectors(assetclassType?: string, specInvInstrument?: string): Observable<IFeedConnector[]> {
    let params = new HttpParams();
    if (assetclassType != null) {
      params = params.set('assetclassType', assetclassType);
    }
    if (specInvInstrument != null) {
      params = params.set('specInvInstrument', specInvInstrument);
    }
    return this.httpClient
      .get<IFeedConnector[]>(`${BaseSettings.API_ENDPOINT}${AppSettings.CURRENCYPAIR_KEY}/feedConnectors`, {
        headers: this.prepareHeaders(),
        params
      })
      .pipe(catchError(this.handleError.bind(this)));
  }

  searchByCriteria(securitycurrencySearch: SecuritycurrencySearch): Observable<Currencypair[]> {
    return this.httpClient
      .get<Currencypair[]>(`${BaseSettings.API_ENDPOINT}${AppSettings.CURRENCYPAIR_KEY}/search`, {
        headers: this.prepareHeaders(),
        params: AppHelper.getHttpParamsOfObject(securitycurrencySearch)
      })
      .pipe(catchError(this.handleError.bind(this)));
  }

  getCurrencypairForCrossRate(crossRateRequest: CrossRateRequest): Observable<CrossRateResponse[]> {
    return this.httpClient
      .get<CrossRateResponse[]>(`${BaseSettings.API_ENDPOINT}${AppSettings.CURRENCYPAIR_KEY}/crossrate`, {
        headers: this.prepareHeaders(),
        params: AppHelper.getHttpParamsOfObject(crossRateRequest)
      })
      .pipe(catchError(this.handleError.bind(this)));
  }

  /**
   * Update the passed currency pair.
   */
  update(currencypair: Currencypair): Observable<Currencypair> {
    return this.updateEntity(currencypair, currencypair.idSecuritycurrency, AppSettings.CURRENCYPAIR_KEY);
  }
}

export class CrossRateRequest {
  constructor(
    public securityCurrencyList: string[],
    public existingCurrencies: string[]
  ) {}
}

export interface CurrenciesAndClosePrice {
  currencypair: Currencypair;
  closeAndDateList: HistoryquoteDateClose[];
}

export interface CrossRateResponse {
  mainCurrency: string;
  currenciesAndClosePrice: CurrenciesAndClosePrice[];
}
