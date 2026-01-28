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
import {MenuItem, SortEvent} from 'primeng/api';
import {InputTextModule} from 'primeng/inputtext';
import {InputNumberModule} from 'primeng/inputnumber';
import {DatePickerModule} from 'primeng/datepicker';
import {SelectModule} from 'primeng/select';
import {CheckboxModule} from 'primeng/checkbox';
import {ButtonModule} from 'primeng/button';
import {ColumnConfig, EditInputType} from './column.config';
import {DataType} from '../dynamic-form/models/data.type';
import {Helper} from '../helper/helper';
import {BaseLocale} from '../dynamic-form/models/base.locale';
import {ValueKeyHtmlSelectOptions} from '../dynamic-form/models/value.key.html.select.options';

/**
 * Event data emitted when a row edit operation occurs.
 */
export interface RowEditEvent<T> {
  /** The row data being edited */
  row: T;
  /** Index of the row in the data array */
  index: number;
}

/**
 * Event data emitted when saving a row.
 */
export interface RowEditSaveEvent<T> extends RowEditEvent<T> {
  /** Original row data before editing (for comparison or rollback) */
  originalRow: T;
  /** Whether this is a new row (not yet persisted) */
  isNew: boolean;
}

/**
 * Event data emitted when a field value changes during editing.
 */
export interface FieldValueChangeEvent<T> {
  /** The row data being edited */
  row: T;
  /** The column configuration of the changed field */
  field: ColumnConfig;
  /** The previous value */
  oldValue: any;
  /** The new value */
  newValue: any;
}

/**
 * Event data emitted when validation fails.
 */
export interface ValidationErrorEvent<T> {
  /** The row that failed validation */
  row: T;
  /** Array of field errors */
  errors: { field: string; message: string }[];
}

/**
 * Reusable table component providing PrimeNG inline row editing functionality.
 * Similar to ConfigurableTableComponent but with full CRUD editing support.
 *
 * Key features:
 * - Inline row editing with PrimeNG pEditableRow, p-cellEditor
 * - Automatic input type selection based on DataType or explicit configuration
 * - Dependent dropdown support (declarative and callback approaches)
 * - Validation with inline error display
 * - Context menu with "Add new row" option
 * - Auto-starts editing on newly added rows
 * - Event-based persistence (parent handles service calls)
 *
 * Usage example:
 * ```html
 * <editable-table
 *   [(data)]="entities"
 *   [fields]="fields"
 *   dataKey="id"
 *   [valueGetterFn]="getValueByPath.bind(this)"
 *   [createNewEntityFn]="createNewEntity.bind(this)"
 *   (rowEditSave)="saveEntity($event.row)">
 * </editable-table>
 * ```
 */
