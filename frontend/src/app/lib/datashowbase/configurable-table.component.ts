import {
  AfterViewChecked,
  Component,
  contentChild,
  ElementRef,
  inject,
  input,
  NgZone,
  OnChanges,
  OnDestroy,
  SimpleChanges,
  TemplateRef,
  output,
  viewChild,
  model
} from '@angular/core';
import { NgTemplateOutlet } from '@angular/common';
import { FormsModule } from '@angular/forms';
import { Table, TableModule } from '@openng/optimus-ui/table';
import { ContextMenuModule } from '@openng/optimus-ui/contextmenu';
import { TooltipModule } from '@openng/optimus-ui/tooltip';
import { SharedModule, SelectItem } from '@openng/optimus-ui/api';
import { MenuItem, SortEvent } from '@openng/optimus-ui/api';
import { DatePickerModule } from '@openng/optimus-ui/datepicker';
import { MultiSelectModule } from '@openng/optimus-ui/multiselect';
import { ColumnConfig, getFilterKey } from './column.config';
import { DataType } from '../dynamic-form/models/data.type';
import { Helper } from '../helper/helper';
import { FilterType } from './filter.type';
import { BaseLocale } from '../dynamic-form/models/base.locale';
import { GlobalSessionNames } from '../global.session.names';
import { BaseSettings } from '../base.settings';
import { toClassString } from '../helper/css-class.helper';

/** Represents one item in the first (group) header row */
interface HeaderGroupItem {
  /** 'single' = ungrouped column (gets rowspan=2), 'group' = group header (gets colspan) */
  type: 'single' | 'group';
  label?: string;
  colspan?: number;
  field?: ColumnConfig;
}

/**
 * A small mark shown before the content of a table cell.
 */
export interface CellMark {
  /** CSS classes of the icon, for example 'fa fa-magic' */
  iconClass: string;
  /** Already translated tooltip of the mark */
  tooltip: string;
  /** Optional text shown in italics after the icon, for example a value that is only proposed */
  text?: string;
}

