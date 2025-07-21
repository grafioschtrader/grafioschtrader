import {TableConfigBase} from '../../datashowbase/table.config.base';
import {Component, EventEmitter, Input, OnChanges, OnInit, Output} from '@angular/core';
import {TranslateService} from '@ngx-translate/core';
import {GlobalparameterService} from '../../../shared/service/globalparameter.service';
import {UserSettingsService} from '../../../shared/service/user.settings.service';
import {Security} from '../../../entities/security';
import {DataType} from '../../../dynamic-form/models/data.type';
import {ColumnConfig} from '../../datashowbase/column.config';
import {SecurityService} from '../../../securitycurrency/service/security.service';
import {IFeedConnector} from '../../../securitycurrency/component/ifeed.connector';
import {FilterService} from 'primeng/api';

/**
 * Shows a table with the missing instruments that do not have a complete price history.
 */
@Component({
  selector: 'tenant-performance-eod-missing-table',
  template: `
    <p-table [columns]="fields" [value]="securities" selectionMode="single"
             [(selection)]="selectedSecurity" (onRowSelect)="onRowSelect($event)"
             (onRowUnselect)="onRowUnselect($event)"
             dataKey="idSecuritycurrency" (sortFunction)="customSort($event)" [customSort]="true"
             sortMode="multiple" [multiSortMeta]="multiSortMeta"
             stripedRows showGridlines>
      <ng-template #caption>
        <h5>{{ 'MISSING_DAY_TABLE_MARK'|translate }}</h5>
      </ng-template>
      <ng-template #header let-fields>
        <tr>
          @for (field of fields; track field) {
            <th [pSortableColumn]="field.field" [style.max-width.px]="field.width"
                [ngStyle]="field.width? {'flex-basis': '0 0 ' + field.width + 'px'}: {}">
              {{ field.headerTranslated }}
              <p-sortIcon [field]="field.field"></p-sortIcon>
            </th>
          }
        </tr>
      </ng-template>
      <ng-template #body let-el let-columns="fields">
        <tr [pSelectableRow]="el"
            [ngClass]="selectedDayIdSecurities.indexOf(el.idSecuritycurrency)>= 0 ? 'rowgroup-total' : null">
          @for (field of fields; track field) {
            <td
              [ngClass]="(field.dataType===DataType.Numeric || field.dataType===DataType.DateTimeNumeric)? 'text-right': ''"
              [style.max-width.px]="field.width"
              [ngStyle]="field.width? {'flex-basis': '0 0 ' + field.width + 'px'}: {}">
              <span [pTooltip]="getValueByPath(el, field)" tooltipPosition="top">{{ getValueByPath(el, field) }}</span>
            </td>
          }
        </tr>
      </ng-template>
    </p-table>
  `,
  standalone: false
})
export class TenantPerformanceEodMissingTableComponent extends TableConfigBase implements OnInit, OnChanges {
  /**
   * Contains the missings days -> key = idSecuritycurrency
   */
  @Input() countIdSecurityMissingsMap: { [key: number]: number };
  @Input() securities: Security[];
  @Input() selectedDayIdSecurities: number[];
  @Output() changedSecurity = new EventEmitter<Security>();

  selectedSecurity: Security;
  private feedConnectorsKV: { [id: string]: string };

  constructor(private securityService: SecurityService,
    filterService: FilterService,
    translateService: TranslateService,
    gps: GlobalparameterService,
    usersettingsService: UserSettingsService) {
    super(filterService, usersettingsService, translateService, gps);
    this.addColumnFeqH(DataType.String, 'name', true, false, {width: 250});
    this.addColumnFeqH(DataType.String, 'isin');
    this.addColumnFeqH(DataType.String, 'currency');
    this.addColumnFeqH(DataType.DateString, 'activeFromDate');
    this.addColumnFeqH(DataType.DateString, 'activeToDate');
    this.addColumn(DataType.String, 'idConnectorHistory', 'HISTORY_DATA_PROVIDER', true, true,
      {fieldValueFN: this.getFeedConnectorReadableName.bind(this)});

    this.addColumn(DataType.Numeric, 'count', 'COUNT_MISSING_YEAR', true, true,
      {fieldValueFN: this.getSecurityMissingCount.bind(this)});
    this.multiSortMeta.push({field: 'name', order: 1});
    this.prepareTableAndTranslate();
  }

  getSecurityMissingCount(dataobject: any, field: ColumnConfig, valueField: any): number {
    return this.countIdSecurityMissingsMap[dataobject.idSecuritycurrency];
  }

  getFeedConnectorReadableName(dataobject: any, field: ColumnConfig, valueField: any): string {
    return this.feedConnectorsKV ? this.feedConnectorsKV[valueField] : null;
  }

  onRowSelect(event): void {
    this.changedSecurity.emit(this.selectedSecurity);
  }

  onRowUnselect(event): void {
    this.changedSecurity.emit(this.selectedSecurity);
  }

  ngOnInit(): void {
    this.securityService.getFeedConnectors().subscribe((feedConnectors: IFeedConnector[]) => {
      this.feedConnectorsKV = feedConnectors.reduce((acc, feedConnector) => {
        acc[feedConnector.id] = feedConnector.readableName;
        return acc;
      }, {} as { [key: string]: string });
    });
  }


  ngOnChanges(): void {
    if (this.selectedDayIdSecurities.length > 0) {
      this.selectedSecurity = null;
    }
  }

}