@Component({
  selector: 'editable-table',
  standalone: true,
  imports: [
    CommonModule, FormsModule, TableModule, ContextMenuModule, TooltipModule,
    InputTextModule, InputNumberModule, DatePickerModule, SelectModule,
    CheckboxModule, ButtonModule
  ],
  template: `
    <div #cmDiv (click)="onComponentClick($event)"
         [ngClass]="containerClass">
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
        [scrollable]="scrollable"
        [scrollHeight]="scrollHeight"
        [loading]="loading"
        [stripedRows]="stripedRows"
        [showGridlines]="showGridlines"
        editMode="row"
        [(expandedRowKeys)]="expandedRowKeys"
        (onRowExpand)="onRowExpand($event)"
        (onRowCollapse)="onRowCollapse($event)"
        (onRowSelect)="onRowSelect($event)"
        (onRowUnselect)="onRowUnselect($event)">

        <!-- Caption slot with content projection -->
        <ng-template pTemplate="caption">
          <ng-content select="[caption]"></ng-content>
        </ng-template>

        <!-- Header template -->
        <ng-template pTemplate="header" let-columns>
          <tr>
            @if (expandable) {
              <th [style.width.px]="expansionColumnWidth"></th>
            }
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
            @if (showEditColumn && !batchMode) {
              <th [style.width.px]="editColumnWidth"></th>
            }
          </tr>
        </ng-template>

        <!-- Body template with row editing -->
        <ng-template pTemplate="body" let-rowData let-columns="columns"
                     let-editing="editing" let-ri="rowIndex" let-expanded="expanded">
          <tr [pEditableRow]="rowData" [pSelectableRow]="rowData"
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

            @for (field of columns; track field.field) {
              @if (field.visible) {
                <td [style.max-width.px]="field.width"
                    [ngStyle]="field.width ? {'flex-basis': '0 0 ' + field.width + 'px'} : {}"
                    [ngClass]="getCellClass(field)">

                  <!-- Batch Mode: Always show edit inputs directly -->
                  @if (startInEditMode || batchMode) {
                    @if (canEditCell(field, rowData)) {
                      <ng-container [ngTemplateOutlet]="editInputTemplate"
                                    [ngTemplateOutletContext]="{field: field, rowData: rowData}">
                      </ng-container>
                      @if (showValidationErrors && getFieldError(rowData, field)) {
                        <small class="p-error">{{ getFieldError(rowData, field) }}</small>
                      }
                    } @else {
                      {{ getValue(rowData, field) }}
                    }
                  } @else {
                    <!-- Normal Mode: Use p-cellEditor for edit/display switching -->
                    <p-cellEditor>
                      <ng-template pTemplate="input">
                        @if (canEditCell(field, rowData)) {
                          <ng-container [ngTemplateOutlet]="editInputTemplate"
                                        [ngTemplateOutletContext]="{field: field, rowData: rowData}">
                          </ng-container>
                          @if (showValidationErrors && getFieldError(rowData, field)) {
                            <small class="p-error">{{ getFieldError(rowData, field) }}</small>
                          }
                        } @else {
                          {{ getValue(rowData, field) }}
                        }
                      </ng-template>

                      <ng-template pTemplate="output">
                        @if (customCellTemplate) {
                          <ng-container *ngTemplateOutlet="customCellTemplate;
                                        context: {$implicit: rowData, field: field, value: getValue(rowData, field)}">
                          </ng-container>
                        } @else if (field.templateName === 'greenRed') {
                          <span [style.color]="isNegativeValue(rowData, field) ? 'red' : 'green'">
                            {{ getValue(rowData, field) }}
                          </span>
                        } @else if (field.templateName === 'check') {
                          <span><i [ngClass]="{'fa fa-check': getValue(rowData, field)}" aria-hidden="true"></i></span>
                        } @else {
                          <span [pTooltip]="getValue(rowData, field)" tooltipPosition="top">
                            {{ getValue(rowData, field) }}
                          </span>
                        }
                      </ng-template>
                    </p-cellEditor>
                  }
                </td>
              }
            }

            <!-- Edit/Save/Cancel Buttons Column -->
            @if (showEditColumn && !batchMode) {
              <td>
                <div class="flex align-items-center justify-content-center gap-2">
                  @if (!editing && canEditRow(rowData)) {
                    <button pButton pRipple type="button" pInitEditableRow
                            icon="pi pi-pencil"
                            (click)="onRowEditInit(rowData, ri)"
                            class="p-button-rounded p-button-text">
                    </button>
                  }
                  @if (editing) {
                    <button pButton pRipple type="button" pSaveEditableRow
                            icon="pi pi-check"
                            (click)="onRowEditSave(rowData, ri)"
                            class="p-button-rounded p-button-text p-button-success mr-2">
                    </button>
                    <button pButton pRipple type="button" pCancelEditableRow
                            icon="pi pi-times"
                            (click)="onRowEditCancel(rowData, ri)"
                            class="p-button-rounded p-button-text p-button-danger">
                    </button>
                  }
                </div>
              </td>
            }
          </tr>
        </ng-template>

        <!-- Expanded row template - always present so PrimeNG can find it -->
        <ng-template #expandedrow let-row let-columns="fields">
          @if (expandable && expandedRowTemplate) {
            <tr>
              <td [attr.colspan]="getExpandedColspan()">
                <ng-container *ngTemplateOutlet="expandedRowTemplate; context: {$implicit: row}">
                </ng-container>
              </td>
            </tr>
          }
        </ng-template>
      </p-table>

      <!-- Edit Input Template (shared between batch and normal modes) -->
      <ng-template #editInputTemplate let-field="field" let-rowData="rowData">
        @switch (getEditInputType(field)) {
          <!-- Select/Dropdown -->
          @case (EditInputType.Select) {
            <select class="form-control input-sm"
                    [(ngModel)]="rowData[field.field]"
                    (ngModelChange)="onFieldChange(field, rowData, $event)"
                    [style.width.px]="field.width">
              @for (option of getOptionsForField(field, rowData); track option.key) {
                <option [value]="option.key" [disabled]="option.disabled">
                  {{ option.value }}
                </option>
              }
            </select>
          }

          <!-- Number Input -->
          @case (EditInputType.Number) {
            <input pInputText type="number"
                   [(ngModel)]="rowData[field.field]"
                   (ngModelChange)="onFieldChange(field, rowData, $event)"
                   [min]="field.cec?.min"
                   [max]="field.cec?.max"
                   [placeholder]="field.cec?.placeholder || ''"
                   [style.width.px]="field.width || 100">
          }

          <!-- PrimeNG InputNumber -->
          @case (EditInputType.InputNumber) {
            <p-inputNumber [(ngModel)]="rowData[field.field]"
                          (ngModelChange)="onFieldChange(field, rowData, $event)"
                          [minFractionDigits]="field.minFractionDigits || 0"
                          [maxFractionDigits]="field.cec?.maxFractionDigits || field.maxFractionDigits || 2"
                          [min]="field.cec?.min"
                          [max]="field.cec?.max"
                          [placeholder]="field.cec?.placeholder || ''">
            </p-inputNumber>
          }

          <!-- DatePicker -->
          @case (EditInputType.DatePicker) {
            <p-datepicker [(ngModel)]="rowData[field.field]"
                         (ngModelChange)="onFieldChange(field, rowData, $event)"
                         [dateFormat]="baseLocale.dateFormat"
                         [minDate]="field.cec?.minDate"
                         [maxDate]="field.cec?.maxDate">
            </p-datepicker>
          }

          <!-- Checkbox -->
          @case (EditInputType.Checkbox) {
            <p-checkbox [(ngModel)]="rowData[field.field]"
                       (ngModelChange)="onFieldChange(field, rowData, $event)"
                       [binary]="true">
            </p-checkbox>
          }

          <!-- ReadOnly - show value even in edit mode -->
          @case (EditInputType.ReadOnly) {
            {{ getValue(rowData, field) }}
          }

          <!-- Text Input (default) -->
          @default {
            <input pInputText type="text"
                   [(ngModel)]="rowData[field.field]"
                   (ngModelChange)="onFieldChange(field, rowData, $event)"
                   [maxlength]="field.cec?.maxLength"
                   [placeholder]="field.cec?.placeholder || ''">
          }
        }
      </ng-template>

      <!-- Context Menu -->
      @if (contextMenuEnabled && showContextMenu) {
        <p-contextMenu [target]="cmDiv" [model]="computedContextMenuItems"></p-contextMenu>
      }
    </div>
  `
})
export class EditableTableComponent<T = any> implements OnChanges {

