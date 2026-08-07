import {Injectable} from '@angular/core';

import {AfterLoginHandler} from '../app/lib/login/service/after-login.handler';
import {ConfigurationWithLogin} from '../app/lib/login/model/configuration-with-login';

/**
 * The {@code AfterLoginHandler} of this host. It is provided although it does nothing, because the hook is how an
 * application stores the extra values its own {@code ConfigurationWithLogin} subclass carries — Grafioschtrader keeps
 * report date, cryptocurrencies and the closed-until date here. This host adds no fields of its own, so everything the
 * session needs has already been written by {@code LoginService.afterSuccessfulLogin}.
 */
@Injectable()
export class GrafioschAfterLoginHandler extends AfterLoginHandler {

  handleAfterLogin(configurationWithLogin: ConfigurationWithLogin): void {
    // Nothing application specific to store.
  }
}
