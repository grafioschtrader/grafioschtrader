import { HttpClient } from '@angular/common/http';
import { Injectable } from '@angular/core';
import { Observable } from 'rxjs';

import { BaseSettings } from '../app/lib/base.settings';
import { AuthServiceWithLogout } from '../app/lib/login/service/base.auth.service.with.logout';
import { LoginService } from '../app/lib/login/service/log-in.service';
import { MessageToastService } from '../app/lib/message/message.toast.service';
import { ServiceEntityUpdate } from '../app/lib/edit/service.entity.update';

/**
 * The tenant of this host. {@code TenantBase} in the backend carries only the name; a real application adds its own
 * columns, which is why neither the entity nor its service can live in the library.
 */
export class GrafioschTenant {
  idTenant: number = null;
  tenantName: string = null;
  createIdUser: number = null;
}

/**
 * Creates and updates the tenant through {@code /api/tenant}, which the backend serves from
 * {@code TenantBaseResource<T>} extended by this module's {@code TenantResource}.
 */
@Injectable()
export class GrafioschTenantService
  extends AuthServiceWithLogout<GrafioschTenant>
  implements ServiceEntityUpdate<GrafioschTenant>
{
  constructor(loginService: LoginService, httpClient: HttpClient, messageToastService: MessageToastService) {
    super(loginService, httpClient, messageToastService);
  }

  update(tenant: GrafioschTenant): Observable<GrafioschTenant> {
    return this.updateEntity(tenant, tenant.idTenant, BaseSettings.TENANT_KEY);
  }
}