  // ============================================================================
  // Data and Column Configuration
  // ============================================================================

  /** Array of data objects to display and edit in the table */
  @Input() data: T[] = [];

  /** Emits when data array changes (for two-way binding) */
  @Output() dataChange = new EventEmitter<T[]>();

  /** Array of column configurations defining table structure */
  @Input() fields: ColumnConfig[] = [];

  /** Property name used as the unique identifier for each row (required) */
  @Input() dataKey: string;

  // ============================================================================
  // Selection Configuration
  // ============================================================================

  /** Row selection mode: 'single' allows one row selection, null disables selection */
  @Input() selectionMode: 'single' | null = 'single';

  /** Currently selected row */
  @Input() selection: T | null = null;

  /** Emits when selection changes */
  @Output() selectionChange = new EventEmitter<T | null>();

  /** Emits when a row is selected */
  @Output() rowSelect = new EventEmitter<any>();

  /** Emits when a row is unselected */
  @Output() rowUnselect = new EventEmitter<any>();

  // ============================================================================
  // Sorting Configuration
  // ============================================================================

  /** Sorting mode: 'single' or 'multiple' column sort */
  @Input() sortMode: 'single' | 'multiple' = 'multiple';

  /** Array of sort metadata for multi-column sorting */
  @Input() multiSortMeta: any[] = [];

  /** Enable custom sorting logic */
  @Input() enableCustomSort = true;

  /** Callback function for custom sorting logic */
  @Input() customSortFn?: (event: SortEvent) => void;

  // ============================================================================
  // Visual Styling
  // ============================================================================

  /** Show alternating row background colors */
  @Input() stripedRows = true;

  /** Show grid lines between cells */
  @Input() showGridlines = true;

