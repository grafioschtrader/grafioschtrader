import { Injectable } from '@angular/core';
import { HttpClient } from '@angular/common/http';
import { Observable, catchError } from 'rxjs';
import { AuthServiceWithLogout } from '../../lib/login/service/base.auth.service.with.logout';
import { LoginService } from '../../lib/login/service/log-in.service';
import { MessageToastService } from '../../lib/message/message.toast.service';
import { BaseSettings } from '../../lib/base.settings';
import { ServiceEntityUpdate } from '../../lib/edit/service.entity.update';
import { DeleteService } from '../../lib/datashowbase/delete.service';
import { TenantAIProvider, TenantAIProviderOptions, AIKeyTestResult } from '../model/tenant-ai-provider';

@Injectable({ providedIn: 'root' })
export class TenantAIProviderService
  extends AuthServiceWithLogout<TenantAIProvider>
  implements ServiceEntityUpdate<TenantAIProvider>, DeleteService
{
  private readonly endpoint = BaseSettings.API_ENDPOINT + 'tenantaiprovider';
  constructor(login: LoginService, http: HttpClient, messages: MessageToastService) {
    super(login, http, messages);
  }
  getAll(): Observable<TenantAIProvider[]> {
    return this.httpClient
      .get<TenantAIProvider[]>(this.endpoint, this.getHeaders())
      .pipe(catchError(this.handleError.bind(this)));
  }
  getProviders(): Observable<TenantAIProviderOptions> {
    return this.httpClient
      .get<TenantAIProviderOptions>(this.endpoint + '/providers', this.getHeaders())
      .pipe(catchError(this.handleError.bind(this)));
  }
  testKey(id: number): Observable<AIKeyTestResult> {
    return this.httpClient
      .post<AIKeyTestResult>(this.endpoint + '/' + id + '/test', {}, this.getHeaders())
      .pipe(catchError(this.handleError.bind(this)));
  }
  update(entity: TenantAIProvider): Observable<TenantAIProvider> {
    return this.updateEntity(entity, entity.idTenantAIProvider, 'tenantaiprovider');
  }
  deleteEntity(id: number): Observable<any> {
    return this.httpClient
      .delete(this.endpoint + '/' + id, this.getHeaders())
      .pipe(catchError(this.handleError.bind(this)));
  }
}
