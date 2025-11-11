import {TranslateService} from '@ngx-translate/core';
import {FilterService, MenuItem, SelectItem, SortEvent, SortMeta} from 'primeng/api';
import {UserSettingsService} from '../services/user.settings.service';
import {Helper} from '../helper/helper';
import {ColumnConfig} from './column.config';
import {Table} from 'primeng/table';
import moment from 'moment';
import {FilterType} from './filter.type';
import {ValueLabelHtmlSelectOptions} from './value.label.html.select.options';
import {DataType} from '../dynamic-form/models/data.type';
import {TableTreetableTotalBase} from './table.treetable.total.base';
import {BaseSettings} from '../base.settings';
import {GlobalparameterService} from '../services/globalparameter.service';

/**
 * Abstract base class for displaying data in PrimeNG table format with comprehensive
 * filtering, sorting, column management, and persistence capabilities.
 *
 * This class provides core table functionality without menu support or data editing.
 * It handles column configuration, user preferences persistence, custom filtering,
 * multi-column sorting, and dynamic column visibility management.
 *
 * Key features:
 * - Column visibility management with persistence
 * - Custom filter registration and date-specific filtering
 * - Multi-column sorting with translated value support
 * - Table configuration checksum validation
 * - Pagination and column resizing support
 * - Group field management for  table layouts
 */
export abstract class TableConfigBase extends TableTreetableTotalBase {
  /**
   * Exposes FilterType enum for use in Angular templates.
   * Required for template-based filter type comparisons.
   */
  FilterType: typeof FilterType = FilterType;

  /** Locale string for form and date formatting */
  formLocale: string;

  /**
   * Marker used to indicate when click events are consumed by child components.
   * Prevents parent components from processing already-handled click events.
   */
  readonly consumedGT = 'consumedGT';

  /** Flag indicating whether any columns have filtering enabled */
  public hasFilter = false;

  /** Custom filter options for PrimeNG table filter dropdowns */
  customMatchModeOptions: SelectItem[] = [];

  /** Names of registered custom filter functions */
  customSearchNames: string[];

  /** Number of rows displayed per page */
  rowsPerPage: number;

  /** Index of the first row on the current page */
  firstRowIndexOnPage = 0;

  /** Array of sort metadata for multi-column sorting */
  multiSortMeta: SortMeta[] = [];

  /** Backup array for restoring column visibility states */
  private visibleRestore: boolean[] = [];

  /**
   * Creates a new table configuration base.
   *
   * @param filterService - PrimeNG service for registering custom filters
   * @param usersettingsService - Service for persisting user table preferences
   * @param translateService - Angular translation service
   * @param gps - Global parameter base service for locale and formatting
   */
  protected constructor(protected filterService: FilterService,
    protected usersettingsService: UserSettingsService,
    translateService: TranslateService,
    gps: GlobalparameterService) {
    super(translateService, gps);
    this.formLocale = gps.getLocale();
  }

  /**
   * Gets the count of currently visible columns.
   *
   * @returns Number of columns with visible property set to true
   */
  get numberOfVisibleColumns(): number {
    return this.fields.filter(field => field.visible).length;
  }

  /**
   * Gets processed group fields with calculated column spans.
   * Automatically calculates and adjusts colspan values for group configurations
   * based on the position of other grouped columns.
   *
   * @returns Array of column configurations with properly calculated colspan values
   */
  get groupFields(): ColumnConfig[] {
    const groupFields: ColumnConfig[] = [];
    for (let i = 0; i < this.fields.length; i++) {
      groupFields.push(this.fields[i]);
      if (this.fields[i].columnGroupConfigs && this.fields[i].columnGroupConfigs[0].colspan) {
        const nextUsedGroupColumn = this.getNextUsedGroupColumnIndex(i + 1);
        const newI = Math.min(i + this.fields[i].columnGroupConfigs[0].colspan, nextUsedGroupColumn);
        this.fields[i].columnGroupConfigs[0].colspan = newI - i;
        i = newI - 1;
      }
    }
    return groupFields;
  }

  /**
   * Initializes table configuration including filtering and translations.
   * Must be called after column definitions are complete to set up proper filtering
   * and header translation functionality.
   */
  prepareTableAndTranslate(): void {
    this.hasFilter = this.fields.filter(field => field.filterType).length > 0;
    if (this.hasFilter) {
      this.registerFilter();
    }
    this.translateHeadersAndColumns();
  }

  /**
   * Persists current table column visibility configuration to user settings.
   * Saves which columns are visible/hidden for restoration on next load.
   *
   * @param key - Unique identifier for this table configuration
   */
  writeTableDefinition(key: string) {
    const visibleColumns: any[] = [];
    this.fields.forEach(field => {
        visibleColumns.push({[field.headerKey]: field.visible});
      }
    );
    this.usersettingsService.saveArray(key, visibleColumns);
  }

