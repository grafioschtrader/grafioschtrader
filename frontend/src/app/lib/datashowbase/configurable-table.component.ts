import {
  Component,
  ContentChild,
  EventEmitter,
  Input,
  OnChanges,
  Output,
  SimpleChanges,
  TemplateRef,
  ViewChild
} from '@angular/core';
import {CommonModule} from '@angular/common';
import {FormsModule} from '@angular/forms';
import {Table, TableModule} from 'primeng/table';
import {ContextMenuModule} from 'primeng/contextmenu';
import {TooltipModule} from 'primeng/tooltip';
import {SharedModule, SelectItem} from 'primeng/api';
import {MenuItem, SortEvent} from 'primeng/api';
import {DatePickerModule} from 'primeng/datepicker';
import {SelectModule} from 'primeng/select';
import {ColumnConfig} from './column.config';
import {DataType} from '../dynamic-form/models/data.type';
import {Helper} from '../helper/helper';
import {FilterType} from './filter.type';
import {BaseLocale} from '../dynamic-form/models/base.locale';

/**
 * Reusable table component providing standardized PrimeNG table functionality with column configuration,
 * sorting, pagination, row expansion, selection, and context menu support.
 *
 * This component is designed to be library-agnostic and can be used across different applications.
 * It eliminates repetitive p-table boilerplate by encapsulating common patterns while maintaining
 * flexibility through content projection and template customization.
 *
 * Key features:
 * - Column-based configuration using ColumnConfig
 * - Single and multiple row selection modes
 * - Optional row expansion with custom content
 * - Context menu integration
 * - Pagination support
 * - Flexible scrolling modes
 * - Custom cell templates via content projection
 * - Drag-and-drop support
 * - Standard cell renderers (text, icons, links, boolean checks, colored values)
 *
 * Usage example (simple):
 * ```html
 * <configurable-table
 *   [data]="entityList"
 *   [fields]="fields"
 *   [dataKey]="'idEntity'"
 *   [(selection)]="selectedEntity"
 *   [contextMenuItems]="menuItems">
 *   <h4 caption>{{title | translate}}</h4>
 * </configurable-table>
 * ```
 *
 * Usage example (with expansion):
 * ```html
 * <configurable-table
 *   [data]="dataList"
 *   [fields]="fields"
 *   [expandable]="true"
 *   [expandedRowTemplate]="expandedContent"
 *   [canExpandFn]="canExpand.bind(this)">
 *   <h4 caption>Title</h4>
 * </configurable-table>
 *
 * <ng-template #expandedContent let-row>
 *   <my-detail-component [data]="row"></my-detail-component>
 * </ng-template>
 * ```
 */
