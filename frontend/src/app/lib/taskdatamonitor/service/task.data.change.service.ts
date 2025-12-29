import {AuthServiceWithLogout} from '../../login/service/base.auth.service.with.logout';
import {TaskDataChange, TaskDataChangeFormConstraints} from '../types/task.data.change';
import {LoginService} from '../../login/service/log-in.service';
import {HttpClient, HttpParams} from '@angular/common/http';
import {MessageToastService} from '../../message/message.toast.service';
import {Observable} from 'rxjs/internal/Observable';
import {catchError} from 'rxjs/operators';
import {Injectable} from '@angular/core';
import {ServiceEntityUpdate} from '../../edit/service.entity.update';
import {DeleteService} from '../../datashowbase/delete.service';
import {BaseSettings} from '../../base.settings';

@Injectable()
export class TaskDataChangeService extends AuthServiceWithLogout<TaskDataChange> implements ServiceEntityUpdate<TaskDataChange>,
  DeleteService {
  constructor(loginService: LoginService, httpClient: HttpClient, messageToastService: MessageToastService) {
    super(loginService, httpClient, messageToastService);
  }

  /**
   * Retrieves all task data changes, optionally filtered by task IDs.
   * @param idTasks Optional array of task IDs to filter by. If null/undefined, all tasks are returned.
   * @returns Observable of TaskDataChange array
   */
  getAllTaskDataChange(idTasks?: number[]): Observable<TaskDataChange[]> {
    let params = new HttpParams();
    if (idTasks && idTasks.length > 0) {
      params = params.set('idTasks', idTasks.join(','));
    }
    return <Observable<TaskDataChange[]>>this.httpClient.get(`${BaseSettings.API_ENDPOINT}${BaseSettings.TASK_DATA_CHANGE_KEY}`,
      {...this.getHeaders(), params}).pipe(catchError(this.handleError.bind(this)));
  }

  getFormConstraints(): Observable<TaskDataChangeFormConstraints> {
    return <Observable<TaskDataChangeFormConstraints>>this.httpClient.get(
      `${BaseSettings.API_ENDPOINT}${BaseSettings.TASK_DATA_CHANGE_KEY}/taskdatachangeconstraints`,
      this.getHeaders()).pipe(catchError(this.handleError.bind(this)));
  }

  update(taskDataChange: TaskDataChange): Observable<TaskDataChange> {
    return this.updateEntity(taskDataChange, taskDataChange.idTaskDataChange, BaseSettings.TASK_DATA_CHANGE_KEY);
  }

  deleteEntity(idTaskDataChange: number): Observable<any> {
    return this.httpClient.delete(`${BaseSettings.API_ENDPOINT}${BaseSettings.TASK_DATA_CHANGE_KEY}/${idTaskDataChange}`,
      this.getHeaders()).pipe(catchError(this.handleError.bind(this)));
  }

  interruptingRunningJob(idTaskDataChange: number): Observable<boolean> {
    return this.httpClient.patch(`${BaseSettings.API_ENDPOINT}${BaseSettings.TASK_DATA_CHANGE_KEY}/interruptingrunningjob`
      + `/${idTaskDataChange}`, null, this.getHeaders()).pipe(catchError(this.handleError.bind(this)));
  }
}