/**
 * Reusable table component providing standardized Optimus table functionality with column configuration,
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
 *   [fields]="fields" [fieldsRevision]="fieldsRevision()"
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
 *   [fields]="fields" [fieldsRevision]="fieldsRevision()"
 *   [expandable]="true"
 *   [expandedRowTemplate]="expandedContent"
 *   [canExpandFn]="canExpand">
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
  imports: [
    FormsModule,
    TableModule,
    ContextMenuModule,
    TooltipModule,
    SharedModule,
    DatePickerModule,
    MultiSelectModule,
    NgTemplateOutlet
  ],
  // Rules for the columns fixed by frozenColumnCount. The theme defines the sticky position only for a scrollable table,
  // these rules hold for every table. A body cell takes the opaque background of its row and is stacked above the cells
  // scrolling underneath, so their text stays hidden; a selected row may have a translucent background and gets the
  // normal row colour below it. The sticky header and footer are lifted above the fixed body cells, otherwise these
  // would cover them on vertical scrolling. The global rule giving every header cell z-index 0 is outweighed by tr >.
  styles: [
    `
      .p-datatable-frozen-column {
        position: sticky;
      }
      tr > th.p-datatable-frozen-column {
        z-index: 1;
      }
      td.p-datatable-frozen-column {
        z-index: 1;
        background: inherit;
      }
      :host ::ng-deep .p-datatable-thead,
      :host ::ng-deep .p-datatable-tfoot {
        z-index: 2;
      }
      tr.p-datatable-row-selected > td.p-datatable-frozen-column {
        background:
          linear-gradient(var(--p-datatable-row-selected-background), var(--p-datatable-row-selected-background)),
          var(--p-datatable-row-background);
      }
      tr.gt-footer-row > td.p-datatable-frozen-column {
        background: var(--p-datatable-footer-cell-background);
      }
      .gt-frozen-last {
        border-inline-end: 2px solid var(--p-datatable-body-cell-border-color);
      }
    `
  ],
  template: `
    @let expandedRowTemplateValue = expandedRowTemplate();
    @let cellMarkFnValue = cellMarkFn();
    @let footerValueFnValue = footerValueFn();

    <div #cmDiv (click)="onComponentClick($event)" [class]="toClassString(containerClass(), customClass())">
      <p-table
        #table
        [columns]="renderedFields"
        [value]="data()"
        [dataKey]="dataKey()"
        [selectionMode]="selectionMode()"
        [selection]="selection()"
        [rowSelectable]="rowSelectableFn() ? isRowSelectable : undefined"
        (selectionChange)="selection.set($event)"
        [sortMode]="sortMode()"
        [multiSortMeta]="multiSortMeta()"
        [customSort]="enableCustomSort()"
        (sortFunction)="onSort($event)"
        [paginator]="effectivePaginator"
        [rows]="rows()"
        [first]="firstRowIndex()"
        (onPage)="onPage($event)"
        [rowsPerPageOptions]="rowsPerPageOptions()"
        [scrollable]="scrollable()"
        [scrollHeight]="scrollHeight()"
        [loading]="loading()"
        [stripedRows]="stripedRows()"
        [showGridlines]="showGridlines()"
        [expandedRowKeys]="expandedRowKeys()"
        (onRowExpand)="onRowExpand($event)"
        (onRowCollapse)="onRowCollapse($event)"
        (onRowSelect)="rowSelect.emit($event)"
        (onRowUnselect)="rowUnselect.emit($event)"
        (onFilter)="filterChange.emit($event)"
        (onColResize)="colResize.emit($event)">
        <!-- Caption slot with content projection -->
        <ng-template pTemplate="caption">
          <ng-content select="[caption]" />
        </ng-template>

        <!-- Header template -->
        <ng-template pTemplate="header" let-columns>
          @if (hasGroups) {
            <!-- Row 1: Group headers + ungrouped columns (with rowspan=2) -->
            <tr>
              @if (expandable()) {
                <th
                  [rowSpan]="2"
                  [style.width.px]="expansionColumnWidth()"
                  [class.p-datatable-frozen-column]="expansionFrozenSlot >= 0"
                  [style.left]="frozenLeft(expansionFrozenSlot)"></th>
              }
              @if (selectionMode() === 'multiple') {
                <th
                  [rowSpan]="2"
                  [style.width.em]="2.25"
                  [class.p-datatable-frozen-column]="selectionFrozenSlot >= 0"
                  [style.left]="frozenLeft(selectionFrozenSlot)">
                  <p-tableHeaderCheckbox />
                </th>
              }
              @for (item of headerGroupRow; track $index) {
                @if (item.type === 'single') {
                  @let slot = frozenSlot(item.field);
                  <th
                    [rowSpan]="2"
                    [class.p-datatable-frozen-column]="slot >= 0"
                    [class.gt-frozen-last]="slot === lastFrozenSlot"
                    [style.left]="frozenLeft(slot)"
                    [pSortableColumn]="item.field.field"
                    [pTooltip]="item.field.headerTooltipTranslated"
                    [style.max-width.px]="item.field.width"
                    [style]="item.field.width ? { 'flex-basis': '0 0 ' + item.field.width + 'px' } : {}"
                    class="word-break-header"
                    [attr.lang]="baseLocale().language">
                    {{ item.field.headerTranslated }}
                    <p-sortIcon [field]="item.field.field" />
                  </th>
                } @else {
                  <th [attr.colspan]="item.colspan" style="text-align:center">
                    {{ item.label }}
                  </th>
                }
              }
            </tr>
            <!-- Row 2: Individual headers for grouped columns only -->
            <tr>
              @for (field of groupedColumns; track field.field) {
                <th
                  [pSortableColumn]="field.field"
                  [pTooltip]="field.headerTooltipTranslated"
                  [style.max-width.px]="field.width"
                  [style]="field.width ? { 'flex-basis': '0 0 ' + field.width + 'px' } : {}"
                  class="word-break-header"
                  [attr.lang]="baseLocale().language">
                  {{ field.headerTranslated }}
                  <p-sortIcon [field]="field.field" />
                </th>
              }
            </tr>
          } @else {
            <!-- Standard single-row header (unchanged) -->
            <tr>
              @if (expandable()) {
                <th
                  [style.width.px]="expansionColumnWidth()"
                  [class.p-datatable-frozen-column]="expansionFrozenSlot >= 0"
                  [style.left]="frozenLeft(expansionFrozenSlot)"></th>
              }
              @if (selectionMode() === 'multiple') {
                <th
                  [style.width.em]="2.25"
                  [class.p-datatable-frozen-column]="selectionFrozenSlot >= 0"
                  [style.left]="frozenLeft(selectionFrozenSlot)">
                  <p-tableHeaderCheckbox />
                </th>
              }
              @for (field of columns; track field.field) {
                @if (field.visible) {
                  @let slot = frozenSlot(field);
                  <th
                    [class.p-datatable-frozen-column]="slot >= 0"
                    [class.gt-frozen-last]="slot === lastFrozenSlot"
                    [style.left]="frozenLeft(slot)"
                    [pSortableColumn]="field.field"
                    [pTooltip]="field.headerTooltipTranslated"
                    [style.max-width.px]="field.width"
                    [style]="field.width ? { 'flex-basis': '0 0 ' + field.width + 'px' } : {}"
                    class="word-break-header"
                    [attr.lang]="baseLocale().language">
                    {{ field.headerTranslated }}
                    <p-sortIcon [field]="field.field" />
                  </th>
                }
              }
            </tr>
          }

          <!-- Filter row -->
          @if (hasFilter()) {
            <tr>
              <!-- Empty cell for expansion column -->
              @if (expandable()) {
                <th
                  [style.width.px]="expansionColumnWidth()"
                  [class.p-datatable-frozen-column]="expansionFrozenSlot >= 0"
                  [style.left]="frozenLeft(expansionFrozenSlot)"></th>
              }

              <!-- Empty cell for multi-select checkbox column -->
              @if (selectionMode() === 'multiple') {
                <th
                  [style.width.em]="2.25"
                  [class.p-datatable-frozen-column]="selectionFrozenSlot >= 0"
                  [style.left]="frozenLeft(selectionFrozenSlot)"></th>
              }

              <!-- Filter cells -->
              @for (field of columns; track field.field) {
                @if (field.visible) {
                  <!-- The width has to be constrained like on the header and the body cell, otherwise a filter control
                       widens its column to fit the longest option label and the column's width is without effect. -->
                  @let slot = frozenSlot(field);
                  <th
                    style="overflow:visible;"
                    [class.p-datatable-frozen-column]="slot >= 0"
                    [class.gt-frozen-last]="slot === lastFrozenSlot"
                    [style.left]="frozenLeft(slot)"
                    [style.max-width.px]="field.width"
                    [style]="field.width ? { 'flex-basis': '0 0 ' + field.width + 'px' } : {}">
                    @switch (field.filterType) {
                      @case (FilterType.likeDataType) {
                        @switch (field.dataType) {
                          @case (
                            field.dataType === DataType.DateString ||
                            field.dataType === DataType.DateNumeric ||
                            field.dataType === DataType.DateTimeString
                              ? field.dataType
                              : ''
                          ) {
                            <p-columnFilter
                              [field]="field.field"
                              display="menu"
                              [showOperator]="true"
                              appendTo="body"
                              [matchModeOptions]="customMatchModeOptions()"
                              [matchMode]="'gtNoFilter'">
                              <ng-template pTemplate="filter" let-value let-filter="filterCallback">
                                <p-datepicker
                                  #cal
                                  [ngModel]="value"
                                  [dateFormat]="baseLocale().dateFormat"
                                  (onSelect)="filter($event)"
                                  [minDate]="minDate()"
                                  [maxDate]="maxDate()"
                                  (onInput)="filter(cal.value)" />
                              </ng-template>
                            </p-columnFilter>
                          }
                          @case (DataType.Numeric) {
                            <p-columnFilter
                              type="numeric"
                              [field]="field.field"
                              [locale]="formLocale()"
                              appendTo="body"
                              minFractionDigits="2"
                              display="menu" />
                          }
                          @case (DataType.NumericShowZero) {
                            <p-columnFilter
                              type="numeric"
                              [field]="field.field"
                              [locale]="formLocale()"
                              appendTo="body"
                              minFractionDigits="0"
                              display="menu" />
                          }
                          @case (DataType.NumericInteger) {
                            <p-columnFilter
                              type="numeric"
                              [field]="field.field"
                              [locale]="formLocale()"
                              appendTo="body"
                              minFractionDigits="0"
                              display="menu" />
                          }
                          @case (DataType.String) {
                            <p-columnFilter type="text" appendTo="body" [field]="filterKey(field)" display="menu" />
                          }
                        }
                      }
                      @case (FilterType.withOptions) {
                        <!-- Several values can be selected, they are combined with OR. An empty selection is handed
                             over as null, so that the table drops the filter instead of keeping an empty one. The clear
                             icon reports only through onClear, never through onChange, so both must reach the table. -->
                        <p-multiSelect
                          [options]="field.filterValues"
                          [style]="{ width: '100%' }"
                          appendTo="body"
                          [ngModel]="getSelectFilterValue(field)"
                          [ngModelOptions]="{ standalone: true }"
                          [maxSelectedLabels]="1"
                          [showClear]="true"
                          [filter]="$safeNavigationMigration(field.filterValues?.length) > 10"
                          (onChange)="table.filter($event.value?.length ? $event.value : null, filterKey(field), 'in')"
                          (onClear)="table.filter(null, filterKey(field), 'in')" />
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
          <tr
            [pSelectableRow]="rowData"
            [pContextMenuRow]="contextMenuEnabled() ? rowData : null"
            [class]="getRowClass(rowData)">
            <!-- Expansion toggle cell -->
            @if (expandable()) {
              <td
                [style.width.px]="expansionColumnWidth()"
                [class.p-datatable-frozen-column]="expansionFrozenSlot >= 0"
                [style.left]="frozenLeft(expansionFrozenSlot)">
                @if (canExpand(rowData)) {
                  <a [pRowToggler]="rowData" href="#" (click)="$event.preventDefault()">
                    <i [class]="expanded ? expandedIcon() : collapsedIcon()"></i>
                  </a>
                }
              </td>
            }

            <!-- Multi-select checkbox cell -->
            @if (selectionMode() === 'multiple') {
              <td
                [class.p-datatable-frozen-column]="selectionFrozenSlot >= 0"
                [style.left]="frozenLeft(selectionFrozenSlot)">
                <p-tableCheckbox [value]="rowData" />
              </td>
            }

            <!-- Dynamic data cells -->
            @for (field of columns; track field.field) {
              @if (field.visible) {
                @let slot = frozenSlot(field);
                <td
                  [class.p-datatable-frozen-column]="slot >= 0"
                  [class.gt-frozen-last]="slot === lastFrozenSlot"
                  [style.left]="frozenLeft(slot)"
                  [style]="getMergedCellStyle(rowData, field)"
                  [style.max-width.px]="field.width"
                  [class]="getCellClass(field)">
                  <!-- Optional mark before the content, for example for an automatically filled value -->
                  @if (cellMarkFnValue) {
                    @if (cellMarkFnValue(rowData, field); as mark) {
                      <i
                        [class]="mark.iconClass"
                        [pTooltip]="mark.tooltip"
                        tooltipPosition="top"
                        style="font-size: 0.75em; margin-right: 3px; opacity: 0.7"></i>
                      @if (mark.text) {
                        <i [pTooltip]="mark.tooltip" tooltipPosition="top">{{ mark.text }}</i>
                      }
                    }
                  }
                  <!-- Cell content rendering -->
                  @if (customCellTemplate(); as customCellTemplateValue) {
                    <!-- Use custom cell template if provided -->
                    <ng-container
                      *ngTemplateOutlet="customCellTemplateValue; context: { $implicit: rowData, field: field }" />
                  } @else if (field.templateName === 'owner') {
                    <span
                      [pTooltip]="getCellTooltip(rowData, field)"
                      [style]="isOwnerHighlighted(rowData, field) ? 'font-weight:700' : null"
                      tooltipPosition="top">
                      {{ getValue(rowData, field) }}
                    </span>
                  } @else if (field.templateName === 'greenRed') {
                    <span
                      [pTooltip]="getCellTooltip(rowData, field)"
                      [style.color]="isNegativeValue(rowData, field) ? 'red' : 'inherit'"
                      tooltipPosition="top">
                      {{ getValue(rowData, field) }}
                    </span>
                  } @else if (field.templateName === 'check') {
                    <span
                      ><i
                        [class.fa]="getValue(rowData, field)"
                        [class.fa-check]="getValue(rowData, field)"
                        aria-hidden="true"></i
                    ></span>
                  } @else if (field.templateName === 'icon') {
                    @if (iconTemplate(); as iconTemplateValue) {
                      <ng-container
                        *ngTemplateOutlet="
                          iconTemplateValue;
                          context: {
                            $implicit: rowData,
                            field: field,
                            value: getValue(rowData, field)
                          }
                        " />
                    } @else {
                      <span>{{ getValue(rowData, field) }}</span>
                    }
                  } @else if (field.templateName === 'svgIcon') {
                    @if (svgIconTemplate(); as svgIconTemplateValue) {
                      <ng-container
                        *ngTemplateOutlet="
                          svgIconTemplateValue;
                          context: {
                            $implicit: rowData,
                            field: field,
                            value: getValue(rowData, field)
                          }
                        " />
                    } @else {
                      <span>{{ getValue(rowData, field) }}</span>
                    }
                  } @else if (field.templateName === 'linkIcon') {
                    @if (getValue(rowData, field)) {
                      <a [href]="getValue(rowData, field)" target="_blank">
                        @if (linkIconTemplate(); as linkIconTemplateValue) {
                          <ng-container
                            *ngTemplateOutlet="linkIconTemplateValue; context: { $implicit: rowData, field: field }" />
                        } @else {
                          <i class="fa fa-external-link"></i>
                        }
                      </a>
                    }
                  } @else {
                    <!-- Default: plain text with tooltip -->
                    <span [pTooltip]="getCellTooltip(rowData, field)" tooltipPosition="top">
                      {{ getValue(rowData, field) }}
                    </span>
                  }
                </td>
              }
            }
          </tr>
        </ng-template>

        <!-- Expanded row template -->
        @if (expandable() && expandedRowTemplateValue) {
          <ng-template #expandedrow let-row let-columns="fields">
            <tr>
              <td [attr.colspan]="getExpandedColspan()">
                <ng-container *ngTemplateOutlet="expandedRowTemplateValue; context: { $implicit: row }" />
              </td>
            </tr>
          </ng-template>
        }

        <!-- Footer template -->
        @if (footerValueFnValue) {
          <ng-template pTemplate="footer">
            <tr class="gt-footer-row">
              @if (expandable()) {
                <td
                  [class]="footerCellClass()"
                  [class.p-datatable-frozen-column]="expansionFrozenSlot >= 0"
                  [style.left]="frozenLeft(expansionFrozenSlot)"></td>
              }
              @if (selectionMode() === 'multiple') {
                <td
                  [class]="footerCellClass()"
                  [class.p-datatable-frozen-column]="selectionFrozenSlot >= 0"
                  [style.left]="frozenLeft(selectionFrozenSlot)"></td>
              }
              @for (field of fields(); track field.field) {
                @if (field.visible) {
                  @let slot = frozenSlot(field);
                  <td
                    [class]="footerCellClass()"
                    [class.p-datatable-frozen-column]="slot >= 0"
                    [class.gt-frozen-last]="slot === lastFrozenSlot"
                    [style.left]="frozenLeft(slot)"
                    [class]="getCellClass(field)"
                    [style.max-width.px]="field.width"
                    [style]="field.width ? { 'flex-basis': '0 0 ' + field.width + 'px' } : {}">
                    {{ footerValueFnValue(field) }}
                  </td>
                }
              }
            </tr>
          </ng-template>
        }
      </p-table>

      <!-- Context menu -->
      @if (contextMenuEnabled() && showContextMenu()) {
        <p-contextMenu [target]="cmDiv" [model]="contextMenuItems()" [appendTo]="contextMenuAppendTo()" />
      }
    </div>
  `
})
export class ConfigurableTableComponent<T = any> implements OnChanges, AfterViewChecked, OnDestroy {
  protected readonly toClassString = toClassString;
  // ============================================================================
  // Data and Column Configuration
  // ============================================================================

  /**
   * Array of data objects to display in the table.
   * Each object represents one row of data. Missing async data is treated as an empty table.
   */
  readonly data = input<T[], T[] | null | undefined>([], { transform: (value) => value ?? [] });

  /**
   * Array of column configurations defining table structure.
   * Uses ColumnConfig from lib/datashowbase for consistent column definitions.
   */
  readonly fields = input<ColumnConfig[]>([]);
  /** New array on a column revision, so the Optimus OnPush table refreshes its embedded templates too. */
  renderedFields: ColumnConfig[] = [];

  /**
   * Revision of the column configuration, bound as [fieldsRevision]="fieldsRevision()" of the owning table base. A
   * new value recomputes what depends on the mutable column state — frozen slots and header groups — and re-renders
   * the table, also when it runs OnPush.
   */
  readonly fieldsRevision = input(0);

  /**
   * Property name used as the unique identifier for each row.
   * Used by Optimus for tracking rows during updates and selections.
   */
  readonly dataKey = input<string>(undefined);

  // ============================================================================
  // Selection Configuration
  // ============================================================================

  /**
   * Row selection mode: 'single' allows one row selection, 'multiple' allows multiple,
   * null disables selection entirely.
   */
  readonly selectionMode = input<'single' | 'multiple' | null>('single');

  /**
   * Currently selected row(s). Type depends on selectionMode:
   * - single mode: T | null
   * - multiple mode: T[]
   */
  readonly selection = model<T | T[] | null>(null);

  /**
   * Emits when selection changes. Used for two-way binding with [(selection)].
   */

  /**
   * Emits when a row is selected (user action).
   */
  readonly rowSelect = output<any>();

  /**
   * Emits when a row is unselected (user action).
   */
  readonly rowUnselect = output<any>();

  // ============================================================================
  // Sorting Configuration
  // ============================================================================

  /**
   * Sorting mode: 'single' for single-column sort, 'multiple' for multi-column sort.
   */
  readonly sortMode = input<'single' | 'multiple'>('multiple');

  /**
   * Array of sort metadata for multi-column sorting.
   * Each entry specifies field name and sort order (1 = ascending, -1 = descending).
   */
  readonly multiSortMeta = input<any[]>([]);

  /**
   * Enable custom sorting logic instead of Optimus's default sorting.
   */
  readonly enableCustomSort = input(true);

  /**
   * Callback function for custom sorting logic.
   * Receives SortEvent and should sort the data array in place.
   */
  readonly customSortFn = input<(event: SortEvent) => void>(undefined);

  // ============================================================================
  // Filtering Configuration
  // ============================================================================

  /**
   * Enable column filtering functionality.
   * When true, displays filter row below column headers.
   */
  readonly hasFilter = input(false);

  /**
   * Custom match mode options for date filtering.
   * Array of SelectItem objects defining filter operators like 'equals', 'before', 'after'.
   */
  readonly customMatchModeOptions = input<SelectItem[]>([]);

  /**
   * Minimum date allowed in date filter pickers.
   */
  readonly minDate = input<Date>(
    new Date(sessionStorage.getItem(GlobalSessionNames.OLDEST_TRADING_DAY) ?? BaseSettings.OLDEST_TRADING_DAY_FALLBACK)
  );

  /**
   * Maximum date allowed in date filter pickers.
   */
  readonly maxDate = input<Date>(new Date('2099-12-31'));

  /**
   * Locale configuration for date formatting in filters.
   */
  readonly baseLocale = input<BaseLocale>({ language: 'en', dateFormat: 'yy-mm-dd' });

  /**
   * Locale string for numeric filter formatting (e.g., 'en-US', 'de-DE').
   */
  readonly formLocale = input('en-US');

  /**
   * Emits whenever the applied column filters change. The event is Optimus's filter event, its `filters` property is
   * the complete filter map of the table. Lets a parent persist and later restore what the user filtered for.
   */
  readonly filterChange = output<any>();

  /**
   * Reference to the Optimus table component for programmatic filter access.
   */
  readonly table = viewChild<Table>('table');

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
  readonly paginator = input(false);

  /**
   * Number of rows to display per page when pagination is enabled.
   */
  readonly rows = input(20);

  /**
   * Options for rows per page dropdown.
   */
  readonly rowsPerPageOptions = input<number[]>([10, 20, 50, 100]);

  /**
   * Returns true only when the paginator input is enabled AND data exceeds the smallest page size option.
   * Auto-hides the paginator when all rows fit on a single page.
   */
  get effectivePaginator(): boolean {
    return this.paginator() && this.data().length > Math.min(...this.rowsPerPageOptions());
  }

  /**
   * Index of the first row on the current page (0-based).
   */
  readonly firstRowIndex = input(0);

  /**
   * Emits when pagination changes (page number or rows per page).
   */
  readonly pageChange = output<any>();

  // ============================================================================
  // Scrolling Configuration
  // ============================================================================

  /**
   * Enable table scrolling mode (virtual scrolling or fixed header scrolling).
   */
  readonly scrollable = input(true);

  /**
   * Height of the scrollable viewport. Can be 'flex' for flexbox layout or a specific value like '400px'.
   */
  readonly scrollHeight = input<string>('flex');

  // ============================================================================
  // Visual Styling
  // ============================================================================

  /**
   * Show alternating row background colors for better readability.
   */
  readonly stripedRows = input(true);

  /**
   * Show grid lines between cells.
   */
  readonly showGridlines = input(true);

  /**
   * Show loading spinner overlay when data is being fetched.
   */
  readonly loading = input(false);

  /**
   * CSS class applied to the outer container div.
   */
  readonly containerClass = input<
    | string
    | string[]
    | {
        [key: string]: boolean;
      }
  >({
    'data-container': true
  });

  /**
   * Additional CSS classes applied to the container.
   */
  readonly customClass = input('');

  // ============================================================================
  // Row Expansion Configuration
  // ============================================================================

  /**
   * Enable row expansion functionality. When true, adds a toggle column.
   */
  readonly expandable = input(false);

  /**
   * Template for expanded row content. Receives the row data as context.
   */
  readonly expandedRowTemplate = input<TemplateRef<any>>(undefined);

  /**
   * Callback function to determine if a specific row can be expanded.
   * If not provided, all rows are expandable when expandable=true.
   */
  readonly canExpandFn = input<(row: T) => boolean>(undefined);

  /**
   * Width in pixels of the expansion toggle column.
   */
  readonly expansionColumnWidth = input(24);

  /**
   * CSS class for the expanded row icon (default: Font Awesome chevron down).
   */
  readonly expandedIcon = input('fa fa-fw fa-chevron-circle-down');

  /**
   * CSS class for the collapsed row icon (default: Font Awesome chevron right).
   */
  readonly collapsedIcon = input('fa fa-fw fa-chevron-circle-right');

  /**
   * Object tracking which rows are currently expanded. Keys are the dataKey values.
   * Managed internally by Optimus when rows are toggled.
   */
  readonly expandedRowKeys = input<{
    [key: string]: boolean;
  }>({});

  /**
   * Emits when a row is expanded. Event contains the row data.
   */
  readonly rowExpand = output<{
    data: T;
  }>();

  /**
   * Emits when a row is collapsed. Event contains the row data.
   */
  readonly rowCollapse = output<{
    data: T;
  }>();

  // ============================================================================
  // Context Menu Configuration
  // ============================================================================

  /**
   * Enable context menu on right-click.
   */
  readonly contextMenuEnabled = input(true);

  /**
   * Array of menu items for the context menu. Uses Optimus MenuItem interface.
   */
  readonly contextMenuItems = input<MenuItem[]>([]);

  /**
   * Controls visibility of the context menu. Typically bound to isActivated() logic.
   */
  readonly showContextMenu = input(false);

  /**
   * Determines where to append the context menu in the DOM.
   * Set to 'body' for nested tables to ensure proper positioning in the X-Y coordinate system.
   * When null, the context menu is appended to its parent container (default behavior).
   *
   * Usage: [contextMenuAppendTo]="'body'" for nested tables
   */
  readonly contextMenuAppendTo = input<string | null>(null);

  // ============================================================================
  // Template Customization
  // ============================================================================

  /**
   * Custom template for rendering cells. Overrides default cell rendering.
   * Receives row and field as context.
   */
  readonly customCellTemplate = contentChild<TemplateRef<any>>('customCell');

  /**
   * Custom template for rendering icon cells (templateName='icon').
   * Receives row, field, and value as context.
   */
  readonly iconTemplate = contentChild<TemplateRef<any>>('iconCell');

  /**
   * Custom template for rendering a second kind of icon cell (templateName='svgIcon'), for a column whose icon is
   * derived from the row while the column value itself stays the plain, sortable and filterable one.
   * Receives row, field, and value as context.
   */
  readonly svgIconTemplate = contentChild<TemplateRef<any>>('svgIconCell');

  /**
   * Custom template for rendering link icon cells (templateName='linkIcon').
   * Receives row and field as context.
   */
  readonly linkIconTemplate = contentChild<TemplateRef<any>>('linkIconCell');

  // ============================================================================
  // Cell Behavior Callbacks
  // ============================================================================

  /**
   * Callback to determine if owner cell should be highlighted.
   * Used with templateName='owner' to apply bold font weight.
   */
  readonly ownerHighlightFn = input<(row: T, field: ColumnConfig) => boolean>(undefined);

  /**
   * Callback to determine if a numeric value is negative.
   * Used with templateName='greenRed' to apply red color.
   */
  readonly negativeValueFn = input<(row: T, field: ColumnConfig) => boolean>(undefined);

  /**
   * Custom function to retrieve cell values. If not provided, uses field configuration or Helper.getValueByPath.
   */
  readonly valueGetterFn = input<(row: T, field: ColumnConfig) => any>(undefined);

  /**
   * Custom function to compute inline styles for individual table cells.
   * Receives the row data and column configuration, returns a style object or null.
   * Useful for conditional cell background coloring (e.g., color gradients based on values).
   */
  readonly cellStyleFn = input<
    (
      row: T,
      field: ColumnConfig
    ) => {
      [key: string]: string;
    } | null
  >(undefined);

  /**
   * Optional function returning a small mark shown before the content of a cell, with a tooltip. Returns null for a
   * cell without mark. Used for example to flag values that were filled automatically.
   */
  readonly cellMarkFn = input<(row: T, field: ColumnConfig) => CellMark | null>(undefined);

  // ============================================================================
  // Row Behavior Callbacks
  // ============================================================================

  /** Callback returning CSS class(es) for a row based on row data. */
  readonly rowClassFn = input<(row: T) => string | null>(undefined);

  /**
   * Callback deciding whether a row can be selected; a row it rejects ignores clicks. Without it every row is
   * selectable. Combine it with {@link rowClassFn} to show such rows as unavailable.
   */
  readonly rowSelectableFn = input<(row: T) => boolean>(undefined);

  /**
   * Adapts {@link rowSelectableFn} to the signature of the table, which passes the row with its index. An arrow
   * function, so that the table can call it without losing this component.
   */
  readonly isRowSelectable = (row: { data: T; index: number }): boolean => {
    const rowSelectableFn = this.rowSelectableFn();
    return !rowSelectableFn || rowSelectableFn(row.data);
  };

  // ============================================================================
  // Footer Configuration
  // ============================================================================

  /** Callback returning the display value for each footer cell. When provided, a footer row is rendered. */
  readonly footerValueFn = input<(field: ColumnConfig) => string>(undefined);

  /** CSS class applied to each footer cell (e.g., 'row-total' for lightskyblue background). */
  readonly footerCellClass = input<string>(undefined);

  // ============================================================================
  // Column Resizing
  // ============================================================================

  /**
   * Emits when a column is resized by the user.
   */
  readonly colResize = output<any>();

  // ============================================================================
  // Drag and Drop
  // ============================================================================

  /**
   * Emits when drag operation starts on a row.
   */
  readonly dragStart = output<{
    event: DragEvent;
    data: T;
  }>();

  /**
   * Emits when drag operation ends on a row.
   */
  readonly dragEnd = output<{
    event: DragEvent;
    data: T;
  }>();

  // ============================================================================
  // Click Events
  // ============================================================================

  /**
   * Emits when the table container is clicked.
   * Useful for focus management and menu handling.
   */
  readonly componentClick = output<any>();

  // ============================================================================
  // Frozen Columns
  // ============================================================================

  /**
   * Number of leading visible columns that stay in place while the table is scrolled horizontally. The expansion and
   * the checkbox column are fixed along with them. Fixing stops at the first column of a header group, a group header
   * spanning fixed and moving columns could not stay in place.
   *
   * The cells only get a CSS class and a left offset. The offsets are measured once from the header cells and again
   * when one of these cells changes its width, which a single ResizeObserver reports. A directive per cell, as the
   * pFrozenColumn of Optimus UI, would observe and measure every cell of the table and makes a large table slow.
   */
  readonly frozenColumnCount = input(0);

  /** Frozen slot of each fixed data column, the expansion and checkbox columns take the first slots */
  private frozenSlotByField = new Map<string, number>();

  /** Frozen slot of the expansion column, -1 when it is not fixed */
  expansionFrozenSlot = -1;

  /** Frozen slot of the checkbox column, -1 when it is not fixed */
  selectionFrozenSlot = -1;

  /** Slot of the rightmost fixed column, which draws the separating border, -1 when nothing is fixed */
  lastFrozenSlot = -1;

  /** Visible columns and settings the frozen slots were assigned for, an empty string when nothing is fixed */
  private frozenSignature = '';

  /** Set when the frozen slots changed and the header cells have to be observed anew once rendered */
  private frozenObserveRequired = false;

  private frozenResizeObserver: ResizeObserver = null;

  private readonly elementRef: ElementRef<HTMLElement> = inject(ElementRef);

  private readonly ngZone = inject(NgZone);

  // ============================================================================
  // Header Group Support
  // ============================================================================

  /** Computed group structure for the first header row */
  headerGroupRow: HeaderGroupItem[] = [];

  /** Columns belonging to groups (for the second header row) */
  groupedColumns: ColumnConfig[] = [];

  /** Whether the current column configuration uses header groups */
  hasGroups = false;

  // ============================================================================
  // Component Methods
  // ============================================================================

  /**
   * Angular lifecycle hook - responds to input property changes.
   * Currently monitors fields changes to ensure proper column updates.
   */
  ngOnChanges(changes: SimpleChanges): void {
    if (changes['fields'] || changes['fieldsRevision']) {
      this.renderedFields = [...this.fields()];
    }
    if (changes['fields'] || changes['data'] || changes['fieldsRevision']) {
      this.computeHeaderGroups();
    }
    if (changes['fieldsRevision'] || changes['frozenColumnCount'] || changes['fields']) {
      this.updateFrozenSlots();
    }
  }

  /** Recomputes the frozen slots when the number of fixed columns or the set of visible columns changed. */
  private updateFrozenSlots(): void {
    const count = Math.max(0, Math.min(this.frozenColumnCount() ?? 0, this.fields().length));
    const signature = count === 0 ? '' : this.createFrozenSignature(count);
    if (signature !== this.frozenSignature) {
      this.frozenSignature = signature;
      this.assignFrozenSlots(count);
      this.frozenObserveRequired = true;
    }
  }

  /**
   * Measures and observes the header cells of the fixed columns once they are rendered for the current frozen slots.
   * As long as the header row is not rendered yet, the next check tries again.
   */
  ngAfterViewChecked(): void {
    if (this.frozenObserveRequired) {
      this.frozenObserveRequired = !this.observeFrozenHeaderCells();
    }
  }

  ngOnDestroy(): void {
    this.frozenResizeObserver?.disconnect();
  }

  /**
   * Returns the frozen slot of a data column.
   *
   * @param field - Column configuration
   * @returns The slot, or -1 when the column moves with the scrolling
   */
  frozenSlot(field: ColumnConfig): number {
    return this.frozenSlotByField.get(field.field) ?? -1;
  }

  /**
   * Returns the left offset of a fixed column as reference to the CSS variable the measurement sets on the host.
   *
   * @param slot - Frozen slot of the column
   * @returns The CSS value for left, or null for a column that is not fixed
   */
  frozenLeft(slot: number): string | null {
    return slot >= 0 ? `var(--gt-frozen-left-${slot}, 0px)` : null;
  }

  /**
   * Describes everything the frozen slots depend on, so that a change of it can be detected cheaply.
   *
   * @param count - Number of data columns to fix
   * @returns The signature
   */
  private createFrozenSignature(count: number): string {
    let signature = `${count}|${this.expandable()}|${this.selectionMode()}`;
    for (const field of this.fields()) {
      if (field.visible) {
        signature += '|' + field.field + (field.headerGroupKey ? '#' : '');
      }
    }
    return signature;
  }

  /**
   * Gives the expansion column, the checkbox column and the first visible data columns up to the given number their
   * frozen slots, numbered from the left. Nothing is fixed when the first visible column already belongs to a group.
   *
   * @param count - Number of data columns to fix
   */
  private assignFrozenSlots(count: number): void {
    this.frozenSlotByField.clear();
    const leadSlots = (this.expandable() ? 1 : 0) + (this.selectionMode() === 'multiple' ? 1 : 0);
    let slot = leadSlots;
    for (const field of this.fields()) {
      if (slot - leadSlots >= count || (field.visible && field.headerGroupKey)) {
        break;
      }
      if (field.visible) {
        this.frozenSlotByField.set(field.field, slot++);
      }
    }
    const fixed = slot > leadSlots;
    this.expansionFrozenSlot = fixed && this.expandable() ? 0 : -1;
    this.selectionFrozenSlot = fixed && this.selectionMode() === 'multiple' ? leadSlots - 1 : -1;
    this.lastFrozenSlot = fixed ? slot - 1 : -1;
  }

  /**
   * Measures the fixed columns right away and observes their header cells, so that they are measured again whenever
   * one of them changes its width, for example after new data, a filter or a resized window.
   *
   * @returns False when the header row holds fewer cells than there are fixed columns, it is not rendered yet
   */
  private observeFrozenHeaderCells(): boolean {
    this.frozenResizeObserver?.disconnect();
    if (this.lastFrozenSlot < 0) {
      return true;
    }
    const cells = this.getFrozenHeaderCells();
    if (cells.length <= this.lastFrozenSlot) {
      return false;
    }
    this.measureFrozenColumns();
    this.ngZone.runOutsideAngular(() => {
      this.frozenResizeObserver ??= new ResizeObserver(() => this.measureFrozenColumns());
      cells.forEach((cell) => this.frozenResizeObserver.observe(cell));
    });
    return true;
  }

  /**
   * Sets the left offset of each fixed column as CSS variable on the host element. The offset is the sum of the widths
   * of the fixed columns to its left. Writing variables instead of bound values needs no change detection.
   */
  private measureFrozenColumns(): void {
    const host = this.elementRef.nativeElement;
    let left = 0;
    this.getFrozenHeaderCells().forEach((cell, slot) => {
      host.style.setProperty(`--gt-frozen-left-${slot}`, left + 'px');
      left += cell.getBoundingClientRect().width;
    });
  }

  /**
   * Returns the cells of the first header row that belong to fixed columns. In this row the cells follow the frozen
   * slots one to one, also with header groups, because fixed columns never belong to a group.
   *
   * @returns The header cells in slot order
   */
  private getFrozenHeaderCells(): HTMLElement[] {
    const headerRow = this.elementRef.nativeElement.querySelector('thead > tr');
    return headerRow ? (Array.from(headerRow.children).slice(0, this.lastFrozenSlot + 1) as HTMLElement[]) : [];
  }

  /**
   * Handles sort events from Optimus table.
   * Delegates to custom sort function if provided, otherwise performs no action
   * (allows Optimus default sorting when enableCustomSort=false).
   *
   * @param event - Optimus sort event containing sort metadata
   */
  onSort(event: SortEvent): void {
    const customSortFn = this.customSortFn();
    if (customSortFn) {
      customSortFn(event);
    }
  }

  /**
   * Returns the values the dropdown filter of a column is currently filtering for. The multi select of a
   * {@link FilterType.withOptions} column writes into the table but has no value of its own, so without this the
   * dropdown would show nothing after a filter was applied programmatically.
   *
   * @param field - Column configuration of the dropdown filter
   * @returns The selected filter values, or null when the column is not filtered
   */
  getSelectFilterValue(field: ColumnConfig): any {
    // Optimus removes the entry instead of blanking it, so a missing key means "not filtered".
    return this.table()?.filters?.[getFilterKey(field)]?.['value'] ?? null;
  }

  /**
   * Returns the property the table filters a column on.
   *
   * @param field - Column configuration of the filter
   * @returns The property name to hand to Optimus
   */
  filterKey(field: ColumnConfig): string {
    return getFilterKey(field);
  }

  /**
   * Removes all active column filters and restores the unfiltered row set. Required when the filter row is hidden
   * again, otherwise filters entered before would keep hiding rows without any visible control to reset them.
   */
  clearFilters(): void {
    const table = this.table();
    if (table) {
      table.filters = {};
      table._filter();
    }
  }

  /**
   * Handles row expand events from Optimus table.
   * Emits the event to parent component for lazy loading support.
   *
   * @param event - Optimus row expand event containing row data
   */
  onRowExpand(event: any): void {
    this.rowExpand.emit(event);
  }

  /**
   * Handles row collapse events from Optimus table.
   * Emits the event to parent component.
   *
   * @param event - Optimus row collapse event containing row data
   */
  onRowCollapse(event: any): void {
    this.rowCollapse.emit(event);
  }

  /**
   * Handles pagination events from Optimus table.
   *
   * @param event - Optimus page event containing page info
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
    const valueGetterFn = this.valueGetterFn();
    if (valueGetterFn) {
      return valueGetterFn(row, field);
    }
    // Use field's custom value function if available
    if (field.fieldValueFN) {
      return field.fieldValueFN(row, field, null);
    }
    // Default to path-based access
    return Helper.getValueByPath(row, field.field);
  }

  /**
   * Returns CSS class(es) for a row using the rowClassFn callback.
   *
   * @param row - Row data object
   * @returns CSS class string or null
   */
  getRowClass(row: T): string | null {
    const rowClassFn = this.rowClassFn();
    return rowClassFn ? rowClassFn(row) : null;
  }

  /**
   * Computes merged inline styles for a table cell, combining the column width style
   * with any custom cell styles from cellStyleFn.
   *
   * @param row - Row data object
   * @param field - Column configuration
   * @returns Merged style object for the native style binding
   */
  getMergedCellStyle(row: T, field: ColumnConfig): { [key: string]: string } {
    const widthStyle: { [key: string]: string } = field.width
      ? { 'flex-basis': '0 0 ' + field.width + 'px' }
      : ({} as { [key: string]: string });
    const cellStyleFn = this.cellStyleFn();
    if (!cellStyleFn) {
      return widthStyle;
    }
    const customStyle = cellStyleFn(row, field);
    if (!customStyle) {
      return widthStyle;
    }
    if (customStyle['background-color'] && this.frozenSlotByField.has(field.field)) {
      // A fixed cell must stay opaque, so a translucent background colour is laid over the row background instead.
      const { 'background-color': tint, ...rest } = customStyle;
      return { ...widthStyle, ...rest, 'box-shadow': `inset 0 0 0 100vmax ${tint}` };
    }
    return { ...widthStyle, ...customStyle };
  }

  /**
   * Determines CSS class for table cell based on data type.
   * Right-aligns numeric columns for better readability.
   *
   * @param field - Column configuration
   * @returns CSS class string
   */
  getCellClass(field: ColumnConfig): string {
    return field.dataType === DataType.Numeric ||
      field.dataType === DataType.NumericShowZero ||
      field.dataType === DataType.NumericInteger ||
      field.dataType === DataType.NumericRaw ||
      field.dataType === DataType.DateTimeNumeric
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
    const canExpandFn = this.canExpandFn();
    return canExpandFn ? canExpandFn(row) : true;
  }

  /**
   * Calculates colspan for expanded row content.
   * Accounts for expansion toggle column and selection checkbox column.
   *
   * @returns Number of columns to span
   */
  getExpandedColspan(): number {
    let count = this.fields().filter((f) => f.visible).length;
    if (this.expandable()) {
      count++;
    }
    if (this.selectionMode() === 'multiple') {
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
    const ownerHighlightFn = this.ownerHighlightFn();
    return ownerHighlightFn ? ownerHighlightFn(row, field) : false;
  }

  /**
   * Returns the cell's hover text. It repeats the displayed value unless the column defines a
   * {@link ColumnConfig.cellTooltipFN}, which lets a column surface something the cell itself has no room for.
   *
   * @param row - Row data object
   * @param field - Column configuration
   * @returns The tooltip text
   */
  getCellTooltip(row: T, field: ColumnConfig): any {
    const value = this.getValue(row, field);
    return field.cellTooltipFN ? field.cellTooltipFN(row, field, value) : value;
  }

  /**
   * Computes header group structure from column configurations.
   * Splits visible columns into ungrouped (rowspan=2) and grouped (colspan) items.
   */
  private computeHeaderGroups(): void {
    const visibleFields = this.fields().filter((f) => f.visible);
    this.hasGroups = visibleFields.some((f) => !!f.headerGroupKey);
    if (!this.hasGroups) {
      this.headerGroupRow = [];
      this.groupedColumns = [];
      return;
    }
    this.headerGroupRow = [];
    this.groupedColumns = [];
    let i = 0;
    while (i < visibleFields.length) {
      const field = visibleFields[i];
      if (!field.headerGroupKey) {
        this.headerGroupRow.push({ type: 'single', field });
        i++;
      } else {
        const groupKey = field.headerGroupKey;
        let colspan = 0;
        while (i < visibleFields.length && visibleFields[i].headerGroupKey === groupKey) {
          this.groupedColumns.push(visibleFields[i]);
          colspan++;
          i++;
        }
        this.headerGroupRow.push({
          type: 'group',
          label: field.headerGroupTranslated || groupKey,
          colspan
        });
      }
    }
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
    const negativeValueFn = this.negativeValueFn();
    if (negativeValueFn) {
      return negativeValueFn(row, field);
    }
    const value = this.getValue(row, field);
    return value != null && String(value).startsWith('-');
  }
}
