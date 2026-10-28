import { Service } from '@angular/core';
import { AuthServiceWithLogout } from '../../login/service/base.auth.service.with.logout';
import { GTNetConfig } from '../model/gtnet';
import { ServiceEntityUpdate } from '../../edit/service.entity.update';
import { Observable } from 'rxjs/internal/Observable';
import { BaseSettings } from '../../base.settings';

/**
 * Service for managing GTNetConfig entities (connection-level configuration).
 * Only update operations are supported - entities are created by the system during handshake.
 */
@Service()
export class GTNetConfigService extends AuthServiceWithLogout<GTNetConfig> implements ServiceEntityUpdate<GTNetConfig> {
  update(entity: GTNetConfig): Observable<GTNetConfig> {
    return this.updateEntity(entity, entity.idGtNet, BaseSettings.GT_NET_CONFIG_KEY);
  }
}
