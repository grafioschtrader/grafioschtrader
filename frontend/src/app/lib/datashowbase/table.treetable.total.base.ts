import {Injector} from '@angular/core';
import {ShowRecordConfigBase} from './show.record.config.base';
import {TranslateService} from '@ngx-translate/core';
import {FilterService, SelectItem} from 'primeng/api';
import moment from 'moment';
import {ColumnConfig, ColumnGroupConfig} from './column.config';
import {AppHelper} from '../helper/app.helper';
import {Auditable} from '../entities/auditable';
import {GlobalparameterService} from '../services/globalparameter.service';
import {BaseSettings} from '../base.settings';
import {FilterType} from './filter.type';
import {DataType} from '../dynamic-form/models/data.type';
import {ValueLabelHtmlSelectOptions} from './value.label.html.select.options';
import {FilterableTable} from './filterable.table.type';

/**
 * Abstract base class extending ShowRecordConfigBase with functionality for handling
 * grouped data display in tables and tree tables. Provides utilities for displaying
 * subtotals, grand totals, and other aggregate values in table group sections.
 *
 * This class is designed for tables that need to show calculated values, totals,
 * or grouped data at various levels, commonly used in financial reports,
 * data summaries, and hierarchical data displays.
 */
export abstract class TableTreetableTotalBase extends ShowRecordConfigBase {

  /**
   * Exposes FilterType enum for use in Angular templates.
   * Required for template-based filter type comparisons.
   */
  FilterType: typeof FilterType = FilterType;

  /** Flag indicating whether any columns have filtering enabled */
  public hasFilter = false;

  /** Custom filter options for PrimeNG table/tree table filter dropdowns */
  customMatchModeOptions: SelectItem[] = [];

  /** Names of registered custom filter functions */
  customSearchNames: string[];

  /**
   * Creates a new table/tree table total base configuration.
   *
   * @param translateService - Angular translation service for internationalization
   * @param gps - Global parameter base service for locale and formatting settings
   * @param injector - Angular injector for lazy service resolution in subclasses
   * @param filterService - Optional PrimeNG service for registering custom filters
   */
  protected constructor(translateService: TranslateService,
    gps: GlobalparameterService,
    injector: Injector = null,
    protected filterService?: FilterService) {
    super(translateService, gps, injector);
  }

  /**
   * Configures columns starting from a specified index to use their field names for group display.
   * Creates default ColumnGroupConfig for each field to enable total/subtotal functionality.
   *
   * @param startIndex - Index from which to start applying group configurations
   */
  setSameFieldNameForGroupField(startIndex: number): void {
    for (let i = startIndex; i < this.fields.length; i++) {
      this.fields[i].columnGroupConfigs = [new ColumnGroupConfig(this.fields[i].field)];
    }
  }

  /**
   * Checks if a column total value is negative for styling purposes.
   *
   * @param columnConfig - Column configuration containing group settings
   * @param arrIndex - Index of the group configuration to check
   * @param data - Data object or collection containing the values
   * @param mapKey - Key for accessing data from collections/maps
   * @returns True if the total value starts with '-', false otherwise
   */
  isValueColumnTotalMinus(columnConfig: ColumnConfig, arrIndex: number, data: any, mapKey: any): boolean {
    return this.getValueColumnTotal(columnConfig, arrIndex, data, mapKey).startsWith('-');
  }

  /**
   * Retrieves and formats the total value for a column group section.
   * Combines label text with field values, supporting both custom functions and standard field access.
   *
   * @param columnConfig - Column configuration containing group settings
   * @param arrIndex - Index of the group configuration to process
   * @param data - Data object or collection containing the values
   * @param mapKey - Key for accessing data from collections/maps
   * @returns Formatted string combining label and value for display
   */
  getValueColumnTotal(columnConfig: ColumnConfig, arrIndex: number, data: any, mapKey: any) {
    let value = '';
    if (columnConfig.columnGroupConfigs && arrIndex < columnConfig.columnGroupConfigs.length) {
      if (columnConfig.columnGroupConfigs[arrIndex].fieldTextFN) {
        value = columnConfig.columnGroupConfigs[arrIndex].fieldTextFN(columnConfig, arrIndex, data, mapKey);
      } else {
        value = this.getTextValueColumnTotal(columnConfig, arrIndex);
        if (columnConfig.columnGroupConfigs[arrIndex].fieldValue) {
          const dataValue = this.getFieldValueColumnTotal(columnConfig, arrIndex, data, mapKey);
          if (dataValue) {
            value += (value.length > 0) ? ' ' + dataValue : dataValue;
          }
        }
      }
    }
    return value;
  }