@Component({
  selector: 'configurable-table',
  standalone: true,
  imports: [CommonModule, FormsModule, TableModule, ContextMenuModule, TooltipModule, SharedModule, DatePickerModule, SelectModule],
  template: `
    <div #cmDiv (click)="onComponentClick($event)"
         [ngClass]="containerClass"
         [class]="customClass">
      <p-table #table
        [columns]="fields"
        [value]="data"
        [dataKey]="dataKey"
        [selectionMode]="selectionMode"
        [(selection)]="selection"
        (selectionChange)="selectionChange.emit($event)"
        [sortMode]="sortMode"
        [multiSortMeta]="multiSortMeta"
        [customSort]="enableCustomSort"
        (sortFunction)="onSort($event)"
        [paginator]="paginator"
        [rows]="rows"
        [first]="firstRowIndex"
        (onPage)="onPage($event)"
        [scrollable]="scrollable"
        [scrollHeight]="scrollHeight"
        [loading]="loading"
        [stripedRows]="stripedRows"
        [showGridlines]="showGridlines"
        [(expandedRowKeys)]="expandedRowKeys"
        (onRowSelect)="rowSelect.emit($event)"
        (onRowUnselect)="rowUnselect.emit($event)"
        (onColResize)="colResize.emit($event)">

        <!-- Caption slot with content projection -->
        <ng-template pTemplate="caption">
          <ng-content select="[caption]"></ng-content>
        </ng-template>

        <!-- Header template -->
        <ng-template pTemplate="header" let-columns>
          <tr>
            <!-- Expansion toggle column -->
            @if (expandable) {
              <th [style.width.px]="expansionColumnWidth"></th>
            }

            <!-- Multi-select checkbox column -->
            @if (selectionMode === 'multiple') {
              <th [style.width.em]="2.25">
                <p-tableHeaderCheckbox></p-tableHeaderCheckbox>
              </th>
            }

            <!-- Dynamic data columns -->
            @for (field of columns; track field.field) {
              @if (field.visible) {
                <th [pSortableColumn]="field.field"
                    [pTooltip]="field.headerTooltipTranslated"
                    [style.max-width.px]="field.width"
                    [ngStyle]="field.width ? {'flex-basis': '0 0 ' + field.width + 'px'} : {}">
                  {{ field.headerTranslated }}
                  <p-sortIcon [field]="field.field"></p-sortIcon>
                </th>
              }
            }
          </tr>

          <!-- Filter row -->
          @if (hasFilter) {
            <tr>
              <!-- Empty cell for expansion column -->
              @if (expandable) {
                <th [style.width.px]="expansionColumnWidth"></th>
              }

              <!-- Empty cell for multi-select checkbox column -->
              @if (selectionMode === 'multiple') {
                <th [style.width.em]="2.25"></th>
              }

              <!-- Filter cells -->
              @for (field of columns; track field.field) {
                @if (field.visible) {
                  <th style="overflow:visible;">
                    @switch (field.filterType) {
                      @case (FilterType.likeDataType) {
                        @switch (field.dataType) {
                          @case (field.dataType === DataType.DateString || field.dataType === DataType.DateNumeric ? field.dataType : '') {
                            <p-columnFilter [field]="field.field" display="menu" [showOperator]="true"
                                            [matchModeOptions]="customMatchModeOptions" [matchMode]="'gtNoFilter'">
                              <ng-template pTemplate="filter" let-value let-filter="filterCallback">
                                <p-datepicker #cal [ngModel]="value" [dateFormat]="baseLocale.dateFormat"
                                              (onSelect)="filter($event)"
                                              [minDate]="minDate" [maxDate]="maxDate"
                                              (onInput)="filter(cal.value)">
                                </p-datepicker>
                              </ng-template>
                            </p-columnFilter>
                          }
                          @case (DataType.Numeric) {
                            <p-columnFilter type="numeric" [field]="field.field"
                                            [locale]="formLocale"
                                            minFractionDigits="2" display="menu"></p-columnFilter>
                          }
                        }
                      }
                      @case (FilterType.withOptions) {
                        <p-select [options]="field.filterValues" [style]="{'width':'100%'}"
                                  (onChange)="table.filter($event.value, field.field, 'equals')"></p-select>
                      }
                    }
                  </th>
                }
              }
            </tr>
          }
        </ng-template>

        <!-- Body template -->
        <ng-template pTemplate="body" let-rowData let-expanded="expanded" let-columns="columns">
          <tr [pSelectableRow]="rowData"
              [pContextMenuRow]="contextMenuEnabled ? rowData : null">

            <!-- Expansion toggle cell -->
            @if (expandable) {
              <td [style.width.px]="expansionColumnWidth">
                @if (canExpand(rowData)) {
                  <a [pRowToggler]="rowData" href="#" (click)="$event.preventDefault()">
                    <i [ngClass]="expanded ? expandedIcon : collapsedIcon"></i>
                  </a>
                }
              </td>
            }

            <!-- Multi-select checkbox cell -->
            @if (selectionMode === 'multiple') {
              <td>
                <p-tableCheckbox [value]="rowData"></p-tableCheckbox>
              </td>
            }

            <!-- Dynamic data cells -->
            @for (field of columns; track field.field) {
              @if (field.visible) {
                <td [ngStyle]="field.width ? {'flex-basis': '0 0 ' + field.width + 'px'} : {}"
                    [style.max-width.px]="field.width"
                    [ngClass]="getCellClass(field)">
                  <!-- Cell content rendering -->
                  @if (customCellTemplate) {
                    <!-- Use custom cell template if provided -->
                    <ng-container *ngTemplateOutlet="customCellTemplate; context: {$implicit: rowData, field: field}">
                    </ng-container>
                  } @else if (field.templateName === 'owner') {
                    <span [pTooltip]="getValue(rowData, field)"
                          [style]='isOwnerHighlighted(rowData, field) ? "font-weight:500" : null'
                          tooltipPosition="top">
                      {{ getValue(rowData, field) }}
                    </span>
                  } @else if (field.templateName === 'greenRed') {
                    <span [style.color]='isNegativeValue(rowData, field) ? "red" : "green"'>
                      {{ getValue(rowData, field) }}
                    </span>
                  } @else if (field.templateName === 'check') {
                    <span><i [ngClass]="{'fa fa-check': getValue(rowData, field)}" aria-hidden="true"></i></span>
                  } @else if (field.templateName === 'icon') {
                    @if (iconTemplate) {
                      <ng-container *ngTemplateOutlet="iconTemplate; context: {$implicit: rowData, field: field, value: getValue(rowData, field)}">
                      </ng-container>
                    } @else {
                      <span>{{ getValue(rowData, field) }}</span>
                    }
                  } @else if (field.templateName === 'linkIcon') {
                    @if (getValue(rowData, field)) {
                      <a [href]="getValue(rowData, field)" target="_blank">
                        @if (linkIconTemplate) {
                          <ng-container *ngTemplateOutlet="linkIconTemplate; context: {$implicit: rowData, field: field}">
                          </ng-container>
                        } @else {
                          <i class="fa fa-external-link"></i>
                        }
                      </a>
                    }
                  } @else {
                    <!-- Default: plain text with tooltip -->
                    <span [pTooltip]="getValue(rowData, field)" tooltipPosition="top">
                      {{ getValue(rowData, field) }}
                    </span>
                  }
                </td>
              }
            }
          </tr>
        </ng-template>

        <!-- Expanded row template -->
        @if (expandable && expandedRowTemplate) {
          <ng-template #expandedrow let-row let-columns="fields">
            <tr>
              <td [attr.colspan]="getExpandedColspan()">
                <ng-container *ngTemplateOutlet="expandedRowTemplate; context: {$implicit: row}">
                </ng-container>
              </td>
            </tr>
          </ng-template>
        }
      </p-table>

      <!-- Context menu -->
      @if (contextMenuEnabled && showContextMenu) {
        <p-contextMenu [target]="cmDiv" [model]="contextMenuItems" [appendTo]="contextMenuAppendTo"></p-contextMenu>
      }
    </div>
  `
})
export class ConfigurableTableComponent<T = any> implements OnChanges {

