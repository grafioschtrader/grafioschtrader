import {
  Component,
  ContentChild,
  EventEmitter,
  Input,
  Output,
  TemplateRef
} from '@angular/core';
import {CommonModule} from '@angular/common';
import {TreeTableModule} from 'primeng/treetable';
import {ContextMenuModule} from 'primeng/contextmenu';
import {TooltipModule} from 'primeng/tooltip';
import {MenuItem, TreeNode} from 'primeng/api';
import {ColumnConfig} from './column.config';
import {DataType} from '../dynamic-form/models/data.type';
import {Helper} from '../helper/helper';

/**
 * Reusable tree table component providing standardized PrimeNG TreeTable functionality with column
 * configuration, sorting, selection, and context menu support.
 *
 * This component is the tree-table counterpart to ConfigurableTableComponent. It eliminates
 * repetitive p-treeTable boilerplate by encapsulating common patterns while maintaining flexibility
 * through content projection and template customization.
 *
 * Key features:
 * - Column-based configuration using ColumnConfig
 * - Single row selection mode
 * - Sortable columns with optional disable
 * - Context menu integration
 * - Pagination support
 * - Custom cell templates via content projection (icon, check, greenRed, owner)
 * - Row class and style callbacks for conditional formatting
 *
 * Usage example (simple):
 * ```html
 * <configurable-tree-table
 *   [data]="treeNodes" [fields]="fields" dataKey="uniqueKey"
 *   [valueGetterFn]="getValueByPath.bind(this)">
 * </configurable-tree-table>
 * ```
 *
 * Usage example (with context menu and footer):
 * ```html
 * <configurable-tree-table
 *   [data]="treeNodes" [fields]="fields" dataKey="id"
 *   [(selection)]="selectedNode" (nodeSelect)="onSelect($event)"
 *   [contextMenuItems]="menuItems" [showContextMenu]="true"
 *   [footerTemplate]="myFooter"
 *   [valueGetterFn]="getValueByPath.bind(this)">
 *   <h5 caption>{{ title | translate }}</h5>
 * </configurable-tree-table>
 *
 * <ng-template #myFooter>
 *   <tr><td>Total</td></tr>
 * </ng-template>
 * ```
 */
@Component({
  selector: 'configurable-tree-table',
  standalone: true,
  imports: [CommonModule, TreeTableModule, ContextMenuModule, TooltipModule],
  template: `
    <div #cmDiv (click)="onComponentClick($event)"
         [ngClass]="containerClass"
         [class]="customClass">
      <p-treeTable
        [value]="data"
        [columns]="fields"
        [dataKey]="dataKey"
        [selectionMode]="selectionMode"
        [(selection)]="selection"
        (selectionChange)="selectionChange.emit($event)"
        (onNodeSelect)="nodeSelect.emit($event)"
        (onNodeUnselect)="nodeUnselect.emit($event)"
        [sortField]="sortField"
        [sortOrder]="sortOrder"
        [paginator]="paginator"
        [rows]="rows"
        [scrollable]="scrollable"
        [scrollHeight]="scrollHeight"
        [showGridlines]="showGridlines">

        <!-- Caption slot with content projection -->
        <ng-template pTemplate="caption">
          <ng-content select="[caption]"></ng-content>
        </ng-template>

        <!-- Header template -->
        <ng-template pTemplate="header" let-columns>
          <tr>
            @for (field of columns; track field.field) {
              @if (field.visible) {
                <th [ttSortableColumn]="enableSort ? field.field : null"
                    [pTooltip]="field.headerTooltipTranslated"
                    [style.width.px]="field.width">
                  {{ field.headerTranslated }}
                  @if (enableSort) {
                    <p-treeTableSortIcon [field]="field.field"></p-treeTableSortIcon>
                  }
                </th>
              }
            }
          </tr>
        </ng-template>

        <!-- Body template -->
        <ng-template pTemplate="body" let-rowNode let-rowData="rowData" let-columns="columns">
          <tr [ttSelectableRow]="rowNode"
              [ngClass]="getRowClass(rowNode, rowData)"
              [style.background-color]="getRowStyle(rowData)">
            @for (field of columns; track field.field; let i = $index) {
              @if (field.visible) {
                <td [ngClass]="getCellClass(field)" [style.width.px]="field.width">
                  @if (i === 0) {
                    <p-treeTableToggler [rowNode]="rowNode"></p-treeTableToggler>
                  }
                  <!-- Cell content rendering -->
                  @if (customCellTemplate) {
                    <ng-container *ngTemplateOutlet="customCellTemplate; context: {$implicit: rowData, field: field}">
                    </ng-container>
                  } @else if (field.templateName === 'owner') {
                    <span [pTooltip]="getValue(rowData, field)"
                          [style]='isOwnerHighlighted(rowData, field) ? "font-weight:700" : null'
                          tooltipPosition="top">
                      {{ getValue(rowData, field) }}
                    </span>
                  } @else if (field.templateName === 'greenRed') {
                    <span [pTooltip]="getValue(rowData, field)"
                          [style.color]='isNegativeValue(rowData, field) ? "red" : "inherit"'
                          tooltipPosition="top">
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

        <!-- Footer template (content-projected) -->
        @if (footerTemplate) {
          <ng-template pTemplate="footer">
            <ng-container *ngTemplateOutlet="footerTemplate"></ng-container>
          </ng-template>
        }
      </p-treeTable>

      <!-- Context menu -->
      @if (showContextMenu) {
        <p-contextMenu [target]="cmDiv" [model]="contextMenuItems" [appendTo]="contextMenuAppendTo"></p-contextMenu>
      }
    </div>
  `
})
export class ConfigurableTreeTableComponent {

