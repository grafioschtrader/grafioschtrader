import {CommonModule} from '@angular/common';
import {Component, EventEmitter, Injector, Input, OnChanges, OnInit, Output} from '@angular/core';
import {TranslateModule, TranslateService} from '@ngx-translate/core';
import {FilterService} from 'primeng/api';
import {ColumnConfig} from '../../lib/datashowbase/column.config';
import {ConfigurableTableComponent} from '../../lib/datashowbase/configurable-table.component';
import {TableConfigBase} from '../../lib/datashowbase/table.config.base';
import {DataType} from '../../lib/dynamic-form/models/data.type';
import {GlobalparameterService} from '../../lib/services/globalparameter.service';
import {UserSettingsService} from '../../lib/services/user.settings.service';
import {Security} from '../../entities/security';
import {SecurityService} from '../../securitycurrency/service/security.service';
import {IFeedConnector} from '../../shared/securitycurrency/ifeed.connector';

/**
 * Shows a table with the missing instruments that do not have a complete price history.
 */
@Component({
  selector: 'tenant-performance-eod-missing-table',
  template: `
    <configurable-table
      [data]="securities"
      [fields]="fields"
      dataKey="idSecuritycurrency"
      selectionMode="single"
      [selection]="selectedSecurity"
      (selectionChange)="onSelectionChange($event)"
      [enableCustomSort]="true"
      [customSortFn]="customSort.bind(this)"
      sortMode="multiple"
      [multiSortMeta]="multiSortMeta"
      [valueGetterFn]="getValueByPath.bind(this)"
      [baseLocale]="baseLocale"
      [rowClassFn]="getRowHighlightClass.bind(this)">
      <h5 caption>{{ 'MISSING_DAY_TABLE_MARK' | translate }}</h5>
    </configurable-table>
  `,
  standalone: true,
  imports: [CommonModule, TranslateModule, ConfigurableTableComponent]
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
    usersettingsService: UserSettingsService,
    injector: Injector) {
    super(filterService, usersettingsService, translateService, gps, injector);
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

  onSelectionChange(security: Security): void {
    this.selectedSecurity = security;
    this.changedSecurity.emit(security);
  }

  getRowHighlightClass(row: Security): string | null {
    return this.selectedDayIdSecurities.indexOf(row.idSecuritycurrency) >= 0 ? 'rowgroup-total' : null;
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