  /** Show loading spinner overlay */
  @Input() loading = false;

  /** CSS class applied to the outer container */
  @Input() containerClass: string | string[] | { [key: string]: boolean } = { 'data-container': true };

  /** Enable table scrolling */
  @Input() scrollable = true;

  /** Height of the scrollable viewport */
  @Input() scrollHeight: string = 'flex';

  // ============================================================================
  // Context Menu Configuration
  // ============================================================================

  /** Enable context menu on right-click */
  @Input() contextMenuEnabled = true;

  /** Array of additional menu items for the context menu */
  @Input() contextMenuItems: MenuItem[] = [];

  /** Controls visibility of the context menu */
  @Input() showContextMenu = false;

  // ============================================================================
  // Value Display Configuration
  // ============================================================================

  /** Custom function to retrieve cell values for display */
  @Input() valueGetterFn?: (row: T, field: ColumnConfig) => any;

  /** Locale configuration for date formatting */
  @Input() baseLocale: BaseLocale = { language: 'en', dateFormat: 'yy-mm-dd' };

  /** Callback to determine if a numeric value is negative (for red/green coloring) */
  @Input() negativeValueFn?: (row: T, field: ColumnConfig) => boolean;

  // ============================================================================
  // Edit-Specific Configuration
  // ============================================================================

  /** Whether to show the edit/save/cancel button column */
  @Input() showEditColumn = true;

  /** Width of the edit button column in pixels */
  @Input() editColumnWidth = 100;

  /** Callback to determine if a specific row can be edited */
  @Input() canEditRowFn?: (row: T) => boolean;

  /** Callback to validate row before saving. Return true if valid. */
  @Input() validateRowFn?: (row: T) => boolean;

  /** Factory function to create new row entities */
  @Input() createNewEntityFn?: () => T;

  /** Field to focus when auto-starting edit on a new row */
  @Input() autoEditField?: string;

  /** Whether to show inline validation error messages */
  @Input() showValidationErrors = true;

  /** Translation key for "Add new row" context menu item */
  @Input() addRowLabel = 'CREATE_NEW_RECORD';

  /**
   * When true, enables batch editing mode:
   * - Hides per-row edit/save/cancel buttons
   * - All editing happens in-memory
   * - Parent is responsible for collecting data and saving
   */
  @Input() batchMode = false;

  /**
   * When true, all rows start in edit mode automatically.
   * Useful for batch editing scenarios where all fields should be immediately editable.
   */
  @Input() startInEditMode = false;

  // ============================================================================
  // Row Expansion Configuration
  // ============================================================================

  /** Enable row expansion functionality. When true, adds a toggle column. */
  @Input() expandable = false;

  /** Template for expanded row content. Receives the row data as context. */
  @Input() expandedRowTemplate?: TemplateRef<any>;

  /** Callback function to determine if a specific row can be expanded. */
  @Input() canExpandFn?: (row: T) => boolean;

  /** Width in pixels of the expansion toggle column. */
  @Input() expansionColumnWidth = 24;

  /** CSS class for the expanded row icon. */
  @Input() expandedIcon = 'fa fa-fw fa-chevron-circle-down';

  /** CSS class for the collapsed row icon. */
  @Input() collapsedIcon = 'fa fa-fw fa-chevron-circle-right';

  // ============================================================================
  // Template Customization
  // ============================================================================

  /** Custom template for rendering cells in display mode */
  @ContentChild('customCell') customCellTemplate?: TemplateRef<any>;

  // ============================================================================
  // Edit Lifecycle Events
  // ============================================================================

  /** Emits when user initiates row editing */
  @Output() rowEditInit = new EventEmitter<RowEditEvent<T>>();

  /** Emits when user saves row edits (parent should handle persistence) */
  @Output() rowEditSave = new EventEmitter<RowEditSaveEvent<T>>();

  /** Emits when user cancels row editing */
  @Output() rowEditCancel = new EventEmitter<RowEditEvent<T>>();

  /** Emits when a new row is added via context menu */
  @Output() rowAdded = new EventEmitter<RowEditEvent<T>>();

  /** Emits when a field value changes during editing */
  @Output() fieldValueChange = new EventEmitter<FieldValueChangeEvent<T>>();

  /** Emits when validation fails during save attempt */
  @Output() validationError = new EventEmitter<ValidationErrorEvent<T>>();

