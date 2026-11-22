import { inject } from '@angular/core';
import { FilterService, MenuItem, SortEvent, SortMeta } from '@openng/optimus-ui/api';
import { TableColResizeEvent, TablePageEvent } from '@openng/optimus-ui/table';
import { Helper } from '../helper/helper';
import { ColumnConfig } from './column.config';
import { TableTreetableTotalBase } from './table.treetable.total.base';

/**
 * Abstract base class for displaying data in Optimus table format with comprehensive
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
  protected override filterService = inject(FilterService);

  /** Locale string for form and date formatting */
  formLocale: string;

  /** Number of rows displayed per page */
  rowsPerPage?: number;

  /** Index of the first row on the current page */
  firstRowIndexOnPage = 0;

  /** Array of sort metadata for multi-column sorting */
  multiSortMeta: SortMeta[] = [];

  /**
   * Whether the filter row is currently displayed. Only meaningful for tables that set
   * {@link filterRowToggleable}; the view must combine it with hasFilter, for example
   * [hasFilter]="hasFilter &amp;&amp; showFilterRow".
   */
  showFilterRow = false;

  /**
   * Opt-in for the show menu entry that turns the filter row on and off. Tables that display their filter row
   * permanently leave this false, so their menu and behaviour stay unchanged.
   */
  protected filterRowToggleable = false;

  /** Backup array for restoring column visibility states */
  private visibleRestore: boolean[] = [];

  /**
   * Stable sort callback for the customSortFn input of the table hosts. It calls customSort, so overrides in
   * subclasses keep working; unlike customSort.bind(this) it is the same function on every check.
   */
  readonly customSorter = (event: SortEvent) => this.customSort(event);

  /** Group fields with their colspan, computed once per column layout instead of while the template renders */
  private groupFieldsCache: { fields: ColumnConfig[]; length: number; groupFields: ColumnConfig[] } | null = null;

  /**
   * Creates a new table configuration base.
   */
  constructor() {
    super();
    this.formLocale = this.gps.getLocale();
  }

  /**
   * Gets the count of currently visible columns.
   *
   * @returns Number of columns with visible property set to true
   */
  get numberOfVisibleColumns(): number {
    return this.fields.filter((field) => field.visible).length;
  }

  /**
   * Gets processed group fields with calculated column spans.
   * Automatically calculates and adjusts colspan values for group configurations
   * based on the position of other grouped columns.
   *
   * @returns Array of column configurations with properly calculated colspan values
   */
  get groupFields(): ColumnConfig[] {
    let cache = this.groupFieldsCache;
    if (!cache || cache.fields !== this.fields || cache.length !== this.fields.length) {
      this.groupFieldsCache = cache = {
        fields: this.fields,
        length: this.fields.length,
        groupFields: this.computeGroupFields()
      };
    }
    return cache.groupFields;
  }

  /** Collects the group fields and adjusts their colspan to the position of the next grouped column. */
  private computeGroupFields(): ColumnConfig[] {
    const groupFields: ColumnConfig[] = [];
    for (let i = 0; i < this.fields.length; i++) {
      groupFields.push(this.fields[i]);
      const group = this.fields[i].columnGroupConfigs?.[0];
      if (group?.colspan) {
        const nextUsedGroupColumn = this.getNextUsedGroupColumnIndex(i + 1);
        const newI = Math.min(i + group.colspan, nextUsedGroupColumn);
        group.colspan = newI - i;
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
    this.initializeFilters();
    this.translateHeadersAndColumns();
  }

  /**
   * Saves the settings only a flat table has, the rows per page and the state of the filter row, together with the
   * column visibility and frozen columns that {@link writeTableDefinition} of the base saves.
   *
   * @param key - Unique identifier for this table configuration
   */
  protected override writeExtraTableSettings(key: string): void {
    if (this.rowsPerPage) {
      this.usersettingsService.saveSingleValue(key + '.rows', this.rowsPerPage);
    }
    if (this.filterRowToggleable) {
      this.usersettingsService.saveSingleValue(key + '.filterrow', this.showFilterRow);
    }
  }

  /**
   * Restores the rows per page and the state of the filter row saved by {@link writeExtraTableSettings}.
   *
   * @param key - Unique identifier for this table configuration
   */
  protected override readExtraTableSettings(key: string): void {
    const storedRows = this.usersettingsService.readSingleValue(key + '.rows');
    if (storedRows != null && storedRows > 0) {
      this.rowsPerPage = storedRows;
    }
    if (this.filterRowToggleable) {
      this.showFilterRow = this.usersettingsService.readSingleValue(key + '.filterrow') === true;
    }
  }

  /**
   * Restores column visibility to previous saved state.
   * Uses the visibleRestore backup array to reset column visibility.
   */
  changeToUserSetting() {
    let i = 0;
    this.fields.forEach((columnConfig) => (columnConfig.visible = this.visibleRestore[i++]));
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
   * Handles pagination events from Optimus table.
   * Updates current page information for pagination state management.
   *
   * @param event - Optimus pagination event containing page info
   */
  onPage(event: TablePageEvent) {
    this.rowsPerPage = event.rows;
    this.firstRowIndexOnPage = event.first;
  }

  /**
   * Handles column resize events from Optimus table.
   * Updates column width in configuration based on user resize actions.
   *
   * @param event - Optimus column resize event
   */
  onColResize(event: TableColResizeEvent) {
    const columnConfig = this.getColumnConfigByHeaderTranslated(event.element.innerText.trim());
    if (!columnConfig) return;
    columnConfig.width = event.element.style.width;
    this.notifyViewChanged();
  }

  /**
   * Gets CSS style object for column width.
   *
   * @param field - Column configuration
   * @returns Style object with width property or empty object
   */
  getStyle(field: ColumnConfig): any {
    return field.width ? { width: field.width + 'px' } : {};
  }

  /**
   * Handles custom sorting for Optimus table.
   * Delegates to single or multi-column sorting based on sort mode.
   *
   * @param event - Optimus sort event containing sort configuration
   */
  customSort(event: SortEvent): void {
    if (event.mode === 'single') {
      if (event.field == null || event.order == null) return;
      this.customSortMultiple(event.data, [{ field: event.field, order: event.order }]);
    } else {
      this.customSortMultiple(event.data, event.multiSortMeta ?? []);
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
    // Guard against invalid or empty sort metadata
    if (!sortMeta || sortMeta.length === 0) {
      return;
    }

    // Filter out any undefined or null entries from sortMeta
    const validSortMeta = sortMeta.filter((meta) => meta != null && meta.field != null);
    if (validSortMeta.length === 0) {
      return;
    }

    data.sort((data1: unknown, data2: unknown) => {
      let i = 0;
      let result = null;
      do {
        const columnConfig = this.getColumnConfigByField(validSortMeta[i].field);
        // Skip sorting if column config not found (can happen during initialization)
        if (!columnConfig) {
          result = 0;
          i++;
          continue;
        }
        const isDirectAccess = columnConfig.translateValues || columnConfig.fieldValueFN;
        const value1 = isDirectAccess
          ? this.getValueByPath(data1, columnConfig)
          : Helper.getValueByPath(data1, columnConfig.field);
        const value2 = isDirectAccess
          ? this.getValueByPath(data2, columnConfig)
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
          result = value1 < value2 ? -1 : value1 > value2 ? 1 : 0;
        }
        i++;
      } while (i < validSortMeta.length && result === 0);

      return validSortMeta[i - 1].order * result;
    });
  }

  /**
   * Shows or hides columns by header key. Programmatically controls column visibility for specific headers.
   *
   * @param fileHeader - Header key of columns to show/hide
   * @param visible - Whether columns should be visible
   */
  hideShowColumnByFileHeader(fileHeader: string, visible: boolean) {
    this.fields.filter((field) => field.headerKey === fileHeader).forEach((field) => (field.visible = visible));
    this.notifyViewChanged();
  }

  /**
   * Creates a single menu item that shows or hides the filter row. Returned only for tables that opted in with
   * {@link filterRowToggleable} and that actually define filter columns.
   *
   * @returns Array containing the filter row toggle menu item, or empty array
   */
  getFilterRowShow(): MenuItem[] {
    if (this.hasFilter && this.filterRowToggleable) {
      return [
        {
          label: 'ON_OFF_FILTER',
          icon: 'fa fa-filter',
          command: () => this.toggleFilterRow()
        }
      ];
    }
    return [];
  }

  /**
   * Shows or hides the filter row. Subclasses override this to additionally reset the filters of their table when the
   * row is hidden, see WatchlistTable.
   */
  toggleFilterRow(): void {
    this.showFilterRow = !this.showFilterRow;
    this.notifyViewChanged();
  }

  /**
   * Gets menu options for column show/hide functionality.
   * Returns the column visibility dialog menu item if toggleable columns exist and, for tables which allow it, the
   * menu item for showing and hiding the filter row.
   *
   * @returns Array of menu items with column options, or null if no options available
   */
  getMenuShowOptions(): MenuItem[] | null {
    const items = [...this.getColumnsShow(), ...this.getFilterRowShow()];
    return items.length > 0 ? items : null;
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
  constructor(
    public fieldName: string,
    public order: number
  ) {}
}
