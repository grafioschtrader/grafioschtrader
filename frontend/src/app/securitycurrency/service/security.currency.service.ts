import { AuthServiceWithLogout } from '../../lib/login/service/base.auth.service.with.logout';
import { HttpParams } from '@angular/common/http';

export abstract class SecurityCurrencyService<T> extends AuthServiceWithLogout<T> {
  protected getOptionsWithIncludeForChart(forchart: boolean) {
    const httpParams = new HttpParams().set('forchart', forchart.toString());
    return { headers: this.prepareHeaders(), params: httpParams };
  }
}