  /** Emits when the table container is clicked */
  @Output() componentClick = new EventEmitter<any>();

  /** Emits when a row is expanded. */
  @Output() rowExpand = new EventEmitter<{ data: T }>();

  /** Emits when a row is collapsed. */
  @Output() rowCollapse = new EventEmitter<{ data: T }>();

  // ============================================================================
  // Internal State
  // ============================================================================

  /** Object tracking which rows are currently expanded. */
  expandedRowKeys: { [key: string]: boolean } = {};

  /** Reference to PrimeNG table component */
  @ViewChild('table') table: Table;

  /** Map of row keys to cloned row data for cancel restoration */
  private clonedRows: { [key: string]: T } = {};

  /** Map of row keys to per-row dropdown options */
  private rowOptions: { [key: string]: { [field: string]: ValueKeyHtmlSelectOptions[] } } = {};

  /** Map of row keys to validation error messages */
  private rowErrors: { [key: string]: { [field: string]: string } } = {};

  /** Set of row keys that are new (not yet persisted) */
  private newRows: Set<string> = new Set();

  /** Counter for generating temporary keys for new rows */
  private newRowCounter = 0;

  /** DataType enum for template usage */
  protected readonly DataType = DataType;

  /** EditInputType enum for template usage */
  protected readonly EditInputType = EditInputType;

  // ============================================================================
  // Computed Properties
  // ============================================================================

  /** Context menu items including "Add new row" if createNewEntityFn is provided */
  get computedContextMenuItems(): MenuItem[] {
    const items: MenuItem[] = [];

    if (this.createNewEntityFn) {
      items.push({
        label: this.addRowLabel,
        icon: 'pi pi-plus',
        command: () => this.addNewRow()
      });
    }

    if (this.contextMenuItems.length > 0) {
      if (items.length > 0) {
        items.push({ separator: true });
      }
      items.push(...this.contextMenuItems);
    }

    return items;
  }

  // ============================================================================
  // Lifecycle Hooks
  // ============================================================================

  ngOnChanges(changes: SimpleChanges): void {
    if (changes['data'] && !changes['data'].firstChange) {
      // Data changed externally - clear internal state
      this.clonedRows = {};
      this.rowOptions = {};
      this.rowErrors = {};
      this.newRows.clear();
    }
  }

  // ============================================================================
  // Row Key Management
  // ============================================================================

  /** Gets unique row key using dataKey property */
  private getRowKey(row: T): string {
    const key = row[this.dataKey];
    return key != null ? String(key) : `new_${this.newRowCounter}`;
  }

  // ============================================================================
  // Edit State Management
  // ============================================================================

  /** Handles row edit initialization */
  onRowEditInit(row: T, index: number): void {
    const rowKey = this.getRowKey(row);

    // Collapse the row if it's expanded (avoid UI confusion during editing)
    if (this.expandedRowKeys[rowKey]) {
      delete this.expandedRowKeys[rowKey];
    }

    // Clone the row for cancel restoration
    this.clonedRows[rowKey] = { ...row };

    // Initialize per-row options for dependent dropdowns
    this.initializeRowOptions(row);

    // Clear any previous errors
    this.rowErrors[rowKey] = {};

    this.rowEditInit.emit({ row, index });
  }

  /** Handles row edit save */
  onRowEditSave(row: T, index: number): void {
    const rowKey = this.getRowKey(row);
    const originalRow = this.clonedRows[rowKey];
    const isNew = this.newRows.has(rowKey);

    // Validate row
    if (!this.validateRow(row)) {
      return;
    }

    // Emit save event for parent to handle persistence
    this.rowEditSave.emit({ row, index, originalRow, isNew });

    // Cleanup
    this.cleanupRowState(rowKey);
  }

  /** Handles row edit cancel */
  onRowEditCancel(row: T, index: number): void {
    const rowKey = this.getRowKey(row);
    const isNew = this.newRows.has(rowKey);

    if (isNew) {
      // Remove new row entirely
      this.data = this.data.filter((_, i) => i !== index);
      this.dataChange.emit(this.data);
    } else if (this.clonedRows[rowKey]) {
      // Restore original values
      Object.assign(row, this.clonedRows[rowKey]);
    }

    // Cleanup
    this.cleanupRowState(rowKey);

    this.rowEditCancel.emit({ row, index });
  }

