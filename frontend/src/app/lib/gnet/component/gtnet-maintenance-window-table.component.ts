import { Component, Injector, input, effect, untracked } from '@angular/core';

import { TranslateModule, TranslateService } from '@ngx-translate/core';
import { FilterService } from '@openng/optimus-ui/api';
import { TableConfigBase } from '../../datashowbase/table.config.base';
import { ConfigurableTableComponent } from '../../datashowbase/configurable-table.component';
import { DataType } from '../../dynamic-form/models/data.type';
import { GlobalparameterService } from '../../services/globalparameter.service';
import { UserSettingsService } from '../../services/user.settings.service';
import { GTNetMaintenanceWindow } from '../model/gtnet';

/**
 * Table of the maintenance windows a remote GTNet instance has announced, shown in the maintenance panel of the
 * expanded row of {@code GTNetSetupTableComponent}.
 *
 * A remote may announce several windows, and past ones stay listed until their announcement is deleted, so the
 * default sort puts the most recent first.
 */
@Component({
  selector: 'gtnet-maintenance-window-table',
  imports: [TranslateModule, ConfigurableTableComponent],
  template: `
    <configurable-table
      [data]="maintenanceWindows()"
      [fields]="fields"
      [dataKey]="'idGtNetMaintenanceWindow'"
      [containerClass]="{ 'data-container-full': true, 'nested-table': true }"
      [fieldsRevision]="fieldsRevision()"
      [valueGetterFn]="valueGetter"
      [customSortFn]="customSorter"
      [multiSortMeta]="multiSortMeta"
      [baseLocale]="baseLocale" />
  `
})
export class GTNetMaintenanceWindowTableComponent extends TableConfigBase {
  readonly maintenanceWindows = input<GTNetMaintenanceWindow[]>([]);

  constructor(
    filterService: FilterService,
    translateService: TranslateService,
    gps: GlobalparameterService,
    usersettingsService: UserSettingsService,
    injector: Injector
  ) {
    super(filterService, usersettingsService, translateService, gps, injector);
    this.addColumnFeqH(DataType.DateTimeString, 'fromDateTime', true, false);
    this.addColumnFeqH(DataType.DateTimeString, 'toDateTime', true, false);
    this.multiSortMeta.push({ field: 'fromDateTime', order: -1 });
    this.prepareTableAndTranslate();

    effect(() => {
      this.maintenanceWindows();
      untracked(() => {
        const rows = this.maintenanceWindows();
        if (rows) this.createTranslatedValueStore(rows);
      });
    });
  }
}
