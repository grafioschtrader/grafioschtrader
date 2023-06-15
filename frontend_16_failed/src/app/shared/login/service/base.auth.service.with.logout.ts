import {MessageToastService} from '../../message/message.toast.service';
import {HttpClient} from '@angular/common/http';
import {LoginService} from './log-in.service';
import {BaseAuthService} from './base.auth.service';

/**
 * Delivers functionality for a logout. For example when user misused this application.
 */
export abstract class AuthServiceWithLogout<T> extends BaseAuthService<T> {

  constructor(protected loginService: LoginService,
              httpClient: HttpClient,
              messageToastService: MessageToastService) {
    super(httpClient, messageToastService);
  }

  protected toLogout(): void {
    this.loginService.logoutWithLoginView();
  }

}
