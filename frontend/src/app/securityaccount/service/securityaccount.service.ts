import { Injectable } from '@angular/core';
import { FxObservationReport } from '../../entities/fx.observation';
import { FxMarkupPreviewRequest, FxQuote } from '../../entities/fx.markup';
import { AppSettings } from '../../shared/app.settings';
import { SecurityPositionGrandSummary } from '../../entities/view/security.position.grand.summary';
import { MessageToastService } from '../../lib/message/message.toast.service';
import { FeeModelComparisonResponse } from '../../entities/fee.model.comparison';
import { Securityaccount } from '../../entities/securityaccount';
import { TradingPeriodTransactionSummary } from '../../entities/trading.period.transaction.summary';
import {
  TransactionCostEstimateRequest,
  TransactionCostEstimateResult
} from '../../entities/transaction.cost.estimate';
import { Observable } from 'rxjs';
import { HttpClient, HttpParams } from '@angular/common/http';
import { ValueKeyHtmlSelectOptions } from '../../lib/dynamic-form/models/value.key.html.select.options';
import { AuthServiceWithLogout } from '../../lib/login/service/base.auth.service.with.logout';
import { ServiceEntityUpdate } from '../../lib/edit/service.entity.update';
import { catchError } from 'rxjs/operators';
import { LoginService } from '../../lib/login/service/log-in.service';
import { AppHelper } from '../../lib/helper/app.helper';
import { BaseSettings } from '../../lib/base.settings';

