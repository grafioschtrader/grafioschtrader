import {Injectable} from '@angular/core';
import {AuthServiceWithLogout} from '../../login/service/base.auth.service.with.logout';
import {GTNet, GTNetWithMessages, MsgRequest} from '../model/gtnet';
import {GTNetMessage} from '../model/gtnet.message';
import {MultiTargetMsgRequest} from '../model/multi-target-msg-request';
import {ServiceEntityUpdate} from '../../edit/service.entity.update';
import {Observable} from 'rxjs/internal/Observable';
import {catchError} from 'rxjs/operators';
import {LoginService} from '../../login/service/log-in.service';
import {HttpClient} from '@angular/common/http';
import {MessageToastService} from '../../message/message.toast.service';
import {ApplicationInfo} from '../../services/actuator.service';
import {BaseSettings} from '../../base.settings';

@Injectable()
export class GTNetService extends AuthServiceWithLogout<GTNet> implements ServiceEntityUpdate<GTNet> {

  constructor(loginService: LoginService, httpClient: HttpClient, messageToastService: MessageToastService) {
    super(loginService, httpClient, messageToastService);
  }

  getAllGTNetsWithMessages(): Observable<GTNetWithMessages> {
    return <Observable<GTNetWithMessages>>this.httpClient.get(`${BaseSettings.API_ENDPOINT}${BaseSettings.GT_NET_KEY}`
      + `/gtnetwithmessage`, this.getHeaders()).pipe(catchError(this.handleError.bind(this)));
  }

  checkRemoteDomainWithApplicationInfo(remoteDomainName: string): Observable<ApplicationInfo> {
    return <Observable<ApplicationInfo>>this.httpClient.get(`${BaseSettings.API_ENDPOINT}${BaseSettings.GT_NET_KEY}`
      + `/remotetest/${remoteDomainName}`, this.getHeaders()).pipe(catchError(this.handleError.bind(this)));
  }

  submitMsg(msgRequest: MsgRequest): Observable<GTNetWithMessages> {
    return <Observable<GTNetWithMessages>>this.httpClient.post(`${BaseSettings.API_ENDPOINT}${BaseSettings.GT_NET_KEY}/submitmsg`,
      msgRequest, this.getHeaders()).pipe(catchError(this.handleError.bind(this)));
  }

  /**
   * Sends an admin message to multiple selected targets via background delivery.
   * Creates one message and queues delivery to all selected targets.
   *
   * @param multiTargetMsgRequest the request containing list of target IDs and message
   * @returns the updated GTNetWithMessages for UI refresh
   */
  submitMsgToMultiple(multiTargetMsgRequest: MultiTargetMsgRequest): Observable<GTNetWithMessages> {
    return <Observable<GTNetWithMessages>>this.httpClient.post(
      `${BaseSettings.API_ENDPOINT}${BaseSettings.GT_NET_KEY}/submitmsgmulti`,
      multiTargetMsgRequest, this.getHeaders()).pipe(catchError(this.handleError.bind(this)));
  }

  /**
   * Fetches messages for a specific GTNet domain (lazy loading).
   * Used when expanding a row in the GTNet setup table.
   *
   * @param idGtNet the GTNet domain ID
   * @returns list of messages ordered by timestamp descending
   */
  getMessagesByIdGtNet(idGtNet: number): Observable<GTNetMessage[]> {
    return <Observable<GTNetMessage[]>>this.httpClient.get(`${BaseSettings.API_ENDPOINT}${BaseSettings.GT_NET_KEY}`
      + `/messages/${idGtNet}`, this.getHeaders()).pipe(catchError(this.handleError.bind(this)));
  }

  update(gtNet: GTNet): Observable<GTNet> {
    return this.updateEntity(gtNet, gtNet.idGtNet, BaseSettings.GT_NET_KEY);
  }

  deleteEntity(idGtNet: number): Observable<any> {
    return this.httpClient.delete(`${BaseSettings.API_ENDPOINT}${BaseSettings.GT_NET_KEY}/${idGtNet}`,
      this.getHeaders()).pipe(catchError(this.handleError.bind(this)));
  }

  /**
   * Deletes a batch of GTNet messages along with their cascade-deleted responses.
   *
   * @param idGtNetMessageList the IDs of the messages to delete
   * @returns Observable that completes when deletion is successful
   */
  deleteMessageBatch(idGtNetMessageList: number[]): Observable<void> {
    return this.httpClient.post<void>(`${BaseSettings.API_ENDPOINT}${BaseSettings.GT_NET_KEY}/deletemessagebatch`,
      idGtNetMessageList, this.getHeaders()).pipe(catchError(this.handleError.bind(this)));
  }
}
