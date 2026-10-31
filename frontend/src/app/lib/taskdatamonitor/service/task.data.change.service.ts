import { AuthServiceWithLogout } from '../../login/service/base.auth.service.with.logout';
import { TaskDataChange, type TaskDataChangeFormConstraints } from '../types/task.data.change';
import { HttpParams } from '@angular/common/http';
import { Observable } from 'rxjs/internal/Observable';
import { catchError } from 'rxjs/operators';
import { Service } from '@angular/core';
import { ServiceEntityUpdate } from '../../edit/service.entity.update';
import { DeleteService } from '../../datashowbase/delete.service';
import { BaseSettings } from '../../base.settings';

@Service()
export class TaskDataChangeService
  extends AuthServiceWithLogout<TaskDataChange>
  implements ServiceEntityUpdate<TaskDataChange>, DeleteService
{
  /**
   * Retrieves all task data changes, optionally filtered by task IDs.
   * @param idTasks Optional array of task IDs to filter by. If null/undefined, all tasks are returned.
   * @returns Observable of TaskDataChange array
   */
  getAllTaskDataChange(idTasks?: number[] | null): Observable<TaskDataChange[]> {
    let params = new HttpParams();
    if (idTasks && idTasks.length > 0) {
      params = params.set('idTasks', idTasks.join(','));
    }
    return this.httpClient
      .get<TaskDataChange[]>(`${BaseSettings.API_ENDPOINT}${BaseSettings.TASK_DATA_CHANGE_KEY}`, {
        ...this.getHeaders(),
        params
      })
      .pipe(catchError(this.handleError.bind(this)));
  }

  getFormConstraints(): Observable<TaskDataChangeFormConstraints> {
    return this.httpClient
      .get<TaskDataChangeFormConstraints>(
        `${BaseSettings.API_ENDPOINT}${BaseSettings.TASK_DATA_CHANGE_KEY}/taskdatachangeconstraints`,
        this.getHeaders()
      )
      .pipe(catchError(this.handleError.bind(this)));
  }

  update(taskDataChange: TaskDataChange): Observable<TaskDataChange> {
    return this.updateEntity(taskDataChange, taskDataChange.idTaskDataChange, BaseSettings.TASK_DATA_CHANGE_KEY);
  }

  deleteEntity(idTaskDataChange: number): Observable<any> {
    return this.httpClient
      .delete(`${BaseSettings.API_ENDPOINT}${BaseSettings.TASK_DATA_CHANGE_KEY}/${idTaskDataChange}`, this.getHeaders())
      .pipe(catchError(this.handleError.bind(this)));
  }

  interruptingRunningJob(idTaskDataChange: number): Observable<boolean> {
    return this.httpClient
      .patch<boolean>(
        `${BaseSettings.API_ENDPOINT}${BaseSettings.TASK_DATA_CHANGE_KEY}/interruptingrunningjob` +
          `/${idTaskDataChange}`,
        null,
        this.getHeaders()
      )
      .pipe(catchError(this.handleError.bind(this)));
  }
}
