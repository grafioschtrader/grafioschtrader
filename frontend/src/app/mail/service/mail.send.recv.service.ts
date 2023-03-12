import {Injectable} from '@angular/core';
import {AuthServiceWithLogout} from '../../shared/login/service/base.auth.service.with.logout';
import {LoginService} from '../../shared/login/service/log-in.service';
import {HttpClient} from '@angular/common/http';
import {MessageToastService} from '../../shared/message/message.toast.service';
import {Observable} from 'rxjs';
import {AppSettings} from '../../shared/app.settings';
import {catchError} from 'rxjs/operators';
import {DeleteService} from '../../shared/datashowbase/delete.service';
import {MailInboxWithSend, MailSendRecv} from '../model/mail.send.recv';

@Injectable()
export class MailSendRecvService extends AuthServiceWithLogout<MailSendRecv> {
  constructor(loginService: LoginService, httpClient: HttpClient, messageToastService: MessageToastService) {
    super(loginService, httpClient, messageToastService);
  }

  public getMailsByUserOrRole(): Observable<MailInboxWithSend> {
    return <Observable<MailInboxWithSend>>this.httpClient.get(`${AppSettings.API_ENDPOINT}${AppSettings.MAIL_SEND_RECV_KEY}`,
      this.getHeaders()).pipe(catchError(this.handleError.bind(this)));
  }

  public markForRead(idMailSendRecv: number): Observable<MailSendRecv> {
    return this.httpClient.post(`${AppSettings.API_ENDPOINT}${AppSettings.MAIL_SEND_RECV_KEY}/${idMailSendRecv}/markforread`,
      null, {headers: this.prepareHeaders()}).pipe(catchError(this.handleError.bind(this)));
  }

  public sendMessage(mailSendRecv: MailSendRecv): Observable<MailSendRecv> {
    return <Observable<MailSendRecv>>this.httpClient.post(`${AppSettings.API_ENDPOINT}${AppSettings.MAIL_SEND_RECV_KEY}`,
      mailSendRecv, {headers: this.prepareHeaders()}).pipe(catchError(this.handleError.bind(this)));
  }

  public deleteSingleOrGroup(idMailSendRecv: number): Observable<any> {
    return this.httpClient.delete(`${AppSettings.API_ENDPOINT}${AppSettings.MAIL_SEND_RECV_KEY}/${idMailSendRecv}`, this.getHeaders())
      .pipe(catchError(this.handleError.bind(this)));
  }
}
