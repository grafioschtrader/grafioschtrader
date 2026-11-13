import { Service } from '@angular/core';
import { Portfolio } from '../../entities/portfolio';
import { AppSettings } from '../../shared/app.settings';
import type { AccountPositionGrandSummary } from '../../entities/view/account.position.grand.summary';
import type { SecurityDividendsGrandTotal } from '../../entities/view/securitydividends/security.dividends.grand.total';
import { SecurityDividendsChart } from '../../entities/view/securitydividends/security.dividends.chart';
import type { RealizedGainGrandTotal } from '../../entities/view/realizedgain/realized.gain.grand.total';
import type { GermanTaxYearReport } from '../../entities/view/germantax/german.tax.year.report';
import { HttpParams } from '@angular/common/http';
import { Observable } from 'rxjs';
import { AuthServiceWithLogout } from '../../lib/login/service/base.auth.service.with.logout';
import type { TransactionCostGrandSummary } from '../../entities/view/transactioncost/transaction.cost.grand.summary';
import { ServiceEntityUpdate } from '../../lib/edit/service.entity.update';
import { catchError } from 'rxjs/operators';
import { AppHelper } from '../../lib/helper/app.helper';
import { TenantPortfolioSummary } from '../../tenant/model/tenant.portfolio.summary';
import { BaseSettings } from '../../lib/base.settings';

@Service()
export class PortfolioService extends AuthServiceWithLogout<Portfolio> implements ServiceEntityUpdate<Portfolio> {
  getPortfolioByIdSecuritycashaccount(idSecuritycashaccount: number): Observable<Portfolio> {
    return this.httpClient
      .get<Portfolio>(
        `${BaseSettings.API_ENDPOINT}${AppSettings.PORTFOLIO_KEY}/` + `account/${idSecuritycashaccount}`,
        this.getHeaders()
      )
      .pipe(catchError(this.handleError.bind(this)));
  }

  getPortfolioByIdPortfolio(idPortfolio: number): Observable<Portfolio> {
    return this.httpClient
      .get<Portfolio>(`${BaseSettings.API_ENDPOINT}${AppSettings.PORTFOLIO_KEY}/${idPortfolio}`, this.getHeaders())
      .pipe(catchError(this.handleError.bind(this)));
  }

  getPortfoliosForTenantOrderByName(): Observable<Portfolio[]> {
    return this.httpClient
      .get<Portfolio[]>(
        `${BaseSettings.API_ENDPOINT}${AppSettings.PORTFOLIO_KEY}/` + `${BaseSettings.TENANT_KEY}`,
        this.getHeaders()
      )
      .pipe(catchError(this.handleError.bind(this)));
  }

  getGroupedAccountsSecuritiesTenantSummary(
    untilDate: Date,
    tenantPortfolioSummary: TenantPortfolioSummary
  ): Observable<AccountPositionGrandSummary> {
    const urlPart =
      tenantPortfolioSummary === TenantPortfolioSummary.GROUP_BY_CURRENCY
        ? AppSettings.CURRENCY_KEY
        : AppSettings.PORTFOLIO_KEY;
    return this.httpClient
      .get<AccountPositionGrandSummary>(
        `${BaseSettings.API_ENDPOINT}${AppSettings.PORTFOLIO_KEY}/securitycashaccountsummary/${urlPart}`,
        AppHelper.getOptionsWithUntilDate(untilDate, this.prepareHeaders())
      )
      .pipe(catchError(this.handleError.bind(this)));
  }

