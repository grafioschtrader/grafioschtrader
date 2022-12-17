import {Stockexchange} from '../../entities/stockexchange';
import {MessageToastService} from '../../shared/message/message.toast.service';
import {AppSettings} from '../../shared/app.settings';
import {Observable} from 'rxjs';
import {Injectable} from '@angular/core';
import {DeleteService} from '../../shared/datashowbase/delete.service';
import {HttpClient, HttpParams} from '@angular/common/http';
import {AuthServiceWithLogout} from '../../shared/login/service/base.auth.service.with.logout';
import {ServiceEntityUpdate} from '../../shared/edit/service.entity.update';
import {catchError} from 'rxjs/operators';
import {LoginService} from '../../shared/login/service/log-in.service';
import {StockexchangeHasSecurity} from '../model/stockexchange.has.security';
import {StockexchangeBaseData} from '../model/stockexchange.base.data';

@Injectable()
export class StockexchangeService extends AuthServiceWithLogout <Stockexchange> implements ServiceEntityUpdate<Stockexchange>,
  DeleteService {

  constructor(loginService: LoginService, httpClient: HttpClient, messageToastService: MessageToastService) {
    super(loginService, httpClient, messageToastService);
  }


  public getAllStockexchangesBaseData(): Observable<StockexchangeBaseData> {
    return <Observable<StockexchangeBaseData>>this.httpClient.get(
      `${AppSettings.API_ENDPOINT}${AppSettings.STOCKEXCHANGE_KEY}/basedata`,
      this.getHeaders()).pipe(catchError(this.handleError.bind(this)));
  }

  public getAllStockexchanges(includeNameOfCalendarIndex: boolean): Observable<Stockexchange[]> {
    return <Observable<Stockexchange[]>>this.httpClient.get(`${AppSettings.API_ENDPOINT}${AppSettings.STOCKEXCHANGE_KEY}`,
      {
        headers: this.prepareHeaders(), params: new HttpParams().set('includeNameOfCalendarIndex',
          includeNameOfCalendarIndex.toString())
      }).pipe(catchError(this.handleError.bind(this)));
  }

  public stockexchangeHasSecurity(idStockexchange: number): Observable<boolean> {
    return <Observable<boolean>>this.httpClient.get(
      `${AppSettings.API_ENDPOINT}${AppSettings.STOCKEXCHANGE_KEY}/${idStockexchange}/hassecurity`,
      this.getHeaders()).pipe(catchError(this.handleError.bind(this)));
  }

  public stockexchangesHasSecurity(): Observable<StockexchangeHasSecurity[]> {
    return <Observable<StockexchangeHasSecurity[]>>this.httpClient.get(`${AppSettings.API_ENDPOINT}`
      + `${AppSettings.STOCKEXCHANGE_KEY}/hassecurity`,
      this.getHeaders()).pipe(catchError(this.handleError.bind(this)));
  }

  update(stockexchange: Stockexchange): Observable<Stockexchange> {
    return this.updateEntity(stockexchange, stockexchange.idStockexchange, AppSettings.STOCKEXCHANGE_KEY);
  }

  deleteEntity(idStockexchange: number): Observable<any> {
    return this.httpClient.delete(`${AppSettings.API_ENDPOINT}${AppSettings.STOCKEXCHANGE_KEY}/${idStockexchange}`,
      this.getHeaders()).pipe(catchError(this.handleError.bind(this)));
  }
}