  /**
   * Checks if a column total value accessed by row index is negative.
   * Alternative access method for row-based data structures.
   *
   * @param columnConfig - Column configuration containing group settings
   * @param arrIndex - Index of the group configuration to check
   * @param groupChangeIndexMap - Mapping of group changes to data indices
   * @param rowIndex - Specific row index to check
   * @returns True if the total value starts with '-', false otherwise
   */
  isColumnTotalByRowIndexMinus(columnConfig: ColumnConfig, arrIndex: number, groupChangeIndexMap: any, rowIndex: number): boolean {
    return this.getValueColumnTotalByRowIndex(columnConfig, arrIndex, groupChangeIndexMap, rowIndex).startsWith('-');
  }

  /**
   * Retrieves and formats column total value using row-based access.
   * Similar to getValueColumnTotal but uses row index for data access instead of direct object access.
   *
   * @param columnConfig - Column configuration containing group settings
   * @param arrIndex - Index of the group configuration to process
   * @param groupChangeIndexMap - Mapping of group changes to data indices
   * @param rowIndex - Specific row index to access
   * @returns Formatted string combining label and value for display
   */
  getValueColumnTotalByRowIndex(columnConfig: ColumnConfig, arrIndex: number, groupChangeIndexMap: any, rowIndex: number): string {
    let value = '';
    if (columnConfig.columnGroupConfigs && arrIndex < columnConfig.columnGroupConfigs.length) {
      if (columnConfig.columnGroupConfigs[arrIndex].fieldTextFN) {
        value = columnConfig.columnGroupConfigs[arrIndex].fieldTextFN(columnConfig, arrIndex, groupChangeIndexMap, rowIndex);
      } else {
        value = this.getTextValueColumnTotal(columnConfig, arrIndex);
        if (columnConfig.columnGroupConfigs[arrIndex].fieldValue) {
          const dataValue = this.getFieldValueColumnTotal(columnConfig, arrIndex, groupChangeIndexMap, rowIndex);
          if (dataValue) {
            value += (value.length > 0) ? ' ' + dataValue : dataValue;
          }
        }
      }
    }
    return value;
  }

  /**
   * Determines if an entity should be displayed with owner highlighting.
   * Used for visually distinguishing user-owned entities in shared data scenarios.
   *
   * @param entity - Auditable entity to check for ownership
   * @param columnConfig - Column configuration with template settings
   * @returns True if entity should be highlighted as user-owned
   */
  public isNotSingleModeAndOwner(entity: Auditable, columnConfig: ColumnConfig): boolean {
    return this.gps.isUiShowMyProperty() && columnConfig.templateName === BaseSettings.OWNER_TEMPLATE && this.gps.isEntityCreatedByUser(entity);
  }

  /**
   * Retrieves the translated label text for a column group section.
   *
   * @param columnConfig - Column configuration containing group settings
   * @param arrIndex - Index of the group configuration to process
   * @returns Translated label text or empty string if no label is configured
   */
  getTextValueColumnTotal(columnConfig: ColumnConfig, arrIndex: number): string {
    if (columnConfig.columnGroupConfigs[arrIndex].textValueKey) {
      return columnConfig.columnGroupConfigs[arrIndex].textValueTranslated;
    }
    return '';
  }

