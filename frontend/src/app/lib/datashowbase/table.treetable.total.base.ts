import { inject } from '@angular/core';
import { ShowRecordConfigBase } from './show.record.config.base';
import { FilterService, MenuItem, SelectItem } from '@openng/optimus-ui/api';
import { DialogService } from '@openng/optimus-ui/dynamicdialog';
import { UserSettingsService } from '../services/user.settings.service';
import { ColumnVisibilityDialogComponent, ColumnVisibilityOwner } from './column-visibility-dialog.component';
import moment from 'moment';
import { ColumnConfig, ColumnGroupConfig } from './column.config';
import { AppHelper } from '../helper/app.helper';
import { Auditable } from '../entities/auditable';
import { BaseSettings } from '../base.settings';
import { FilterType } from './filter.type';
import { DataType } from '../dynamic-form/models/data.type';
import { ValueLabelHtmlSelectOptions } from './value.label.html.select.options';
import { FilterableTable } from './filterable.table.type';

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
  /** Only descendants that offer table filters resolve the filter service. */
  protected filterService?: FilterService;

  /**
   * Marker used to indicate when click events are consumed by child components.
   * Prevents parent components from processing already-handled click events. It lives here rather than on
   * TableConfigBase because a tree table can host a nested table too, and both hierarchies must agree on the literal.
   */
  readonly consumedGT = 'consumedGT';

  /**
   * Exposes FilterType enum for use in Angular templates.
   * Required for template-based filter type comparisons.
   */
  FilterType: typeof FilterType = FilterType;

  /** Flag indicating whether any columns have filtering enabled */
  public hasFilter = false;

  /** Custom filter options for Optimus table/tree table filter dropdowns */
  customMatchModeOptions: SelectItem[] = [];

  /** Names of registered custom filter functions */
  customSearchNames: string[] = [];

  protected usersettingsService: UserSettingsService = inject(UserSettingsService);

  /**
   * Number of leading visible columns that stay in place while the table is scrolled horizontally. The view hands it to
   * ConfigurableTableComponent or ConfigurableTreeTableComponent, for example [frozenColumnCount]="frozenColumnCount".
   * Only tables which set {@link frozenColumnsSelectable} offer it in the column visibility dialog and save it.
   */
  frozenColumnCount = 0;

  /**
   * Opt-in for the slider of the column visibility dialog that sets {@link frozenColumnCount}. Only a table bound to
   * a scrollable ConfigurableTableComponent or ConfigurableTreeTableComponent can make use of it.
   */
  protected frozenColumnsSelectable = false;

  /**
   * Key under which readTableDefinition found the column settings of this table. The column visibility dialog saves
   * them under it as soon as it closes; otherwise a choice made there would only be saved when the component is
   * destroyed, which a page reload never does.
   */
  private tableDefinitionKey: string | null = null;

  /**
   * Persists the column visibility and, for a table that offers it, the number of frozen columns to the user settings.
   * Settings that only a kind of table has are saved by {@link writeExtraTableSettings}.
   *
   * @param key - Unique identifier for this table configuration
   */
  writeTableDefinition(key: string): void {
    const visibleColumns: Record<string, boolean>[] = [];
    this.fields.forEach((field) => {
      if (field.headerKey != null) visibleColumns.push({ [field.headerKey]: field.visible });
    });
    this.usersettingsService.saveArray(key, visibleColumns);
    if (this.frozenColumnsSelectable) {
      this.usersettingsService.saveSingleValue(key + '.frozen', this.frozenColumnCount);
    }
    this.writeExtraTableSettings(key);
  }

  /**
   * Restores the column visibility and the number of frozen columns from the user settings. The visibility is only
   * applied when the column structure is unchanged since it was saved (checksum validation).
   *
   * @param key - Unique identifier for this table configuration
   */
  readTableDefinition(key: string): void {
    this.tableDefinitionKey = key;
    if (this.hasSameChecksum(key)) {
      const readFields: Record<string, boolean>[] = this.usersettingsService.readArray(key);
      if (readFields != null && readFields.length > 0) {
        const fieldObject: Record<string, boolean> = Object.assign({}, ...readFields);
        this.fields.forEach((field) => {
          // Dynamic columns may be newer than the saved visibility settings.
          if (field.headerKey != null && Object.hasOwn(fieldObject, field.headerKey))
            field.visible = fieldObject[field.headerKey];
        });
      }
    }
    this.readExtraTableSettings(key);
    if (this.frozenColumnsSelectable) {
      const storedFrozen = this.usersettingsService.readSingleValue(key + '.frozen');
      this.frozenColumnCount =
        storedFrozen > 0 ? Math.min(storedFrozen, ColumnVisibilityDialogComponent.MAX_FROZEN_COLUMNS) : 0;
    }
    this.notifyViewChanged();
  }

  /**
   * Hook for settings that only a kind of table saves together with its columns, such as the rows per page of a flat
   * table. Does nothing by default.
   *
   * @param _key - Unique identifier for this table configuration
   */
  protected writeExtraTableSettings(_key: string): void {}

  /**
   * Hook restoring the settings saved by {@link writeExtraTableSettings}. Does nothing by default.
   *
   * @param _key - Unique identifier for this table configuration
   */
  protected readExtraTableSettings(_key: string): void {}

  /**
   * Creates a single menu item that opens a dialog for column visibility management.
   * Returns a menu item only if there are toggleable columns available.
   *
   * @returns Array containing a single menu item for column visibility dialog, or empty array
   */
  getColumnsShow(): MenuItem[] {
    const hasToggleableColumns = this.fields.some((field) => field.changeVisibility);
    if (hasToggleableColumns) {
      return [
        {
          label: 'ON_OFF_COLUMNS' + BaseSettings.DIALOG_MENU_SUFFIX,
          icon: 'fa fa-columns',
          command: () => this.openColumnVisibilityDialog()
        }
      ];
    }
    return [];
  }

  /**
   * Opens a dialog for toggling column visibility.
   * The dialog shows checkboxes for all columns with changeVisibility enabled.
   * Changes are applied in real-time as checkboxes are toggled and saved when the dialog closes, provided the table
   * reads its settings through readTableDefinition. A table that set {@link frozenColumnsSelectable} additionally gets
   * a slider for the number of columns fixed on the left.
   */
  openColumnVisibilityDialog(): void {
    if (!this.injector) {
      console.warn('Injector not available for opening column visibility dialog');
      return;
    }
    const dialogService = this.injector.get(DialogService);
    this.translateService.get('ON_OFF_COLUMNS').subscribe((header) => {
      dialogService
        .open(ColumnVisibilityDialogComponent, {
          header,
          width: '300px',
          modal: true,
          draggable: true,
          closable: true,
          closeOnEscape: true,
          position: 'right',
          data: {
            fields: this.fields,
            frozenColumns: this.frozenColumnsSelectable ? this : null,
            owner: this satisfies ColumnVisibilityOwner
          }
        })
        ?.onClose.subscribe(() => this.tableDefinitionKey && this.writeTableDefinition(this.tableDefinitionKey));
    });
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
    const existingChecksum = localStorage.getItem(keyChecksum);
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
    let sum = 0;
    this.fields.forEach((field) => {
      const fieldName = field.field.replace(/(\.de|\.en)$/, '');
      sum = sum + 65027 / this.charSum(fieldName);
    });
    return ('' + sum).slice(0, 16);
  }

  /**
   * Calculates character sum for hash generation.
   * Uses character codes and position weights to create consistent hash values.
   *
   * @param s - String to calculate hash for
   * @returns Numeric hash value
   */
  private charSum(s: string): number {
    let sum = 0;
    for (let i = 0; i < s.length; i++) {
      sum += s.charCodeAt(i) * (i + 1);
    }
    return sum;
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
    return (this.getValueColumnTotal(columnConfig, arrIndex, data, mapKey) ?? '').startsWith('-');
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
            value += value.length > 0 ? ' ' + dataValue : dataValue;
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
  isColumnTotalByRowIndexMinus(
    columnConfig: ColumnConfig,
    arrIndex: number,
    groupChangeIndexMap: any,
    rowIndex: number
  ): boolean {
    return (this.getValueColumnTotalByRowIndex(columnConfig, arrIndex, groupChangeIndexMap, rowIndex) ?? '').startsWith(
      '-'
    );
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
  getValueColumnTotalByRowIndex(
    columnConfig: ColumnConfig,
    arrIndex: number,
    groupChangeIndexMap: any,
    rowIndex: number
  ): string {
    let value = '';
    if (columnConfig.columnGroupConfigs && arrIndex < columnConfig.columnGroupConfigs.length) {
      if (columnConfig.columnGroupConfigs[arrIndex].fieldTextFN) {
        value = columnConfig.columnGroupConfigs[arrIndex].fieldTextFN(
          columnConfig,
          arrIndex,
          groupChangeIndexMap,
          rowIndex
        );
      } else {
        value = this.getTextValueColumnTotal(columnConfig, arrIndex);
        if (columnConfig.columnGroupConfigs[arrIndex].fieldValue) {
          const dataValue = this.getFieldValueColumnTotal(columnConfig, arrIndex, groupChangeIndexMap, rowIndex);
          if (dataValue) {
            value += value.length > 0 ? ' ' + dataValue : dataValue;
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
    return (
      this.gps.isUiShowMyProperty() &&
      columnConfig.templateName === BaseSettings.OWNER_TEMPLATE &&
      this.gps.isEntityCreatedByUser(entity)
    );
  }

  /**
   * Retrieves the translated label text for a column group section.
   *
   * @param columnConfig - Column configuration containing group settings
   * @param arrIndex - Index of the group configuration to process
   * @returns Translated label text or empty string if no label is configured
   */
  getTextValueColumnTotal(columnConfig: ColumnConfig, arrIndex: number): string {
    const group = columnConfig.columnGroupConfigs?.[arrIndex];
    if (group?.textValueKey) {
      // Empty until the asynchronous translation arrives; a group row may render before that.
      return group.textValueTranslated ?? '';
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
   * @param currencyFieldOverride - Optional path to the currency code within the group data object for
   *                                currency-specific precision formatting
   * @returns Formatted field value ready for display
   */
  getFieldValueForFieldColumnTotal(
    columnConfig: ColumnConfig,
    arrIndex: number,
    data: any,
    mapKey: any,
    field: string,
    currencyFieldOverride?: string
  ) {
    if (mapKey !== null) {
      data = data.get(mapKey);
    }
    return AppHelper.getValueByPathWithField(
      this.gps,
      this.translateService,
      data,
      columnConfig,
      field,
      currencyFieldOverride
    );
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
    const group = columnConfig.columnGroupConfigs?.[arrIndex];
    if (group?.fieldValue == null) return null;
    return this.getFieldValueForFieldColumnTotal(
      columnConfig,
      arrIndex,
      data,
      mapKey,
      group.fieldValue,
      group.currencyPrecisionField
    );
  }

  /**
   * Initializes filtering by checking for filter columns and registering custom filters.
   * Should be called after column definitions are complete.
   */
  protected initializeFilters(): void {
    this.hasFilter = this.fields.filter((field) => field.filterType).length > 0;
    if (this.hasFilter && this.filterService) {
      this.registerFilter();
    }
  }

  /**
   * Registers custom filter functions with Optimus FilterService.
   * Sets up date-specific filters and translates filter option labels.
   */
  protected registerFilter(): void {
    const filterService = this.filterService;
    if (!filterService) {
      return;
    }
    const filters: FilterFN[] = [
      { name: 'gtNoFilter', fn: null },
      { name: 'gtIS', fn: this.isEqual.bind(this) },
      { name: 'gtSameOrBefore', fn: this.sameOrBefore.bind(this) },
      { name: 'gtSameAfter', fn: this.sameOrAfter.bind(this) }
    ];
    this.customSearchNames = filters.map((v) => v.name);
    this.translateService
      .get('GT_FILTER')
      .subscribe((tr) =>
        filters.forEach((f) => this.customMatchModeOptions.push({ value: f.name, label: tr[f.name] }))
      );
    filters.forEach((f) => {
      filterService.register(f.name, (value: moment.MomentInput, filter: moment.MomentInput): boolean => {
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
  protected sameOrBefore(value: moment.MomentInput, filter: moment.MomentInput): boolean {
    return moment(value).isSameOrBefore(filter, 'day');
  }

  /**
   * Date filter function: same or after comparison.
   *
   * @param value - Date value from data
   * @param filter - Filter date to compare against
   * @returns True if value date is same or after filter date
   */
  protected sameOrAfter(value: moment.MomentInput, filter: moment.MomentInput): boolean {
    return moment(value).isSameOrAfter(filter, 'day');
  }

  /**
   * Date filter function: exact date match.
   *
   * @param value - Date value from data
   * @param filter - Filter date to compare against
   * @returns True if dates are the same day
   */
  protected isEqual(value: moment.MomentInput, filter: moment.MomentInput): boolean {
    return moment(value).isSame(filter, 'day');
  }

  /**
   * Generates filter options for the multi select filters based on data content.
   * Creates sorted lists of unique values for columns with withOptions filter type.
   * Supports both translated and non-translated value filtering.
   *
   * The list holds no empty entry for "no filter": the multi select is reset with its clear icon or by removing every
   * selected value, an empty entry would only be a selectable value that filters for the empty string.
   *
   * @param data - Array of data objects to extract filter values from
   */
  public prepareFilter(data: any[]): void {
    this.fields.forEach((field) => {
      if (field.filterType && field.filterType === FilterType.withOptions) {
        const valueLabelHtmlSelectOptions: ValueLabelHtmlSelectOptions[] = [];
        if (field.translateValues && field.translatedValueMap) {
          Object.keys(field.translatedValueMap)
            .sort((a, b) =>
              field.translatedValueMap[a] < field.translatedValueMap[b]
                ? -1
                : field.translatedValueMap[a] > field.translatedValueMap[b]
                  ? 1
                  : 0
            )
            .forEach((key) =>
              valueLabelHtmlSelectOptions.push(new ValueLabelHtmlSelectOptions(key, field.translatedValueMap[key]))
            );
        } else {
          const uniqueValuesSet = new Set(
            data.map((item) => this.getValueByPath(item, field)).filter((v) => v != null)
          );

          Array.from(uniqueValuesSet)
            .sort((a, b) => (a.toLowerCase() < b.toLowerCase() ? -1 : a.toLowerCase() > b.toLowerCase() ? 1 : 0))
            .forEach((value) => {
              valueLabelHtmlSelectOptions.push(new ValueLabelHtmlSelectOptions(value, value));
            });
        }
        field.filterValues = valueLabelHtmlSelectOptions;
      }
    });
    this.notifyViewChanged();
  }

  /**
   * Creates additional filter fields for date columns. Adds formatted date strings with '$' suffix to enable date
   * filtering on DateNumeric columns using formatted display values.
   *
   * Columns whose displayed value is produced by a fieldValueFN get a shadow property as well, see
   * {@link createFilterFieldForValueFN}.
   *
   * @param data - Array of data objects to process
   */
  createFilterField(data: any[]): void {
    const columnConfigs = this.fields.filter(
      (columnConfig) => columnConfig.filterType && columnConfig.dataType === DataType.DateNumeric
    );
    columnConfigs.forEach((cc) => {
      const fieldName = cc.field + BaseSettings.FIELD_SUFFIX;
      data.forEach((item) => (item[fieldName] = this.getValueByPath(item, cc)));
    });
    this.createFilterFieldForValueFN(data);
  }

  /**
   * Materializes the displayed value of columns that transform their value with a fieldValueFN into a shadow property,
   * and points {@link ColumnConfig.filterField} at it. Without this, filtering such a column would match against the
   * raw data value while the user sees, and filters for, the transformed one.
   *
   * The shadow property is written flat on the row with all dots of the field path replaced, because Optimus resolves
   * a dotted field name by walking the object graph instead of reading a property of that literal name. Columns with
   * translated values are excluded, they already carry their own '$' field through the translated value store.
   *
   * @param data - Array of data objects to process
   */
  private createFilterFieldForValueFN(data: any[]): void {
    const columnConfigs = this.fields.filter(
      (columnConfig) => columnConfig.filterType && columnConfig.fieldValueFN && !columnConfig.translateValues
    );
    columnConfigs.forEach((cc) => {
      const filterField = cc.field.replace(/\./g, '_') + BaseSettings.FIELD_SUFFIX;
      cc.filterField = filterField;
      data.forEach((item) => (item[filterField] = this.getValueByPath(item, cc)));
    });
  }

  /**
   * Handles date input filtering from calendar components.
   * Processes calendar input events and applies date filtering to the table.
   *
   * @param event - Calendar input event
   * @param columnConfig - Column configuration for the date field
   * @param table - Optimus table or tree table instance
   * @param calendar - Calendar component instance
   */
  public dateInputFilter(event: unknown, columnConfig: ColumnConfig, table: FilterableTable, calendar: any): void {
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
   * @param table - Optimus table or tree table instance to apply filter to
   */
  public filterDate(event: moment.MomentInput, columnConfig: ColumnConfig, table: FilterableTable): void {
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
      table.filter(
        null,
        columnConfig.field + (columnConfig.dataType === DataType.DateNumeric ? BaseSettings.FIELD_SUFFIX : ''),
        null
      );
    }
  }

  /**
   * Applies decimal/numeric filtering to table columns. Filters table based on exact numeric matches.
   *
   * @param event - Input event containing filter value
   * @param columnConfig - Column configuration for the numeric field
   * @param table - Optimus table or tree table instance to apply filter to
   */
  public filterDecimal(
    event: { target: { value: string | string[] } },
    columnConfig: ColumnConfig,
    table: FilterableTable
  ): void {
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
  fn: ((value: moment.MomentInput, filter: moment.MomentInput) => boolean) | null;
}
