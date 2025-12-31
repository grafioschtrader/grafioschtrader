import {Injectable} from '@angular/core';
import {AuthServiceWithLogout} from '../../lib/login/service/base.auth.service.with.logout';
import {GTNetConfigEntity} from '../model/gtnet';
import {ServiceEntityUpdate} from '../../lib/edit/service.entity.update';
import {Observable} from 'rxjs/internal/Observable';
import {AppSettings} from '../../shared/app.settings';
import {catchError} from 'rxjs/operators';
import {LoginService} from '../../lib/login/service/log-in.service';
import {HttpClient} from '@angular/common/http';
import {MessageToastService} from '../../lib/message/message.toast.service';
import {BaseSettings} from '../../lib/base.settings';

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
    return this.updateEntity(entity, entity.idGtNetEntity, AppSettings.GT_NET_CONFIG_ENTITY_KEY);
  }
}
