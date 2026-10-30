import { httpResource } from '@angular/common/http';
import { Component, computed, DestroyRef, effect, inject, input, output, signal, untracked } from '@angular/core';
import { TranslatePipe } from '@ngx-translate/core';
import { TableModule } from '@openng/optimus-ui/table';
import { TimeSeriesQuotesService } from '../../historyquote/service/time.series.quotes.service';
import { TableConfigBase } from '../../lib/datashowbase/table.config.base';
import { DataType } from '../../lib/dynamic-form/models/data.type';
import { reportResourceErrors } from '../../lib/login/service/resource.error.report';
import { HistoryquoteQualityIds, IHistoryquoteQualityWithSecurityProp } from '../model/historyquote.quality.group';
import { SecurityService } from '../service/security.service';

/**
 * Shows the securities in a table.
 */
@Component({
  selector: 'security-historyquote-quality-table',
  template: `
    <p-table
      [columns]="tableFields()"
      [value]="hqwspList()"
      selectionMode="single"
      [selection]="selectedSecurity()"
      (selectionChange)="selectedSecurity.set($event)"
      (onRowSelect)="onRowSelect($event)"
      (onRowUnselect)="onRowUnselect($event)"
      dataKey="idSecurity"
      (sortFunction)="customSort($event)"
      [customSort]="true"
      sortMode="multiple"
      [multiSortMeta]="multiSortMeta"
      stripedRows
      showGridlines>
      <ng-template #caption>
        <h5>{{ groupTitle() | translate }}</h5>
      </ng-template>
      <ng-template #header>
        <tr>
          @for (field of tableFields(); track field) {
            <th [pSortableColumn]="field.field" [style.width.px]="field.width">
              {{ field.headerTranslated }}
              <p-sortIcon [field]="field.field" />
            </th>
          }
        </tr>
      </ng-template>
      <ng-template #body let-el>
        <tr [pSelectableRow]="el">
          @for (field of tableFields(); track field) {
            <td
              [class]="
                field.dataType === DataType.Numeric ||
                field.dataType === DataType.NumericInteger ||
                field.dataType === DataType.DateTimeNumeric
                  ? 'text-end'
                  : ''
              ">
              {{ getValueByPath(el, field) }}
            </td>
          }
        </tr>
      </ng-template>
    </p-table>
  `,
  imports: [TableModule, TranslatePipe]
})
export class SecurityHistoryquoteQualityTableComponent extends TableConfigBase {
  private timeSeriesQuotesService: TimeSeriesQuotesService = inject(TimeSeriesQuotesService);
  private securityService: SecurityService = inject(SecurityService);

  readonly historyquoteQualityIds = input<HistoryquoteQualityIds>();
  readonly groupTitle = input<string>();
  readonly changedIdSecurity = output<SecurityIdWithCurrency | null>();

  readonly selectedSecurity = signal<IHistoryquoteQualityWithSecurityProp | null>(null);
  readonly hqwspList = computed(() => (this.qualityResource.hasValue() ? this.qualityResource.value() : []));
  /** A new columns reference re-renders the header and body templates of the OnPush p-table after translation. */
  readonly tableFields = computed(() => {
    this.fieldsRevision();
    return [...this.fields];
  });
  private readonly destroyRef = inject(DestroyRef);

  readonly qualityResource = httpResource<IHistoryquoteQualityWithSecurityProp[]>(() => {
    const ids = this.historyquoteQualityIds();
    return ids ? this.securityService.getHistoryquoteQualityByIdsRequest(ids) : undefined;
  });
  constructor() {
    super();

    this.addColumnFeqH(DataType.String, 'name', true, false, { width: 250 });
    this.addColumnFeqH(DataType.DateString, 'activeFromDate');
    this.addColumnFeqH(DataType.DateString, 'minDate');
    this.addColumnFeqH(DataType.NumericInteger, 'missingStart');
    this.addColumnFeqH(DataType.DateString, 'activeToDate');
    this.addColumnFeqH(DataType.DateString, 'maxDate');
    this.addColumnFeqH(DataType.NumericInteger, 'missingEnd');
    this.addColumnFeqH(DataType.NumericInteger, 'totalMissing');
    this.addColumnFeqH(DataType.NumericInteger, 'expectedTotal');
    this.addColumnFeqH(DataType.NumericInteger, 'connectorCreated');
    this.addColumnFeqH(DataType.NumericInteger, 'manualImported');
    this.addColumnFeqH(DataType.NumericInteger, 'filledLinear');
    this.addColumnFeqH(DataType.Numeric, 'qualityPercentage', true, false, {
      headerSuffix: '%'
    });
    this.addColumnFeqH(DataType.Numeric, 'ohlPercentage', true, false, {
      headerSuffix: '%'
    });
    this.addColumnFeqH(DataType.NumericInteger, 'toManyAsCalendar');
    this.addColumnFeqH(DataType.NumericInteger, 'quoteSaturday');
    this.addColumnFeqH(DataType.NumericInteger, 'quoteSunday');
    this.multiSortMeta.push({ field: 'name', order: 1 });
    this.prepareTableAndTranslate();

    reportResourceErrors(this.qualityResource, this.securityService);
    effect(() => {
      this.qualityResource.status();
      untracked(() => this.selectedSecurity.set(null));
    });
  }

  onRowSelect(event: { data?: IHistoryquoteQualityWithSecurityProp | IHistoryquoteQualityWithSecurityProp[] }): void {
    const selected = Array.isArray(event.data) ? event.data[0] : event.data;
    if (selected) this.changedIdSecurity.emit(new SecurityIdWithCurrency(selected.idSecurity, selected.currency));
  }

  onRowUnselect(event: unknown): void {
    this.changedIdSecurity.emit(null);
  }
}

export class SecurityIdWithCurrency {
  public constructor(
    public idSecurity: number,
    public currency: string
  ) {}
}
