import { ChangeDetectionStrategy, Component, Injector, Input, OnChanges, SimpleChanges } from '@angular/core';
import { CommonModule } from '@angular/common';
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
  standalone: true,
  imports: [CommonModule, TranslateModule, ConfigurableTableComponent],
  template: `
    <configurable-table
      [data]="maintenanceWindows"
      [fields]="fields"
      [dataKey]="'idGtNetMaintenanceWindow'"
      [containerClass]="{ 'data-container-full': true, 'nested-table': true }"
      [valueGetterFn]="getValueByPath.bind(this)"
      [customSortFn]="customSort.bind(this)"
      [multiSortMeta]="multiSortMeta"
      [baseLocale]="baseLocale">
    </configurable-table>
  `,
  changeDetection: ChangeDetectionStrategy.Eager
})
export class GTNetMaintenanceWindowTableComponent extends TableConfigBase implements OnChanges {
  @Input() maintenanceWindows: GTNetMaintenanceWindow[] = [];

  constructor(
    filterService: FilterService,
    translateService: TranslateService,
    gps: GlobalparameterService,
    usersettingsService: UserSettingsService,
    injector: Injector
  ) {
    super(filterService, usersettingsService, translateService, gps, injector);
    this.addColumnFeqH(DataType.DateTimeNumeric, 'fromDateTime', true, false);
    this.addColumnFeqH(DataType.DateTimeNumeric, 'toDateTime', true, false);
    this.multiSortMeta.push({ field: 'fromDateTime', order: -1 });
    this.prepareTableAndTranslate();
  }

  ngOnChanges(changes: SimpleChanges): void {
    if (changes['maintenanceWindows'] && this.maintenanceWindows) {
      this.createTranslatedValueStore(this.maintenanceWindows);
    }
  }
}
