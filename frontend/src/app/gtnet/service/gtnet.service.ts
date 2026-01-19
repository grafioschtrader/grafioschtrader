import {Injectable} from '@angular/core';
import {AuthServiceWithLogout} from '../../lib/login/service/base.auth.service.with.logout';
import {GTNet, GTNetWithMessages, MsgRequest} from '../model/gtnet';
import {GTNetMessage} from '../model/gtnet.message';
import {ServiceEntityUpdate} from '../../lib/edit/service.entity.update';
import {Observable} from 'rxjs/internal/Observable';
import {AppSettings} from '../../shared/app.settings';
import {catchError} from 'rxjs/operators';
import {LoginService} from '../../lib/login/service/log-in.service';
import {HttpClient} from '@angular/common/http';
import {MessageToastService} from '../../lib/message/message.toast.service';
import {ApplicationInfo} from '../../lib/services/actuator.service';
import {BaseSettings} from '../../lib/base.settings';

@Injectable()
export class GTNetService extends AuthServiceWithLogout<GTNet> implements ServiceEntityUpdate<GTNet> {

  constructor(loginService: LoginService, httpClient: HttpClient, messageToastService: MessageToastService) {
    super(loginService, httpClient, messageToastService);
  }

  getAllGTNetsWithMessages(): Observable<GTNetWithMessages> {
    return <Observable<GTNetWithMessages>>this.httpClient.get(`${BaseSettings.API_ENDPOINT}${AppSettings.GT_NET_KEY}`
      + `/gtnetwithmessage`, this.getHeaders()).pipe(catchError(this.handleError.bind(this)));
  }

  checkRemoteDomainWithApplicationInfo(remoteDomainName: string): Observable<ApplicationInfo> {
    return <Observable<ApplicationInfo>>this.httpClient.get(`${BaseSettings.API_ENDPOINT}${AppSettings.GT_NET_KEY}`
      + `/remotetest/${remoteDomainName}`, this.getHeaders()).pipe(catchError(this.handleError.bind(this)));
  }

  submitMsg(msgRequest: MsgRequest): Observable<GTNetWithMessages> {
    return <Observable<GTNetWithMessages>>this.httpClient.post(`${BaseSettings.API_ENDPOINT}${AppSettings.GT_NET_KEY}/submitmsg`,
      msgRequest, this.getHeaders()).pipe(catchError(this.handleError.bind(this)));
  }

  /**
   * Fetches messages for a specific GTNet domain (lazy loading).
   * Used when expanding a row in the GTNet setup table.
   *
   * @param idGtNet the GTNet domain ID
   * @returns list of messages ordered by timestamp descending
   */
  getMessagesByIdGtNet(idGtNet: number): Observable<GTNetMessage[]> {
    return <Observable<GTNetMessage[]>>this.httpClient.get(`${BaseSettings.API_ENDPOINT}${AppSettings.GT_NET_KEY}`
      + `/messages/${idGtNet}`, this.getHeaders()).pipe(catchError(this.handleError.bind(this)));
  }

  update(gtNet: GTNet): Observable<GTNet> {
    return this.updateEntity(gtNet, gtNet.idGtNet, AppSettings.GT_NET_KEY);
  }

  deleteEntity(idGtNet: number): Observable<any> {
    return this.httpClient.delete(`${BaseSettings.API_ENDPOINT}${AppSettings.GT_NET_KEY}/${idGtNet}`,
      this.getHeaders()).pipe(catchError(this.handleError.bind(this)));
  }

  /**
   * Deletes a batch of GTNet messages along with their cascade-deleted responses.
   *
   * @param idGtNetMessageList the IDs of the messages to delete
   * @returns Observable that completes when deletion is successful
   */
  deleteMessageBatch(idGtNetMessageList: number[]): Observable<void> {
    return this.httpClient.post<void>(`${BaseSettings.API_ENDPOINT}${AppSettings.GT_NET_KEY}/deletemessagebatch`,
      idGtNetMessageList, this.getHeaders()).pipe(catchError(this.handleError.bind(this)));
  }
}
