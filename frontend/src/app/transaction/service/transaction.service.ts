import {Injectable} from '@angular/core';

import {AppSettings} from '../../shared/app.settings';
import {Transaction} from '../../entities/transaction';
import {ExDateFromTaxDataResult} from '../../entities/ex.date.from.tax.data.result';
import {CashaccountTransactionPosition} from '../../entities/view/cashaccount.transaction.position';
import {MessageToastService} from '../../lib/message/message.toast.service';
import {HttpClient, HttpHeaders, HttpParams, HttpResponse} from '@angular/common/http';

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

  /**
   * Requests re-importable receipt PDFs for the given security transactions. The backend responds with a single PDF
   * for one transaction or a ZIP archive for multiple transactions; the file name is taken from the
   * Content-Disposition header by the caller.
   *
   * @param idTransactions the IDs of the selected security transactions
   * @returns the full HTTP response containing the binary document
   */
  downloadReceipts(idTransactions: number[]): Observable<HttpResponse<Blob>> {
    return <Observable<HttpResponse<Blob>>><unknown>this.httpClient.post(
      `${BaseSettings.API_ENDPOINT}${AppSettings.TRANSACTION_KEY}/receipts`, {idTransactions},
      {headers: this.prepareHeaders(), observe: 'response', responseType: 'blob' as 'json'})
      .pipe(catchError(this.handleError.bind(this)));
  }

  /**
   * Requests the re-importable CSV export of the tenant's transactions. The backend responds with a single CSV for
   * one securities account or a ZIP archive with one CSV per securities account; the file name is taken from the
   * Content-Disposition header by the caller.
   *
   * @param idPortfolio optional portfolio filter; null exports the whole tenant
   * @param dateFrom optional inclusive lower bound on the transaction date (yyyy-MM-dd)
   * @param dateTo optional inclusive upper bound on the transaction date (yyyy-MM-dd)
   * @returns the full HTTP response containing the binary document
   */
  exportTransactionsCsv(idPortfolio: number, dateFrom: string, dateTo: string): Observable<HttpResponse<Blob>> {
    return <Observable<HttpResponse<Blob>>><unknown>this.httpClient.post(
      `${BaseSettings.API_ENDPOINT}${AppSettings.TRANSACTION_KEY}/exportcsv`, {idPortfolio, dateFrom, dateTo},
      {headers: this.prepareHeaders(), observe: 'response', responseType: 'blob' as 'json'})
      .pipe(catchError(this.handleError.bind(this)));
  }

  /**
   * Connects the unlinked withdrawal/deposit sides of cash account transfers. Maintenance step after re-importing
   * exported CSV files; only unambiguous one-to-one matches are linked.
   *
   * @returns the counts of examined, linked, ambiguous and rejected candidates
   */
  connectCashTransfers(): Observable<CashTransferRelinkResult> {
    return <Observable<CashTransferRelinkResult>>this.httpClient.post(
      `${BaseSettings.API_ENDPOINT}${AppSettings.TRANSACTION_KEY}/connecttransfers`, null,
      this.getHeaders()).pipe(catchError(this.handleError.bind(this)));
  }

  /**
   * Changes only the taxable flag of a dividend or interest transaction, bypassing the closedUntil period lock.
   *
   * @param idTransaction the id of the transaction to update
   * @param taxableInterest the new taxable state to set
   * @returns the updated transaction
   */
  updateTaxableInterest(idTransaction: number, taxableInterest: boolean): Observable<Transaction> {
    return <Observable<Transaction>>this.httpClient.put(`${BaseSettings.API_ENDPOINT}${AppSettings.TRANSACTION_KEY}`
      + `/${idTransaction}/taxableinterest/${taxableInterest}`, null,
      this.getHeaders()).pipe(catchError(this.handleError.bind(this)));
  }

  /**
   * Back-fills the ex-date of the tenant's dividend transactions of a tax year from the imported Swiss ICTax tax data,
   * bypassing the closedUntil lock and never overwriting an existing ex-date.
   *
   * @param year the tax year whose dividend transactions are processed
   * @returns counts of examined, already-set, assigned and unmatched transactions
   */
  applyExDatesFromTaxData(year: number): Observable<ExDateFromTaxDataResult> {
    return <Observable<ExDateFromTaxDataResult>>this.httpClient.put(`${BaseSettings.API_ENDPOINT}`
      + `${AppSettings.TRANSACTION_KEY}/exdatefromtaxdata/${year}`, null,
      this.getHeaders()).pipe(catchError(this.handleError.bind(this)));
  }

}

export class CashAccountTransfer {
  constructor(public withdrawalTransaction: Transaction, public depositTransaction: Transaction) {
  }
}

/** Result of the cash transfer relink run over the tenant's unconnected withdrawal/deposit transactions. */
export interface CashTransferRelinkResult {
  /** Number of unconnected withdrawal/deposit transactions examined */
  checked: number;
  /** Number of transfer pairs that were connected */
  linkedPairs: number;
  /** Number of transactions with ambiguous match candidates, skipped for safety */
  ambiguous: number;
  /** Number of matched pairs rejected by the transfer validation */
  failed: number;
}
