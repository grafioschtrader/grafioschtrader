import { Component, computed, effect, input, untracked } from '@angular/core';
import { DialogService } from '@openng/optimus-ui/dynamicdialog';
import { FxObservationGroup } from '../../entities/fx.observation';
import { TranslateValue } from '../../lib/datashowbase/column.config';
import { ConfigurableTableComponent } from '../../lib/datashowbase/configurable-table.component';
import { TableConfigBase } from '../../lib/datashowbase/table.config.base';
import { DataType } from '../../lib/dynamic-form/models/data.type';

/** Shared account/transfer statistics table; arithmetic and model coverage are supplied by the backend. */
@Component({
  selector: 'fx-observation-table',
  template: `
    <configurable-table
      [data]="rows()"
      [fields]="fields"
      [fieldsRevision]="fieldsRevision()"
      [valueGetterFn]="valueGetter"
      [customSortFn]="customSorter"
      [multiSortMeta]="multiSortMeta"
      [baseLocale]="baseLocale"
      [paginator]="true"
      [rows]="rowsPerPage ?? 20"
      (pageChange)="onPage($event)"
      customClass="datatable" />
  `,
  imports: [ConfigurableTableComponent],
  providers: [DialogService]
})
export class FxObservationTableComponent extends TableConfigBase {
  readonly groups = input<FxObservationGroup[]>([]);
  /** Display labels follow the current input without retaining a second mutable set of rows. */
  readonly rows = computed(() =>
    this.groups().map((group) => ({
      ...group,
      kindLabel: 'FX_KIND_' + group.kind,
      sampleHint: group.count < 5 ? 'FX_OBSERVATION_SMALL_SAMPLE' : ''
    }))
  );

  constructor() {
    super();
    this.rowsPerPage = 10;
    this.addColumn(DataType.String, 'currencyPair', 'CURRENCY_PAIR', true, true);
    this.addColumn(DataType.String, 'kindLabel', 'FX_CONVERSION_KIND', true, true, {
      translateValues: TranslateValue.NORMAL
    });
    this.addColumn(DataType.DateString, 'periodFrom', 'FX_OBSERVATION_PERIOD_FROM', true, true);
    this.addColumn(DataType.DateString, 'periodTo', 'FX_OBSERVATION_PERIOD_TO', true, true);
    for (const [field, label] of [
      ['count', 'FX_OBSERVATION_COUNT'],
      ['skippedNoClose', 'FX_OBSERVATION_NO_CLOSE'],
      ['skippedNoRate', 'FX_OBSERVATION_NO_RATE'],
      ['skippedOutlier', 'FX_OBSERVATION_OUTLIER'],
      ['modelledCount', 'FX_OBSERVATION_MODELLED_COUNT'],
      ['uncoveredCount', 'FX_OBSERVATION_UNCOVERED']
    ]) {
      this.addColumn(DataType.NumericShowZero, field, label, true, true, { maxFractionDigits: 0 });
    }
    for (const [field, label] of [
      ['mean', 'FX_OBSERVATION_MEAN'],
      ['median', 'FX_OBSERVATION_MEDIAN'],
      ['stdDev', 'FX_OBSERVATION_STD_DEV'],
      ['volumeWeightedMean', 'FX_OBSERVATION_WEIGHTED_MEAN'],
      ['modelledMean', 'FX_OBSERVATION_MODELLED_MEAN']
    ]) {
      this.addColumn(DataType.NumericShowZero, field, label, true, true, { maxFractionDigits: 2 });
    }
    for (const outcome of ['NO_SECTION', 'NO_PERIOD', 'NO_RULE', 'NO_TIER_RATE', 'INVALID']) {
      this.addColumn(DataType.NumericShowZero, 'outcomes.' + outcome, 'FX_OUTCOME_' + outcome, true, true, {
        maxFractionDigits: 0
      });
    }
    this.addColumn(DataType.String, 'sampleHint', 'FX_OBSERVATION_SAMPLE', true, false, {
      translateValues: TranslateValue.NORMAL
    });
    this.addColumn(DataType.String, 'error', 'ERROR', true, false);
    this.multiSortMeta.push({ field: 'currencyPair', order: 1 });
    this.prepareTableAndTranslate();
    effect(() => {
      const rows = this.rows();
      untracked(() => this.createTranslatedValueStoreAndFilterField(rows));
    });
  }
}
