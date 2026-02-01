import {AuthServiceWithLogout} from '../../lib/login/service/base.auth.service.with.logout';
import {ServiceEntityUpdate} from '../../lib/edit/service.entity.update';
import {GTNetMessage} from '../model/gtnet.message';
import {LoginService} from '../../lib/login/service/log-in.service';
import {HttpClient} from '@angular/common/http';
import {MessageToastService} from '../../lib/message/message.toast.service';
import {Observable} from 'rxjs/internal/Observable';
import {AppSettings} from '../../shared/app.settings';
import {catchError} from 'rxjs/operators';
import {Injectable} from '@angular/core';
import {ClassDescriptorInputAndShow} from '../../lib/dynamicfield/field.descriptor.input.and.show';
import {BaseSettings} from '../../lib/base.settings';

@Injectable()
export class GTNetMessageService extends AuthServiceWithLogout<GTNetMessage> implements ServiceEntityUpdate<GTNetMessage> {

  constructor(loginService: LoginService, httpClient: HttpClient, messageToastService: MessageToastService) {
    super(loginService, httpClient, messageToastService);
  }

  getAllFormDefinitionsWithClass(): Observable<{ [type: string]: ClassDescriptorInputAndShow }> {
    return <Observable<{ [type: string]: ClassDescriptorInputAndShow }>>this.httpClient.get(
      `${BaseSettings.API_ENDPOINT}${AppSettings.GT_NET_MESSAGE_KEY}`
      + `/msgformdefinition`, this.getHeaders()).pipe(catchError(this.handleError.bind(this)));
  }

  update(gtNetMessage: GTNetMessage): Observable<GTNetMessage> {
    return this.updateEntity(gtNetMessage, gtNetMessage.idGtNetMessage, AppSettings.GT_NET_MESSAGE_KEY);
  }

  markAsRead(idGtNetMessage: number): Observable<void> {
    return this.httpClient.patch<void>(
      `${BaseSettings.API_ENDPOINT}${AppSettings.GT_NET_MESSAGE_KEY}/${idGtNetMessage}/markasread`,
      null, this.getHeaders()).pipe(catchError(this.handleError.bind(this)));
  }

  deleteEntity(gtNetMessage: number): Observable<any> {
    return this.httpClient.delete(`${BaseSettings.API_ENDPOINT}${AppSettings.GT_NET_MESSAGE_KEY}/${gtNetMessage}`,
      this.getHeaders()).pipe(catchError(this.handleError.bind(this)));
  }

  /**
   * Retrieves all admin-only messages. Requires ROLE_ADMIN.
   */
  getAdminMessages(): Observable<GTNetMessage[]> {
    return <Observable<GTNetMessage[]>>this.httpClient.get(
      `${BaseSettings.API_ENDPOINT}${AppSettings.GT_NET_MESSAGE_KEY}/admin`,
      this.getHeaders()).pipe(catchError(this.handleError.bind(this)));
  }

  /**
   * Retrieves counts of admin-only messages grouped by idGtNet. Requires ROLE_ADMIN.
   */
  getAdminMessageCounts(): Observable<{ [idGtNet: number]: number }> {
    return <Observable<{ [idGtNet: number]: number }>>this.httpClient.get(
      `${BaseSettings.API_ENDPOINT}${AppSettings.GT_NET_MESSAGE_KEY}/admin/count`,
      this.getHeaders()).pipe(catchError(this.handleError.bind(this)));
  }
}
