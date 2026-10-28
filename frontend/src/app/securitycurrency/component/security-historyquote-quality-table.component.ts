import {
  Component,
  computed,
  DestroyRef,
  EventEmitter,
  inject,
  Injector,
  Input,
  OnChanges,
  Output,
  signal
} from '@angular/core';
import { Subscription } from 'rxjs';
import { TableConfigBase } from '../../lib/datashowbase/table.config.base';
import { SecurityService } from '../service/security.service';
import { TranslatePipe, TranslateService } from '@ngx-translate/core';
import { GlobalparameterService } from '../../lib/services/globalparameter.service';
import { UserSettingsService } from '../../lib/services/user.settings.service';
import { HistoryquoteQualityIds, IHistoryquoteQualityWithSecurityProp } from '../model/historyquote.quality.group';
import { DataType } from '../../lib/dynamic-form/models/data.type';
import { TimeSeriesQuotesService } from '../../historyquote/service/time.series.quotes.service';
import { FilterService } from '@openng/optimus-ui/api';
import { TableModule } from '@openng/optimus-ui/table';

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
        <h5>{{ groupTitle | translate }}</h5>
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
export class SecurityHistoryquoteQualityTableComponent extends TableConfigBase implements OnChanges {
  @Input() historyquoteQualityIds: HistoryquoteQualityIds;
  @Input() groupTitle: string;
  @Output() changedIdSecurity = new EventEmitter<SecurityIdWithCurrency>();

  readonly selectedSecurity = signal<IHistoryquoteQualityWithSecurityProp>(null);
  readonly hqwspList = signal<IHistoryquoteQualityWithSecurityProp[]>([]);
  /** A new columns reference re-renders the header and body templates of the OnPush p-table after translation. */
  readonly tableFields = computed(() => {
    this.fieldsRevision();
    return [...this.fields];
  });
  private readonly destroyRef = inject(DestroyRef);
  private loadSubscription: Subscription;

  constructor(
    private timeSeriesQuotesService: TimeSeriesQuotesService,
    private securityService: SecurityService,
    filterService: FilterService,
    translateService: TranslateService,
    gps: GlobalparameterService,
    usersettingsService: UserSettingsService,
    injector: Injector
  ) {
    super(filterService, usersettingsService, translateService, gps, injector);

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
    this.destroyRef.onDestroy(() => this.loadSubscription?.unsubscribe());
  }

  /** Loads the securities of the selected group; a newer group replaces the request of the previous one. */
  ngOnChanges(): void {
    if (this.historyquoteQualityIds) {
      this.loadSubscription?.unsubscribe();
      this.loadSubscription = this.securityService
        .getHistoryquoteQualityByIds(this.historyquoteQualityIds)
        .subscribe((historyquoteQualityWithSecurityPropList) => {
          this.selectedSecurity.set(null);
          this.hqwspList.set(historyquoteQualityWithSecurityPropList);
        });
    }
  }

  onRowSelect(event): void {
    const selected: IHistoryquoteQualityWithSecurityProp = event.data;
    this.changedIdSecurity.emit(new SecurityIdWithCurrency(selected.idSecurity, selected.currency));
  }

  onRowUnselect(event): void {
    this.changedIdSecurity.emit(null);
  }
}

export class SecurityIdWithCurrency {
  public constructor(
    public idSecurity: number,
    public currency: string
  ) {}
}
