import {Component, EventEmitter, Input, OnChanges, Output} from '@angular/core';
import {TableConfigBase} from '../../lib/datashowbase/table.config.base';
import {SecurityService} from '../service/security.service';
import {TranslateService} from '@ngx-translate/core';
import {GlobalparameterService} from '../../lib/services/globalparameter.service';
import {UserSettingsService} from '../../lib/services/user.settings.service';
import {HistoryquoteQualityIds, IHistoryquoteQualityWithSecurityProp} from '../model/historyquote.quality.group';
import {DataType} from '../../lib/dynamic-form/models/data.type';
import {TimeSeriesQuotesService} from '../../historyquote/service/time.series.quotes.service';
import {FilterService} from 'primeng/api';

/**
 * Shows the securities in a table.
 */
@Component({
    selector: 'security-historyquote-quality-table',
  template: `
    <p-table [columns]="fields" [value]="hqwspList" selectionMode="single"
             [(selection)]="selectedSecurity" (onRowSelect)="onRowSelect($event)"
             (onRowUnselect)="onRowUnselect($event)" dataKey="idSecurity"
             (sortFunction)="customSort($event)" [customSort]="true" sortMode="multiple" [multiSortMeta]="multiSortMeta"
             stripedRows showGridlines>
      <ng-template #caption>
        <h5>{{groupTitle | translate}}</h5>
      </ng-template>
      <ng-template #header let-fields>
        <tr>
          @for (field of fields; track field) {
            <th [pSortableColumn]="field.field" [style.width.px]="field.width">
              {{field.headerTranslated}}
              <p-sortIcon [field]="field.field"></p-sortIcon>
            </th>
          }
        </tr>
      </ng-template>
      <ng-template #body let-el let-columns="fields">
        <tr [pSelectableRow]="el">
          @for (field of fields; track field) {
            <td [ngClass]="(field.dataType===DataType.Numeric || field.dataType===DataType.NumericInteger
              || field.dataType===DataType.DateTimeNumeric)? 'text-right': ''">
              {{getValueByPath(el, field)}}
            </td>
          }
        </tr>
      </ng-template>
    </p-table>
  `,
    standalone: false
})
export class SecurityHistoryquoteQualityTableComponent extends TableConfigBase implements OnChanges {
  @Input() historyquoteQualityIds: HistoryquoteQualityIds;
  @Input() groupTitle: string;
  @Output() changedIdSecurity = new EventEmitter<SecurityIdWithCurrency>();

  selectedSecurity: IHistoryquoteQualityWithSecurityProp;
  hqwspList: IHistoryquoteQualityWithSecurityProp[];

  constructor(private timeSeriesQuotesService: TimeSeriesQuotesService,
              private securityService: SecurityService,
              filterService: FilterService,
              translateService: TranslateService,
              gps: GlobalparameterService,
              usersettingsService: UserSettingsService) {
    super(filterService, usersettingsService, translateService, gps);

    this.addColumnFeqH(DataType.String, 'name', true, false, {width: 250});
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
    this.addColumnFeqH(DataType.Numeric, 'qualityPercentage', true, false, {headerSuffix: '%'});
    this.addColumnFeqH(DataType.NumericInteger, 'toManyAsCalendar');
    this.addColumnFeqH(DataType.NumericInteger, 'quoteSaturday');
    this.addColumnFeqH(DataType.NumericInteger, 'quoteSunday');
    this.multiSortMeta.push({field: 'name', order: 1});
    this.prepareTableAndTranslate();
  }

  ngOnChanges(): void {
    if (this.historyquoteQualityIds) {
      this.securityService.getHistoryquoteQualityByIds(this.historyquoteQualityIds).subscribe(historyquoteQualityWithSecurityPropList => {
        this.hqwspList = historyquoteQualityWithSecurityPropList;
      });
    }
  }

  onRowSelect(event): void {
    this.changedIdSecurity.emit(new SecurityIdWithCurrency(this.selectedSecurity.idSecurity, this.selectedSecurity.currency));
  }

  onRowUnselect(event): void {
    this.changedIdSecurity.emit(null);
  }
}

export class SecurityIdWithCurrency {
  public constructor(public idSecurity: number, public currency: string) {
  }
}
