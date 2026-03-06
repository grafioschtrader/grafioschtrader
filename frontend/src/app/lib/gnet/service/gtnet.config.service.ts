import {Injectable} from '@angular/core';
import {AuthServiceWithLogout} from '../../login/service/base.auth.service.with.logout';
import {GTNetConfig} from '../model/gtnet';
import {ServiceEntityUpdate} from '../../edit/service.entity.update';
import {Observable} from 'rxjs/internal/Observable';
import {LoginService} from '../../login/service/log-in.service';
import {HttpClient} from '@angular/common/http';
import {MessageToastService} from '../../message/message.toast.service';
import {BaseSettings} from '../../base.settings';

/**
 * Service for managing GTNetConfig entities (connection-level configuration).
 * Only update operations are supported - entities are created by the system during handshake.
 */
@Injectable()
export class GTNetConfigService extends AuthServiceWithLogout<GTNetConfig> implements ServiceEntityUpdate<GTNetConfig> {

  constructor(loginService: LoginService, httpClient: HttpClient, messageToastService: MessageToastService) {
    super(loginService, httpClient, messageToastService);
  }

  update(entity: GTNetConfig): Observable<GTNetConfig> {
    return this.updateEntity(entity, entity.idGtNet, BaseSettings.GT_NET_CONFIG_KEY);
  }
}
