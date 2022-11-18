import {Injectable} from '@angular/core';
import {AuthServiceWithLogout} from '../../shared/login/service/base.auth.service.with.logout';
import {GTNet, GTNetWithMessages} from '../model/gtnet';
import {ServiceEntityUpdate} from '../../shared/edit/service.entity.update';
import {Observable} from 'rxjs/internal/Observable';
import {AppSettings} from '../../shared/app.settings';
import {catchError} from 'rxjs/operators';
import {LoginService} from '../../shared/login/service/log-in.service';
import {HttpClient} from '@angular/common/http';
import {MessageToastService} from '../../shared/message/message.toast.service';
import {ApplicationInfo} from '../../shared/service/actuator.service';

@Injectable()
export class GTNetService extends AuthServiceWithLogout<GTNet> implements ServiceEntityUpdate<GTNet> {

  constructor(loginService: LoginService, httpClient: HttpClient, messageToastService: MessageToastService) {
    super(loginService, httpClient, messageToastService);
  }

  getAllGTNetsWithMessages(): Observable<GTNetWithMessages> {
    return <Observable<GTNetWithMessages>>this.httpClient.get(`${AppSettings.API_ENDPOINT}${AppSettings.GTNET_KEY}`
      + `/gtnetwithmessage`, this.getHeaders()).pipe(catchError(this.handleError.bind(this)));
  }

  checkRemoteDomainWithApplicationInfo(remoteDomainName: string): Observable<ApplicationInfo> {
    return <Observable<ApplicationInfo>>this.httpClient.get(`${AppSettings.API_ENDPOINT}${AppSettings.GTNET_KEY}`
      + `/remotetest/${remoteDomainName}`, this.getHeaders()).pipe(catchError(this.handleError.bind(this)));
  }

  update(gtNet: GTNet): Observable<GTNet> {
    return this.updateEntity(gtNet, gtNet.idGtNet, AppSettings.GTNET_KEY);
  }

  deleteEntity(idGtNet: number): Observable<any> {
    return this.httpClient.delete(`${AppSettings.API_ENDPOINT}${AppSettings.GTNET_KEY}/${idGtNet}`,
      this.getHeaders()).pipe(catchError(this.handleError.bind(this)));
  }
}
