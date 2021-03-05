import {Observable} from 'rxjs';

export interface DeleteService {
  deleteEntity(id: number): Observable<any>;
}
