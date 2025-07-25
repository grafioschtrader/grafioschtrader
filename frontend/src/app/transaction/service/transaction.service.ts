import {Injectable} from '@angular/core';

import {AppSettings} from '../../shared/app.settings';
import {Transaction} from '../../entities/transaction';
import {CashaccountTransactionPosition} from '../../entities/view/cashaccount.transaction.position';
import {MessageToastService} from '../../lib/message/message.toast.service';
import {HttpClient, HttpHeaders, HttpParams} from '@angular/common/http';

import {AuthServiceWithLogout} from '../../lib/login/service/base.auth.service.with.logout';
import {catchError} from 'rxjs/operators';
import {Observable} from 'rxjs/internal/Observable';
import {LoginService} from '../../lib/login/service/log-in.service';
import {ProposedMarginFinanceCost} from '../model/proposed.margin.finance.cost';
import {ClosedMarginPosition} from '../model/closed.margin.position';
import {CashAccountTableInputFilter} from '../component/transaction-cashaccount-table.component';
import {BaseSettings} from '../../lib/base.settings';


@Injectable()
export class TransactionService extends AuthServiceWithLogout<Transaction> {

  constructor(loginService: LoginService, httpClient: HttpClient, messageToastService: MessageToastService) {
    super(loginService, httpClient, messageToastService);
  }

  getConnectedMarginPositionByIdTransaction(idTransaction: number): Observable<ClosedMarginPosition> {
    return <Observable<ClosedMarginPosition>>this.httpClient.get(`${BaseSettings.API_ENDPOINT}${AppSettings.TRANSACTION_KEY}/`
      + `connectedmargin/${idTransaction}`, this.getHeaders()).pipe(catchError(this.handleError.bind(this)));
  }

  getTransactionByTenant(): Observable<Transaction[]> {
    return <Observable<Transaction[]>>this.httpClient.get(`${BaseSettings.API_ENDPOINT}${AppSettings.TRANSACTION_KEY}`,
      this.getHeaders()).pipe(catchError(this.handleError.bind(this)));
  }

  getTransactionsByIdPortfolio(idPortfolio: number): Observable<Transaction[]> {
    return <Observable<Transaction[]>>this.httpClient.get(`${BaseSettings.API_ENDPOINT}${AppSettings.TRANSACTION_KEY}/`
      + `${AppSettings.PORTFOLIO_KEY}/${idPortfolio}`,
      this.getHeaders()).pipe(catchError(this.handleError.bind(this)));
  }

  getTransactionByIdTransaction(idTransaction: number): Observable<Transaction> {
    return <Observable<Transaction>>this.httpClient.get(`${BaseSettings.API_ENDPOINT}${AppSettings.TRANSACTION_KEY}/${idTransaction}`,
      this.getHeaders()).pipe(catchError(this.handleError.bind(this)));
  }

  getTransactionsWithBalanceForCashaccount(idCashaccount: number, cashAccountTableInputFilter: CashAccountTableInputFilter): Observable<CashaccountTransactionPosition[]> {
    return <Observable<CashaccountTransactionPosition[]>>this.httpClient.get(`${BaseSettings.API_ENDPOINT}`
      + `${AppSettings.TRANSACTION_KEY}/${idCashaccount}/${AppSettings.CASHACCOUNT_KEY}`,
      this.getYearTransactionTypes(cashAccountTableInputFilter, this.prepareHeaders())).pipe(catchError(this.handleError.bind(this)));
  }

  private getYearTransactionTypes(cashAccountTableInputFilter: CashAccountTableInputFilter, httpHeaders: HttpHeaders) {
    let httpParams = new HttpParams({
      fromObject: {
        'transactionTypes[]': cashAccountTableInputFilter.transactionTypes,
        'year': cashAccountTableInputFilter.year
      }
    });
    return {headers: httpHeaders, params: httpParams};
  }

  getEstimatedMarginFinanceCost(idTransaction: number): Observable<ProposedMarginFinanceCost> {
    return <Observable<ProposedMarginFinanceCost>>this.httpClient.get(`${BaseSettings.API_ENDPOINT}`
      + `${AppSettings.TRANSACTION_KEY}/financecost/${idTransaction}`,
      this.getHeaders()).pipe(catchError(this.handleError.bind(this)));
  }

  updateCreateSingle(transaction: Transaction): Observable<Transaction> {
    return this.updateEntity(transaction, transaction.idTransaction, AppSettings.TRANSACTION_KEY);
  }

  updateCreateSecurityTrans(transaction: Transaction): Observable<Transaction> {
    return this.updateEntity(transaction, transaction.idTransaction, `${AppSettings.TRANSACTION_KEY}/securitytrans`);
  }

  updateCreateSingleCashTrans(transaction: Transaction): Observable<Transaction> {
    return this.updateEntity(transaction, transaction.idTransaction, `${AppSettings.TRANSACTION_KEY}/singlecashtrans`);
  }

  updateCreateDoubleTransaction(cashAccountTransfer: CashAccountTransfer): Observable<CashAccountTransfer> {
    return <Observable<CashAccountTransfer>>this.httpClient.post(`${BaseSettings.API_ENDPOINT}`
      + `${AppSettings.TRANSACTION_KEY}/cashaccounttransfer`, cashAccountTransfer,
      this.getHeaders()).pipe(catchError(this.handleError.bind(this)));
  }

  deleteTransaction(idTransaction: number) {
    return this.httpClient.delete(`${BaseSettings.API_ENDPOINT}${AppSettings.TRANSACTION_KEY}/${idTransaction}`,
      this.getHeaders()).pipe(catchError(this.handleError.bind(this)));
  }

}

export class CashAccountTransfer {
  constructor(public withdrawalTransaction: Transaction, public depositTransaction: Transaction) {
  }
}