@Injectable()
export class SecurityaccountService
  extends AuthServiceWithLogout<Securityaccount>
  implements ServiceEntityUpdate<Securityaccount>
{
  constructor(loginService: LoginService, httpClient: HttpClient, messageToastService: MessageToastService) {
    super(loginService, httpClient, messageToastService);
  }

  getFxObservations(idAccount: number): Observable<FxObservationReport> {
    return this.httpClient
      .get<FxObservationReport>(
        `${BaseSettings.API_ENDPOINT}${AppSettings.SECURITYACCOUNT_KEY}/${idAccount}/fxobservations`,
        this.getHeaders()
      )
      .pipe(catchError(this.handleError.bind(this)));
  }

  getSecurityPositionSummaryTenant(
    group: string,
    includeClosedPosition: boolean,
    untilDate: Date
  ): Observable<SecurityPositionGrandSummary> {
    return <Observable<SecurityPositionGrandSummary>>(
      this.httpClient
        .get(
          `${BaseSettings.API_ENDPOINT}` + `${AppSettings.SECURITYACCOUNT_KEY}/tenantsecurityaccountsummary/${group}`,
          AppHelper.getOptionsWithIncludeClosedPositionAndUntilDate(
            includeClosedPosition,
            untilDate,
            this.prepareHeaders()
          )
        )
        .pipe(catchError(this.handleError.bind(this)))
    );
  }

  /**
   * The same positions and cash accounts as the asset class report, grouped by the buckets of one strategy and
   * extended with target share, actual share, deviation and the recommended action.
   *
   * @param idAlgoTop - The strategy whose allocation the portfolio is compared against
   * @param includeClosedPosition - Whether positions that are no longer held are listed
   * @param untilDate - Requested valuation day; the backend moves it back to the last completed day when needed and
   *                    reports what it used in valuationDate
   * @returns The report with the allocation comparison written onto every row, group and total
   */
  getRebalancingSummaryTenant(
    idAlgoTop: number,
    includeClosedPosition: boolean,
    untilDate: Date
  ): Observable<SecurityPositionGrandSummary> {
    return <Observable<SecurityPositionGrandSummary>>(
      this.httpClient
        .get(
          `${BaseSettings.API_ENDPOINT}${AppSettings.SECURITYACCOUNT_KEY}/` +
            `tenantsecurityaccountsummary/rebalancing/${idAlgoTop}`,
          AppHelper.getOptionsWithIncludeClosedPositionAndUntilDate(
            includeClosedPosition,
            untilDate,
            this.prepareHeaders()
          )
        )
        .pipe(catchError(this.handleError.bind(this)))
    );
  }

  getSecurityPositionSummaryPortfolio(
    idPortfolio: number,
    group: string,
    includeClosedPosition: boolean,
    untilDate: Date
  ): Observable<SecurityPositionGrandSummary> {
    return <Observable<SecurityPositionGrandSummary>>(
      this.httpClient
        .get(
          `${BaseSettings.API_ENDPOINT}${AppSettings.SECURITYACCOUNT_KEY}/` +
            `${idPortfolio}/portfoliosecurityaccountsummary/${group}`,
          AppHelper.getOptionsWithIncludeClosedPositionAndUntilDate(
            includeClosedPosition,
            untilDate,
            this.prepareHeaders()
          )
        )
        .pipe(catchError(this.handleError.bind(this)))
    );
  }

  getPositionSummarySecurityaccount(
    idSecurityaccount: number,
    group: string,
    includeClosedPosition: boolean,
    untilDate: Date
  ): Observable<SecurityPositionGrandSummary> {
    return <Observable<SecurityPositionGrandSummary>>(
      this.httpClient
        .get(
          `${BaseSettings.API_ENDPOINT}` +
            `${AppSettings.SECURITYACCOUNT_KEY}/${idSecurityaccount}/securityaccountsummary/${group}`,
          AppHelper.getOptionsWithIncludeClosedPositionAndUntilDate(
            includeClosedPosition,
            untilDate,
            this.prepareHeaders()
          )
        )
        .pipe(catchError(this.handleError.bind(this)))
    );
  }

  getTransactionSummaries(idSecuritycashAccount: number): Observable<TradingPeriodTransactionSummary[]> {
    return this.httpClient
      .get<TradingPeriodTransactionSummary[]>(
        `${BaseSettings.API_ENDPOINT}${AppSettings.SECURITYACCOUNT_KEY}/${idSecuritycashAccount}/transactionsummaries`,
        this.getHeaders()
      )
      .pipe(catchError(this.handleError.bind(this)));
  }

  /**
   * The security accounts that may be named as trading priority of an algo node: those whose trading periods allow the
   * instrument type. The backend reads the type from the instrument, else from the asset class; with neither, as for a
   * custom category, every security account of the tenant is returned.
   *
   * @param idSecuritycurrency - The instrument of an algo security node, if any
   * @param idAssetClass - The asset class of an algo asset class node, if any
   * @returns Options keyed by the security account id, labelled 'portfolio / account'
   */
  getAlgoAccountOptions(idSecuritycurrency?: number, idAssetClass?: number): Observable<ValueKeyHtmlSelectOptions[]> {
    let params = new HttpParams();
    if (idSecuritycurrency != null) {
      params = params.set('idSecuritycurrency', idSecuritycurrency.toString());
    } else if (idAssetClass != null) {
      params = params.set('idAssetClass', idAssetClass.toString());
    }
    return this.httpClient
      .get<ValueKeyHtmlSelectOptions[]>(
        `${BaseSettings.API_ENDPOINT}${AppSettings.SECURITYACCOUNT_KEY}/algoaccountoptions`,
        { headers: this.prepareHeaders(), params }
      )
      .pipe(catchError(this.handleError.bind(this)));
  }

  update(securityaccount: Securityaccount): Observable<Securityaccount> {
    return this.updateEntity(securityaccount, securityaccount.idSecuritycashAccount, AppSettings.SECURITYACCOUNT_KEY);
  }

  getFeeModelComparison(
    idSecuritycashAccount: number,
    excludeZeroCost: boolean
  ): Observable<FeeModelComparisonResponse> {
    return this.httpClient
      .get<FeeModelComparisonResponse>(
        `${BaseSettings.API_ENDPOINT}${AppSettings.SECURITYACCOUNT_KEY}/${idSecuritycashAccount}/feemodelcomparison`,
        { headers: this.prepareHeaders(), params: { excludeZeroCost: excludeZeroCost.toString() } }
      )
      .pipe(catchError(this.handleError.bind(this)));
  }

  estimateCostFromYaml(request: TransactionCostEstimateRequest): Observable<TransactionCostEstimateResult> {
    return this.httpClient
      .post<TransactionCostEstimateResult>(
        `${BaseSettings.API_ENDPOINT}${AppSettings.SECURITYACCOUNT_KEY}/estimatecostyaml`,
        request,
        this.getHeaders()
      )
      .pipe(catchError(this.handleError.bind(this)));
  }

  estimateFxMarkup(request: FxMarkupPreviewRequest): Observable<FxQuote> {
    return this.httpClient
      .post<FxQuote>(
        `${BaseSettings.API_ENDPOINT}${AppSettings.SECURITYACCOUNT_KEY}/estimatefxmarkupyaml`,
        request,
        this.getHeaders()
      )
      .pipe(catchError(this.handleError.bind(this)));
  }

  deleteSecurityaccount(idSecuritycashaccount: number) {
    return this.httpClient
      .delete(
        `${BaseSettings.API_ENDPOINT}${AppSettings.SECURITYACCOUNT_KEY}/${idSecuritycashaccount}`,
        this.getHeaders()
      )
      .pipe(catchError(this.handleError.bind(this)));
  }
}
