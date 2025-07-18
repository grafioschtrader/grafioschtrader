import {Injectable} from '@angular/core';
import {AuthServiceWithLogout} from '../../../shared/login/service/base.auth.service.with.logout';
import {MailSendForwardDefault, MailSettingForward} from '../model/mail.send.recv';
import {LoginService} from '../../../shared/login/service/log-in.service';
import {HttpClient} from '@angular/common/http';
import {MessageToastService} from '../../message/message.toast.service';
import {Observable} from 'rxjs';
import {AppSettings} from '../../../shared/app.settings';
import {catchError} from 'rxjs/operators';
import {ServiceEntityUpdate} from '../../edit/service.entity.update';

@Injectable()
export class MailSettingForwardService extends AuthServiceWithLogout<MailSettingForward> implements ServiceEntityUpdate<MailSettingForward> {

  constructor(loginService: LoginService, httpClient: HttpClient, messageToastService: MessageToastService) {
    super(loginService, httpClient, messageToastService);
  }

  public getMailSettingForwardByUser(): Observable<MailSettingForward[]> {
    return <Observable<MailSettingForward[]>>this.httpClient.get(`${AppSettings.API_ENDPOINT}${AppSettings.MAIL_SETTING_FORWARD_KEY}/user`,
      this.getHeaders()).pipe(catchError(this.handleError.bind(this)));
  }

  public getSendForwardDefault(): Observable<MailSendForwardDefault> {
    return <Observable<MailSendForwardDefault>>this.httpClient.get(`
    ${AppSettings.API_ENDPOINT}${AppSettings.MAIL_SETTING_FORWARD_KEY}/defaultforward`,
      this.getHeaders()).pipe(catchError(this.handleError.bind(this)));
  }

  public deleteEntity(idMailSettingForward: number): Observable<any> {
    return this.httpClient.delete(`${AppSettings.API_ENDPOINT}${AppSettings.MAIL_SETTING_FORWARD_KEY}/${idMailSettingForward}`,
      this.getHeaders()).pipe(catchError(this.handleError.bind(this)));
  }

  update(mailSettingForward: MailSettingForward): Observable<MailSettingForward> {
    return this.updateEntity(mailSettingForward, mailSettingForward.idMailSettingForward, AppSettings.MAIL_SETTING_FORWARD_KEY);
  }

}
