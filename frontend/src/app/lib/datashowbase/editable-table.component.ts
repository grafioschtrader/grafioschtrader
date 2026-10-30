import {
  ChangeDetectorRef,
  Component,
  contentChild,
  inject,
  input,
  TemplateRef,
  signal,
  afterNextRender,
  Injector,
  DestroyRef,
  output,
  viewChild,
  model,
  computed,
  effect,
  untracked
} from '@angular/core';
import { takeUntilDestroyed } from '@angular/core/rxjs-interop';
import { Subscription } from 'rxjs';
import { NgTemplateOutlet } from '@angular/common';
import { toClassString } from '../helper/css-class.helper';
import { TranslateService } from '@ngx-translate/core';
import { FormsModule, Validators } from '@angular/forms';
import { Table, TableModule } from '@openng/optimus-ui/table';
import { ContextMenuModule } from '@openng/optimus-ui/contextmenu';
import { TooltipModule } from '@openng/optimus-ui/tooltip';
import { MenuItem, SortEvent } from '@openng/optimus-ui/api';
import { InputTextModule } from '@openng/optimus-ui/inputtext';
import { InputNumberModule } from '@openng/optimus-ui/inputnumber';
import { DatePickerModule } from '@openng/optimus-ui/datepicker';
import { SelectModule } from '@openng/optimus-ui/select';
import { CheckboxModule } from '@openng/optimus-ui/checkbox';
import { ButtonModule } from '@openng/optimus-ui/button';
import { Textarea } from '@openng/optimus-ui/textarea';
import { ColumnConfig, EditInputType } from './column.config';
import { DataType } from '../dynamic-form/models/data.type';
import { Helper } from '../helper/helper';
import { BaseLocale } from '../dynamic-form/models/base.locale';
import { ValueKeyHtmlSelectOptions } from '../dynamic-form/models/value.key.html.select.options';

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
 * Reusable table component providing Optimus inline row editing functionality.
 * Similar to ConfigurableTableComponent but with full CRUD editing support.
 *
 * Key features:
 * - Inline row editing with Optimus pEditableRow, p-cellEditor
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
 *   [fields]="fields" [fieldsRevision]="fieldsRevision()"
 *   dataKey="id"
 *   [valueGetterFn]="valueGetter"
 *   [createNewEntityFn]="createNewEntity"
 *   (rowEditSave)="saveEntity($event.row)">
 * </editable-table>
 * ```
 */
@Component({
  selector: 'editable-table',
  imports: [
    FormsModule,
    TableModule,
    ContextMenuModule,
    TooltipModule,
    InputTextModule,
    InputNumberModule,
    DatePickerModule,
    SelectModule,
    CheckboxModule,
    ButtonModule,
    Textarea,
    NgTemplateOutlet
  ],
  template: `
    @let expandedRowTemplateValue = expandedRowTemplate();
    @let customCellTemplateValue = customCellTemplate();

    <div #cmDiv (click)="onComponentClick($event)" [class]="toClassString(containerClass())">
      <p-table
        #table
        [columns]="renderedFields()"
        [value]="rows()"
        [dataKey]="dataKey()"
        [selectionMode]="selectionMode()"
        [selection]="selection()"
        (selectionChange)="selection.set($event)"
        [sortMode]="sortMode()"
        [multiSortMeta]="multiSortMeta()"
        [customSort]="enableCustomSort()"
        (sortFunction)="onSort($event)"
        [scrollable]="scrollable()"
        [scrollHeight]="scrollHeight()"
        [loading]="loading()"
        [stripedRows]="stripedRows()"
        [showGridlines]="showGridlines()"
        editMode="row"
        [expandedRowKeys]="expandedRowKeys"
        (onRowExpand)="onRowExpand($event)"
        (onRowCollapse)="onRowCollapse($event)"
        (onRowSelect)="onRowSelect($event)"
        (onRowUnselect)="onRowUnselect($event)">
        <!-- Caption slot with content projection -->
        <ng-template pTemplate="caption">
          <ng-content select="[caption]" />
        </ng-template>

        <!-- Header template -->
        <ng-template pTemplate="header" let-columns>
          <tr>
            @if (expandable()) {
              <th [style.width.px]="expansionColumnWidth()"></th>
            }
            @for (field of columns; track field.field) {
              @if (field.visible) {
                <th
                  [pSortableColumn]="field.field"
                  [pTooltip]="field.headerTooltipTranslated"
                  [style.max-width.px]="field.width"
                  [style]="field.width ? { 'flex-basis': '0 0 ' + field.width + 'px' } : {}">
                  {{ field.headerTranslated }}
                  @if (isFieldRequired(field)) {
                    <span class="et-required-marker"> *</span>
                  }
                  <p-sortIcon [field]="field.field" />
                </th>
              }
            }
            @if (showEditColumn() && !batchMode()) {
              <th [style.width.px]="editColumnWidth()"></th>
            }
          </tr>
        </ng-template>

        <!-- Body template with row editing -->
        <ng-template
          pTemplate="body"
          let-rowData
          let-columns="columns"
          let-editing="editing"
          let-ri="rowIndex"
          let-expanded="expanded">
          <tr
            [pEditableRow]="rowData"
            [pSelectableRow]="rowData"
            [pContextMenuRow]="contextMenuEnabled() ? rowData : null">
            <!-- Expansion toggle cell -->
            @if (expandable()) {
              <td [style.width.px]="expansionColumnWidth()">
                @if (canExpand(rowData)) {
                  <a [pRowToggler]="rowData" href="#" (click)="$event.preventDefault()">
                    <i [class]="expanded ? expandedIcon() : collapsedIcon()"></i>
                  </a>
                }
              </td>
            }

            @for (field of columns; track field.field) {
              @if (field.visible) {
                <td
                  [style.max-width.px]="field.width"
                  [style]="field.width ? { 'flex-basis': '0 0 ' + field.width + 'px' } : {}"
                  [class]="getCellClass(field)">
                  <!-- Batch Mode: Always show edit inputs directly -->
                  @if (startInEditMode() || batchMode()) {
                    @if (canEditCell(field, rowData)) {
                      <ng-container
                        [ngTemplateOutlet]="editInputTemplate"
                        [ngTemplateOutletContext]="{
                          field: field,
                          rowData: rowData
                        }" />
                      @if (showValidationErrors() && getFieldError(rowData, field)) {
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
                          <ng-container
                            [ngTemplateOutlet]="editInputTemplate"
                            [ngTemplateOutletContext]="{
                              field: field,
                              rowData: rowData
                            }" />
                          @if (showValidationErrors() && getFieldError(rowData, field)) {
                            <small class="p-error">{{ getFieldError(rowData, field) }}</small>
                          }
                        } @else {
                          {{ getValue(rowData, field) }}
                        }
                      </ng-template>

                      <ng-template pTemplate="output">
                        @if (customCellTemplateValue) {
                          <ng-container
                            *ngTemplateOutlet="
                              customCellTemplateValue;
                              context: {
                                $implicit: rowData,
                                field: field,
                                value: getValue(rowData, field)
                              }
                            " />
                        } @else if (field.templateName === 'greenRed') {
                          <span [style.color]="isNegativeValue(rowData, field) ? 'red' : 'green'">
                            {{ getValue(rowData, field) }}
                          </span>
                        } @else if (field.templateName === 'check') {
                          <span
                            ><i
                              [class.fa]="getValue(rowData, field)"
                              [class.fa-check]="getValue(rowData, field)"
                              aria-hidden="true"></i
                          ></span>
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
            @if (showEditColumn() && !batchMode()) {
              <td>
                <div class="flex align-items-center justify-content-center gap-2">
                  @if (!editing && canEditRow(rowData)) {
                    <button
                      pButton
                      pRipple
                      type="button"
                      pInitEditableRow
                      (click)="onRowEditInit(rowData, ri)"
                      class="p-button-rounded p-button-text">
                      <i class="pi pi-pencil" pButtonIcon></i>
                    </button>
                  }
                  @if (!editing && canDeleteRowFn() && canDeleteRowFn()(rowData)) {
                    <button
                      pButton
                      pRipple
                      type="button"
                      (click)="onRowDeleteClick(rowData, ri)"
                      class="p-button-rounded p-button-text p-button-danger">
                      <i class="pi pi-trash" pButtonIcon></i>
                    </button>
                  }
                  @if (editing) {
                    <button
                      pButton
                      pRipple
                      type="button"
                      pSaveEditableRow
                      (click)="onRowEditSave(rowData, ri)"
                      class="p-button-rounded p-button-text p-button-success mr-2">
                      <i class="pi pi-check" pButtonIcon></i>
                    </button>
                    <button
                      pButton
                      pRipple
                      type="button"
                      pCancelEditableRow
                      (click)="onRowEditCancel(rowData, ri)"
                      class="p-button-rounded p-button-text p-button-danger">
                      <i class="pi pi-times" pButtonIcon></i>
                    </button>
                  }
                </div>
              </td>
            }
          </tr>
        </ng-template>

        <!-- Expanded row template - always present so Optimus can find it -->
        <ng-template #expandedrow let-row let-columns="fields">
          @if (expandable() && expandedRowTemplateValue) {
            <tr>
              <td [attr.colspan]="getExpandedColspan()">
                <ng-container *ngTemplateOutlet="expandedRowTemplateValue; context: { $implicit: row }" />
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
            <select
              class="form-control input-sm"
              [attr.data-field]="field.field"
              [class.required-input]="isFieldRequired(field)"
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
            <input
              pInputText
              type="number"
              [class.required-input]="isFieldRequired(field)"
              [(ngModel)]="rowData[field.field]"
              (ngModelChange)="onFieldChange(field, rowData, $event)"
              [min]="$safeNavigationMigration(field.cec?.min)"
              [max]="$safeNavigationMigration(field.cec?.max)"
              [placeholder]="field.cec?.placeholder || ''"
              [style.width.px]="field.width || 100" />
          }

          <!-- Optimus InputNumber -->
          @case (EditInputType.InputNumber) {
            <p-inputNumber
              [(ngModel)]="rowData[field.field]"
              (ngModelChange)="onFieldChange(field, rowData, $event)"
              [locale]="numberLocale()"
              [minFractionDigits]="field.minFractionDigits || 0"
              [maxFractionDigits]="field.cec?.maxFractionDigits || field.maxFractionDigits || 2"
              [min]="$safeNavigationMigration(field.cec?.min)"
              [max]="$safeNavigationMigration(field.cec?.max)"
              [placeholder]="field.cec?.placeholder || ''"
              [inputStyleClass]="isFieldRequired(field) ? 'text-end required-input' : 'text-end'" />
          }

          <!-- DatePicker -->
          @case (EditInputType.DatePicker) {
            <p-datepicker
              [(ngModel)]="rowData[field.field]"
              (ngModelChange)="onFieldChange(field, rowData, $event)"
              [dateFormat]="baseLocale().dateFormat"
              [minDate]="$safeNavigationMigration(field.cec?.minDate)"
              [maxDate]="$safeNavigationMigration(field.cec?.maxDate)"
              dataType="date"
              selectionMode="single"
              [showOnFocus]="false"
              [showIcon]="true"
              appendTo="body"
              [inputStyleClass]="isFieldRequired(field) ? 'required-input' : ''" />
          }

          <!-- Checkbox -->
          @case (EditInputType.Checkbox) {
            <p-checkbox
              [(ngModel)]="rowData[field.field]"
              (ngModelChange)="onFieldChange(field, rowData, $event)"
              [binary]="true" />
          }

          <!-- Textarea for multi-line string values -->
          @case (EditInputType.Textarea) {
            <textarea
              pTextarea
              [class.required-input]="isFieldRequired(field)"
              [(ngModel)]="rowData[field.field]"
              (ngModelChange)="onFieldChange(field, rowData, $event)"
              [maxlength]="$safeNavigationMigration(field.cec?.maxLength)"
              [rows]="field.cec?.rows || 3"
              [placeholder]="field.cec?.placeholder || ''"
              style="width: 100%"></textarea>
          }

          <!-- ReadOnly - show value even in edit mode -->
          @case (EditInputType.ReadOnly) {
            {{ getValue(rowData, field) }}
          }

          <!-- Text Input (default) -->
          @default {
            <input
              pInputText
              type="text"
              [attr.data-field]="field.field"
              [class.required-input]="isFieldRequired(field)"
              [(ngModel)]="rowData[field.field]"
              (ngModelChange)="onFieldChange(field, rowData, $event)"
              [maxlength]="$safeNavigationMigration(field.cec?.maxLength)"
              [placeholder]="field.cec?.placeholder || ''" />
          }
        }
      </ng-template>

      <!-- Context Menu -->
      @if (contextMenuEnabled() && showContextMenu()) {
        <p-contextMenu [target]="cmDiv" [model]="menuModel()" [appendTo]="contextMenuAppendTo()" />
      }
    </div>
  `
})
export class EditableTableComponent<T extends object = any> {
  private translateService = inject(TranslateService);

  protected readonly toClassString = toClassString;
  // ============================================================================
  // Data and Column Configuration
  // ============================================================================

  /** Array of data objects to display and edit in the table */
  readonly data = input<T[]>([]);

  /** Rows owned by the table, including additions not yet echoed by its parent. */
  readonly rows = signal<T[]>([]);
  private emittedRows?: T[];

  /** Emits when data array changes (for two-way binding) */
  readonly dataChange = output<T[]>();

  /** Array of column configurations defining table structure */
  readonly fields = input<ColumnConfig[]>([]);
  /** New reference carries column revisions into the Optimus OnPush table. */
  readonly renderedFields = computed(() => {
    this.fieldsRevision();
    return [...this.fields()];
  });

  /**
   * Revision of the column configuration, bound as [fieldsRevision]="fieldsRevision()" of the owning table base. A
   * new value re-renders the table after the columns were changed in place, also when it runs OnPush.
   */
  readonly fieldsRevision = input(0);

  /** Property name used as the unique identifier for each row (required) */
  readonly dataKey = input<string>();

  // ============================================================================
  // Selection Configuration
  // ============================================================================

  /** Row selection mode: 'single' allows one row selection, null disables selection */
  readonly selectionMode = input<'single' | null>('single');

  /** Currently selected row */
  readonly selection = model<T | null>(null);

  /** Emits when selection changes */

  /** Emits when a row is selected */
  readonly rowSelect = output<any>();

  /** Emits when a row is unselected */
  readonly rowUnselect = output<any>();

  // ============================================================================
  // Sorting Configuration
  // ============================================================================

  /** Sorting mode: 'single' or 'multiple' column sort */
  readonly sortMode = input<'single' | 'multiple'>('multiple');

  /** Array of sort metadata for multi-column sorting */
  readonly multiSortMeta = input<any[]>([]);

  /** Enable custom sorting logic */
  readonly enableCustomSort = input(true);

  /** Callback function for custom sorting logic */
  readonly customSortFn = input<(event: SortEvent) => void>();

  // ============================================================================
  // Visual Styling
  // ============================================================================

  /** Show alternating row background colors */
  readonly stripedRows = input(true);

  /** Show grid lines between cells */
  readonly showGridlines = input(true);

  /** Show loading spinner overlay */
  readonly loading = input(false);

  /** CSS class applied to the outer container */
  readonly containerClass = input<
    | string
    | string[]
    | {
        [key: string]: boolean;
      }
  >({
    'data-container': true
  });

  /** Enable table scrolling */
  readonly scrollable = input(true);

  /** Height of the scrollable viewport */
  readonly scrollHeight = input<string>('flex');

  // ============================================================================
  // Context Menu Configuration
  // ============================================================================

  /** Enable context menu on right-click */
  readonly contextMenuEnabled = input(true);

  /** Array of additional menu items for the context menu */
  readonly contextMenuItems = input<MenuItem[]>([]);

  /** Controls visibility of the context menu */
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
  // Value Display Configuration
  // ============================================================================

  /** Custom function to retrieve cell values for display */
  readonly valueGetterFn = input<(row: T, field: ColumnConfig) => any>();

  /** Locale configuration for date formatting */
  readonly baseLocale = input<BaseLocale>({ language: 'en', dateFormat: 'yy-mm-dd' });

  /**
   * BCP 47 locale (e.g. 'de-CH') for the InputNumber edit control so its grouping/decimal separators match the
   * user's GT number formatting (GlobalparameterService.getLocale()). Falls back to the runtime default when
   * not set.
   */
  readonly numberLocale = input<string>();

  /** Callback to determine if a numeric value is negative (for red/green coloring) */
  readonly negativeValueFn = input<(row: T, field: ColumnConfig) => boolean>();

  // ============================================================================
  // Edit-Specific Configuration
  // ============================================================================

  /** Whether to show the edit/save/cancel button column */
  readonly showEditColumn = input(true);

  /** Width of the edit button column in pixels */
  readonly editColumnWidth = input(100);

  /** Callback to determine if a specific row can be edited */
  readonly canEditRowFn = input<(row: T) => boolean>();

  /** Callback to determine if a specific row can be deleted. When set, shows a trash button per row. */
  readonly canDeleteRowFn = input<(row: T) => boolean>();

  /** Callback to validate row before saving. Return true if valid. */
  readonly validateRowFn = input<(row: T) => boolean>();

  /** Factory function to create new row entities */
  readonly createNewEntityFn = input<() => T>();

  /** Field to focus when auto-starting edit on a new row */
  readonly autoEditField = input<string>();

  /** Whether to show inline validation error messages */
  readonly showValidationErrors = input(true);

  /** Translation key for "Add new row" context menu item */
  readonly addRowLabel = input('CREATE_NEW_RECORD');

  /**
   * When true, enables batch editing mode:
   * - Hides per-row edit/save/cancel buttons
   * - All editing happens in-memory
   * - Parent is responsible for collecting data and saving
   */
  readonly batchMode = input(false);

  /**
   * When true, all rows start in edit mode automatically.
   * Useful for batch editing scenarios where all fields should be immediately editable.
   */
  readonly startInEditMode = input(false);

  // ============================================================================
  // Row Expansion Configuration
  // ============================================================================

  /** Enable row expansion functionality. When true, adds a toggle column. */
  readonly expandable = input(false);

  /** Template for expanded row content. Receives the row data as context. */
  readonly expandedRowTemplate = input<TemplateRef<any>>();

  /** Callback function to determine if a specific row can be expanded. */
  readonly canExpandFn = input<(row: T) => boolean>();

  /** Width in pixels of the expansion toggle column. */
  readonly expansionColumnWidth = input(24);

  /** CSS class for the expanded row icon. */
  readonly expandedIcon = input('fa fa-fw fa-chevron-circle-down');

  /** CSS class for the collapsed row icon. */
  readonly collapsedIcon = input('fa fa-fw fa-chevron-circle-right');

  // ============================================================================
  // Template Customization
  // ============================================================================

  /** Custom template for rendering cells in display mode */
  readonly customCellTemplate = contentChild<TemplateRef<any>>('customCell');

  // ============================================================================
  // Edit Lifecycle Events
  // ============================================================================

  /** Emits when user initiates row editing */
  readonly rowEditInit = output<RowEditEvent<T>>();

  /** Emits when user saves row edits (parent should handle persistence) */
  readonly rowEditSave = output<RowEditSaveEvent<T>>();

  /** Emits when user cancels row editing */
  readonly rowEditCancel = output<RowEditEvent<T>>();

  /** Emits when a new row is added via context menu */
  readonly rowAdded = output<RowEditEvent<T>>();

  /** Emits when a row delete button is clicked. Parent handles actual deletion logic. */
  readonly rowDelete = output<RowEditEvent<T>>();

  /** Emits when a field value changes during editing */
  readonly fieldValueChange = output<FieldValueChangeEvent<T>>();

  /** Emits when validation fails during save attempt */
  readonly validationError = output<ValidationErrorEvent<T>>();

  /** Emits when the table container is clicked */
  readonly componentClick = output<any>();

  /** Emits when a row is expanded. */
  readonly rowExpand = output<{
    data: T;
  }>();

  /** Emits when a row is collapsed. */
  readonly rowCollapse = output<{
    data: T;
  }>();

  // ============================================================================
  // Internal State
  // ============================================================================

  /** Object tracking which rows are currently expanded. */
  expandedRowKeys: { [key: string]: boolean } = {};

  /** Reference to Optimus table component */
  readonly table = viewChild<Table>('table');

  /** Map of row keys to cloned row data for cancel restoration */
  private clonedRows: { [key: string]: T } = {};

  /** Map of row keys to per-row dropdown options */
  private rowOptions: {
    [key: string]: { [field: string]: ValueKeyHtmlSelectOptions[] };
  } = {};

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
  // Context Menu Model
  // ============================================================================

  /**
   * Stable context-menu model bound to the Optimus context menu. Rebuilt only when the relevant
   * inputs change (see {@link buildContextMenu}). It must hold a stable array reference between
   * rebuilds: Optimus's ContextMenu reprocesses its internal items on every `model` assignment, so
   * binding a getter that returns a fresh array each change-detection cycle would rebuild the menu
   * continuously and swallow item clicks.
   */
  readonly menuModel = signal<MenuItem[]>([]);
  private readonly renderInjector = inject(Injector);
  private readonly destroyRef = inject(DestroyRef);
  private menuTranslation?: Subscription;

  /** Marks this table after state changes made outside a template event, such as the delayed start of editing. */
  private readonly changeDetectorRef = inject(ChangeDetectorRef);

  constructor() {
    effect(() => {
      this.data();
      untracked(() => {
        const data = this.data();
        if (data !== this.emittedRows) {
          this.rows.set(data ?? []);
          this.clonedRows = {};
          this.rowOptions = {};
          this.rowErrors = {};
          this.newRows.clear();
        }
        this.emittedRows = undefined;
      });
    });
    effect(() => {
      this.contextMenuItems();
      this.createNewEntityFn();
      this.addRowLabel();
      untracked(() => {
        this.buildContextMenu();
      });
    });
  }

  /**
   * Rebuilds {@link menuModel} from the current inputs. Prepends an "Add new row" item when
   * {@link createNewEntityFn} is provided and translates its label (the only label this component
   * owns); the parent-supplied {@link contextMenuItems} arrive pre-translated and are appended as-is.
   */
  private buildContextMenu(): void {
    this.menuTranslation?.unsubscribe();
    const items: MenuItem[] = [];

    const createNewEntityFn = this.createNewEntityFn();
    if (createNewEntityFn) {
      const addRowItem: MenuItem = {
        label: this.addRowLabel(),
        icon: 'pi pi-plus',
        command: () => this.addNewRow()
      };

      items.push(addRowItem);
    }

    if (this.contextMenuItems().length > 0) {
      if (items.length > 0) {
        items.push({ separator: true });
      }
      items.push(...this.contextMenuItems());
    }

    if (createNewEntityFn) {
      this.menuTranslation = this.translateService
        .get(this.addRowLabel())
        .pipe(takeUntilDestroyed(this.destroyRef))
        .subscribe((label) =>
          this.menuModel.set(items.map((item, index) => (index === 0 ? { ...item, label } : item)))
        );
    } else {
      this.menuModel.set(items);
    }
  }

  // ============================================================================
  // Row Key Management
  // ============================================================================

  /** Gets unique row key using dataKey property */
  private getRowKey(row: T): string {
    const key = row[this.dataKey() as keyof T];
    return key != null ? String(key) : String(-this.newRowCounter);
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
      this.rows.update((rows) => rows.filter((_, i) => i !== index));
      this.publishRows();
    } else if (this.clonedRows[rowKey]) {
      // Restore original values
      Object.assign(row, this.clonedRows[rowKey]);
    }

    // Cleanup
    this.cleanupRowState(rowKey);

    this.rowEditCancel.emit({ row, index });
  }

  /** Handles row delete button click */
  onRowDeleteClick(row: T, index: number): void {
    this.rowDelete.emit({ row, index });
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
    const createNewEntityFn = this.createNewEntityFn();
    const dataKey = this.dataKey();
    if (!createNewEntityFn || dataKey == null) {
      return;
    }

    const newEntity = createNewEntityFn();
    const tempKey = -++this.newRowCounter;

    // Assign a temporary key only when the row has no identifier.
    if (Reflect.get(newEntity, dataKey) == null) {
      Reflect.set(newEntity, dataKey, tempKey);
    }

    const rowKey = this.getRowKey(newEntity);
    this.newRows.add(rowKey);

    // Add to data array
    this.rows.update((rows) => [...rows, newEntity]);
    this.publishRows();

    const index = this.rows().length - 1;

    // Emit rowAdded event
    this.rowAdded.emit({ row: newEntity, index });

    this.startEditingRow(newEntity);
  }

  /** Remembers the emitted reference so a two-way binding preserves the current row's edit session. */
  private publishRows(): void {
    this.emittedRows = this.rows();
    this.dataChange.emit(this.emittedRows);
  }

  // ============================================================================
  // Field Change Handling
  // ============================================================================

  /** Handles field value changes during editing */
  onFieldChange(field: ColumnConfig, row: T, newValue: any): void {
    const rowKey = this.getRowKey(row);
    const oldValue = this.clonedRows[rowKey]?.[field.field as keyof T];

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

    for (const field of this.fields()) {
      if (field.cec?.dependsOnField === changedField.field) {
        // Clear the dependent field's value
        Object.assign(row, { [field.field]: null });

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

    for (const field of this.fields()) {
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
      const parentValue = row[cec.dependsOnField as keyof T] as string | number;
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
    const value = row[field.field as keyof T];

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
          const errorRule = field.cec.errors?.find((e) => e.name === errorKey);
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

    for (const field of this.fields()) {
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
    const validateRowFn = this.validateRowFn();
    if (isValid && validateRowFn && !validateRowFn(row)) {
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
    const canEditRowFn = this.canEditRowFn();
    if (canEditRowFn) {
      return canEditRowFn(row);
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
      (field.cec?.valueKeyHtmlOptions?.length ?? 0) > 0 ||
      field.cec?.optionsProviderFn ||
      (field.cec?.dependsOnField && field.cec?.optionsMap)
    );
  }

  /** Checks if a field has Validators.required in its validation rules */
  isFieldRequired(field: ColumnConfig): boolean {
    return field.cec?.validation?.includes(Validators.required) ?? false;
  }

  // ============================================================================
  // Value Display
  // ============================================================================

  /** Retrieves cell value from row data using field configuration */
  getValue(row: T, field: ColumnConfig): any {
    const valueGetterFn = this.valueGetterFn();
    if (valueGetterFn) {
      return valueGetterFn(row, field);
    }
    if (field.fieldValueFN) {
      return field.fieldValueFN(row, field, null);
    }
    return Helper.getValueByPath(row, field.field);
  }

  /** Determines CSS class for table cell based on data type */
  getCellClass(field: ColumnConfig): string {
    return field.dataType === DataType.Numeric ||
      field.dataType === DataType.NumericShowZero ||
      field.dataType === DataType.NumericInteger ||
      field.dataType === DataType.NumericRaw ||
      field.dataType === DataType.DateTimeNumeric
      ? 'text-end'
      : '';
  }

  /** Determines if a value is negative (for red/green coloring) */
  isNegativeValue(row: T, field: ColumnConfig): boolean {
    const negativeValueFn = this.negativeValueFn();
    if (negativeValueFn) {
      return negativeValueFn(row, field);
    }
    const value = this.getValue(row, field);
    return value != null && String(value).startsWith('-');
  }

  // ============================================================================
  // Event Handlers
  // ============================================================================

  /** Handles sort events from Optimus table */
  onSort(event: SortEvent): void {
    const customSortFn = this.customSortFn();
    if (customSortFn) {
      customSortFn(event);
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
    const canExpandFn = this.canExpandFn();
    return canExpandFn ? canExpandFn(row) : true;
  }

  /**
   * Calculates colspan for expanded row content.
   * Accounts for expansion toggle column and edit buttons column.
   */
  getExpandedColspan(): number {
    let count = this.fields().filter((f) => f.visible).length;
    if (this.expandable()) {
      count++;
    }
    if (this.showEditColumn() && !this.batchMode()) {
      count++;
    }
    return count;
  }

  /** Handles row expand events from Optimus table. */
  onRowExpand(event: any): void {
    this.rowExpand.emit(event);
  }

  /** Handles row collapse events from Optimus table. */
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
    return this.rows();
  }

  /**
   * Validates all rows and returns true if all are valid.
   * Useful for batch mode before saving.
   */
  validateAllRows(): boolean {
    let allValid = true;
    for (const row of this.rows()) {
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
    const rowIndex = this.rows().findIndex((r) => this.getRowKey(r) === rowKey);

    if (rowIndex >= 0) {
      // Initialize edit state (clone for cancel, init options, clear errors)
      this.onRowEditInit(row, rowIndex);

      const table = this.table();
      if (table) {
        // Directly set Optimus's editing state — no DOM manipulation needed
        table.initRowEdit(row);
        // A new value input also refreshes the Optimus table when this method is called by a menu command.
        this.rows.update((rows) => [...rows]);
        this.changeDetectorRef.markForCheck();

        // Focus first editable cell after Angular change detection renders the edit inputs
        afterNextRender(
          () => {
            const renderedTable = this.table();
            if (!renderedTable) return;
            const tableEl = renderedTable.el.nativeElement;
            const rows = tableEl.querySelectorAll('tbody tr');
            if (rows[rowIndex]) {
              this.focusFirstEditableCell(rows[rowIndex]);
            }
          },
          { injector: this.renderInjector }
        );
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
