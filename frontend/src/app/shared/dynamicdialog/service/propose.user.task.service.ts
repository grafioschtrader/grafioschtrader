import {Injectable} from '@angular/core';
import {AuthServiceWithLogout} from '../../../lib/login/service/base.auth.service.with.logout';
import {DeleteService} from '../../../lib/datashowbase/delete.service';
import {ServiceEntityUpdate} from '../../../lib/edit/service.entity.update';
import {ProposeUserTask} from '../../../lib/entities/propose.user.task';
import {Observable} from 'rxjs';
import {AppSettings} from '../../app.settings';
import {catchError} from 'rxjs/operators';
import {LoginService} from '../../../lib/login/service/log-in.service';
import {HttpClient} from '@angular/common/http';
import {MessageToastService} from '../../../lib/message/message.toast.service';
import {FieldDescriptorInputAndShow} from '../../../lib/dynamicfield/field.descriptor.input.and.show';
import {UserTaskType} from '../../../lib/types/user.task.type';
import {StringResponse} from '../../../entities/backend/string.response';
import {BaseSettings} from '../../../lib/base.settings';

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
      `${BaseSettings.API_ENDPOINT}${AppSettings.PROPOSE_USER_TASK_KEY}/reject/${idProposeRequest}`,
      rejectNote, this.getHeaders()).pipe(catchError(this.handleError.bind(this)));
  }

  public getFormDefinitionsByUserTaskType(userTaskType: UserTaskType): Observable<FieldDescriptorInputAndShow[]> {
    return <Observable<FieldDescriptorInputAndShow[]>>this.httpClient.get(`${BaseSettings.API_ENDPOINT}`
      + `${AppSettings.PROPOSE_USER_TASK_KEY}/form/${userTaskType}`, this.getHeaders()).pipe(catchError(this.handleError.bind(this)));
  }

  public update(userTask: ProposeUserTask): Observable<ProposeUserTask> {
    return this.updateEntity(userTask, userTask.idProposeRequest, AppSettings.PROPOSE_USER_TASK_KEY);
  }

  public deleteEntity(idProposeRequest: number): Observable<any> {
    return this.httpClient.delete(`${BaseSettings.API_ENDPOINT}${AppSettings.PROPOSE_USER_TASK_KEY}/${idProposeRequest}`, this.getHeaders())
      .pipe(catchError(this.handleError.bind(this)));
  }

}
