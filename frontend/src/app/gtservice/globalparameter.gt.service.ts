import {Observable, of} from 'rxjs';
import {GlobalSessionNames} from '../shared/global.session.names';
import {GlobalparameterService} from '../shared/service/globalparameter.service';
import {Injectable} from '@angular/core';
import {HttpClient} from '@angular/common/http';
import {MessageToastService} from '../lib/message/message.toast.service';
import {ValueKeyHtmlSelectOptions} from '../dynamic-form/models/value.key.html.select.options';
import {AppSettings} from '../shared/app.settings';
import {catchError, tap} from 'rxjs/operators';
import {BaseAuthService} from '../shared/login/service/base.auth.service';
import {Globalparameters} from '../lib/entities/globalparameters';
import {AssetclassType} from '../shared/types/assetclass.type';
import {SpecialInvestmentInstruments} from '../shared/types/special.investment.instruments';

@Injectable()
export class GlobalparameterGTService extends BaseAuthService<Globalparameters> {

  constructor(httpClient: HttpClient, messageToastService: MessageToastService) {
    super(httpClient, messageToastService);
  }

  public getIntraUpdateTimeout(): Observable<number> {
    return this.getGlobalparameterNumber(GlobalSessionNames.UPDATE_TIME,
      AppSettings.GT_GLOBALPARAMETERS_P_KEY, 'updatetimeout');
  }

  public getStartFeedDateAsTime(): Observable<number> {
    return this.getGlobalparameterNumber(GlobalSessionNames.START_FEED_DATE,
      AppSettings.GT_GLOBALPARAMETERS_P_KEY, 'startfeeddate');
  }

  public getCurrencies(): Observable<ValueKeyHtmlSelectOptions[]> {
    return <Observable<ValueKeyHtmlSelectOptions[]>>this.httpClient.get(`${AppSettings.API_ENDPOINT}`
      + `${AppSettings.GT_GLOBALPARAMETERS_P_KEY}/${AppSettings.CURRENCIES_P_KEY}`,
      this.getHeaders()).pipe(catchError(this.handleError.bind(this)));
  }

  public getPossibleAssetclassInstrumentMap(): Observable<{ [key in AssetclassType]: SpecialInvestmentInstruments[] }> {
    return <Observable<{ [key in AssetclassType]: SpecialInvestmentInstruments[] }>>
      this.httpClient.get(`${AppSettings.API_ENDPOINT}${AppSettings.GT_GLOBALPARAMETERS_P_KEY}/possibleassetclassspezinstrument`,
        this.getHeaders()).pipe(catchError(this.handleError.bind(this)));
  }

  private getGlobalparameterNumber(globalSessionNames: GlobalSessionNames, requestMapping: string, uriPart: string): Observable<number> {
    if (sessionStorage.getItem(globalSessionNames)) {
      return of(+sessionStorage.getItem(globalSessionNames));
    } else {
      return <Observable<number>>this.httpClient.get(`${AppSettings.API_ENDPOINT}`
        + `${requestMapping}/${uriPart}`,
        this.getHeaders()).pipe(tap(value => sessionStorage.setItem(globalSessionNames, '' + value)),
        catchError(this.handleError.bind(this)));
    }
  }

}
