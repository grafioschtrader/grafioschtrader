import { Component, output, input, linkedSignal, effect, untracked } from '@angular/core';
import { TranslateModule } from '@ngx-translate/core';
import { MessageModule } from '@openng/optimus-ui/message';

import { TableConfigBase } from '../../lib/datashowbase/table.config.base';
import { ConfigurableTableComponent } from '../../lib/datashowbase/configurable-table.component';
import { ColumnConfig, TranslateValue } from '../../lib/datashowbase/column.config';
import { DataType } from '../../lib/dynamic-form/models/data.type';
import { ConnectorAlternative } from '../model/security-lookup';
import { isUsableAlternative, LookupFeed } from '../model/security-lookup-selection';
import { Helper } from '../../lib/helper/helper';

/**
 * One of the four connector tables of the security lookup dialog (history, intraday, dividend, split). Each row is a
 * connector with the URL extension it derived and the outcome of the trial request, so the user sees whether the feed
 * actually delivers data before taking the connector over. A connector whose probe failed stays visible with its
 * reason, but cannot be selected.
 */
@Component({
  selector: 'security-lookup-connector-table',
  imports: [ConfigurableTableComponent, TranslateModule, MessageModule],
  template: `
    @let disabledMessageKeyValue = disabledMessageKey();

    <div [class.lookup-table-disabled]="disabled()">
      <configurable-table
        [data]="alternatives()"
        [fields]="fields"
        [fieldsRevision]="fieldsRevision()"
        dataKey="idConnector"
        [selectionMode]="disabled() ? null : 'single'"
        [selection]="selectedAlternative()"
        [rowSelectableFn]="isSelectable"
        [rowClassFn]="rowClass"
        (selectionChange)="onSelectionChange($event)"
        [customSortFn]="customSorter"
        [multiSortMeta]="multiSortMeta"
        [valueGetterFn]="valueGetter"
        [contextMenuEnabled]="false"
        [scrollable]="false"
        [baseLocale]="baseLocale">
        <h5 caption>{{ titleKey() | translate }}</h5>
      </configurable-table>
      @if (disabled() && disabledMessageKeyValue) {
        <p-message severity="info" [text]="disabledMessageKeyValue | translate" />
      }
    </div>
  `,
  styles: [
    `
      .lookup-table-disabled configurable-table {
        opacity: 0.5;
        pointer-events: none;
      }
      :host ::ng-deep tr.lookup-row-unusable {
        opacity: 0.55;
        cursor: not-allowed;
      }
    `
  ]
})
export class SecurityLookupConnectorTableComponent extends TableConfigBase {
  private readonly distributionFrequencyColumn: ColumnConfig;

  /** Translation key of the table title, e.g. ID_CONNECTOR_HISTORY */
  readonly titleKey = input<string>();

  /** Feed of the table; decides whether a connector without data may be chosen */
  readonly feed = input.required<LookupFeed>();

  /** Connectors offered for this feed */
  readonly alternatives = input<ConnectorAlternative[]>([]);

  /** The chosen connector; the dialog preselects it when only one usable connector exists */
  readonly selectedAlternativeInput = input<ConnectorAlternative | null>(undefined, { alias: 'selectedAlternative' });
  readonly selectedAlternative = linkedSignal(() => this.selectedAlternativeInput());

  /** Shows the distribution frequency derived from the dividends; only meaningful in the dividend table */
  readonly showDistributionFrequency = input(false);

  /** Shows the table greyed out without selection, e.g. the dividend table of an instrument that pays nothing */
  readonly disabled = input(false);

  /** Translation key of the hint shown below a disabled table */
  readonly disabledMessageKey = input<string>();

  /** Emits the chosen connector, or null when the selection is removed */
  readonly alternativeSelected = output<ConnectorAlternative | null>();

  /**
   * Creates the table columns.
   *
   */
  constructor() {
    super();
    this.addColumn(DataType.String, 'readableName', 'CONNECTOR', true, false, { width: 150 });
    this.addColumnFeqH(DataType.String, 'urlExtension', true, false, { width: 150 });
    this.addColumnFeqH(DataType.String, 'probeStatus', true, false, {
      width: 90,
      translateValues: TranslateValue.NORMAL
    });
    this.addColumnFeqH(DataType.DateString, 'sampleDate', true, false, { width: 90 });
    this.addColumnFeqH(DataType.Numeric, 'sampleValue', true, false, { width: 80 });
    this.addColumnFeqH(DataType.NumericInteger, 'sampleCount', true, false, { width: 60 });
    this.distributionFrequencyColumn = this.addColumnFeqH(DataType.String, 'distributionFrequency', false, false, {
      width: 110,
      translateValues: TranslateValue.NORMAL
    });
    this.addColumnFeqH(DataType.String, 'probeDetail', true, false, { width: 200 });
    this.prepareTableAndTranslate();

    effect(() => {
      this.showDistributionFrequency();
      untracked(() => {
        this.distributionFrequencyColumn.visible = this.showDistributionFrequency();
        this.notifyViewChanged();
      });
    });
    effect(() => {
      this.alternatives();
      untracked(() => {
        const rows = this.alternatives();
        if (rows) this.createTranslatedValueStore(rows);
      });
    });
  }

  /**
   * Tells whether a connector may be chosen: only one whose probe succeeded.
   *
   * @param alternative - The row
   * @returns True when the row is selectable
   */
  readonly isSelectable = (alternative: ConnectorAlternative): boolean => {
    return isUsableAlternative(this.feed(), alternative);
  };

  /**
   * Marks a connector that cannot be chosen, so that it is shown as unavailable.
   *
   * @param alternative - The row
   * @returns The CSS class of an unusable row, otherwise null
   */
  readonly rowClass = (alternative: ConnectorAlternative): string | null => {
    return this.isSelectable(alternative) ? null : 'lookup-row-unusable';
  };

  /**
   * Keeps the selection and reports it to the dialog.
   *
   * @param alternative - The chosen connector, or null when the selection is removed
   */
  onSelectionChange(selection: ConnectorAlternative | ConnectorAlternative[]): void {
    const alternative = Helper.singleSelection(selection);
    this.selectedAlternative.set(alternative);
    this.alternativeSelected.emit(alternative);
  }
}
