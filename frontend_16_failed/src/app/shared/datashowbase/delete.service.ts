import {Observable} from 'rxjs';

export interface DeleteService {
  deleteEntity(id: number | string): Observable<any>;
}
