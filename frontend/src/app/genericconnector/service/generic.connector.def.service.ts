import {Injectable} from '@angular/core';
import {Observable} from 'rxjs';
import {AppSettings} from '../../shared/app.settings';
import {MessageToastService} from '../../lib/message/message.toast.service';
import {DeleteService} from '../../lib/datashowbase/delete.service';
import {HttpClient} from '@angular/common/http';
import {AuthServiceWithLogout} from '../../lib/login/service/base.auth.service.with.logout';
import {GenericConnectorDef} from '../../entities/generic.connector.def';
import {ServiceEntityUpdate} from '../../lib/edit/service.entity.update';
import {catchError} from 'rxjs/operators';
import {LoginService} from '../../lib/login/service/log-in.service';
import {BaseSettings} from '../../lib/base.settings';

@Injectable()
export class GenericConnectorDefService extends AuthServiceWithLogout<GenericConnectorDef>
  implements ServiceEntityUpdate<GenericConnectorDef>, DeleteService {

  constructor(loginService: LoginService, httpClient: HttpClient, messageToastService: MessageToastService) {
    super(loginService, httpClient, messageToastService);
  }

  public getAllGenericConnectors(): Observable<GenericConnectorDef[]> {
    return <Observable<GenericConnectorDef[]>>this.httpClient.get(
      `${BaseSettings.API_ENDPOINT}${AppSettings.GENERIC_CONNECTOR_KEY}`,
      this.getHeaders()).pipe(catchError(this.handleError.bind(this)));
  }

  public deleteEntity(id: number): Observable<any> {
    return this.httpClient.delete(
      `${BaseSettings.API_ENDPOINT}${AppSettings.GENERIC_CONNECTOR_KEY}/${id}`,
      this.getHeaders()).pipe(catchError(this.handleError.bind(this)));
  }

  update(entity: GenericConnectorDef): Observable<GenericConnectorDef> {
    return this.updateEntity(entity, entity.idGenericConnector, AppSettings.GENERIC_CONNECTOR_KEY);
  }

  public activateConnector(id: number): Observable<GenericConnectorDef> {
    return <Observable<GenericConnectorDef>>this.httpClient.post(
      `${BaseSettings.API_ENDPOINT}${AppSettings.GENERIC_CONNECTOR_KEY}/activate/${id}`,
      null, this.getHeaders()).pipe(catchError(this.handleError.bind(this)));
  }

  public reloadConnectors(): Observable<void> {
    return <Observable<void>>this.httpClient.post(
      `${BaseSettings.API_ENDPOINT}${AppSettings.GENERIC_CONNECTOR_KEY}/reload`,
      null, this.getHeaders()).pipe(catchError(this.handleError.bind(this)));
  }
}