  /**
   * Restores table column visibility from persisted user settings.
   * Only applies saved settings if the table structure hasn't changed (checksum validation).
   *
   * @param key - Unique identifier for this table configuration
   */
  readTableDefinition(key: string): void {
    if (this.hasSameChecksum(key)) {
      const readFields: any[] = this.usersettingsService.readArray(key);
      if (readFields != null && readFields.length > 0) {
        const fieldObject: any = Object.assign({}, ...readFields);
        this.fields.forEach(field => {
          field.visible = fieldObject[field.headerKey];
        });
      }
    }
  }

  /**
   * Validates that table structure hasn't changed since last save.
   * Compares checksum of current field names with stored checksum to detect
   * backend changes that would invalidate saved column preferences.
   *
   * @param key - Base key for table configuration
   * @returns True if table structure is unchanged, false if recreation needed
   */
  private hasSameChecksum(key: string): boolean {
    const keyChecksum = key + '.checksum';
    const existingChecksum: string = localStorage.getItem(keyChecksum);
    const newChecksum: string = this.calculateHashAllOverFieldNames();
    localStorage.setItem(keyChecksum, newChecksum);
    return existingChecksum === newChecksum;
  }

  /**
   * Calculates hash of all field names for structure change detection.
   * Creates a numeric hash based on field names to detect when backend
   * adds/removes/renames fields that would break saved configurations.
   *
   * @returns String representation of calculated hash
   */
  private calculateHashAllOverFieldNames(): string {
    let i: number;
    let sum: number = 0;
    this.fields.forEach(field => {
        let fieldName = field.field.replace(/(\.de|\.en)$/, '');
        let cs = this.charSum(fieldName);
        sum = sum + (65027 / cs);
      }
    );
    return ('' + sum).slice(0, 16)
  }

  /**
   * Calculates character sum for hash generation.
   * Uses character codes and position weights to create consistent hash values.
   *
   * @param s - String to calculate hash for
   * @returns Numeric hash value
   * @private
   */
  private charSum(s: string): number {
    let i: number;
    let sum = 0;
    for (i = 0; i < s.length; i++) {
      sum += (s.charCodeAt(i) * (i + 1));
    }
    return sum;
  }

