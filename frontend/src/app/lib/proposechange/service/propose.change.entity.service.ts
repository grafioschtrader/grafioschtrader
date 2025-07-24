import {Injectable} from '@angular/core';
import {Observable} from 'rxjs/internal/Observable';
import {ProposeChangeEntity} from '../../entities/propose.change.entity';
import {AppSettings} from '../../../shared/app.settings';
import {catchError} from 'rxjs/operators';
import {AuthServiceWithLogout} from '../../login/service/base.auth.service.with.logout';
import {MessageToastService} from '../../message/message.toast.service';
import {HttpClient} from '@angular/common/http';
import {ProposeChangeEntityWithEntity} from '../model/propose.change.entity.whit.entity';
import {DeleteService} from '../../datashowbase/delete.service';
import {LoginService} from '../../login/service/log-in.service';
import {BaseSettings} from '../../base.settings';


@Injectable()
export class ProposeChangeEntityService extends AuthServiceWithLogout<ProposeChangeEntity> implements DeleteService {

  constructor(loginService: LoginService, httpClient: HttpClient, messageToastService: MessageToastService) {
    super(loginService, httpClient, messageToastService);
  }

  getProposeChangeEntityListByCreatedBy(): Observable<ProposeChangeEntity[]> {
    return <Observable<ProposeChangeEntity[]>>
      this.httpClient.get(`${BaseSettings.API_ENDPOINT}${AppSettings.PROPOSE_CHANGE_ENTITY_KEY}`,
        this.getHeaders()).pipe(catchError(this.handleError.bind(this)));
  }

  getProposeChangeEntityWithEntity(): Observable<ProposeChangeEntityWithEntity[]> {
    return <Observable<ProposeChangeEntityWithEntity[]>>
      this.httpClient.get(`${BaseSettings.API_ENDPOINT}${AppSettings.PROPOSE_CHANGE_ENTITY_KEY}/withentity`,
        this.getHeaders()).pipe(catchError(this.handleError.bind(this)));
  }

  public update(proposeChangeEntity: ProposeChangeEntity): Observable<ProposeChangeEntity> {
    return this.updateEntity(proposeChangeEntity, proposeChangeEntity.idProposeRequest, AppSettings.PROPOSE_CHANGE_ENTITY_KEY);
  }

  public deleteEntity(idProposeRequest: number): Observable<any> {
    return this.httpClient.delete(`${BaseSettings.API_ENDPOINT}${AppSettings.PROPOSE_CHANGE_ENTITY_KEY}/${idProposeRequest}`,
      this.getHeaders()).pipe(catchError(this.handleError.bind(this)));
  }
}