  /**
   * Retrieves and formats a field value for column totals with custom field path.
   * Supports both direct data access and map-based data retrieval.
   *
   * @param columnConfig - Column configuration for formatting
   * @param arrIndex - Index of the group configuration
   * @param data - Data object or collection containing values
   * @param mapKey - Key for accessing data from collections/maps
   * @param field - Specific field path to access
   * @returns Formatted field value ready for display
   */
  getFieldValueForFieldColumnTotal(columnConfig: ColumnConfig, arrIndex: number, data: any, mapKey: any, field: string) {
    if (mapKey !== null) {
      data = data.get(mapKey);
    }
    return AppHelper.getValueByPathWithField(this.gps, this.translateService, data, columnConfig,
      field);
  }

  /**
   * Retrieves and formats the configured field value for a column group.
   * Uses the fieldValue property from the group configuration to access data.
   *
   * @param columnConfig - Column configuration containing group settings
   * @param arrIndex - Index of the group configuration to process
   * @param data - Data object or collection containing values
   * @param mapKey - Key for accessing data from collections/maps
   * @returns Formatted field value ready for display
   */
  getFieldValueColumnTotal(columnConfig: ColumnConfig, arrIndex: number, data: any, mapKey: any) {
    return this.getFieldValueForFieldColumnTotal(columnConfig, arrIndex, data, mapKey,
      columnConfig.columnGroupConfigs[arrIndex].fieldValue);
  }

  /**
   * Initializes filtering by checking for filter columns and registering custom filters.
   * Should be called after column definitions are complete.
   */
  protected initializeFilters(): void {
    this.hasFilter = this.fields.filter(field => field.filterType).length > 0;
    if (this.hasFilter && this.filterService) {
      this.registerFilter();
    }
  }

  /**
   * Registers custom filter functions with PrimeNG FilterService.
   * Sets up date-specific filters and translates filter option labels.
   */
  protected registerFilter(): void {
    if (!this.filterService) {
      return;
    }
    const filters: FilterFN[] = [{name: 'gtNoFilter', fn: null},
      {name: 'gtIS', fn: this.isEqual.bind(this)},
      {name: 'gtSameOrBefore', fn: this.sameOrBefore.bind(this)},
      {name: 'gtSameAfter', fn: this.sameOrAfter.bind(this)},
    ];
    this.customSearchNames = filters.map(v => v.name);
    this.translateService.get('GT_FILTER').subscribe(tr => filters.forEach(f =>
      this.customMatchModeOptions.push({value: f.name, label: tr[f.name]})));
    filters.forEach(f => {
      this.filterService.register(f.name, (value, filter): boolean => {
        if (filter === undefined || filter === null || f.fn === null) {
          return true;
        }
        return f.fn(value, filter);
      });
    });
  }

  /**
   * Date filter function: same or before comparison.
   *
   * @param value - Date value from data
   * @param filter - Filter date to compare against
   * @returns True if value date is same or before filter date
   */
  protected sameOrBefore(value, filter): boolean {
    return moment(value).isSameOrBefore(filter, 'day');
  }

  /**
   * Date filter function: same or after comparison.
   *
   * @param value - Date value from data
   * @param filter - Filter date to compare against
   * @returns True if value date is same or after filter date
   */
  protected sameOrAfter(value, filter): boolean {
    return moment(value).isSameOrAfter(filter, 'day');
  }

  /**
   * Date filter function: exact date match.
   *
   * @param value - Date value from data
   * @param filter - Filter date to compare against
   * @returns True if dates are the same day
   */
  protected isEqual(value, filter): boolean {
    return moment(value).isSame(filter, 'day');
  }