  /** Cleans up internal state for a row */
  private cleanupRowState(rowKey: string): void {
    delete this.clonedRows[rowKey];
    delete this.rowOptions[rowKey];
    delete this.rowErrors[rowKey];
    this.newRows.delete(rowKey);
  }

  // ============================================================================
  // New Row Management
  // ============================================================================

  /** Adds a new row and starts editing */
  addNewRow(): void {
    if (!this.createNewEntityFn) {
      return;
    }

    const newEntity = this.createNewEntityFn();
    const tempKey = `new_${++this.newRowCounter}`;

    // Assign temporary key if dataKey is null/undefined
    if (newEntity[this.dataKey] == null) {
      newEntity[this.dataKey] = tempKey;
    }

    const rowKey = this.getRowKey(newEntity);
    this.newRows.add(rowKey);

    // Add to data array
    this.data = [...this.data, newEntity];
    this.dataChange.emit(this.data);

    const index = this.data.length - 1;

    // Emit rowAdded event
    this.rowAdded.emit({ row: newEntity, index });

    // Initialize edit state and start editing (delayed to allow DOM update)
    setTimeout(() => {
      this.startEditingRow(newEntity);
    }, 50);
  }

  // ============================================================================
  // Field Change Handling
  // ============================================================================

  /** Handles field value changes during editing */
  onFieldChange(field: ColumnConfig, row: T, newValue: any): void {
    const rowKey = this.getRowKey(row);
    const oldValue = this.clonedRows[rowKey]?.[field.field];

    // Call column-specific change handler if configured
    if (field.cec?.onChangeFn) {
      field.cec.onChangeFn(row, field, newValue);
    }

    // Handle dependent fields
    this.updateDependentFields(field, row);

    // Emit change event
    this.fieldValueChange.emit({ row, field, oldValue, newValue });

    // Re-validate field
    this.validateField(row, field);
  }

  /** Updates options for fields that depend on the changed field */
  private updateDependentFields(changedField: ColumnConfig, row: T): void {
    const rowKey = this.getRowKey(row);

    for (const field of this.fields) {
      if (field.cec?.dependsOnField === changedField.field) {
        // Clear the dependent field's value
        row[field.field] = null;

        // Update options cache
        if (this.rowOptions[rowKey]) {
          delete this.rowOptions[rowKey][field.field];
        }
      }
    }
  }

  // ============================================================================
  // Options Management for Dropdowns
  // ============================================================================

  /** Initializes per-row options for all dropdown fields */
  private initializeRowOptions(row: T): void {
    const rowKey = this.getRowKey(row);
    this.rowOptions[rowKey] = {};

    for (const field of this.fields) {
      if (field.cec && this.getEditInputType(field) === EditInputType.Select) {
        this.rowOptions[rowKey][field.field] = this.resolveOptions(field, row);
      }
    }
  }

  /** Gets dropdown options for a field, supporting dependent dropdowns */
  getOptionsForField(field: ColumnConfig, row: T): ValueKeyHtmlSelectOptions[] {
    const rowKey = this.getRowKey(row);

    // Check cached options first
    if (this.rowOptions[rowKey]?.[field.field]) {
      return this.rowOptions[rowKey][field.field];
    }

    // Resolve and cache options
    const options = this.resolveOptions(field, row);
    if (!this.rowOptions[rowKey]) {
      this.rowOptions[rowKey] = {};
    }
    this.rowOptions[rowKey][field.field] = options;

    return options;
  }

  /** Resolves options for a dropdown field using priority: callback > optionsMap > static */
  private resolveOptions(field: ColumnConfig, row: T): ValueKeyHtmlSelectOptions[] {
    const cec = field.cec;

    // Priority 1: Callback function
    if (cec?.optionsProviderFn) {
      return cec.optionsProviderFn(row, field);
    }

    // Priority 2: Declarative optionsMap with dependsOnField
    if (cec?.dependsOnField && cec?.optionsMap) {
      const parentValue = row[cec.dependsOnField];
      if (parentValue != null && cec.optionsMap[parentValue]) {
        return cec.optionsMap[parentValue];
      }
      return [];
    }

    // Priority 3: Static options from valueKeyHtmlOptions
    return cec?.valueKeyHtmlOptions || [];
  }

  // ============================================================================
  // Validation
  // ============================================================================

