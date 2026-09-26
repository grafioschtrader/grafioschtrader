import { AuthServiceWithLogout } from '../../lib/login/service/base.auth.service.with.logout';
import { DeleteService } from '../../lib/datashowbase/delete.service';
import { ServiceEntityUpdate } from '../../lib/edit/service.entity.update';
import { AlgoAssetclass } from '../model/algo.assetclass';
import { LoginService } from '../../lib/login/service/log-in.service';
import { HttpClient } from '@angular/common/http';
import { MessageToastService } from '../../lib/message/message.toast.service';
import { Observable } from 'rxjs';
import { AppSettings } from '../../shared/app.settings';
import { catchError } from 'rxjs/operators';
import { Injectable } from '@angular/core';
import { BaseSettings } from '../../lib/base.settings';
import { AddSearchToListService } from '../../watchlist/component/add-instrument-table.component';
import { SecuritycurrencySearch } from '../../entities/search/securitycurrency.search';
import { SecuritycurrencyLists } from '../../entities/view/securitycurrency.lists';
import { AppHelper } from '../../lib/helper/app.helper';

@Injectable()
export class AlgoAssetclassService
  extends AuthServiceWithLogout<AlgoAssetclass>
  implements DeleteService, ServiceEntityUpdate<AlgoAssetclass>, AddSearchToListService<AlgoAssetclass>
{
  constructor(loginService: LoginService, httpClient: HttpClient, messageToastService: MessageToastService) {
    super(loginService, httpClient, messageToastService);
  }

  getAlgoAssetclassByIdTenantAndIdAlgoAssetclassParent(idAlgoAssetclassSecurity: number): Observable<AlgoAssetclass[]> {
    return <Observable<AlgoAssetclass[]>>(
      this.httpClient
        .get(
          `${BaseSettings.API_ENDPOINT}${AppSettings.ALGO_ASSETCLASS_KEY}` + `/${idAlgoAssetclassSecurity}`,
          this.getHeaders()
        )
        .pipe(catchError(this.handleError.bind(this)))
    );
  }

  /**
   * Searches the instruments that may still be added to a custom category, independent of the hierarchy's watchlist.
   * The backend leaves out instruments already in the category and those a simulation cannot trade.
   *
   * @param idAlgoAssetclassSecurity - The custom category
   * @param securitycurrencySearch - The search criteria
   * @returns The matching securities; the currency pair list is always empty
   */
  searchByCriteria(
    idAlgoAssetclassSecurity: number,
    securitycurrencySearch: SecuritycurrencySearch
  ): Observable<SecuritycurrencyLists> {
    return <Observable<SecuritycurrencyLists>>this.httpClient
      .get(`${BaseSettings.API_ENDPOINT}${AppSettings.ALGO_ASSETCLASS_KEY}/${idAlgoAssetclassSecurity}/search`, {
        headers: this.prepareHeaders(),
        params: AppHelper.getHttpParamsOfObjectAllowBooleanNullFields(securitycurrencySearch, ['onlyTenantPrivate'])
      })
      .pipe(catchError(this.handleError.bind(this)));
  }

  /**
   * Adds the selected securities as instrument nodes to a custom category. The backend weights them and normalizes the
   * category to 100 percent.
   *
   * @param idAlgoAssetclassSecurity - The custom category
   * @param securitycurrencyLists - The selected securities
   * @returns The custom category after the addition
   */
  addSecuritycurrenciesToList(
    idAlgoAssetclassSecurity: number,
    securitycurrencyLists: SecuritycurrencyLists
  ): Observable<AlgoAssetclass> {
    return <Observable<AlgoAssetclass>>(
      this.httpClient
        .put(
          `${BaseSettings.API_ENDPOINT}${AppSettings.ALGO_ASSETCLASS_KEY}/${idAlgoAssetclassSecurity}/addSecurity`,
          securitycurrencyLists,
          this.getHeaders()
        )
        .pipe(catchError(this.handleError.bind(this)))
    );
  }

  public update(algoAssetclass: AlgoAssetclass): Observable<AlgoAssetclass> {
    return this.updateEntity(algoAssetclass, algoAssetclass.idAlgoAssetclassSecurity, AppSettings.ALGO_ASSETCLASS_KEY);
  }

  public deleteEntity(idAlgoAssetclassSecurity: number): Observable<any> {
    return this.httpClient
      .delete(
        `${BaseSettings.API_ENDPOINT}${AppSettings.ALGO_ASSETCLASS_KEY}/${idAlgoAssetclassSecurity}`,
        this.getHeaders()
      )
      .pipe(catchError(this.handleError.bind(this)));
  }
}
