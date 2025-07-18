import {Observable} from 'rxjs';
import {ColumnConfig} from '../../../lib/datashowbase/column.config';

export interface ITaskExtendService {
  supportAdditionalToolTipData(): boolean;
  getAdditionalData(): Observable<any>;
  getToolTipByPath(dataobject: any, field: ColumnConfig, additionalData: any): any
}
