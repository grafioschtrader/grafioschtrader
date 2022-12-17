import {Injectable} from '@angular/core';
import {AuthServiceWithLogout} from '../../shared/login/service/base.auth.service.with.logout';
import {DeleteService} from '../../shared/datashowbase/delete.service';
import {ServiceEntityUpdate} from '../../shared/edit/service.entity.update';
import {MailInbox} from '../model/mail.inbox';
import {LoginService} from '../../shared/login/service/log-in.service';
import {HttpClient} from '@angular/common/http';
import {MessageToastService} from '../../shared/message/message.toast.service';
import {Observable} from 'rxjs';
import {AppSettings} from '../../shared/app.settings';
import {catchError} from 'rxjs/operators';

@Injectable()
export class MailInboxService extends AuthServiceWithLogout<MailInbox> implements DeleteService, ServiceEntityUpdate<MailInbox> {
  constructor(loginService: LoginService, httpClient: HttpClient, messageToastService: MessageToastService) {
    super(loginService, httpClient, messageToastService);
  }

  public getAllInboxByUser(): Observable<MailInbox[]> {
    return <Observable<MailInbox[]>>this.httpClient.get(`${AppSettings.API_ENDPOINT}${AppSettings.MAIL_INBOX_KEY}`,
      this.getHeaders()).pipe(catchError(this.handleError.bind(this)));
  }

  public update(mailInbox: MailInbox): Observable<MailInbox> {
    return this.updateEntity(mailInbox, mailInbox.idMailInOut, AppSettings.MAIL_INBOX_KEY);
  }

  public markForRead(idMailInbox: number): Observable<MailInbox> {
    return this.httpClient.post(`${AppSettings.API_ENDPOINT}${AppSettings.MAIL_INBOX_KEY}/${idMailInbox}/markforread`,
      null, {headers: this.prepareHeaders()}).pipe(catchError(this.handleError.bind(this)));
  }

  public deleteEntity(idMailInbox: number): Observable<any> {
    return this.httpClient.delete(`${AppSettings.API_ENDPOINT}${AppSettings.MAIL_INBOX_KEY}/${idMailInbox}`, this.getHeaders())
      .pipe(catchError(this.handleError.bind(this)));
  }
}
