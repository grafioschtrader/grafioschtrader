import { Service } from '@angular/core';
import { Observable } from 'rxjs';
import { AppSettings } from '../../shared/app.settings';
import { DeleteService } from '../../lib/datashowbase/delete.service';
import { AuthServiceWithLogout } from '../../lib/login/service/base.auth.service.with.logout';
import { GenericConnectorDef } from '../../entities/generic.connector.def';
import { GenericConnectorTestRequest, GenericConnectorTestResult } from '../model/generic-connector-test.model';
import { ServiceEntityUpdate } from '../../lib/edit/service.entity.update';
import { catchError } from 'rxjs/operators';
import { BaseSettings } from '../../lib/base.settings';

@Service()
export class GenericConnectorDefService
  extends AuthServiceWithLogout<GenericConnectorDef>
  implements ServiceEntityUpdate<GenericConnectorDef>, DeleteService
{
  public getAllGenericConnectors(): Observable<GenericConnectorDef[]> {
    return <Observable<GenericConnectorDef[]>>(
      this.httpClient
        .get(`${BaseSettings.API_ENDPOINT}${AppSettings.GENERIC_CONNECTOR_KEY}`, this.getHeaders())
        .pipe(catchError(this.handleError.bind(this)))
    );
  }

  public deleteEntity(id: number): Observable<any> {
    return this.httpClient
      .delete(`${BaseSettings.API_ENDPOINT}${AppSettings.GENERIC_CONNECTOR_KEY}/${id}`, this.getHeaders())
      .pipe(catchError(this.handleError.bind(this)));
  }

  update(entity: GenericConnectorDef): Observable<GenericConnectorDef> {
    return this.updateEntity(entity, entity.idGenericConnector, AppSettings.GENERIC_CONNECTOR_KEY);
  }

  public activateConnector(id: number): Observable<GenericConnectorDef> {
    return <Observable<GenericConnectorDef>>(
      this.httpClient
        .post(
          `${BaseSettings.API_ENDPOINT}${AppSettings.GENERIC_CONNECTOR_KEY}/activate/${id}`,
          null,
          this.getHeaders()
        )
        .pipe(catchError(this.handleError.bind(this)))
    );
  }

  public deactivateConnector(id: number): Observable<GenericConnectorDef> {
    return <Observable<GenericConnectorDef>>(
      this.httpClient
        .post(
          `${BaseSettings.API_ENDPOINT}${AppSettings.GENERIC_CONNECTOR_KEY}/deactivate/${id}`,
          null,
          this.getHeaders()
        )
        .pipe(catchError(this.handleError.bind(this)))
    );
  }

  public reloadConnectors(): Observable<void> {
    return <Observable<void>>(
      this.httpClient
        .post(`${BaseSettings.API_ENDPOINT}${AppSettings.GENERIC_CONNECTOR_KEY}/reload`, null, this.getHeaders())
        .pipe(catchError(this.handleError.bind(this)))
    );
  }

  public testEndpoint(request: GenericConnectorTestRequest): Observable<GenericConnectorTestResult> {
    return <Observable<GenericConnectorTestResult>>(
      this.httpClient
        .post(`${BaseSettings.API_ENDPOINT}${AppSettings.GENERIC_CONNECTOR_KEY}/test`, request, this.getHeaders())
        .pipe(catchError(this.handleError.bind(this)))
    );
  }
}
