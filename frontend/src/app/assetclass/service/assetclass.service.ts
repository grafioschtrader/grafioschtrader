import { Assetclass } from '../../entities/assetclass';
import { Service } from '@angular/core';
import { Observable } from 'rxjs';
import { AppSettings } from '../../shared/app.settings';
import { DeleteService } from '../../lib/datashowbase/delete.service';
import { AuthServiceWithLogout } from '../../lib/login/service/base.auth.service.with.logout';
import { ValueKeyHtmlSelectOptions } from '../../lib/dynamic-form/models/value.key.html.select.options';
import { ServiceEntityUpdate } from '../../lib/edit/service.entity.update';
import { catchError } from 'rxjs/operators';
import { BaseSettings } from '../../lib/base.settings';

@Service()
export class AssetclassService
  extends AuthServiceWithLogout<Assetclass>
  implements DeleteService, ServiceEntityUpdate<Assetclass>
{
  public getAllAssetclass(): Observable<Assetclass[]> {
    return <Observable<Assetclass[]>>(
      this.httpClient
        .get(`${BaseSettings.API_ENDPOINT}${AppSettings.ASSETCLASS_KEY}`, this.getHeaders())
        .pipe(catchError(this.handleError.bind(this)))
    );
  }

  public getPossibleAssetclassForExistingSecurityOrAll(idSecuritycurrency: number): Observable<Assetclass[]> {
    return <Observable<Assetclass[]>>(
      this.httpClient
        .get(
          `${BaseSettings.API_ENDPOINT}${AppSettings.ASSETCLASS_KEY}/` + `possible/${idSecuritycurrency}`,
          this.getHeaders()
        )
        .pipe(catchError(this.handleError.bind(this)))
    );
  }

  public getInvestableAssetclassesByWatchlist(idWatchlist: number): Observable<Assetclass[]> {
    return <Observable<Assetclass[]>>(
      this.httpClient
        .get(
          `${BaseSettings.API_ENDPOINT}${AppSettings.ASSETCLASS_KEY}/` + `${AppSettings.WATCHLIST_KEY}/${idWatchlist}`,
          this.getHeaders()
        )
        .pipe(catchError(this.handleError.bind(this)))
    );
  }

  public getAssetclass(idAssetclass: number): Observable<Assetclass> {
    return <Observable<Assetclass>>(
      this.httpClient
        .get(`${BaseSettings.API_ENDPOINT}${AppSettings.ASSETCLASS_KEY}/${idAssetclass}`, this.getHeaders())
        .pipe(catchError(this.handleError.bind(this)))
    );
  }

  public getUnusedAssetclassForAlgo(idAlgoAssetclassSecurity: number): Observable<Assetclass[]> {
    return <Observable<Assetclass[]>>(
      this.httpClient
        .get(
          `${BaseSettings.API_ENDPOINT}${AppSettings.ASSETCLASS_KEY}/` + `algounused/${idAlgoAssetclassSecurity}`,
          this.getHeaders()
        )
        .pipe(catchError(this.handleError.bind(this)))
    );
  }

  public assetclassesHasSecurity(): Observable<[number, number][]> {
    return <Observable<[number, number][]>>(
      this.httpClient
        .get(`${BaseSettings.API_ENDPOINT}${AppSettings.ASSETCLASS_KEY}/hassecurity`, this.getHeaders())
        .pipe(catchError(this.handleError.bind(this)))
    );
  }

  public assetclassHasSecurity(idAssetclass: number): Observable<boolean> {
    return <Observable<boolean>>(
      this.httpClient
        .get(`${BaseSettings.API_ENDPOINT}${AppSettings.ASSETCLASS_KEY}/${idAssetclass}/hassecurity`, this.getHeaders())
        .pipe(catchError(this.handleError.bind(this)))
    );
  }

  public update(assetclass: Assetclass): Observable<Assetclass> {
    return this.updateEntity(assetclass, assetclass.idAssetClass, AppSettings.ASSETCLASS_KEY);
  }

  public deleteEntity(idAssetclass: number): Observable<any> {
    return this.httpClient
      .delete(`${BaseSettings.API_ENDPOINT}${AppSettings.ASSETCLASS_KEY}/${idAssetclass}`, this.getHeaders())
      .pipe(catchError(this.handleError.bind(this)));
  }

  public getSubcategoryForLanguage(): Observable<ValueKeyHtmlSelectOptions[]> {
    return <Observable<ValueKeyHtmlSelectOptions[]>>(
      this.httpClient
        .get(`${BaseSettings.API_ENDPOINT}${AppSettings.ASSETCLASS_KEY}/subcategory`, this.getHeaders())
        .pipe(catchError(this.handleError.bind(this)))
    );
  }
}
