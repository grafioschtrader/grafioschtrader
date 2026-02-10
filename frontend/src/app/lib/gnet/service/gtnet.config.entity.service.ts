import {Injectable} from '@angular/core';
import {AuthServiceWithLogout} from '../../login/service/base.auth.service.with.logout';
import {GTNetConfigEntity} from '../model/gtnet';
import {ServiceEntityUpdate} from '../../edit/service.entity.update';
import {Observable} from 'rxjs/internal/Observable';
import {LoginService} from '../../login/service/log-in.service';
import {HttpClient} from '@angular/common/http';
import {MessageToastService} from '../../message/message.toast.service';
import {BaseSettings} from '../../base.settings';

/**
 * Service for managing GTNetConfigEntity.
 * Only update operations are supported - entities are created by the system
 * when exchange requests are accepted.
 */
@Injectable()
export class GTNetConfigEntityService extends AuthServiceWithLogout<GTNetConfigEntity>
  implements ServiceEntityUpdate<GTNetConfigEntity> {

  constructor(loginService: LoginService, httpClient: HttpClient, messageToastService: MessageToastService) {
    super(loginService, httpClient, messageToastService);
  }

  /**
   * Updates a GTNetConfigEntity. Only useDetailLog and consumerUsage can be modified.
   */
  update(entity: GTNetConfigEntity): Observable<GTNetConfigEntity> {
    return this.updateEntity(entity, entity.idGtNetEntity, BaseSettings.GT_NET_CONFIG_ENTITY_KEY);
  }
}
