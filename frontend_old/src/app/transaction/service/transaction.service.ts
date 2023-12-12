import {Injectable} from '@angular/core';

import {AppSettings} from '../../shared/app.settings';
import {Transaction} from '../../entities/transaction';
import {CashaccountTransactionPosition} from '../../entities/view/cashaccount.transaction.position';
import {MessageToastService} from '../../shared/message/message.toast.service';
import {HttpClient} from '@angular/common/http';

import {AuthServiceWithLogout} from '../../shared/login/service/base.auth.service.with.logout';
import {catchError} from 'rxjs/operators';
import {Observable} from 'rxjs/internal/Observable';
import {LoginService} from '../../shared/login/service/log-in.service';
import {ProposedMarginFinanceCost} from '../model/proposed.margin.finance.cost';
import {ClosedMarginPosition} from '../model/closed.margin.position';


@Injectable()
export class TransactionService extends AuthServiceWithLogout<Transaction> {

  constructor(loginService: LoginService, httpClient: HttpClient, messageToastService: MessageToastService) {
    super(loginService, httpClient, messageToastService);
  }

  getConnectedMarginPositionByIdTransaction(idTransaction: number): Observable<ClosedMarginPosition> {
    return <Observable<ClosedMarginPosition>>this.httpClient.get(`${AppSettings.API_ENDPOINT}${AppSettings.TRANSACTION_KEY}/`
      + `connectedmargin/${idTransaction}`, this.getHeaders()).pipe(catchError(this.handleError.bind(this)));
  }


  getTransactionByTenant(): Observable<Transaction[]> {
    return <Observable<Transaction[]>>this.httpClient.get(`${AppSettings.API_ENDPOINT}${AppSettings.TRANSACTION_KEY}`,
      this.getHeaders()).pipe(catchError(this.handleError.bind(this)));
  }

  getTransactionsByIdPortfolio(idPortfolio: number): Observable<Transaction[]> {
    return <Observable<Transaction[]>>this.httpClient.get(`${AppSettings.API_ENDPOINT}${AppSettings.TRANSACTION_KEY}/`
      + `${AppSettings.PORTFOLIO_KEY}/${idPortfolio}`,
      this.getHeaders()).pipe(catchError(this.handleError.bind(this)));
  }

  getTransactionByIdTransaction(idTransaction: number): Observable<Transaction> {
    return <Observable<Transaction>>this.httpClient.get(`${AppSettings.API_ENDPOINT}${AppSettings.TRANSACTION_KEY}/${idTransaction}`,
      this.getHeaders()).pipe(catchError(this.handleError.bind(this)));
  }

  getTransactionsWithSaldoForCashaccount(idCashaccount: number): Observable<CashaccountTransactionPosition[]> {
    return <Observable<CashaccountTransactionPosition[]>>this.httpClient.get(`${AppSettings.API_ENDPOINT}`
      + `${AppSettings.TRANSACTION_KEY}/${idCashaccount}/${AppSettings.CASHACCOUNT_KEY}`,
      this.getHeaders()).pipe(catchError(this.handleError.bind(this)));
  }

  getEstimatedMarginFinanceCost(idTransaction: number): Observable<ProposedMarginFinanceCost> {
    return <Observable<ProposedMarginFinanceCost>>this.httpClient.get(`${AppSettings.API_ENDPOINT}`
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
    return <Observable<CashAccountTransfer>>this.httpClient.post(`${AppSettings.API_ENDPOINT}`
      + `${AppSettings.TRANSACTION_KEY}/cashaccounttransfer`, cashAccountTransfer,
      this.getHeaders()).pipe(catchError(this.handleError.bind(this)));
  }

  deleteTransaction(idTransaction: number) {
    return this.httpClient.delete(`${AppSettings.API_ENDPOINT}${AppSettings.TRANSACTION_KEY}/${idTransaction}`,
      this.getHeaders()).pipe(catchError(this.handleError.bind(this)));
  }

}

export class CashAccountTransfer {
  constructor(public withdrawalTransaction: Transaction, public depositTransaction: Transaction) {
  }
}