  /** Validates a single field */
  private validateField(row: T, field: ColumnConfig): boolean {
    const rowKey = this.getRowKey(row);
    const value = row[field.field];

    if (!this.rowErrors[rowKey]) {
      this.rowErrors[rowKey] = {};
    }

    // Check required validation
    if (field.cec?.validation) {
      for (const validator of field.cec.validation) {
        const control = { value } as any;
        const result = validator(control);
        if (result) {
          const errorKey = Object.keys(result)[0];
          const errorRule = field.cec.errors?.find(e => e.name === errorKey);
          this.rowErrors[rowKey][field.field] = errorRule?.text || 'Invalid value';
          return false;
        }
      }
    }

    // Clear error if validation passes
    delete this.rowErrors[rowKey][field.field];
    return true;
  }

  /** Validates entire row */
  private validateRow(row: T): boolean {
    let isValid = true;
    const errors: { field: string; message: string }[] = [];
    const rowKey = this.getRowKey(row);

    for (const field of this.fields) {
      if (field.visible && field.cec) {
        if (!this.validateField(row, field)) {
          isValid = false;
          errors.push({
            field: field.field,
            message: this.rowErrors[rowKey]?.[field.field] || 'Invalid'
          });
        }
      }
    }

    // Call custom validation if provided
    if (isValid && this.validateRowFn && !this.validateRowFn(row)) {
      isValid = false;
    }

    if (!isValid) {
      this.validationError.emit({ row, errors });
    }

    return isValid;
  }

  /** Gets validation error for a field */
  getFieldError(row: T, field: ColumnConfig): string | null {
    const rowKey = this.getRowKey(row);
    return this.rowErrors[rowKey]?.[field.field] || null;
  }

  // ============================================================================
  // Editability Checks
  // ============================================================================

  /** Checks if a row can be edited */
  canEditRow(row: T): boolean {
    if (this.canEditRowFn) {
      return this.canEditRowFn(row);
    }
    return true;
  }

  /** Checks if a specific cell can be edited */
  canEditCell(field: ColumnConfig, row: T): boolean {
    // Must have cec configured
    if (!field.cec) {
      return false;
    }

    // Check explicit read-only
    if (field.cec.inputType === EditInputType.ReadOnly) {
      return false;
    }

    // Check row-specific editability callback
    if (field.cec.canEditFn) {
      return field.cec.canEditFn(row, field);
    }

    return true;
  }

  // ============================================================================
  // Input Type Resolution
  // ============================================================================

  /** Determines the appropriate input type for editing based on column config */
  getEditInputType(field: ColumnConfig): EditInputType {
    // Explicit input type takes priority
    if (field.cec?.inputType) {
      return field.cec.inputType;
    }

    // Infer from DataType
    switch (field.dataType) {
      case DataType.Numeric:
      case DataType.NumericShowZero:
        return EditInputType.InputNumber;
      case DataType.NumericInteger:
        return EditInputType.Number;
      case DataType.DateNumeric:
      case DataType.DateString:
      case DataType.DateTimeNumeric:
        return EditInputType.DatePicker;
      case DataType.Boolean:
        return EditInputType.Checkbox;
      case DataType.String:
        // Check if dropdown options are configured
        if (this.hasDropdownOptions(field)) {
          return EditInputType.Select;
        }
        return EditInputType.Text;
      default:
        return EditInputType.Text;
    }
  }

  /** Checks if a field has dropdown options configured */
  private hasDropdownOptions(field: ColumnConfig): boolean {
    return !!(
      field.cec?.valueKeyHtmlOptions?.length > 0 ||
      field.cec?.optionsProviderFn ||
      (field.cec?.dependsOnField && field.cec?.optionsMap)
    );
  }

  // ============================================================================
  // Value Display
  // ============================================================================

  /** Retrieves cell value from row data using field configuration */
  getValue(row: T, field: ColumnConfig): any {
    if (this.valueGetterFn) {
      return this.valueGetterFn(row, field);
    }
    if (field.fieldValueFN) {
      return field.fieldValueFN(row, field, null);
    }
    return Helper.getValueByPath(row, field.field);
  }

  /** Determines CSS class for table cell based on data type */
  getCellClass(field: ColumnConfig): string {
    return (field.dataType === DataType.Numeric ||
      field.dataType === DataType.NumericShowZero ||
      field.dataType === DataType.NumericInteger ||
      field.dataType === DataType.DateTimeNumeric)
      ? 'text-end'
      : '';
  }

