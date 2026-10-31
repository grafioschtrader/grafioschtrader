import { Service } from '@angular/core';
import { AuthServiceWithLogout } from '../../login/service/base.auth.service.with.logout';
import { DeleteService } from '../../datashowbase/delete.service';
import { ServiceEntityUpdate } from '../../edit/service.entity.update';
import { ProposeUserTask } from '../../entities/propose.user.task';
import { Observable } from 'rxjs';
import { catchError } from 'rxjs/operators';
import { FieldDescriptorInputAndShow } from '../../dynamicfield/field.descriptor.input.and.show';
import { UserTaskType } from '../../types/user.task.type';
import type { StringResponse } from '../../types/string.response';
import { BaseSettings } from '../../base.settings';

@Service()
export class ProposeUserTaskService
  extends AuthServiceWithLogout<ProposeUserTask>
  implements DeleteService, ServiceEntityUpdate<ProposeUserTask>
{
  public rejectUserTask(idProposeRequest: number, rejectNote: string): Observable<StringResponse> {
    return this.httpClient
      .post<StringResponse>(
        `${BaseSettings.API_ENDPOINT}${BaseSettings.PROPOSE_USER_TASK_KEY}/reject/${idProposeRequest}`,
        rejectNote,
        this.getHeaders()
      )
      .pipe(catchError(this.handleError.bind(this)));
  }

  public getFormDefinitionsByUserTaskType(userTaskType: UserTaskType): Observable<FieldDescriptorInputAndShow[]> {
    return this.httpClient
      .get<FieldDescriptorInputAndShow[]>(
        `${BaseSettings.API_ENDPOINT}` + `${BaseSettings.PROPOSE_USER_TASK_KEY}/form/${userTaskType}`,
        this.getHeaders()
      )
      .pipe(catchError(this.handleError.bind(this)));
  }

  public update(userTask: ProposeUserTask): Observable<ProposeUserTask> {
    return this.updateEntity(userTask, userTask.idProposeRequest, BaseSettings.PROPOSE_USER_TASK_KEY);
  }

  public deleteEntity(idProposeRequest: number): Observable<any> {
    return this.httpClient
      .delete(
        `${BaseSettings.API_ENDPOINT}${BaseSettings.PROPOSE_USER_TASK_KEY}/${idProposeRequest}`,
        this.getHeaders()
      )
      .pipe(catchError(this.handleError.bind(this)));
  }
}