  // ============================================================================
  // Data and Column Configuration
  // ============================================================================

  /**
   * Array of data objects to display in the table.
   * Each object represents one row of data.
   */
  @Input() data: T[] = [];

  /**
   * Array of column configurations defining table structure.
   * Uses ColumnConfig from lib/datashowbase for consistent column definitions.
   */
  @Input() fields: ColumnConfig[] = [];

  /**
   * Property name used as the unique identifier for each row.
   * Used by PrimeNG for tracking rows during updates and selections.
   */
  @Input() dataKey: string;

  // ============================================================================
  // Selection Configuration
  // ============================================================================

  /**
   * Row selection mode: 'single' allows one row selection, 'multiple' allows multiple,
   * null disables selection entirely.
   */
  @Input() selectionMode: 'single' | 'multiple' | null = 'single';

  /**
   * Currently selected row(s). Type depends on selectionMode:
   * - single mode: T | null
   * - multiple mode: T[]
   */
  @Input() selection: T | T[] | null = null;

  /**
   * Emits when selection changes. Used for two-way binding with [(selection)].
   */
  @Output() selectionChange = new EventEmitter<T | T[] | null>();

  /**
   * Emits when a row is selected (user action).
   */
  @Output() rowSelect = new EventEmitter<any>();

  /**
   * Emits when a row is unselected (user action).
   */
  @Output() rowUnselect = new EventEmitter<any>();

