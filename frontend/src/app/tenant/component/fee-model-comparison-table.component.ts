import {Component, Injector, Input, OnChanges, OnDestroy, SimpleChanges} from '@angular/core';
import {TranslateService} from '@ngx-translate/core';
import {FilterService} from 'primeng/api';
import {DialogService} from 'primeng/dynamicdialog';
import {FeeModelComparisonDetail} from '../../entities/fee.model.comparison';
import {ColumnConfig, TranslateValue} from '../../lib/datashowbase/column.config';
import {FilterType} from '../../lib/datashowbase/filter.type';
import {ConfigurableTableComponent} from '../../lib/datashowbase/configurable-table.component';
import {TableConfigBase} from '../../lib/datashowbase/table.config.base';
import {DataType} from '../../lib/dynamic-form/models/data.type';
import {Helper} from '../../lib/helper/helper';
import {GlobalparameterService} from '../../lib/services/globalparameter.service';
import {UserSettingsService} from '../../lib/services/user.settings.service';
import {AppSettings} from '../../shared/app.settings';

/**
 * Table component for displaying fee model comparison detail rows. Extends TableConfigBase
 * to get sorting, filtering, and column visibility out of the box.
 */
@Component({
  selector: 'fee-model-comparison-table',
  template: `
    <configurable-table
      [data]="details"
      [fields]="fields"
      [(selection)]="selectedRow"
      [valueGetterFn]="getValueByPath.bind(this)"
      [customSortFn]="customSort.bind(this)"
      [cellStyleFn]="getCellStyle.bind(this)"
      [hasFilter]="true"
      [paginator]="true"
      [rows]="rowsPerPage"
      (pageChange)="onPage($event)"
      customClass="datatable">
    </configurable-table>
  `,
  standalone: true,
  imports: [ConfigurableTableComponent],
  providers: [DialogService]
})
export class FeeModelComparisonTableComponent extends TableConfigBase implements OnChanges, OnDestroy {

  @Input() details: FeeModelComparisonDetail[] = [];
  selectedRow: FeeModelComparisonDetail;

  private fieldsInitialized = false;

  constructor(
    filterService: FilterService,
    translateService: TranslateService,
    gps: GlobalparameterService,
    usersettingsService: UserSettingsService,
    injector: Injector
  ) {
    super(filterService, usersettingsService, translateService, gps, injector);
    this.rowsPerPage = 20;
    this.addColumnFeqH(DataType.DateString, 'transactionDate', true, true,
      {filterType: FilterType.likeDataType});
    this.addColumnFeqH(DataType.String, 'transactionType', true, true,
      {translateValues: TranslateValue.NORMAL, filterType: FilterType.withOptions});
    this.addColumn(DataType.String, 'securityName', 'SECURITY', true, true,
      {filterType: FilterType.likeDataType});
    this.addColumnFeqH(DataType.String, 'categoryType', true, true,
      {translateValues: TranslateValue.NORMAL, filterType: FilterType.withOptions});
    this.addColumnFeqH(DataType.String, 'specInvestInstrument', true, true,
      {translateValues: TranslateValue.NORMAL, filterType: FilterType.withOptions});
    this.addColumnFeqH(DataType.String, 'mic', true, true,
      {filterType: FilterType.likeDataType});
    this.addColumnFeqH(DataType.Numeric, 'actualCost', true, true, {maxFractionDigits: 2});
    this.addColumnFeqH(DataType.Numeric, 'estimatedCost', true, true, {maxFractionDigits: 2});
    this.addColumnFeqH(DataType.Numeric, 'relativeError', true, true, {
      maxFractionDigits: 2,
      fieldValueFN: (dataobject: any, field: ColumnConfig, valueField: any) => {
        const rawValue = Helper.getValueByPath(dataobject, field.field);
        if (rawValue != null) {
          return Number(rawValue).toFixed(2).split('.').join(gps.getDecimalSymbol());
        }
        return rawValue;
      }
    });
    this.addColumnFeqH(DataType.String, 'currency', true, true,
      {filterType: FilterType.likeDataType});
    this.addColumnFeqH(DataType.Numeric, 'tradeValue', true, true, {maxFractionDigits: 2});
    this.addColumnFeqH(DataType.Numeric, 'quotation', true, true);
    this.addColumnFeqH(DataType.Numeric, 'units', true, true);

    this.addColumnFeqH(DataType.String, 'matchedRuleName', true, true,
      {filterType: FilterType.likeDataType});
    this.multiSortMeta.push({field: 'transactionDate', order: 1});
    this.prepareTableAndTranslate();
    this.readTableDefinition(AppSettings.FEE_MODEL_COMPARISON_TABLE_SETTINGS_STORE);
    this.fieldsInitialized = true;
  }

  /**
   * Returns background color style for the relativeError column using a green-yellow-red gradient.
   * 0% error = green, ~25% = yellow, 50%+ = red.
   */
  getCellStyle(row: FeeModelComparisonDetail, field: ColumnConfig): { [key: string]: string } | null {
    if (field.field === 'relativeError' && row.relativeError != null) {
      const absError = Math.abs(row.relativeError);
      const maxError = 50;
      const hue = Math.max(0, 120 - (Math.min(absError, maxError) / maxError) * 120);
      return {'background-color': `hsl(${hue}, 70%, 85%)`};
    }
    return null;
  }

  ngOnChanges(changes: SimpleChanges): void {
    if (changes['details'] && this.details && this.fieldsInitialized) {
      this.createTranslatedValueStoreAndFilterField(this.details);
      this.prepareFilter(this.details);
    }
  }

  ngOnDestroy(): void {
    this.writeTableDefinition(AppSettings.FEE_MODEL_COMPARISON_TABLE_SETTINGS_STORE);
  }
}
