import { AuthServiceWithLogout } from '../../login/service/base.auth.service.with.logout';
import { ConnectorApiKey, SubscriptionTypeReadableName } from '../types/connector.api.key';
import { ServiceEntityUpdate } from '../../edit/service.entity.update';
import { DeleteService } from '../../datashowbase/delete.service';
import { Service } from '@angular/core';
import { Observable } from 'rxjs';
import { catchError } from 'rxjs/operators';
import { BaseSettings } from '../../base.settings';

@Service()
export class ConnectorApiKeyService
  extends AuthServiceWithLogout<ConnectorApiKey>
  implements ServiceEntityUpdate<ConnectorApiKey>, DeleteService
{
  public getAllConnectorApiKeys(): Observable<ConnectorApiKey[]> {
    return <Observable<ConnectorApiKey[]>>(
      this.httpClient
        .get(`${BaseSettings.API_ENDPOINT}${BaseSettings.CONNECTOR_API_KEY_KEY}`, this.getHeaders())
        .pipe(catchError(this.handleError.bind(this)))
    );
  }

  public getFeedSubscriptionType(): Observable<{ [id: string]: SubscriptionTypeReadableName }> {
    return <Observable<{ [id: string]: SubscriptionTypeReadableName }>>(
      this.httpClient
        .get(
          `${BaseSettings.API_ENDPOINT}${BaseSettings.CONNECTOR_API_KEY_KEY}/subscriptiontypeconnector`,
          this.getHeaders()
        )
        .pipe(catchError(this.handleError.bind(this)))
    );
  }

  public deleteEntity(idProvider: string): Observable<any> {
    return this.httpClient
      .delete(`${BaseSettings.API_ENDPOINT}${BaseSettings.CONNECTOR_API_KEY_KEY}/${idProvider}`, this.getHeaders())
      .pipe(catchError(this.handleError.bind(this)));
  }

  update(connectorApiKey: ConnectorApiKey): Observable<ConnectorApiKey> {
    return this.updateEntity(connectorApiKey, connectorApiKey.idProvider, BaseSettings.CONNECTOR_API_KEY_KEY);
  }
}
