import {Observable} from 'rxjs';

export interface ServiceEntityUpdate<T> {
  update(entity: T): Observable<T>;
}
