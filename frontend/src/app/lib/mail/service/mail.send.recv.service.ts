import { Service } from '@angular/core';
import { AuthServiceWithLogout } from '../../login/service/base.auth.service.with.logout';
import { Observable } from 'rxjs';
import { catchError } from 'rxjs/operators';
import { MailInboxWithSend, MailSendRecv } from '../model/mail.send.recv';
import { BaseSettings } from '../../base.settings';

@Service()
export class MailSendRecvService extends AuthServiceWithLogout<MailSendRecv> {
  public getMailsByUserOrRole(): Observable<MailInboxWithSend> {
    return this.httpClient
      .get<MailInboxWithSend>(`${BaseSettings.API_ENDPOINT}${BaseSettings.MAIL_SEND_RECV_KEY}`, this.getHeaders())
      .pipe(catchError(this.handleError.bind(this)));
  }

  public markForRead(idMailSendRecv: number): Observable<MailSendRecv> {
    return this.httpClient
      .post<MailSendRecv>(
        `${BaseSettings.API_ENDPOINT}${BaseSettings.MAIL_SEND_RECV_KEY}/${idMailSendRecv}/markforread`,
        null,
        {
          headers: this.prepareHeaders()
        }
      )
      .pipe(catchError(this.handleError.bind(this)));
  }

  public sendMessage(mailSendRecv: MailSendRecv): Observable<MailSendRecv> {
    return this.httpClient
      .post<MailSendRecv>(`${BaseSettings.API_ENDPOINT}${BaseSettings.MAIL_SEND_RECV_KEY}`, mailSendRecv, {
        headers: this.prepareHeaders()
      })
      .pipe(catchError(this.handleError.bind(this)));
  }

  public deleteSingleOrGroup(idMailSendRecv: number): Observable<any> {
    return this.httpClient
      .delete(`${BaseSettings.API_ENDPOINT}${BaseSettings.MAIL_SEND_RECV_KEY}/${idMailSendRecv}`, this.getHeaders())
      .pipe(catchError(this.handleError.bind(this)));
  }
}
