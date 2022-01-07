import {Injectable} from '@angular/core';
import {Portfolio} from '../../entities/portfolio';
import {AppSettings} from '../../shared/app.settings';
import {AccountPositionGrandSummary} from '../../entities/view/account.position.grand.summary';
import {MessageToastService} from '../../shared/message/message.toast.service';
import {SecurityDividendsGrandTotal} from '../../entities/view/securitydividends/security.dividends.grand.total';
import {HttpClient, HttpParams} from '@angular/common/http';
import {Observable} from 'rxjs';
import {AuthServiceWithLogout} from '../../shared/login/service/base.auth.service.with.logout';
import {TransactionCostGrandSummary} from '../../entities/view/transactioncost/transaction.cost.grand.summary';
import {ServiceEntityUpdate} from '../../shared/edit/service.entity.update';
import {catchError} from 'rxjs/operators';
import {LoginService} from '../../shared/login/service/log-in.service';
import {AppHelper} from '../../shared/helper/app.helper';
import {TenantPortfolioSummary} from '../../tenant/model/tenant.portfolio.summary';


@Injectable()
export class PortfolioService extends AuthServiceWithLogout<Portfolio> implements ServiceEntityUpdate<Portfolio> {

  constructor(loginService: LoginService, httpClient: HttpClient, messageToastService: MessageToastService) {
    super(loginService, httpClient, messageToastService);
  }


  getPortfolioByIdSecuritycashaccount(idSecuritycashaccount: number): Observable<Portfolio> {
    return <Observable<Portfolio>>this.httpClient.get(`${AppSettings.API_ENDPOINT}${AppSettings.PORTFOLIO_KEY}/`
      + `account/${idSecuritycashaccount}`,
      this.getHeaders()).pipe(catchError(this.handleError.bind(this)));
  }

  getPortfolioByIdPortfolio(idPortfolio: number): Observable<Portfolio> {
    return <Observable<Portfolio>>this.httpClient.get(`${AppSettings.API_ENDPOINT}${AppSettings.PORTFOLIO_KEY}/${idPortfolio}`,
      this.getHeaders()).pipe(catchError(this.handleError.bind(this)));
  }

  getPortfoliosForTenantOrderByName(): Observable<Portfolio[]> {
    return <Observable<Portfolio[]>>this.httpClient.get(`${AppSettings.API_ENDPOINT}${AppSettings.PORTFOLIO_KEY}/`
      + `${AppSettings.TENANT_KEY}`, this.getHeaders()).pipe(catchError(this.handleError.bind(this)));
  }

  getGroupedAccountsSecuritiesTenantSummary(untilDate: Date, tenantPortfolioSummary: TenantPortfolioSummary):
    Observable<AccountPositionGrandSummary> {
    const urlPart = tenantPortfolioSummary === TenantPortfolioSummary.GROUP_BY_CURRENCY
      ? AppSettings.CURRENCY_KEY : AppSettings.PORTFOLIO_KEY;
    return <Observable<AccountPositionGrandSummary>>this.httpClient.get(
      `${AppSettings.API_ENDPOINT}${AppSettings.PORTFOLIO_KEY}/securitycashaccountsummary/${urlPart}`,
      AppHelper.getOptionsWithUntilDate(untilDate, this.prepareHeaders()))
      .pipe(catchError(this.handleError.bind(this)));
  }


  getSecurityDividendsGrandTotalByTenant(idsSecurityaccount: number[], idsCashaccount: number[]): Observable<SecurityDividendsGrandTotal> {
    let httpParams = new HttpParams();
    httpParams = httpParams.append('idsSecurityaccount', idsSecurityaccount.join(','));
    httpParams = httpParams.append('idsCashaccount', idsCashaccount.join(','));
    return <Observable<SecurityDividendsGrandTotal>>this.httpClient.get(
      `${AppSettings.API_ENDPOINT}${AppSettings.PORTFOLIO_KEY}/dividends`,
      {headers: this.prepareHeaders(), params: httpParams})
      .pipe(catchError(this.handleError.bind(this)));
  }

  getTransactionCostGrandSummaryByTenant(): Observable<TransactionCostGrandSummary> {
    return <Observable<TransactionCostGrandSummary>>this.httpClient.get(
      `${AppSettings.API_ENDPOINT}${AppSettings.PORTFOLIO_KEY}/transactioncost`, this.getHeaders())
      .pipe(catchError(this.handleError.bind(this)));
  }

  /**
   * Update the passed portfolio.
   */
  update(portfolio: Portfolio): Observable<Portfolio> {
    return this.updateEntity(portfolio, portfolio.idPortfolio, AppSettings.PORTFOLIO_KEY);
  }

  deletePortfolio(idPortfolio: number) {
    return this.httpClient.delete(`${AppSettings.API_ENDPOINT}${AppSettings.PORTFOLIO_KEY}/${idPortfolio}`, this.getHeaders())
      .pipe(catchError(this.handleError.bind(this)));
  }

}
