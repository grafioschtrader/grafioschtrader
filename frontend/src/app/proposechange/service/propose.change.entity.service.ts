import {Injectable} from '@angular/core';
import {Observable} from 'rxjs/internal/Observable';
import {ProposeChangeEntity} from '../../entities/propose.change.entity';
import {AppSettings} from '../../shared/app.settings';
import {catchError} from 'rxjs/operators';
import {AuthServiceWithLogout} from '../../shared/login/service/base.auth.service.with.logout';
import {MessageToastService} from '../../shared/message/message.toast.service';
import {HttpClient} from '@angular/common/http';
import {ProposeChangeEntityWithEntity} from '../../entities/proposechange/propose.change.entity.whit.entity';
import {DeleteService} from '../../shared/datashowbase/delete.service';
import {LoginService} from '../../shared/login/service/log-in.service';


@Injectable()
export class ProposeChangeEntityService extends AuthServiceWithLogout<ProposeChangeEntity> implements DeleteService {

  constructor(loginService: LoginService, httpClient: HttpClient, messageToastService: MessageToastService) {
    super(loginService, httpClient, messageToastService);
  }

  getProposeChangeEntityListByCreatedBy(): Observable<ProposeChangeEntity[]> {
    return <Observable<ProposeChangeEntity[]>>
      this.httpClient.get(`${AppSettings.API_ENDPOINT}${AppSettings.PROPOSE_CHANGE_ENTITY_KEY}`,
        this.getHeaders()).pipe(catchError(this.handleError.bind(this)));
  }

  getProposeChangeEntityWithEntity(): Observable<ProposeChangeEntityWithEntity[]> {
    return <Observable<ProposeChangeEntityWithEntity[]>>
      this.httpClient.get(`${AppSettings.API_ENDPOINT}${AppSettings.PROPOSE_CHANGE_ENTITY_KEY}/withentity`,
        this.getHeaders()).pipe(catchError(this.handleError.bind(this)));
  }

  public update(proposeChangeEntity: ProposeChangeEntity): Observable<ProposeChangeEntity> {
    return this.updateEntity(proposeChangeEntity, proposeChangeEntity.idProposeRequest, AppSettings.PROPOSE_CHANGE_ENTITY_KEY);
  }

  public deleteEntity(idProposeRequest: number): Observable<any> {
    return this.httpClient.delete(`${AppSettings.API_ENDPOINT}${AppSettings.PROPOSE_CHANGE_ENTITY_KEY}/${idProposeRequest}`,
      this.getHeaders()).pipe(catchError(this.handleError.bind(this)));
  }
}


