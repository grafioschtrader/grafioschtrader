import {Injectable} from '@angular/core';
import {Observable} from 'rxjs';
import {catchError} from 'rxjs/operators';
import {HttpClient} from '@angular/common/http';
import {LoginService} from '../../lib/login/service/log-in.service';
import {MessageToastService} from '../../lib/message/message.toast.service';
import {AuthServiceWithLogout} from '../../lib/login/service/base.auth.service.with.logout';
import {BaseSettings} from '../../lib/base.settings';
import {SecurityAction, SecurityActionApplication, SecurityActionTreeData, SecurityTransfer} from '../model/security-action.model';
import {ServiceEntityUpdate} from '../../lib/edit/service.entity.update';

@Injectable()
export class SecurityActionService extends AuthServiceWithLogout<SecurityAction> implements ServiceEntityUpdate<SecurityAction> {

  private static readonly BASE_URL = BaseSettings.API_ENDPOINT + 'securityaction';

  constructor(loginService: LoginService, httpClient: HttpClient, messageToastService: MessageToastService) {
    super(loginService, httpClient, messageToastService);
  }

  update(entity: SecurityAction): Observable<SecurityAction> {
    return this.createSecurityAction(entity);
  }

  getTree(): Observable<SecurityActionTreeData> {
    return <Observable<SecurityActionTreeData>>this.httpClient.get(`${SecurityActionService.BASE_URL}/tree`,
      this.getHeaders()).pipe(catchError(this.handleError.bind(this)));
  }

  createSecurityAction(action: SecurityAction): Observable<SecurityAction> {
    return <Observable<SecurityAction>>this.httpClient.post(`${SecurityActionService.BASE_URL}`,
      action, this.getHeaders()).pipe(catchError(this.handleError.bind(this)));
  }

  deleteSecurityAction(id: number): Observable<any> {
    return this.httpClient.delete(`${SecurityActionService.BASE_URL}/${id}`,
      this.getHeaders()).pipe(catchError(this.handleError.bind(this)));
  }

  applySecurityAction(id: number): Observable<SecurityActionApplication> {
    return <Observable<SecurityActionApplication>>this.httpClient.post(
      `${SecurityActionService.BASE_URL}/${id}/apply`, null,
      this.getHeaders()).pipe(catchError(this.handleError.bind(this)));
  }

  reverseSecurityAction(id: number): Observable<any> {
    return this.httpClient.post(`${SecurityActionService.BASE_URL}/${id}/reverse`, null,
      this.getHeaders()).pipe(catchError(this.handleError.bind(this)));
  }

  createTransfer(transfer: SecurityTransfer): Observable<SecurityTransfer> {
    return <Observable<SecurityTransfer>>this.httpClient.post(
      `${SecurityActionService.BASE_URL}/transfer`, transfer,
      this.getHeaders()).pipe(catchError(this.handleError.bind(this)));
  }

  reverseTransfer(id: number): Observable<any> {
    return this.httpClient.delete(`${SecurityActionService.BASE_URL}/transfer/${id}`,
      this.getHeaders()).pipe(catchError(this.handleError.bind(this)));
  }
}
