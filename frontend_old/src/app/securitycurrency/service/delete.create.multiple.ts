import {Observable} from 'rxjs';

export interface DeleteCreateMultiple<T> {
  deleteAndCreateMultiple(idSecuritycurrency: number, securitysplits: T[], noteRequest: string): Observable<T[]>;
}
