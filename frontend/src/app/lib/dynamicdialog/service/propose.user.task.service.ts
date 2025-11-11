import {Injectable} from '@angular/core';
import {AuthServiceWithLogout} from '../../login/service/base.auth.service.with.logout';
import {DeleteService} from '../../datashowbase/delete.service';
import {ServiceEntityUpdate} from '../../edit/service.entity.update';
import {ProposeUserTask} from '../../entities/propose.user.task';
import {Observable} from 'rxjs';
import {catchError} from 'rxjs/operators';
import {LoginService} from '../../login/service/log-in.service';
import {HttpClient} from '@angular/common/http';
import {MessageToastService} from '../../message/message.toast.service';
import {FieldDescriptorInputAndShow} from '../../dynamicfield/field.descriptor.input.and.show';
import {UserTaskType} from '../../types/user.task.type';
import {StringResponse} from '../../types/string.response';
import {BaseSettings} from '../../base.settings';

@Injectable()
export class ProposeUserTaskService extends AuthServiceWithLogout<ProposeUserTask>
  implements DeleteService, ServiceEntityUpdate<ProposeUserTask> {

  constructor(loginService: LoginService,
    httpClient: HttpClient,
    messageToastService: MessageToastService) {
    super(loginService, httpClient, messageToastService);
  }

  public rejectUserTask(idProposeRequest: number, rejectNote: string): Observable<StringResponse> {
    return <Observable<StringResponse>>this.httpClient.post(
      `${BaseSettings.API_ENDPOINT}${BaseSettings.PROPOSE_USER_TASK_KEY}/reject/${idProposeRequest}`,
      rejectNote, this.getHeaders()).pipe(catchError(this.handleError.bind(this)));
  }

  public getFormDefinitionsByUserTaskType(userTaskType: UserTaskType): Observable<FieldDescriptorInputAndShow[]> {
    return <Observable<FieldDescriptorInputAndShow[]>>this.httpClient.get(`${BaseSettings.API_ENDPOINT}`
      + `${BaseSettings.PROPOSE_USER_TASK_KEY}/form/${userTaskType}`, this.getHeaders()).pipe(catchError(this.handleError.bind(this)));
  }

  public update(userTask: ProposeUserTask): Observable<ProposeUserTask> {
    return this.updateEntity(userTask, userTask.idProposeRequest, BaseSettings.PROPOSE_USER_TASK_KEY);
  }

  public deleteEntity(idProposeRequest: number): Observable<any> {
    return this.httpClient.delete(`${BaseSettings.API_ENDPOINT}${BaseSettings.PROPOSE_USER_TASK_KEY}/${idProposeRequest}`, this.getHeaders())
      .pipe(catchError(this.handleError.bind(this)));
  }

}
