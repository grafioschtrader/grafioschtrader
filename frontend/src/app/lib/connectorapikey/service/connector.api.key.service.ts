import {AuthServiceWithLogout} from '../../login/service/base.auth.service.with.logout';
import {ConnectorApiKey, SubscriptionTypeReadableName} from '../types/connector.api.key';
import {ServiceEntityUpdate} from '../../edit/service.entity.update';
import {DeleteService} from '../../datashowbase/delete.service';
import {Injectable} from '@angular/core';
import {LoginService} from '../../login/service/log-in.service';
import {HttpClient} from '@angular/common/http';
import {MessageToastService} from '../../message/message.toast.service';
import {Observable} from 'rxjs';
import {catchError} from 'rxjs/operators';
import {BaseSettings} from '../../base.settings';

@Injectable()
export class ConnectorApiKeyService extends AuthServiceWithLogout<ConnectorApiKey>
  implements ServiceEntityUpdate<ConnectorApiKey>, DeleteService {

  constructor(loginService: LoginService, httpClient: HttpClient, messageToastService: MessageToastService) {
    super(loginService, httpClient, messageToastService);
  }

  public getAllConnectorApiKeys(): Observable<ConnectorApiKey[]> {
    return <Observable<ConnectorApiKey[]>>this.httpClient.get(`${BaseSettings.API_ENDPOINT}${BaseSettings.CONNECTOR_API_KEY_KEY}`,
      this.getHeaders()).pipe(catchError(this.handleError.bind(this)));
  }

  public getFeedSubscriptionType(): Observable<{ [id: string]: SubscriptionTypeReadableName }> {
    return <Observable<{ [id: string]: SubscriptionTypeReadableName }>>this.httpClient.get(
      `${BaseSettings.API_ENDPOINT}${BaseSettings.CONNECTOR_API_KEY_KEY}/subscriptiontypeconnector`,
      this.getHeaders()).pipe(catchError(this.handleError.bind(this)));
  }

  public deleteEntity(idProvider: string): Observable<any> {
    return this.httpClient.delete(`${BaseSettings.API_ENDPOINT}${BaseSettings.CONNECTOR_API_KEY_KEY}/${idProvider}`,
      this.getHeaders()).pipe(catchError(this.handleError.bind(this)));
  }

  update(connectorApiKey: ConnectorApiKey): Observable<ConnectorApiKey> {
    return this.updateEntity(connectorApiKey, connectorApiKey.idProvider, BaseSettings.CONNECTOR_API_KEY_KEY);
  }
}
