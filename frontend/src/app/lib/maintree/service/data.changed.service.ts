import {ProcessedActionData} from '../../types/processed.action.data';
import {Subject} from 'rxjs';
import {Injectable} from '@angular/core';

@Injectable()
export class DataChangedService {

  private dataChanged = new Subject<ProcessedActionData>();
  dateChanged$ = this.dataChanged.asObservable();

  dataHasChanged(processedActionData: ProcessedActionData) {
    this.dataChanged.next(processedActionData);
  }
}