  /**
   * Generates filter options for dropdown filters based on data content.
   * Creates sorted lists of unique values for columns with withOptions filter type.
   * Supports both translated and non-translated value filtering.
   *
   * @param data - Array of data objects to extract filter values from
   */
  public prepareFilter(data: any[]): void {
    this.fields.forEach(field => {
      if (field.filterType && field.filterType === FilterType.withOptions) {
        const valueLabelHtmlSelectOptions: ValueLabelHtmlSelectOptions[] = [];
        valueLabelHtmlSelectOptions.push(new ValueLabelHtmlSelectOptions('', ' '));
        if (field.translateValues && field.translatedValueMap) {
          Object.keys(field.translatedValueMap).sort((a, b) => field.translatedValueMap[a] < field.translatedValueMap[b]
            ? -1 : field.translatedValueMap[a] > field.translatedValueMap[b] ? 1 : 0)
            .forEach(key =>
              valueLabelHtmlSelectOptions.push(new ValueLabelHtmlSelectOptions(key, field.translatedValueMap[key]))
            );
        } else {
          const uniqueValuesSet = new Set(data.map(item => this.getValueByPath(item, field)));

          Array.from(uniqueValuesSet).sort((a, b) => a.toLowerCase() < b.toLowerCase() ? -1 :
            a.toLowerCase() > b.toLowerCase() ? 1 : 0).forEach(value => {
            valueLabelHtmlSelectOptions.push(new ValueLabelHtmlSelectOptions(value, value));
          });
        }
        field.filterValues = valueLabelHtmlSelectOptions;
      }
    });
  }

  /**
   * Creates additional filter fields for date columns. Adds formatted date strings with '$' suffix to enable date
   * filtering on DateNumeric columns using formatted display values.
   *
   * @param data - Array of data objects to process
   */
  createFilterField(data: any[]): void {
    const columnConfigs = this.fields.filter(columnConfig => columnConfig.filterType && columnConfig.dataType === DataType.DateNumeric);
    columnConfigs.forEach(cc => {
      const fieldName = cc.field + BaseSettings.FIELD_SUFFIX;
      data.forEach(item => item[fieldName] = this.getValueByPath(item, cc));
    });
  }

  /**
   * Handles date input filtering from calendar components.
   * Processes calendar input events and applies date filtering to the table.
   *
   * @param event - Calendar input event
   * @param columnConfig - Column configuration for the date field
   * @param table - PrimeNG table or tree table instance
   * @param calendar - Calendar component instance
   */
  public dateInputFilter(event, columnConfig: ColumnConfig, table: FilterableTable, calendar: any): void {
    if (calendar.value || !calendar.filled) {
      this.filterDate(calendar.value, columnConfig, table);
    }
  }

  /**
   * Applies date filtering to table columns.
   * Formats dates appropriately for different data types and applies table filters.
   *
   * @param event - Date value to filter by
   * @param columnConfig - Column configuration containing data type info
   * @param table - PrimeNG table or tree table instance to apply filter to
   */
  public filterDate(event, columnConfig: ColumnConfig, table: FilterableTable): void {
    if (event) {
      if (columnConfig.dataType === DataType.DateNumeric) {
        const dateString = moment(event).format(this.gps.getDateFormat());
        table.filter(dateString, columnConfig.field + BaseSettings.FIELD_SUFFIX, 'equals');
      } else {
        const dateStringUS = moment(event).format(BaseSettings.FORMAT_DATE_SHORT_NATIVE);
        table.filter(dateStringUS, columnConfig.field, 'equals');
      }
    } else {
      // Without value
      table.filter(null, columnConfig.field + (columnConfig.dataType === DataType.DateNumeric ? BaseSettings.FIELD_SUFFIX : ''), null);
    }
  }

  /**
   * Applies decimal/numeric filtering to table columns. Filters table based on exact numeric matches.
   *
   * @param event - Input event containing filter value
   * @param columnConfig - Column configuration for the numeric field
   * @param table - PrimeNG table or tree table instance to apply filter to
   */
  public filterDecimal(event, columnConfig: ColumnConfig, table: FilterableTable): void {
    table.filter(event.target.value, columnConfig.field, 'equals');
  }
}

/**
 * Interface for filter function definitions.
 * Defines structure for custom filter functions used in table filtering.
 */
interface FilterFN {
  /** Unique name for the filter function */
  name: string;
  /** Filter function implementation or null for pass-through filters */
  fn: (value, filter) => boolean;
}
