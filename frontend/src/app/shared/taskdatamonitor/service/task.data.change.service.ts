import {AuthServiceWithLogout} from '../../login/service/base.auth.service.with.logout';
import {TaskDataChange, TaskDataChangeFormConstraints} from '../../../entities/task.data.change';
import {LoginService} from '../../login/service/log-in.service';
import {HttpClient} from '@angular/common/http';
import {MessageToastService} from '../../message/message.toast.service';
import {Observable} from 'rxjs/internal/Observable';
import {AppSettings} from '../../app.settings';
import {catchError} from 'rxjs/operators';
import {Injectable} from '@angular/core';
import {ServiceEntityUpdate} from '../../edit/service.entity.update';
import {DeleteService} from '../../datashowbase/delete.service';

@Injectable()
export class TaskDataChangeService extends AuthServiceWithLogout<TaskDataChange> implements ServiceEntityUpdate<TaskDataChange>,
  DeleteService {
  constructor(loginService: LoginService, httpClient: HttpClient, messageToastService: MessageToastService) {
    super(loginService, httpClient, messageToastService);
  }

  getAllTaskDataChange(): Observable<TaskDataChange[]> {
    return <Observable<TaskDataChange[]>>this.httpClient.get(`${AppSettings.API_ENDPOINT}${AppSettings.TASK_DATA_CHANGE_KEY}`,
      this.getHeaders()).pipe(catchError(this.handleError.bind(this)));
  }

  getFormConstraints(): Observable<TaskDataChangeFormConstraints> {
    return <Observable<TaskDataChangeFormConstraints>>this.httpClient.get(
      `${AppSettings.API_ENDPOINT}${AppSettings.TASK_DATA_CHANGE_KEY}/taskdatachangeconstraints`,
      this.getHeaders()).pipe(catchError(this.handleError.bind(this)));
  }

  update(taskDataChange: TaskDataChange): Observable<TaskDataChange> {
    return this.updateEntity(taskDataChange, taskDataChange.idTaskDataChange, AppSettings.TASK_DATA_CHANGE_KEY);
  }

  deleteEntity(idTaskDataChange: number): Observable<any> {
    return this.httpClient.delete(`${AppSettings.API_ENDPOINT}${AppSettings.TASK_DATA_CHANGE_KEY}/${idTaskDataChange}`,
      this.getHeaders()).pipe(catchError(this.handleError.bind(this)));
  }

  interruptingRunningJob(idTaskDataChange: number): Observable<boolean> {
    return this.httpClient.patch(`${AppSettings.API_ENDPOINT}${AppSettings.TASK_DATA_CHANGE_KEY}/interruptingrunningjob`
      + `/${idTaskDataChange}`, null, this.getHeaders()).pipe(catchError(this.handleError.bind(this)));
  }
}
