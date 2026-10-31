import { Service } from '@angular/core';
import { Observable } from 'rxjs/internal/Observable';
import { ProposeChangeEntity } from '../../entities/propose.change.entity';
import { catchError } from 'rxjs/operators';
import { AuthServiceWithLogout } from '../../login/service/base.auth.service.with.logout';
import type { ProposeChangeEntityWithEntity } from '../model/propose.change.entity.whit.entity';
import { DeleteService } from '../../datashowbase/delete.service';
import { BaseSettings } from '../../base.settings';

@Service()
export class ProposeChangeEntityService extends AuthServiceWithLogout<ProposeChangeEntity> implements DeleteService {
  getProposeChangeEntityListByCreatedBy(): Observable<ProposeChangeEntity[]> {
    return this.httpClient
      .get<ProposeChangeEntity[]>(
        `${BaseSettings.API_ENDPOINT}${BaseSettings.PROPOSE_CHANGE_ENTITY_KEY}`,
        this.getHeaders()
      )
      .pipe(catchError(this.handleError.bind(this)));
  }

  getProposeChangeEntityWithEntity(): Observable<ProposeChangeEntityWithEntity[]> {
    return this.httpClient
      .get<ProposeChangeEntityWithEntity[]>(
        `${BaseSettings.API_ENDPOINT}${BaseSettings.PROPOSE_CHANGE_ENTITY_KEY}/withentity`,
        this.getHeaders()
      )
      .pipe(catchError(this.handleError.bind(this)));
  }

  public update(proposeChangeEntity: ProposeChangeEntity): Observable<ProposeChangeEntity> {
    return this.updateEntity(
      proposeChangeEntity,
      proposeChangeEntity.idProposeRequest,
      BaseSettings.PROPOSE_CHANGE_ENTITY_KEY
    );
  }

  public deleteEntity(idProposeRequest: number): Observable<any> {
    return this.httpClient
      .delete(
        `${BaseSettings.API_ENDPOINT}${BaseSettings.PROPOSE_CHANGE_ENTITY_KEY}/${idProposeRequest}`,
        this.getHeaders()
      )
      .pipe(catchError(this.handleError.bind(this)));
  }
}
