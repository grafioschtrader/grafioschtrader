import { inject, Service } from '@angular/core';
import { BaseService } from '../../lib/login/service/base.service';
import { HttpClient } from '@angular/common/http';
import { Observable } from 'rxjs';
import { AppSettings } from '../app.settings';
import { ValueKeyHtmlSelectOptions } from '../../lib/dynamic-form/models/value.key.html.select.options';
import { Stockexchange } from '../../entities/stockexchange';
import { BaseSettings } from '../../lib/base.settings';

@Service()
export class MultipleRequestToOneService extends BaseService {
  private httpClient = inject(HttpClient);

  public getDataForCurrencySecuritySearch(): Observable<DataForCurrencySecuritySearch> {
    return <Observable<DataForCurrencySecuritySearch>>(
      this.httpClient.get(
        `${BaseSettings.API_ENDPOINT}` + `${AppSettings.MULTIPLE_REQUEST_TO_ONE_KEY}/dataforcurrencysecuritysearch`,
        this.getHeaders()
      )
    );
  }
}

export interface DataForCurrencySecuritySearch {
  currencies: ValueKeyHtmlSelectOptions[];
  assetclasses: ValueKeyHtmlSelectOptions[];
  feedConnectorsHistory: ValueKeyHtmlSelectOptions[];
  feedConnectorsIntra: ValueKeyHtmlSelectOptions[];
  stockexchanges: Stockexchange[];
}
