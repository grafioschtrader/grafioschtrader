import {Injectable} from '@angular/core';
import {BaseService} from '../login/service/base.service';
import {HttpClient} from '@angular/common/http';
import {Observable} from 'rxjs';
import {AppSettings} from '../app.settings';
import {ValueKeyHtmlSelectOptions} from '../../dynamic-form/models/value.key.html.select.options';
import {Stockexchange} from '../../entities/stockexchange';

@Injectable()
export class MultipleRequestToOneService extends BaseService {

  constructor(private httpClient: HttpClient) {
    super();
  }

  public getDataForCurrencySecuritySearch(): Observable<DataForCurrencySecuritySearch> {
    return <Observable<DataForCurrencySecuritySearch>>this.httpClient.get(`${AppSettings.API_ENDPOINT}`
      + `${AppSettings.MULTIPLE_REQUEST_TO_ONE_KEY}/dataforcurrencysecuritysearch`,
      this.getHeaders());
  }
}

export interface DataForCurrencySecuritySearch {
  currencies: ValueKeyHtmlSelectOptions[];
  assetclasses: ValueKeyHtmlSelectOptions[];
  feedConnectorsHistory: ValueKeyHtmlSelectOptions[];
  feedConnectorsIntra: ValueKeyHtmlSelectOptions[];
  stockexchanges: Stockexchange[];
}
