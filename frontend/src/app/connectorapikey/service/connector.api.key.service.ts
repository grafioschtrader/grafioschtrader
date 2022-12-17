import {AuthServiceWithLogout} from '../../shared/login/service/base.auth.service.with.logout';
import {ConnectorApiKey, SubscriptionTypeReadableName} from '../../entities/connector.api.key';
import {ServiceEntityUpdate} from '../../shared/edit/service.entity.update';
import {DeleteService} from '../../shared/datashowbase/delete.service';
import {Injectable} from '@angular/core';
import {LoginService} from '../../shared/login/service/log-in.service';
import {HttpClient} from '@angular/common/http';
import {MessageToastService} from '../../shared/message/message.toast.service';
import {Observable} from 'rxjs';
import {AppSettings} from '../../shared/app.settings';
import {catchError} from 'rxjs/operators';

@Injectable()
export class ConnectorApiKeyService extends AuthServiceWithLogout<ConnectorApiKey>
  implements ServiceEntityUpdate<ConnectorApiKey>, DeleteService {

  constructor(loginService: LoginService, httpClient: HttpClient, messageToastService: MessageToastService) {
    super(loginService, httpClient, messageToastService);
  }

  public getAllConnectorApiKeys(): Observable<ConnectorApiKey[]> {
    return <Observable<ConnectorApiKey[]>>this.httpClient.get(`${AppSettings.API_ENDPOINT}${AppSettings.CONNECTOR_API_KEY_KEY}`,
      this.getHeaders()).pipe(catchError(this.handleError.bind(this)));
  }

  public getFeedSubscriptionType(): Observable<{ [id: string]: SubscriptionTypeReadableName }> {
    return <Observable<{ [id: string]: SubscriptionTypeReadableName }>>this.httpClient.get(
      `${AppSettings.API_ENDPOINT}${AppSettings.CONNECTOR_API_KEY_KEY}/subscriptiontypeconnector`,
      this.getHeaders()).pipe(catchError(this.handleError.bind(this)));
  }

  public deleteEntity(idProvider: string): Observable<any> {
    return this.httpClient.delete(`${AppSettings.API_ENDPOINT}${AppSettings.CONNECTOR_API_KEY_KEY}/${idProvider}`,
      this.getHeaders()).pipe(catchError(this.handleError.bind(this)));
  }

  update(connectorApiKey: ConnectorApiKey): Observable<ConnectorApiKey> {
    return this.updateEntity(connectorApiKey, connectorApiKey.idProvider, AppSettings.CONNECTOR_API_KEY_KEY);
  }
}
