import {Assetclass} from '../../entities/assetclass';
import {Injectable} from '@angular/core';
import {Observable} from 'rxjs';
import {AppSettings} from '../../shared/app.settings';
import {MessageToastService} from '../../lib/message/message.toast.service';
import {DeleteService} from '../../lib/datashowbase/delete.service';
import {HttpClient} from '@angular/common/http';
import {AuthServiceWithLogout} from '../../shared/login/service/base.auth.service.with.logout';
import {ValueKeyHtmlSelectOptions} from '../../dynamic-form/models/value.key.html.select.options';
import {ServiceEntityUpdate} from '../../lib/edit/service.entity.update';
import {catchError} from 'rxjs/operators';
import {LoginService} from '../../shared/login/service/log-in.service';


@Injectable()
export class AssetclassService extends AuthServiceWithLogout<Assetclass> implements DeleteService, ServiceEntityUpdate<Assetclass> {

  constructor(loginService: LoginService, httpClient: HttpClient, messageToastService: MessageToastService) {
    super(loginService, httpClient, messageToastService);
  }

  public getAllAssetclass(): Observable<Assetclass[]> {
    return <Observable<Assetclass[]>>this.httpClient.get(`${AppSettings.API_ENDPOINT}${AppSettings.ASSETCLASS_KEY}`,
      this.getHeaders()).pipe(catchError(this.handleError.bind(this)));
  }

  public getPossibleAssetclassForExistingSecurityOrAll(idSecuritycurrency: number): Observable<Assetclass[]> {
    return <Observable<Assetclass[]>>this.httpClient.get(`${AppSettings.API_ENDPOINT}${AppSettings.ASSETCLASS_KEY}/`
      + `possible/${idSecuritycurrency}`, this.getHeaders()).pipe(catchError(this.handleError.bind(this)));
  }

  public getInvestableAssetclassesByWatchlist(idWatchlist: number): Observable<Assetclass[]> {
    return <Observable<Assetclass[]>>this.httpClient.get(`${AppSettings.API_ENDPOINT}${AppSettings.ASSETCLASS_KEY}/`
      + `${AppSettings.WATCHLIST_KEY}/${idWatchlist}`, this.getHeaders()).pipe(catchError(this.handleError.bind(this)));
  }

  public getAssetclass(idAssetclass: number): Observable<Assetclass> {
    return <Observable<Assetclass>>this.httpClient.get(`${AppSettings.API_ENDPOINT}${AppSettings.ASSETCLASS_KEY}/${idAssetclass}`,
      this.getHeaders()).pipe(catchError(this.handleError.bind(this)));
  }

  public getUnusedAssetclassForAlgo(idAlgoAssetclassSecurity: number): Observable<Assetclass[]> {
    return <Observable<Assetclass[]>>this.httpClient.get(`${AppSettings.API_ENDPOINT}${AppSettings.ASSETCLASS_KEY}/`
      + `algounused/${idAlgoAssetclassSecurity}`, this.getHeaders()).pipe(catchError(this.handleError.bind(this)));
  }

  public assetclassesHasSecurity(): Observable<number[]> {
    return <Observable<number[]>>this.httpClient.get(`${AppSettings.API_ENDPOINT}${AppSettings.ASSETCLASS_KEY}/hassecurity`,
      this.getHeaders()).pipe(catchError(this.handleError.bind(this)));
  }

  public assetclassHasSecurity(idAssetclass: number): Observable<boolean> {
    return <Observable<boolean>>this.httpClient.get(`${AppSettings.API_ENDPOINT}${AppSettings.ASSETCLASS_KEY}/${idAssetclass}/hassecurity`,
      this.getHeaders()).pipe(catchError(this.handleError.bind(this)));
  }

  public update(assetclass: Assetclass): Observable<Assetclass> {
    return this.updateEntity(assetclass, assetclass.idAssetClass, AppSettings.ASSETCLASS_KEY);
  }

  public deleteEntity(idAssetclass: number): Observable<any> {
    return this.httpClient.delete(`${AppSettings.API_ENDPOINT}${AppSettings.ASSETCLASS_KEY}/${idAssetclass}`, this.getHeaders())
      .pipe(catchError(this.handleError.bind(this)));
  }

  public getSubcategoryForLanguage(): Observable<ValueKeyHtmlSelectOptions[]> {
    return <Observable<ValueKeyHtmlSelectOptions[]>>
      this.httpClient.get(`${AppSettings.API_ENDPOINT}${AppSettings.ASSETCLASS_KEY}/subcategory`,
        this.getHeaders()).pipe(catchError(this.handleError.bind(this)));

  }
}