  /**
   * Generates filter options for dropdown filters based on data content.
   * Creates sorted lists of unique values for columns with withOptions filter type.
   * Supports both translated and non-translated value filtering.
   *
   * @param data - Array of data objects to extract filter values from
   */
  public prepareFilter(data: any[]) {
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
   * @param table - PrimeNG table instance
   * @param calendar - Calendar component instance
   */
  public dateInputFilter(event, columnConfig: ColumnConfig, table: Table, calendar: any): void {
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
   * @param table - PrimeNG table instance to apply filter to
   */
  public filterDate(event, columnConfig: ColumnConfig, table: Table): void {
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
   * @param table - PrimeNG table instance to apply filter to
   */
  public filterDecimal(event, columnConfig: ColumnConfig, table: Table): void {
    table.filter(event.target.value, columnConfig.field, 'equals');
    // startsWith
  }

  /**
   * Restores column visibility to previous saved state.
   * Uses the visibleRestore backup array to reset column visibility.
   */
  changeToUserSetting() {
    let i = 0;
    this.fields.forEach(columnConfig => columnConfig.visible = this.visibleRestore[i++]);
  }

  /**
   * Translates header for a specific column configuration.
   * Updates the headerTranslated property for a single column.
   *
   * @param columConfig - Column configuration to translate header for
   */
  setFieldHeaderTranslation(columConfig: ColumnConfig): void {
    this.translateHeaders([columConfig.headerKey], [columConfig]);
  }

  /**
   * Creates translated value store and filter fields for the data.
   * Combines translation preparation with filter field creation for efficient setup.
   *
   * @param data - Array of data objects to process for translation and filtering
   */
  createTranslatedValueStoreAndFilterField(data: any[]): void {
    this.createTranslatedValueStore(data);
    this.createFilterField(data);
  }

  /**
   * Handles pagination events from PrimeNG table.
   * Updates current page information for pagination state management.
   *
   * @param event - PrimeNG pagination event containing page info
   */
  onPage(event) {
    this.rowsPerPage = event.rows;
    this.firstRowIndexOnPage = event.first;
  }

  /**
   * Handles column resize events from PrimeNG table.
   * Updates column width in configuration based on user resize actions.
   *
   * @param event - PrimeNG column resize event
   */
  onColResize(event) {
    const columnConfig = this.getColumnConfigByHeaderTranslated(event.element.innerText.trim());
    columnConfig.width = event.element.style.width;
  }

  /**
   * Gets CSS style object for column width.
   *
   * @param field - Column configuration
   * @returns Style object with width property or empty object
   */
  getStyle(field: ColumnConfig): any {
    return (field.width) ? {width: field.width + 'px'} : {};
  }

  /**
   * Handles custom sorting for PrimeNG table.
   * Delegates to single or multi-column sorting based on sort mode.
   *
   * @param event - PrimeNG sort event containing sort configuration
   */
  customSort(event: SortEvent): void {
    if (event.mode === 'single') {
      this.customSortMultiple(event.data, [{field: event.field, order: event.order}]);
    } else {
      this.customSortMultiple(event.data, event.multiSortMeta);
    }
  }

  /**
   * Performs multi-column sorting with support for translated values.
   * Handles null values, string comparison with locale support, and numeric sorting.
   * Uses translated field values when available for proper alphabetical sorting.
   *
   * @param data - Array of data to sort
   * @param sortMeta - Array of sort metadata defining sort columns and directions
   */
  customSortMultiple(data: any, sortMeta: SortMeta[]): void {
    data.sort((data1, data2) => {
      let i = 0;
      let result = null;
      do {
        const columnConfig = this.getColumnConfigByField(sortMeta[i].field);
        const isDirectAccess = columnConfig.translateValues || columnConfig.fieldValueFN;
        const value1 = isDirectAccess ? this.getValueByPath(data1, columnConfig)
          : Helper.getValueByPath(data1, columnConfig.field);
        const value2 = isDirectAccess ? this.getValueByPath(data2, columnConfig)
          : Helper.getValueByPath(data2, columnConfig.field);

        if (value1 == null && value2 != null) {
          result = -1;
        } else if (value1 != null && value2 == null) {
          result = 1;
        } else if (value1 == null && value2 == null) {
          result = 0;
        } else if (typeof value1 === 'string' && typeof value2 === 'string') {
          result = value1.localeCompare(value2);
        } else {
          result = (value1 < value2) ? -1 : (value1 > value2) ? 1 : 0;
        }
        i++;
      } while (i < sortMeta.length && result === 0);

      return (sortMeta[i - 1].order * result);
    });
  }

  /**
   * Creates menu items for column show/hide functionality.
   * Generates menu items allowing users to toggle column visibility.
   *
   * @returns Array of menu items for column visibility control
   */
  getColumnsShow(): MenuItem[] {
    const columnsMenuItems: MenuItem[] = [];
    this.fields.forEach(field => {
      if (field.changeVisibility) {
        columnsMenuItems.push({
          label: field.headerKey,
          icon: (field.visible) ? BaseSettings.ICONNAME_SQUARE_CHECK : BaseSettings.ICONNAME_SQUARE_EMTPY,
          command: (event) => this.handleHideShowColumn(event, field)
        });
      }
    });
    // AppHelper.translateMenuItems(columnsMenuItems, this.translateService);
    return columnsMenuItems;
  }

  /**
   * Shows or hides columns by header key. Programmatically controls column visibility for specific headers.
   *
   * @param fileHeader - Header key of columns to show/hide
   * @param visible - Whether columns should be visible
   */
  hideShowColumnByFileHeader(fileHeader: string, visible: boolean) {
    this.fields.filter(field => field.headerKey === fileHeader).forEach(field => field.visible = visible);
  }

  /**
   * Handles individual column show/hide toggle. Updates column visibility and menu icon when user clicks column toggle.
   *
   * @param event - Menu click event
   * @param field - Column configuration to toggle
   */
  handleHideShowColumn(event, field: ColumnConfig) {
    field.visible = !field.visible;
    event.item.icon = (field.visible) ? BaseSettings.ICONNAME_SQUARE_CHECK : BaseSettings.ICONNAME_SQUARE_EMTPY;
    //  this.changeDetectionStrategy.markForCheck();
  }

  /**
   * Gets menu options for column show/hide functionality.
   * Creates hierarchical menu structure for column visibility management.
   *
   * @returns Array of menu items with column options, or null if no options available
   */
  getMenuShowOptions(): MenuItem[] {
    const items = this.getColumnsShow();
    return items.length > 0 ? [{label: 'ON_OFF_COLUMNS', items}] : null;
  }

  /**
   * Registers custom filter functions with PrimeNG FilterService.
   * Sets up date-specific filters and translates filter option labels.
   */
  private registerFilter(): void {
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
  private sameOrBefore(value, filter): boolean {
    return moment(value).isSameOrBefore(filter, 'day');
  }

  /**
   * Date filter function: same or after comparison.
   *
   * @param value - Date value from data
   * @param filter - Filter date to compare against
   * @returns True if value date is same or after filter date
   */
  private sameOrAfter(value, filter): boolean {
    return moment(value).isSameOrAfter(filter, 'day');
  }

  /**
   * Date filter function: exact date match.
   *
   * @param value - Date value from data
   * @param filter - Filter date to compare against
   * @returns True if dates are the same day
   */
  private isEqual(value, filter): boolean {
    return moment(value).isSame(filter, 'day');
  }

  /**
   * Finds the next column index that has group configurations.
   * Used for calculating column spans in group field processing.
   *
   * @param startIndex - Index to start searching from
   * @returns Index of next grouped column or last column index
   */
  private getNextUsedGroupColumnIndex(startIndex: number): number {
    for (let i = startIndex; i < this.fields.length; i++) {
      if (this.fields[i].columnGroupConfigs) {
        return i;
      }
    }
    return this.fields.length - 1;
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

/**
 * Helper class for sort field definitions.
 * Stores field name and sort order for sorting operations.
 */
class SortFields {
  /**
   * Creates a new sort field definition.
   *
   * @param fieldName - Name of the field to sort by
   * @param order - Sort order (1 for ascending, -1 for descending)
   */
  constructor(public fieldName: string, public order: number) {
  }

}