  // ============================================================================
  // Data and Column Configuration
  // ============================================================================

  /** Array of TreeNode objects to display in the tree table. */
  @Input() data: TreeNode[] = [];

  /** Array of column configurations defining tree table structure. */
  @Input() fields: ColumnConfig[] = [];

  /** Property name used as the unique identifier for each node. */
  @Input() dataKey: string;

  // ============================================================================
  // Selection Configuration
  // ============================================================================

  /** Row selection mode: 'single' allows one row selection, null disables selection. */
  @Input() selectionMode: 'single' | null = 'single';

  /** Currently selected TreeNode. */
  @Input() selection: TreeNode | null = null;

  /** Emits when selection changes. Used for two-way binding with [(selection)]. */
  @Output() selectionChange = new EventEmitter<TreeNode | null>();

  /** Emits when a tree node is selected (user action). */
  @Output() nodeSelect = new EventEmitter<any>();

  /** Emits when a tree node is unselected (user action). */
  @Output() nodeUnselect = new EventEmitter<any>();

  // ============================================================================
  // Sorting Configuration
  // ============================================================================

  /** Field name for default sort column. */
  @Input() sortField: string;

  /** Sort order: 1 = ascending, -1 = descending. */
  @Input() sortOrder = 1;

  /** Enable sort icons and sorting on columns. Set to false to disable all sorting. */
  @Input() enableSort = true;

  // ============================================================================
  // Pagination Configuration
  // ============================================================================

  /** Enable pagination controls at the bottom of the tree table. */
  @Input() paginator = false;

  /** Number of rows to display per page when pagination is enabled. */
  @Input() rows = 20;

  // ============================================================================
  // Scrolling Configuration
  // ============================================================================

  /** Enable tree table scrolling mode. */
  @Input() scrollable = false;

  /** Height of the scrollable viewport. */
  @Input() scrollHeight: string;

  // ============================================================================
  // Visual Styling
  // ============================================================================

  /** Show grid lines between cells. */
  @Input() showGridlines = true;

  /** CSS class applied to the outer container div. */
  @Input() containerClass: string | string[] | { [key: string]: boolean } = {};

  /** Additional CSS classes applied to the container. */
  @Input() customClass = '';

