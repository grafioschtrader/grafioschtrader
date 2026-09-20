import { Component, Input, OnChanges } from '@angular/core';
import { TranslateService } from '@ngx-translate/core';
import { FilterService } from '@openng/optimus-ui/api';
import { TableConfigBase } from '../datashowbase/table.config.base';
import { ConfigurableTableComponent } from '../datashowbase/configurable-table.component';
import { UserSettingsService } from '../services/user.settings.service';
import { GlobalparameterService } from '../services/globalparameter.service';
import { DataType } from '../dynamic-form/models/data.type';
import { DashboardRow } from './dashboard.types';
import { TranslateValue } from '../datashowbase/column.config';

/** Compact projection table; processing remains on the destination screen. */
@Component({
  selector: 'dashboard-table',
  standalone: true,
  imports: [ConfigurableTableComponent],
  template: `<configurable-table
    [data]="rows"
    [fields]="fields"
    dataKey="id"
    [valueGetterFn]="getValueByPath.bind(this)"></configurable-table>`
})
export class DashboardTableComponent extends TableConfigBase implements OnChanges {
  @Input() rows: DashboardRow[] = [];
  @Input() type = '';
  constructor(
    filter: FilterService,
    settings: UserSettingsService,
    translate: TranslateService,
    gps: GlobalparameterService
  ) {
    super(filter, settings, translate, gps);
  }
  ngOnChanges(): void {
    this.removeAllColumns();
    if (this.type !== 'PROPOSE_CHANGE_OPEN') this.addColumnFeqH(DataType.String, 'nickname');
    if (this.type === 'UNREAD_MAIL') this.addColumnFeqH(DataType.String, 'subject');
    else {
      this.addColumn(DataType.String, 'entity', 'ENTITY', true, false, { translateValues: TranslateValue.UPPER_CASE });
      if (this.type === 'USER_LIMIT_REQUESTS') {
        this.addColumn(DataType.NumericInteger, 'dayLimit', 'DASHBOARD_REQUESTED_LIMIT');
        this.addColumn(DataType.DateString, 'validUntil', 'VALID_UNTIL');
      }
      this.addColumnFeqH(DataType.String, 'noteRequest');
    }
    this.addColumn(DataType.DateTimeString, 'creationTime', 'CREATION_TIME');
    this.prepareTableAndTranslate();
  }
}