  // ============================================================================
  // Sorting Configuration
  // ============================================================================

  /**
   * Sorting mode: 'single' for single-column sort, 'multiple' for multi-column sort.
   */
  @Input() sortMode: 'single' | 'multiple' = 'multiple';

  /**
   * Array of sort metadata for multi-column sorting.
   * Each entry specifies field name and sort order (1 = ascending, -1 = descending).
   */
  @Input() multiSortMeta: any[] = [];

  /**
   * Enable custom sorting logic instead of PrimeNG's default sorting.
   */
  @Input() enableCustomSort = true;

  /**
   * Callback function for custom sorting logic.
   * Receives SortEvent and should sort the data array in place.
   */
  @Input() customSortFn?: (event: SortEvent) => void;

  // ============================================================================
  // Filtering Configuration
  // ============================================================================

  /**
   * Enable column filtering functionality.
   * When true, displays filter row below column headers.
   */
  @Input() hasFilter = false;

  /**
   * Custom match mode options for date filtering.
   * Array of SelectItem objects defining filter operators like 'equals', 'before', 'after'.
   */
  @Input() customMatchModeOptions: SelectItem[] = [];

  /**
   * Minimum date allowed in date filter pickers.
   */
  @Input() minDate: Date = new Date('2000-01-01');

  /**
   * Maximum date allowed in date filter pickers.
   */
  @Input() maxDate: Date = new Date('2099-12-31');

  /**
   * Locale configuration for date formatting in filters.
   */
  @Input() baseLocale: BaseLocale = { language: 'en', dateFormat: 'yy-mm-dd' };

  /**
   * Locale string for numeric filter formatting (e.g., 'en-US', 'de-DE').
   */
  @Input() formLocale = 'en-US';

  /**
   * Reference to the PrimeNG table component for programmatic filter access.
   */
  @ViewChild('table') table: Table;

  /**
   * FilterType enum exposed for template usage.
   */
  protected readonly FilterType = FilterType;

  /**
   * DataType enum exposed for template usage.
   */
  protected readonly DataType = DataType;

  // ============================================================================
  // Pagination Configuration
  // ============================================================================

  /**
   * Enable pagination controls at the bottom of the table.
   */
  @Input() paginator = false;

  /**
   * Number of rows to display per page when pagination is enabled.
   */
  @Input() rows = 20;

  /**
   * Index of the first row on the current page (0-based).
   */
  @Input() firstRowIndex = 0;

  /**
   * Emits when pagination changes (page number or rows per page).
   */
  @Output() pageChange = new EventEmitter<any>();

  // ============================================================================
  // Scrolling Configuration
  // ============================================================================

  /**
   * Enable table scrolling mode (virtual scrolling or fixed header scrolling).
   */
  @Input() scrollable = true;

  /**
   * Height of the scrollable viewport. Can be 'flex' for flexbox layout or a specific value like '400px'.
   */
  @Input() scrollHeight: string = 'flex';

  // ============================================================================
  // Visual Styling
  // ============================================================================

  /**
   * Show alternating row background colors for better readability.
   */
  @Input() stripedRows = true;

  /**
   * Show grid lines between cells.
   */
  @Input() showGridlines = true;

  /**
   * Show loading spinner overlay when data is being fetched.
   */
  @Input() loading = false;

  /**
   * CSS class applied to the outer container div.
   */
  @Input() containerClass: string | string[] | { [key: string]: boolean } = {
    'data-container': true
  };

  /**
   * Additional CSS classes applied to the container.
   */
  @Input() customClass = '';

  // ============================================================================
  // Row Expansion Configuration
  // ============================================================================

  /**
   * Enable row expansion functionality. When true, adds a toggle column.
   */
  @Input() expandable = false;

