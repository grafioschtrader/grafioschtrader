import {AuthServiceWithLogout} from '../../shared/login/service/base.auth.service.with.logout';
import {ImportTransactionPos} from '../../entities/import.transaction.pos';
import {Injectable} from '@angular/core';
import {MessageToastService} from '../../shared/message/message.toast.service';
import {HttpClient} from '@angular/common/http';
import {Observable} from 'rxjs';
import {AppSettings} from '../../shared/app.settings';
import {DeleteService} from '../../shared/datashowbase/delete.service';
import {ServiceEntityUpdate} from '../../shared/edit/service.entity.update';
import {CombineTemplateAndImpTransPos} from '../component/combine.template.and.imp.trans.pos';
import {catchError} from 'rxjs/operators';
import {LoginService} from '../../shared/login/service/log-in.service';

@Injectable()
export class ImportTransactionPosService extends AuthServiceWithLogout<ImportTransactionPos>
  implements DeleteService, ServiceEntityUpdate<ImportTransactionPos> {

  constructor(loginService: LoginService, httpClient: HttpClient, messageToastService: MessageToastService) {
    super(loginService, httpClient, messageToastService);
  }

  getCombineTemplateAndImpTransPosListByTransactionHead(idTransactionHead: number): Observable<CombineTemplateAndImpTransPos[]> {
    return <Observable<CombineTemplateAndImpTransPos[]>>this.httpClient.get(`${AppSettings.API_ENDPOINT}`
      + `${AppSettings.IMPORT_TRANSACTION_POS_KEY}/${AppSettings.IMPORT_TRANSACTION_HEAD_KEY}/${idTransactionHead}`,
      this.getHeaders()).pipe(catchError(this.handleError.bind(this)));
  }

  public deleteEntity(idTransactionImportPos: number): Observable<any> {
    return this.httpClient.delete(`${AppSettings.API_ENDPOINT}${AppSettings.IMPORT_TRANSACTION_POS_KEY}/${idTransactionImportPos}`,
      this.getHeaders()).pipe(catchError(this.handleError.bind(this)));
  }

  public adjustQuotation(idTransactionPosList: number[]): Observable<any> {
    return this.httpClient.post(`${AppSettings.API_ENDPOINT}${AppSettings.IMPORT_TRANSACTION_POS_KEY}/adjustquotation`,
      idTransactionPosList, this.getHeaders()).pipe(catchError(this.handleError.bind(this)));
  }

  public adjustCurrencyExRateOrQuotation(idTransactionPosList: number[]): Observable<any> {
    return this.httpClient.post(`${AppSettings.API_ENDPOINT}${AppSettings.IMPORT_TRANSACTION_POS_KEY}/adjustcurrencyexrateorquotation`,
      idTransactionPosList, this.getHeaders()).pipe(catchError(this.handleError.bind(this)));
  }

  public acceptTotalDiff(idTransactionPosList: number[]): Observable<any> {
    return this.httpClient.post(`${AppSettings.API_ENDPOINT}${AppSettings.IMPORT_TRANSACTION_POS_KEY}/accepttotaldiff`,
      idTransactionPosList, this.getHeaders()).pipe(catchError(this.handleError.bind(this)));
  }

  public deleteMultiple(idTransactionPosList: number[]): Observable<any> {
    return this.httpClient.post(`${AppSettings.API_ENDPOINT}${AppSettings.IMPORT_TRANSACTION_POS_KEY}/deletes`,
      idTransactionPosList, this.getHeaders()).pipe(catchError(this.handleError.bind(this)));
  }

  public createAndSaveTransactions(idTransactionPosList: number[]): Observable<any> {
    return this.httpClient.post(`${AppSettings.API_ENDPOINT}${AppSettings.IMPORT_TRANSACTION_POS_KEY}/createtransaction`,
      idTransactionPosList, this.getHeaders()).pipe(catchError(this.handleError.bind(this)));
  }

  update(importTransactionPos: ImportTransactionPos): Observable<ImportTransactionPos> {
    return this.updateEntity(importTransactionPos, importTransactionPos.idTransactionPos,
      AppSettings.IMPORT_TRANSACTION_POS_KEY);
  }

  setSecurity(idSecuritycurrency: number, idTransactionPosList: number[]): Observable<ImportTransactionPos[]> {
    return <Observable<ImportTransactionPos[]>>this.httpClient.put(`${AppSettings.API_ENDPOINT}`
      + `${AppSettings.IMPORT_TRANSACTION_POS_KEY}/setsecurity`,
      {idSecuritycurrency, idTransactionPosList},
      {headers: this.prepareHeaders()}).pipe(catchError(this.handleError.bind(this)));
  }

  setCashAccount(idSecuritycashAccount: number, idTransactionPosList: number[]): Observable<ImportTransactionPos[]> {
    return <Observable<ImportTransactionPos[]>>this.httpClient.put(`${AppSettings.API_ENDPOINT}`
      + `${AppSettings.IMPORT_TRANSACTION_POS_KEY}/setcashaccount`,
      {idSecuritycashAccount, idTransactionPosList},
      {headers: this.prepareHeaders()}).pipe(catchError(this.handleError.bind(this)));
  }

  setIdTransactionMayBe(idTransactionMayBe: number, idTransactionPosList: number[]): Observable<ImportTransactionPos[]> {
    return <Observable<ImportTransactionPos[]>>this.httpClient.patch(`${AppSettings.API_ENDPOINT}`
      + `${AppSettings.IMPORT_TRANSACTION_POS_KEY}/setidtransactionmaybe`,
      {idTransactionMayBe, idTransactionPosList},
      {headers: this.prepareHeaders()}).pipe(catchError(this.handleError.bind(this)));
  }

}
