import { Component, Injector, input, effect, untracked } from '@angular/core';

import { TranslateModule, TranslateService } from '@ngx-translate/core';
import { FilterService } from '@openng/optimus-ui/api';
import { TranslateValue } from '../../datashowbase/column.config';
import { ConfigurableTableComponent } from '../../datashowbase/configurable-table.component';
import { TableConfigBase } from '../../datashowbase/table.config.base';
import { DataType } from '../../dynamic-form/models/data.type';
import { GlobalparameterService } from '../../services/globalparameter.service';
import { UserSettingsService } from '../../services/user.settings.service';
import { GTNetMessageAttemptView } from '../model/gtnet-message-attempt';

/** Read-only administrator table of the per-target outcomes of outgoing GTNet background messages. */
@Component({
  selector: 'gtnet-message-attempt-table',
  imports: [TranslateModule, ConfigurableTableComponent],
  template: `
    <configurable-table
      [data]="messageAttempts()"
      [fields]="fields"
      [fieldsRevision]="fieldsRevision()"
      [dataKey]="'idGtNetMessageAttempt'"
      [containerClass]="{ 'data-container-full': true, 'nested-table': true }"
      [valueGetterFn]="valueGetter"
      [customSortFn]="customSorter"
      [multiSortMeta]="multiSortMeta"
      [baseLocale]="baseLocale" />
  `
})
export class GTNetMessageAttemptTableComponent extends TableConfigBase {
  readonly messageAttempts = input<GTNetMessageAttemptView[]>([]);

  constructor(
    filterService: FilterService,
    translateService: TranslateService,
    gps: GlobalparameterService,
    usersettingsService: UserSettingsService,
    injector: Injector
  ) {
    super(filterService, usersettingsService, translateService, gps, injector);
    this.addColumnFeqH(DataType.DateTimeNumeric, 'messageTimestamp', true, false);
    this.addColumnFeqH(DataType.String, 'messageCode', true, false, { translateValues: TranslateValue.NORMAL });
    this.addColumnFeqH(DataType.String, 'targetDomain', true, false);
    this.addColumnFeqH(DataType.String, 'attemptStatus', true, false, { translateValues: TranslateValue.NORMAL });
    this.addColumnFeqH(DataType.Numeric, 'tryCount', true, false);
    this.addColumnFeqH(DataType.DateTimeNumeric, 'lastAttemptTimestamp', true, false);
    this.addColumnFeqH(DataType.DateTimeNumeric, 'sendTimestamp', true, false);
    this.addColumnFeqH(DataType.String, 'lastError', true, false);
    this.multiSortMeta.push({ field: 'messageTimestamp', order: -1 });
    this.prepareTableAndTranslate();

    effect(() => {
      this.messageAttempts();
      untracked(() => {
        const rows = this.messageAttempts();
        if (rows) this.createTranslatedValueStore(rows);
      });
    });
  }
}
