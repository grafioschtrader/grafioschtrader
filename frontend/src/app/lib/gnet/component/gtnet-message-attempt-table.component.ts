import { ChangeDetectionStrategy, Component, Injector, Input, OnChanges, SimpleChanges } from '@angular/core';
import { CommonModule } from '@angular/common';
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
  standalone: true,
  imports: [CommonModule, TranslateModule, ConfigurableTableComponent],
  template: `
    <configurable-table
      [data]="messageAttempts"
      [fields]="fields"
      [dataKey]="'idGtNetMessageAttempt'"
      [containerClass]="{ 'data-container-full': true, 'nested-table': true }"
      [valueGetterFn]="getValueByPath.bind(this)"
      [customSortFn]="customSort.bind(this)"
      [multiSortMeta]="multiSortMeta"
      [baseLocale]="baseLocale">
    </configurable-table>
  `,
  changeDetection: ChangeDetectionStrategy.Eager
})
export class GTNetMessageAttemptTableComponent extends TableConfigBase implements OnChanges {
  @Input() messageAttempts: GTNetMessageAttemptView[] = [];

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
  }

  ngOnChanges(changes: SimpleChanges): void {
    if (changes['messageAttempts'] && this.messageAttempts) {
      this.createTranslatedValueStore(this.messageAttempts);
    }
  }
}