  /**
   * Loads the income and costs of the dividends report aggregated per year, month and asset class for its charts.
   *
   * @param idsSecurityaccount - Security accounts to include; [] or [-1] includes all
   * @param idsCashaccount - Cash accounts to include; [-1] includes all
   * @returns The chart data in the tenant's main currency
   */
  getSecurityDividendsChartByTenant(
    idsSecurityaccount: number[],
    idsCashaccount: number[]
  ): Observable<SecurityDividendsChart> {
    let httpParams = new HttpParams();
    httpParams = httpParams.append('idsSecurityaccount', idsSecurityaccount.join(','));
    httpParams = httpParams.append('idsCashaccount', idsCashaccount.join(','));
    return this.httpClient
      .get<SecurityDividendsChart>(`${BaseSettings.API_ENDPOINT}${AppSettings.PORTFOLIO_KEY}/dividendschart`, {
        headers: this.prepareHeaders(),
        params: httpParams
      })
      .pipe(catchError(this.handleError.bind(this)));
  }

  getSecurityDividendsGrandTotalByTenant(
    idsSecurityaccount: number[],
    idsCashaccount: number[]
  ): Observable<SecurityDividendsGrandTotal> {
    let httpParams = new HttpParams();
    httpParams = httpParams.append('idsSecurityaccount', idsSecurityaccount.join(','));
    httpParams = httpParams.append('idsCashaccount', idsCashaccount.join(','));
    return this.httpClient
      .get<SecurityDividendsGrandTotal>(`${BaseSettings.API_ENDPOINT}${AppSettings.PORTFOLIO_KEY}/dividends`, {
        headers: this.prepareHeaders(),
        params: httpParams
      })
      .pipe(catchError(this.handleError.bind(this)));
  }

  /**
   * Loads the capital gains the tenant realized by sales, grouped by year, in the main currency.
   *
   * @param idsSecurityaccount - Security accounts whose sales are listed; [] or [-1] lists all
   * @param costBasisMethod - Name of the cost basis method for all security accounts; null or empty uses the method of
   *   each security account as tenant and portfolio set it
   * @returns The years with their sales and the totals
   */
  getRealizedGainsByTenant(
    idsSecurityaccount: number[],
    costBasisMethod: string | null
  ): Observable<RealizedGainGrandTotal> {
    let httpParams = new HttpParams().append('idsSecurityaccount', idsSecurityaccount.join(','));
    if (costBasisMethod) {
      httpParams = httpParams.append('costBasisMethod', costBasisMethod);
    }
    return this.httpClient
      .get<RealizedGainGrandTotal>(`${BaseSettings.API_ENDPOINT}${AppSettings.PORTFOLIO_KEY}/realizedgains`, {
        headers: this.prepareHeaders(),
        params: httpParams
      })
      .pipe(catchError(this.handleError.bind(this)));
  }

  /**
   * Loads the starting values of a tax year for the German tax return. The server answers only a client that reports in
   * euro and has Germany as its country.
   *
   * @param taxYear - the tax year, from 2018 to the current year
   * @returns the sums, the warnings and the derivation
   */
  getTaxGermany(taxYear: number): Observable<GermanTaxYearReport> {
    return this.httpClient
      .get<GermanTaxYearReport>(
        `${BaseSettings.API_ENDPOINT}${AppSettings.PORTFOLIO_KEY}/taxgermany/${taxYear}`,
        this.getHeaders()
      )
      .pipe(catchError(this.handleError.bind(this)));
  }

  getTransactionCostGrandSummaryByTenant(): Observable<TransactionCostGrandSummary> {
    return this.httpClient
      .get<TransactionCostGrandSummary>(
        `${BaseSettings.API_ENDPOINT}${AppSettings.PORTFOLIO_KEY}/transactioncost`,
        this.getHeaders()
      )
      .pipe(catchError(this.handleError.bind(this)));
  }

  /**
   * Update the passed portfolio.
   */
  update(portfolio: Portfolio): Observable<Portfolio> {
    return this.updateEntity(portfolio, portfolio.idPortfolio, AppSettings.PORTFOLIO_KEY);
  }

  deletePortfolio(idPortfolio: number) {
    return this.httpClient
      .delete(`${BaseSettings.API_ENDPOINT}${AppSettings.PORTFOLIO_KEY}/${idPortfolio}`, this.getHeaders())
      .pipe(catchError(this.handleError.bind(this)));
  }
}
