import {Injectable} from '@angular/core';
import {AuthServiceWithLogout} from '../../shared/login/service/base.auth.service.with.logout';
import {MailSendbox} from '../model/mail.sendbox';
import {LoginService} from '../../shared/login/service/log-in.service';
import {HttpClient} from '@angular/common/http';
import {MessageToastService} from '../../shared/message/message.toast.service';
import {Observable} from 'rxjs';
import {AppSettings} from '../../shared/app.settings';
import {catchError} from 'rxjs/operators';
import {DeleteService} from '../../shared/datashowbase/delete.service';

@Injectable()
export class MailSendboxService extends AuthServiceWithLogout<MailSendbox> implements DeleteService {
  constructor(loginService: LoginService, httpClient: HttpClient, messageToastService: MessageToastService) {
    super(loginService, httpClient, messageToastService);
  }

  public getAllSendboxByUser(): Observable<MailSendbox[]> {
    return <Observable<MailSendbox[]>>this.httpClient.get(`${AppSettings.API_ENDPOINT}${AppSettings.MAIL_SENDBOX_KEY}`,
      this.getHeaders()).pipe(catchError(this.handleError.bind(this)));
  }


  public replyMessage(mailSendbox: MailSendbox): Observable<MailSendbox> {
    return <Observable<MailSendbox>>this.httpClient.post(`${AppSettings.API_ENDPOINT}${AppSettings.MAIL_SENDBOX_KEY}`,
      mailSendbox, {headers: this.prepareHeaders()}).pipe(catchError(this.handleError.bind(this)));
  }

  public deleteEntity(idMailInbox: number): Observable<any> {
    return this.httpClient.delete(`${AppSettings.API_ENDPOINT}${AppSettings.MAIL_SENDBOX_KEY}/${idMailInbox}`, this.getHeaders())
      .pipe(catchError(this.handleError.bind(this)));
  }
}