  /** Determines if a value is negative (for red/green coloring) */
  isNegativeValue(row: T, field: ColumnConfig): boolean {
    if (this.negativeValueFn) {
      return this.negativeValueFn(row, field);
    }
    const value = this.getValue(row, field);
    return value != null && String(value).startsWith('-');
  }

  // ============================================================================
  // Event Handlers
  // ============================================================================

  /** Handles sort events from PrimeNG table */
  onSort(event: SortEvent): void {
    if (this.customSortFn) {
      this.customSortFn(event);
    }
  }

  /** Handles container click events */
  onComponentClick(event: any): void {
    this.componentClick.emit(event);
  }

  /** Handles row select events */
  onRowSelect(event: any): void {
    this.rowSelect.emit(event);
  }

  /** Handles row unselect events */
  onRowUnselect(event: any): void {
    this.rowUnselect.emit(event);
  }

  // ============================================================================
  // Row Expansion Methods
  // ============================================================================

  /**
   * Determines if a row can be expanded.
   * Uses custom function if provided, otherwise allows all rows.
   */
  canExpand(row: T): boolean {
    return this.canExpandFn ? this.canExpandFn(row) : true;
  }

  /**
   * Calculates colspan for expanded row content.
   * Accounts for expansion toggle column and edit buttons column.
   */
  getExpandedColspan(): number {
    let count = this.fields.filter(f => f.visible).length;
    if (this.expandable) {
      count++;
    }
    if (this.showEditColumn && !this.batchMode) {
      count++;
    }
    return count;
  }

  /** Handles row expand events from PrimeNG table. */
  onRowExpand(event: any): void {
    this.rowExpand.emit(event);
  }

  /** Handles row collapse events from PrimeNG table. */
  onRowCollapse(event: any): void {
    this.rowCollapse.emit(event);
  }

  // ============================================================================
  // Batch Mode Methods
  // ============================================================================

  /**
   * Returns the current data array. In batch mode, this returns the edited data
   * that can be collected by the parent component for saving.
   */
  getData(): T[] {
    return this.data;
  }

  /**
   * Validates all rows and returns true if all are valid.
   * Useful for batch mode before saving.
   */
  validateAllRows(): boolean {
    let allValid = true;
    for (const row of this.data) {
      if (!this.validateRow(row)) {
        allValid = false;
      }
    }
    return allValid;
  }

  /**
   * Programmatically initiates editing for a specific row.
   * Finds the edit button for the row, clicks it, and focuses the first editable cell.
   *
   * @param row The row data to start editing
   */
  startEditingRow(row: T): void {
    const rowKey = this.getRowKey(row);
    const rowIndex = this.data.findIndex(r => this.getRowKey(r) === rowKey);

    if (rowIndex >= 0) {
      // Initialize edit state
      this.onRowEditInit(row, rowIndex);

      // PrimeNG Table uses internal state for row editing.
      // We trigger editing by simulating the pInitEditableRow behavior.
      if (this.table) {
        // Use setTimeout to ensure DOM is updated
        setTimeout(() => {
          // Find and click the edit button programmatically
          const tableEl = this.table.el.nativeElement;
          const rows = tableEl.querySelectorAll('tbody tr');
          const targetRow = rows[rowIndex];
          if (targetRow) {
            const editButton = targetRow.querySelector('[pInitEditableRow]');
            if (editButton) {
              (editButton as HTMLElement).click();

              // Focus the first editable input after edit mode is activated
              setTimeout(() => {
                this.focusFirstEditableCell(targetRow);
              }, 50);
            }
          }
        }, 0);
      }
    }
  }

  /**
   * Focuses the first editable input element in the specified row.
   *
   * @param rowElement The HTML row element to search for inputs
   */
  private focusFirstEditableCell(rowElement: Element): void {
    // Find the first visible editable input in the row
    const editableInputs = rowElement.querySelectorAll(
      'input:not([type="hidden"]):not([disabled]), ' +
      'select:not([disabled]), ' +
      'textarea:not([disabled]), ' +
      'p-inputNumber input, ' +
      'p-datepicker input, ' +
      'p-select select'
    );

    if (editableInputs.length > 0) {
      const firstInput = editableInputs[0] as HTMLElement;
      firstInput.focus();

      // If it's a text input, select all text for easy replacement
      if (firstInput instanceof HTMLInputElement && firstInput.type === 'text') {
        firstInput.select();
      }
    }
  }
}
