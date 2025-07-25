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
import {ClassDescriptorInputAndShow} from '../../shared/dynamicfield/field.descriptor.input.and.show';
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

  deleteEntity(gtNetMessage: number): Observable<any> {
    return this.httpClient.delete(`${BaseSettings.API_ENDPOINT}${AppSettings.GT_NET_MESSAGE_KEY}/${gtNetMessage}`,
      this.getHeaders()).pipe(catchError(this.handleError.bind(this)));
  }
}