  /**
   * Template for expanded row content. Receives the row data as context.
   */
  @Input() expandedRowTemplate?: TemplateRef<any>;

  /**
   * Callback function to determine if a specific row can be expanded.
   * If not provided, all rows are expandable when expandable=true.
   */
  @Input() canExpandFn?: (row: T) => boolean;

  /**
   * Width in pixels of the expansion toggle column.
   */
  @Input() expansionColumnWidth = 24;

  /**
   * CSS class for the expanded row icon (default: Font Awesome chevron down).
   */
  @Input() expandedIcon = 'fa fa-fw fa-chevron-circle-down';

  /**
   * CSS class for the collapsed row icon (default: Font Awesome chevron right).
   */
  @Input() collapsedIcon = 'fa fa-fw fa-chevron-circle-right';

  /**
   * Object tracking which rows are currently expanded. Keys are the dataKey values.
   * Managed internally by PrimeNG when rows are toggled.
   */
  expandedRowKeys: { [key: string]: boolean } = {};

  // ============================================================================
  // Context Menu Configuration
  // ============================================================================

  /**
   * Enable context menu on right-click.
   */
  @Input() contextMenuEnabled = true;

  /**
   * Array of menu items for the context menu. Uses PrimeNG MenuItem interface.
   */
  @Input() contextMenuItems: MenuItem[] = [];

  /**
   * Controls visibility of the context menu. Typically bound to isActivated() logic.
   */
  @Input() showContextMenu = false;

  /**
   * Determines where to append the context menu in the DOM.
   * Set to 'body' for nested tables to ensure proper positioning in the X-Y coordinate system.
   * When null, the context menu is appended to its parent container (default behavior).
   *
   * Usage: [contextMenuAppendTo]="'body'" for nested tables
   */
  @Input() contextMenuAppendTo: string | null = null;

  // ============================================================================
  // Template Customization
  // ============================================================================

  /**
   * Custom template for rendering cells. Overrides default cell rendering.
   * Receives row and field as context.
   */
  @ContentChild('customCell') customCellTemplate?: TemplateRef<any>;

  /**
   * Custom template for rendering icon cells (templateName='icon').
   * Receives row, field, and value as context.
   */
  @ContentChild('iconCell') iconTemplate?: TemplateRef<any>;

  /**
   * Custom template for rendering link icon cells (templateName='linkIcon').
   * Receives row and field as context.
   */
  @ContentChild('linkIconCell') linkIconTemplate?: TemplateRef<any>;

  // ============================================================================
  // Cell Behavior Callbacks
  // ============================================================================

  /**
   * Callback to determine if owner cell should be highlighted.
   * Used with templateName='owner' to apply bold font weight.
   */
  @Input() ownerHighlightFn?: (row: T, field: ColumnConfig) => boolean;

  /**
   * Callback to determine if a numeric value is negative.
   * Used with templateName='greenRed' to apply red color.
   */
  @Input() negativeValueFn?: (row: T, field: ColumnConfig) => boolean;

  /**
   * Custom function to retrieve cell values. If not provided, uses field configuration or Helper.getValueByPath.
   */
  @Input() valueGetterFn?: (row: T, field: ColumnConfig) => any;

  // ============================================================================
  // Column Resizing
  // ============================================================================

  /**
   * Emits when a column is resized by the user.
   */
  @Output() colResize = new EventEmitter<any>();

  // ============================================================================
  // Drag and Drop
  // ============================================================================

  /**
   * Emits when drag operation starts on a row.
   */
  @Output() dragStart = new EventEmitter<{ event: DragEvent; data: T }>();

  /**
   * Emits when drag operation ends on a row.
   */
  @Output() dragEnd = new EventEmitter<{ event: DragEvent; data: T }>();

  // ============================================================================
  // Click Events
  // ============================================================================

  /**
   * Emits when the table container is clicked.
   * Useful for focus management and menu handling.
   */
  @Output() componentClick = new EventEmitter<any>();

  // ============================================================================
  // Multi-select Configuration
  // ============================================================================

