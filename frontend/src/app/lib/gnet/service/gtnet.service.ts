import { Injectable } from '@angular/core';
import { AuthServiceWithLogout } from '../../login/service/base.auth.service.with.logout';
import { GTNet, GTNetMaintenanceWindow, GTNetWithMessages, MsgRequest } from '../model/gtnet';
import { GTNetMessage } from '../model/gtnet.message';
import { MultiTargetMsgRequest } from '../model/multi-target-msg-request';
import { ServiceEntityUpdate } from '../../edit/service.entity.update';
import { Observable } from 'rxjs/internal/Observable';
import { lastValueFrom } from 'rxjs';
import { catchError } from 'rxjs/operators';
import { LoginService } from '../../login/service/log-in.service';
import { HttpClient } from '@angular/common/http';
import { MessageToastService } from '../../message/message.toast.service';
import { BaseSettings } from '../../base.settings';

@Injectable()
export class GTNetService extends AuthServiceWithLogout<GTNet> implements ServiceEntityUpdate<GTNet> {
  constructor(loginService: LoginService, httpClient: HttpClient, messageToastService: MessageToastService) {
    super(loginService, httpClient, messageToastService);
  }

  getAllGTNetsWithMessages(): Observable<GTNetWithMessages> {
    return <Observable<GTNetWithMessages>>(
      this.httpClient
        .get(`${BaseSettings.API_ENDPOINT}${BaseSettings.GT_NET_KEY}` + `/gtnetwithmessage`, this.getHeaders())
        .pipe(catchError(this.handleError.bind(this)))
    );
  }

  submitMsg(msgRequest: MsgRequest): Observable<GTNetWithMessages> {
    return <Observable<GTNetWithMessages>>(
      this.httpClient
        .post(`${BaseSettings.API_ENDPOINT}${BaseSettings.GT_NET_KEY}/submitmsg`, msgRequest, this.getHeaders())
        .pipe(catchError(this.handleError.bind(this)))
    );
  }

  /**
   * Triggers an immediate online-status check for a single GTNet peer.
   * The backend pings the peer (if the outbound handshake is complete) and returns
   * the updated GTNet entry so the caller can refresh the row in place.
   *
   * @param idGtNet the ID of the remote GTNet entry to probe
   * @returns the updated GTNet entry
   */
  checkPeerStatus(idGtNet: number): Observable<GTNet> {
    return <Observable<GTNet>>(
      this.httpClient
        .post(`${BaseSettings.API_ENDPOINT}${BaseSettings.GT_NET_KEY}/${idGtNet}/checkstatus`, null, this.getHeaders())
        .pipe(catchError(this.handleError.bind(this)))
    );
  }

  /**
   * Sends an admin message to multiple selected targets via background delivery.
   * Creates one message and queues delivery to all selected targets.
   *
   * @param multiTargetMsgRequest the request containing list of target IDs and message
   * @returns the updated GTNetWithMessages for UI refresh
   */
  submitMsgToMultiple(multiTargetMsgRequest: MultiTargetMsgRequest): Observable<GTNetWithMessages> {
    return <Observable<GTNetWithMessages>>(
      this.httpClient
        .post(
          `${BaseSettings.API_ENDPOINT}${BaseSettings.GT_NET_KEY}/submitmsgmulti`,
          multiTargetMsgRequest,
          this.getHeaders()
        )
        .pipe(catchError(this.handleError.bind(this)))
    );
  }

  /**
   * Fetches messages for a specific GTNet domain (lazy loading).
   * Used when expanding a row in the GTNet setup table.
   *
   * @param idGtNet the GTNet domain ID
   * @returns list of messages ordered by timestamp descending
   */
  getMessagesByIdGtNet(idGtNet: number): Observable<GTNetMessage[]> {
    return <Observable<GTNetMessage[]>>(
      this.httpClient
        .get(`${BaseSettings.API_ENDPOINT}${BaseSettings.GT_NET_KEY}` + `/messages/${idGtNet}`, this.getHeaders())
        .pipe(catchError(this.handleError.bind(this)))
    );
  }

  /**
   * Loads the announced maintenance windows of one GTNet domain. Called when the maintenance panel of the expanded
   * row is opened; the counts that drive the panel header already come with {@link getAllGTNetsWithMessages}.
   *
   * @param idGtNet the GTNet domain ID
   * @returns the announced windows, most recent first
   */
  getMaintenanceWindowsByIdGtNet(idGtNet: number): Observable<GTNetMaintenanceWindow[]> {
    return <Observable<GTNetMaintenanceWindow[]>>(
      this.httpClient
        .get(
          `${BaseSettings.API_ENDPOINT}${BaseSettings.GT_NET_KEY}` + `/maintenancewindows/${idGtNet}`,
          this.getHeaders()
        )
        .pipe(catchError(this.handleError.bind(this)))
    );
  }

  update(gtNet: GTNet): Observable<GTNet> {
    return this.updateEntity(gtNet, gtNet.idGtNet, BaseSettings.GT_NET_KEY);
  }

  /**
   * Discards the tokens this instance shares with one peer, so that the peer may complete a first handshake again.
   * Needed when the peer lost its own copy of the tokens - a rebuilt database, a restored backup, a migrated
   * instance - because its handshake is otherwise refused with HANDSHAKE_ALREADY_ESTABLISHED. The peer entry, its
   * messages and its settings are kept.
   *
   * @param idGtNet the ID of the remote GTNet entry whose handshake is reset
   * @returns every GTNet entry with its messages, so the table can be refreshed
   */
  resetHandshake(idGtNet: number): Observable<GTNetWithMessages> {
    return <Observable<GTNetWithMessages>>(
      this.httpClient
        .post(
          `${BaseSettings.API_ENDPOINT}${BaseSettings.GT_NET_KEY}/${idGtNet}/resethandshake`,
          null,
          this.getHeaders()
        )
        .pipe(catchError(this.handleError.bind(this)))
    );
  }

  deleteEntity(idGtNet: number): Observable<any> {
    return this.httpClient
      .delete(`${BaseSettings.API_ENDPOINT}${BaseSettings.GT_NET_KEY}/${idGtNet}`, this.getHeaders())
      .pipe(catchError(this.handleError.bind(this)));
  }

  /**
   * Deletes a batch of GTNet messages along with their cascade-deleted responses.
   *
   * @param idGtNetMessageList the IDs of the messages to delete
   * @returns Observable that completes when deletion is successful
   */
  deleteMessageBatch(idGtNetMessageList: number[]): Observable<void> {
    return this.httpClient
      .post<void>(
        `${BaseSettings.API_ENDPOINT}${BaseSettings.GT_NET_KEY}/deletemessagebatch`,
        idGtNetMessageList,
        this.getHeaders()
      )
      .pipe(catchError(this.handleError.bind(this)));
  }

  async exportGTNetData(): Promise<Blob> {
    const blob$ = this.httpClient.get<Blob>(`${BaseSettings.API_ENDPOINT}gtnetdataexport/export`, {
      headers: this.prepareHeaders('text/plain'),
      responseType: 'blob' as 'json'
    });
    return await lastValueFrom(blob$);
  }

  importGTNetData(formData: FormData): Observable<any> {
    const options: any = this.getMultipartHeaders();
    options.responseType = 'text';
    return this.httpClient
      .post(`${BaseSettings.API_ENDPOINT}gtnetdataexport/import`, formData, options)
      .pipe(catchError(this.handleError.bind(this)));
  }
}
