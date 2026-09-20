import { ChangeDetectionStrategy, Component, Input, OnChanges } from '@angular/core';
import { TranslateService } from '@ngx-translate/core';
import { FilterService } from '@openng/optimus-ui/api';
import { TableConfigBase } from '../../lib/datashowbase/table.config.base';
import { ConfigurableTableComponent } from '../../lib/datashowbase/configurable-table.component';
import { GlobalparameterService } from '../../lib/services/globalparameter.service';
import { UserSettingsService } from '../../lib/services/user.settings.service';
import { DataType } from '../../lib/dynamic-form/models/data.type';
import { FilterType } from '../../lib/datashowbase/filter.type';
import { TranslateValue } from '../../lib/datashowbase/column.config';
import { SimulationRunEvent } from '../model/simulation.run';

/** The audit trail of a historical replay: what was decided on which day, what was executed and what was refused. */
@Component({
  selector: 'algo-simulation-run-table',
  standalone: true,
  changeDetection: ChangeDetectionStrategy.Eager,
  imports: [ConfigurableTableComponent],
  template: `<configurable-table
    [data]="rows"
    [fields]="fields"
    dataKey="idAlgoEvent"
    [valueGetterFn]="getValueByPath.bind(this)"
    [baseLocale]="baseLocale"
    [customSortFn]="customSort.bind(this)"
    [multiSortMeta]="multiSortMeta"
    [paginator]="true"
    [rows]="rowsPerPage"
    (pageChange)="onPage($event)"
    [scrollable]="true"
    [scrollHeight]="scrollHeight" />`
})
export class AlgoSimulationRunTableComponent extends TableConfigBase implements OnChanges {
  @Input() rows: SimulationRunEvent[] = [];
  /** Height at which the trail starts to scroll, so the dialog keeps its form and its metrics in view. */
  @Input() scrollHeight = '40vh';
  private fieldsInitialized = false;

  constructor(
    filter: FilterService,
    settings: UserSettingsService,
    translate: TranslateService,
    gps: GlobalparameterService
  ) {
    super(filter, settings, translate, gps);
    this.rowsPerPage = 20;
  }

  ngOnChanges(): void {
    if (!this.fieldsInitialized) {
      this.addColumnFeqH(DataType.DateString, 'eventDate');
      this.addColumnFeqH(DataType.String, 'eventType', true, false, {
        translateValues: TranslateValue.NORMAL,
        filterType: FilterType.withOptions
      });
      // Every rationale the replay writes is a message key, so the column reads in the user's language.
      this.addColumnFeqH(DataType.String, 'rationale', true, false, {
        translateValues: TranslateValue.NORMAL,
        filterType: FilterType.withOptions
      });
      this.addColumnFeqH(DataType.Numeric, 'units', true, false, {
        maxFractionDigits: this.gps.getMaxFractionDigits()
      });
      this.addColumnFeqH(DataType.Numeric, 'price', true, false, {
        maxFractionDigits: this.gps.getMaxFractionDigits()
      });
      this.addColumnFeqH(DataType.Numeric, 'amount', true, false, { currencyPrecisionField: 'currency' });
      this.addColumnFeqH(DataType.String, 'currency');
      // A detail is either an NLS key on its own - the trigger of a plan, the status a run ended in - or text the
      // server already resolved because it names dates, accounts or amounts. Translating the value leaves the latter
      // untouched, because the translation of a text that is not a key is that text.
      this.addColumnFeqH(DataType.String, 'details', true, false, { translateValues: TranslateValue.NORMAL });
      this.prepareTableAndTranslate();
      this.fieldsInitialized = true;
    }
    this.createTranslatedValueStoreAndFilterField(this.rows);
    this.prepareFilter(this.rows);
  }
}