  /**
   * When true and selectionMode='multiple', the checkbox column is frozen (sticky).
   */
  @Input() freezeSelectionColumn = false;

  // ============================================================================
  // Component Methods
  // ============================================================================

  /**
   * Angular lifecycle hook - responds to input property changes.
   * Currently monitors fields changes to ensure proper column updates.
   */
  ngOnChanges(changes: SimpleChanges): void {
    if (changes['fields']) {
      // Fields configuration changed - may need to refresh column setup
    }
    if (changes['data']) {
      // Data changed - table will re-render
    }
  }

  /**
   * Handles sort events from PrimeNG table.
   * Delegates to custom sort function if provided, otherwise performs no action
   * (allows PrimeNG default sorting when enableCustomSort=false).
   *
   * @param event - PrimeNG sort event containing sort metadata
   */
  onSort(event: SortEvent): void {
    if (this.customSortFn) {
      this.customSortFn(event);
    }
  }

  /**
   * Handles pagination events from PrimeNG table.
   *
   * @param event - PrimeNG page event containing page info
   */
  onPage(event: any): void {
    this.pageChange.emit(event);
  }

  /**
   * Handles container click events.
   * Propagates event to parent component for menu management.
   *
   * @param event - DOM click event
   */
  onComponentClick(event: any): void {
    this.componentClick.emit(event);
  }

  /**
   * Retrieves cell value from row data using field configuration.
   * Uses custom getter if provided, otherwise delegates to field's value function or Helper.getValueByPath.
   *
   * @param row - Row data object
   * @param field - Column configuration
   * @returns Formatted cell value
   */
  getValue(row: T, field: ColumnConfig): any {
    if (this.valueGetterFn) {
      return this.valueGetterFn(row, field);
    }
    // Use field's custom value function if available
    if (field.fieldValueFN) {
      return field.fieldValueFN(row, field, null);
    }
    // Default to path-based access
    return Helper.getValueByPath(row, field.field);
  }

  /**
   * Determines CSS class for table cell based on data type.
   * Right-aligns numeric columns for better readability.
   *
   * @param field - Column configuration
   * @returns CSS class string
   */
  getCellClass(field: ColumnConfig): string {
    return (field.dataType === DataType.Numeric
      || field.dataType === DataType.NumericShowZero
      || field.dataType === DataType.NumericInteger
      || field.dataType === DataType.DateTimeNumeric)
      ? 'text-end'
      : '';
  }

  /**
   * Determines if a row can be expanded.
   * Uses custom function if provided, otherwise allows all rows.
   *
   * @param row - Row data object
   * @returns True if row can be expanded
   */
  canExpand(row: T): boolean {
    return this.canExpandFn ? this.canExpandFn(row) : true;
  }

  /**
   * Calculates colspan for expanded row content.
   * Accounts for expansion toggle column and selection checkbox column.
   *
   * @returns Number of columns to span
   */
  getExpandedColspan(): number {
    let count = this.fields.filter(f => f.visible).length;
    if (this.expandable) {
      count++;
    }
    if (this.selectionMode === 'multiple') {
      count++;
    }
    return count;
  }

  /**
   * Determines if owner cell should be highlighted (bold).
   * Uses callback function if provided.
   *
   * @param row - Row data object
   * @param field - Column configuration
   * @returns True if cell should be highlighted
   */
  isOwnerHighlighted(row: T, field: ColumnConfig): boolean {
    return this.ownerHighlightFn ? this.ownerHighlightFn(row, field) : false;
  }

  /**
   * Determines if a value is negative (for red/green coloring).
   * Uses callback function if provided, otherwise checks if value starts with '-'.
   *
   * @param row - Row data object
   * @param field - Column configuration
   * @returns True if value should be colored red
   */
  isNegativeValue(row: T, field: ColumnConfig): boolean {
    if (this.negativeValueFn) {
      return this.negativeValueFn(row, field);
    }
    const value = this.getValue(row, field);
    return value != null && String(value).startsWith('-');
  }
}
