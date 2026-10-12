import {
  ChangeDetectionStrategy,
  Component,
  EventEmitter,
  Injector,
  Input,
  OnChanges,
  Output,
  SimpleChanges
} from '@angular/core';
import { TranslateModule, TranslateService } from '@ngx-translate/core';
import { FilterService } from '@openng/optimus-ui/api';
import { MessageModule } from '@openng/optimus-ui/message';

import { TableConfigBase } from '../../lib/datashowbase/table.config.base';
import { ConfigurableTableComponent } from '../../lib/datashowbase/configurable-table.component';
import { ColumnConfig, TranslateValue } from '../../lib/datashowbase/column.config';
import { DataType } from '../../lib/dynamic-form/models/data.type';
import { GlobalparameterService } from '../../lib/services/globalparameter.service';
import { UserSettingsService } from '../../lib/services/user.settings.service';
import { ConnectorAlternative } from '../model/security-lookup';
import { isUsableAlternative, LookupFeed } from '../model/security-lookup-selection';

/**
 * One of the four connector tables of the security lookup dialog (history, intraday, dividend, split). Each row is a
 * connector with the URL extension it derived and the outcome of the trial request, so the user sees whether the feed
 * actually delivers data before taking the connector over. A connector whose probe failed stays visible with its
 * reason, but cannot be selected.
 */
@Component({
  selector: 'security-lookup-connector-table',
  standalone: true,
  imports: [ConfigurableTableComponent, TranslateModule, MessageModule],
  changeDetection: ChangeDetectionStrategy.Eager,
  template: `
    <div [class.lookup-table-disabled]="disabled">
      <configurable-table
        [data]="alternatives"
        [fields]="fields"
        dataKey="idConnector"
        [selectionMode]="disabled ? null : 'single'"
        [selection]="selectedAlternative"
        [rowSelectableFn]="isSelectable.bind(this)"
        [rowClassFn]="rowClass.bind(this)"
        (selectionChange)="onSelectionChange($event)"
        [customSortFn]="customSort.bind(this)"
        [multiSortMeta]="multiSortMeta"
        [valueGetterFn]="getValueByPath.bind(this)"
        [contextMenuEnabled]="false"
        [scrollable]="false"
        [baseLocale]="baseLocale">
        <h5 caption>{{ titleKey | translate }}</h5>
      </configurable-table>
      @if (disabled && disabledMessageKey) {
        <p-message severity="info" [text]="disabledMessageKey | translate"></p-message>
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
export class SecurityLookupConnectorTableComponent extends TableConfigBase implements OnChanges {
  private readonly distributionFrequencyColumn: ColumnConfig;

  /** Translation key of the table title, e.g. ID_CONNECTOR_HISTORY */
  @Input() titleKey: string;

  /** Feed of the table; decides whether a connector without data may be chosen */
  @Input() feed: LookupFeed;

  /** Connectors offered for this feed */
  @Input() alternatives: ConnectorAlternative[] = [];

  /** The chosen connector; the dialog preselects it when only one usable connector exists */
  @Input() selectedAlternative: ConnectorAlternative;

  /** Shows the distribution frequency derived from the dividends; only meaningful in the dividend table */
  @Input() showDistributionFrequency = false;

  /** Shows the table greyed out without selection, e.g. the dividend table of an instrument that pays nothing */
  @Input() disabled = false;

  /** Translation key of the hint shown below a disabled table */
  @Input() disabledMessageKey: string;

  /** Emits the chosen connector, or null when the selection is removed */
  @Output() alternativeSelected = new EventEmitter<ConnectorAlternative>();

  /**
   * Creates the table columns.
   *
   * @param filterService - Optimus UI filter service
   * @param usersettingsService - Persists table settings
   * @param translateService - Translation of headers and probe statuses
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
  }

  ngOnChanges(changes: SimpleChanges): void {
    if (changes['showDistributionFrequency']) {
      this.distributionFrequencyColumn.visible = this.showDistributionFrequency;
    }
    if (changes['alternatives'] && this.alternatives) {
      this.createTranslatedValueStore(this.alternatives);
    }
  }

  /**
   * Tells whether a connector may be chosen: only one whose probe succeeded.
   *
   * @param alternative - The row
   * @returns True when the row is selectable
   */
  isSelectable(alternative: ConnectorAlternative): boolean {
    return isUsableAlternative(this.feed, alternative);
  }

  /**
   * Marks a connector that cannot be chosen, so that it is shown as unavailable.
   *
   * @param alternative - The row
   * @returns The CSS class of an unusable row, otherwise null
   */
  rowClass(alternative: ConnectorAlternative): string | null {
    return this.isSelectable(alternative) ? null : 'lookup-row-unusable';
  }

  /**
   * Keeps the selection and reports it to the dialog.
   *
   * @param alternative - The chosen connector, or null when the selection is removed
   */
  onSelectionChange(alternative: ConnectorAlternative): void {
    this.selectedAlternative = alternative;
    this.alternativeSelected.emit(alternative);
  }
}
