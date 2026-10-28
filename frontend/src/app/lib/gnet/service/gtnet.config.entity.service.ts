import { Service } from '@angular/core';
import { AuthServiceWithLogout } from '../../login/service/base.auth.service.with.logout';
import { GTNetConfigEntity } from '../model/gtnet';
import { ServiceEntityUpdate } from '../../edit/service.entity.update';
import { Observable } from 'rxjs/internal/Observable';
import { BaseSettings } from '../../base.settings';

/**
 * Service for managing GTNetConfigEntity.
 * Only update operations are supported - entities are created by the system
 * when exchange requests are accepted.
 */
@Service()
export class GTNetConfigEntityService
  extends AuthServiceWithLogout<GTNetConfigEntity>
  implements ServiceEntityUpdate<GTNetConfigEntity>
{
  /**
   * Updates a GTNetConfigEntity. Only useDetailLog and consumerUsage can be modified.
   */
  update(entity: GTNetConfigEntity): Observable<GTNetConfigEntity> {
    return this.updateEntity(entity, entity.idGtNetEntity, BaseSettings.GT_NET_CONFIG_ENTITY_KEY);
  }
}
