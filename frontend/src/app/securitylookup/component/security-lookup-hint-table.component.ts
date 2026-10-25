import { Component, computed, effect, Injector, input, Input, untracked } from '@angular/core';
import { TranslateModule, TranslateService } from '@ngx-translate/core';
import { FilterService } from '@openng/optimus-ui/api';

import { TableConfigBase } from '../../lib/datashowbase/table.config.base';
import { ConfigurableTableComponent } from '../../lib/datashowbase/configurable-table.component';
import { TranslateValue } from '../../lib/datashowbase/column.config';
import { DataType } from '../../lib/dynamic-form/models/data.type';
import { GlobalparameterService } from '../../lib/services/globalparameter.service';
import { UserSettingsService } from '../../lib/services/user.settings.service';
import { AppHelper } from '../../lib/helper/app.helper';
import { SecurityLookupHint } from '../model/security-lookup';
import { hintParams, LookupHintRow } from '../model/security-lookup-hint';

/**
 * Hints of the security lookup dialog: what the lookup could not resolve, why, and what follows for the security form,
 * e.g. a first trading day no provider delivers or an instrument the providers found only on another exchange. The
 * dialog shows one table for the query as a whole and one for the selected listing. The backend decides which hints
 * exist; this table only translates and shows them.
 */
@Component({
  selector: 'security-lookup-hint-table',
  standalone: true,
  imports: [ConfigurableTableComponent, TranslateModule],
  template: `
    @if (rows().length > 0) {
      <configurable-table
        [data]="rows()"
        [fields]="fields"
        [fieldsRevision]="fieldsRevision()"
        [customSortFn]="customSorter"
        [multiSortMeta]="multiSortMeta"
        [valueGetterFn]="valueGetter"
        [contextMenuEnabled]="false"
        [scrollable]="false"
        [baseLocale]="baseLocale">
        <h5 caption>{{ titleKey | translate }}</h5>
      </configurable-table>
    }
  `
})
export class SecurityLookupHintTableComponent extends TableConfigBase {
  /** Translation key of the table title */
  @Input() titleKey: string;

  /** Hints to show; the table is hidden when there is none */
  readonly hints = input<SecurityLookupHint[]>([]);

  /** Display rows derived from the current hints, without a second mutable copy. */
  readonly rows = computed(() => (this.hints() ?? []).map((hint) => this.toRow(hint)));

  /**
   * Creates the table columns.
   *
   * @param filterService - Optimus UI filter service
   * @param usersettingsService - Persists table settings
   * @param translateService - Translation of headers, severities, field labels and hint texts
   * @param gps - Locale and formatting settings
   * @param injector - Injector of the base class
   */
  constructor(
    filterService: FilterService,
    usersettingsService: UserSettingsService,
    translateService: TranslateService,
    gps: GlobalparameterService,
    injector: Injector
  ) {
    super(filterService, usersettingsService, translateService, gps, injector);
    this.addColumnFeqH(DataType.String, 'severity', true, false, {
      width: 70,
      translateValues: TranslateValue.NORMAL
    });
    this.addColumn(DataType.String, 'fieldKey', 'LOOKUP_HINT_FIELD', true, false, {
      width: 130,
      translateValues: TranslateValue.NORMAL
    });
    this.addColumn(DataType.String, 'text', 'LOOKUP_HINT_TEXT', true, false, { width: 600 });
    this.prepareTableAndTranslate();
    effect(() => {
      const rows = this.rows();
      untracked(() => this.createTranslatedValueStore(rows));
    });
  }

  /** Translates the text of a hint and derives the translation key of its field label from the field name. */
  private toRow(hint: SecurityLookupHint): LookupHintRow {
    const params = hintParams(
      hint.args,
      (key) => this.translateService.instant(key),
      (isoDate) => AppHelper.getDateByFormat(this.gps, isoDate)
    );
    return {
      severity: hint.severity,
      fieldKey: hint.field ? AppHelper.toUpperCaseWithUnderscore(hint.field) : null,
      text: this.translateService.instant(hint.messageKey, params)
    };
  }
}
