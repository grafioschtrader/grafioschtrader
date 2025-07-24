import {AuthServiceWithLogout} from '../../lib/login/service/base.auth.service.with.logout';
import {LoginService} from '../../lib/login/service/log-in.service';
import {HttpClient, HttpParams} from '@angular/common/http';
import {MessageToastService} from '../../lib/message/message.toast.service';

export abstract class SecurityCurrencyService<T> extends AuthServiceWithLogout<T> {
  protected constructor(loginService: LoginService, httpClient: HttpClient, messageToastService: MessageToastService) {
    super(loginService, httpClient, messageToastService);
  }

  protected getOptionsWithIncludeForChart(forchart: boolean) {
    const httpParams = new HttpParams().set('forchart', forchart.toString());
    return {headers: this.prepareHeaders(), params: httpParams};
  }

}