  // ============================================================================
  // Context Menu Configuration
  // ============================================================================

  /** Array of menu items for the context menu. */
  @Input() contextMenuItems: MenuItem[] = [];

  /** Controls visibility of the context menu. */
  @Input() showContextMenu = false;

  /** Determines where to append the context menu in the DOM. */
  @Input() contextMenuAppendTo: string | null = null;

  // ============================================================================
  // Template Customization
  // ============================================================================

  /** Custom template for rendering cells. Overrides default cell rendering. */
  @ContentChild('customCell') customCellTemplate?: TemplateRef<any>;

  /** Custom template for rendering icon cells (templateName='icon'). */
  @ContentChild('iconCell') iconTemplate?: TemplateRef<any>;

  /** Footer template projected into p-treeTable footer slot. */
  @Input() footerTemplate?: TemplateRef<any>;

  // ============================================================================
  // Cell Behavior Callbacks
  // ============================================================================

  /** Callback to determine if owner cell should be highlighted (bold). */
  @Input() ownerHighlightFn?: (row: any, field: ColumnConfig) => boolean;

  /** Callback to determine if a numeric value is negative. Used with greenRed template. */
  @Input() negativeValueFn?: (row: any, field: ColumnConfig) => boolean;

  /** Custom function to retrieve cell values. */
  @Input() valueGetterFn?: (row: any, field: ColumnConfig) => any;

  // ============================================================================
  // Row Behavior Callbacks
  // ============================================================================

  /** Callback returning CSS class(es) for a row based on rowNode and rowData. */
  @Input() rowClassFn?: (rowNode: any, rowData: any) => string | null;

  /** Callback returning inline background-color for a row. */
  @Input() rowStyleFn?: (rowData: any) => string | null;

  // ============================================================================
  // Click Events
  // ============================================================================

  /** Emits when the tree table container is clicked. */
  @Output() componentClick = new EventEmitter<any>();

  // ============================================================================
  // Component Methods
  // ============================================================================

  /**
   * Handles container click events.
   *
   * @param event - DOM click event
   */
  onComponentClick(event: any): void {
    this.componentClick.emit(event);
  }

  /**
   * Retrieves cell value from row data using field configuration.
   *
   * @param row - Row data object
   * @param field - Column configuration
   * @returns Formatted cell value
   */
  getValue(row: any, field: ColumnConfig): any {
    if (this.valueGetterFn) {
      return this.valueGetterFn(row, field);
    }
    if (field.fieldValueFN) {
      return field.fieldValueFN(row, field, null);
    }
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
   * Determines if a value is negative (for red/green coloring).
   *
   * @param row - Row data object
   * @param field - Column configuration
   * @returns True if value should be colored red
   */
  isNegativeValue(row: any, field: ColumnConfig): boolean {
    if (this.negativeValueFn) {
      return this.negativeValueFn(row, field);
    }
    const value = this.getValue(row, field);
    return value != null && String(value).startsWith('-');
  }

  /**
   * Determines if owner cell should be highlighted (bold).
   *
   * @param row - Row data object
   * @param field - Column configuration
   * @returns True if cell should be highlighted
   */
  isOwnerHighlighted(row: any, field: ColumnConfig): boolean {
    return this.ownerHighlightFn ? this.ownerHighlightFn(row, field) : false;
  }

  /**
   * Returns CSS class(es) for a row using the rowClassFn callback.
   *
   * @param rowNode - PrimeNG TreeNode wrapper
   * @param rowData - Row data object
   * @returns CSS class string or null
   */
  getRowClass(rowNode: any, rowData: any): string | null {
    return this.rowClassFn ? this.rowClassFn(rowNode, rowData) : null;
  }

  /**
   * Returns inline background-color style for a row using the rowStyleFn callback.
   *
   * @param rowData - Row data object
   * @returns CSS color string or null
   */
  getRowStyle(rowData: any): string | null {
    return this.rowStyleFn ? this.rowStyleFn(rowData) : null;
  }
}
