import {AuthServiceWithLogout} from '../../shared/login/service/base.auth.service.with.logout';
import {LoginService} from '../../shared/login/service/log-in.service';
import {HttpClient, HttpParams} from '@angular/common/http';
import {MessageToastService} from '../../shared/message/message.toast.service';

export abstract class SecurityCurrencyService<T> extends AuthServiceWithLogout<T> {
  constructor(loginService: LoginService, httpClient: HttpClient, messageToastService: MessageToastService) {
    super(loginService, httpClient, messageToastService);
  }

  protected getOptionsWithIncludeForChart(forchart: boolean) {
    const httpParams = new HttpParams().set('forchart', forchart.toString());
    return {headers: this.prepareHeaders(), params: httpParams};
  }

}
