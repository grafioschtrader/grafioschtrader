import {Component, EventEmitter, Inject, Input, Output} from '@angular/core';
import {CommonModule} from '@angular/common';
import {FormsModule} from '@angular/forms';
import {TranslateModule, TranslateService} from '@ngx-translate/core';
import {DialogModule} from 'primeng/dialog';
import {ButtonModule} from 'primeng/button';
import {TASK_TYPE_ENUM} from '../service/task.type.enum.token';
import {ShowRecordConfigBase} from '../../datashowbase/show.record.config.base';
import {GlobalparameterService} from '../../services/globalparameter.service';
import {DataType} from '../../dynamic-form/models/data.type';
import {ConfigurableTableComponent} from '../../datashowbase/configurable-table.component';
import {TranslateValue} from '../../datashowbase/column.config';

/**
 * Represents a single task type item for display in the filter dialog.
 * Contains the task name, numeric ID, and selection state.
 */
export interface TaskFilterItem {
  /** The enum name of the task (e.g., 'GTNET_SERVER_STATUS_CHECK') */
  idTask: string;
  /** The numeric ID of the task (e.g., 20) */
  taskAsId: number;
}

/**
 * Dialog component for filtering which task types are displayed in the TaskDataChangeTableComponent.
 * Provides a table with checkboxes allowing users to select which idTask values to include.
 * Selections are persisted to LocalStorage for user preference retention.
 *
 * Extends ShowRecordConfigBase to leverage the standard column configuration and translation infrastructure.
 */
@Component({
  selector: 'task-filter-dialog',
  template: `
    <p-dialog header="{{ 'TASK_FILTER' | translate }}" [(visible)]="visibleDialog"
              [style]="{width: '500px'}"
              [contentStyle]="{'max-height':'600px'}"
              (onShow)="onShow()" (onHide)="onHide()" [modal]="true">

      <configurable-table
        [data]="taskFilterItems"
        [fields]="fields"
        [dataKey]="'idTask'"
        [selectionMode]="'multiple'"
        [(selection)]="selectedItems"
        [scrollable]="true"
        [scrollHeight]="'400px'"
        [showContextMenu]="false"
        [stripedRows]="true"
        [valueGetterFn]="getValueByPath.bind(this)"
        [baseLocale]="baseLocale">
      </configurable-table>

      <div class="flex justify-content-end mt-3">
        <p-button [disabled]="!hasSelection()"
                  [label]="'APPLY' | translate"
                  (click)="applyFilter()">
        </p-button>
      </div>
    </p-dialog>
  `,
  standalone: true,
  imports: [
    CommonModule,
    FormsModule,
    TranslateModule,
    DialogModule,
    ButtonModule,
    ConfigurableTableComponent
  ]
})
export class TaskFilterDialogComponent extends ShowRecordConfigBase {
  @Input() visibleDialog = false;
  @Output() closeDialog = new EventEmitter<number[] | null>();

  taskFilterItems: TaskFilterItem[] = [];
  selectedItems: TaskFilterItem[] = [];

  constructor(
    @Inject(TASK_TYPE_ENUM) private taskTypeEnum: any,
    translateService: TranslateService,
    gps: GlobalparameterService
  ) {
    super(translateService, gps);
    this.initializeColumns();
  }

  /**
   * Initializes the table columns with proper translation support.
   */
  private initializeColumns(): void {
    this.addColumnFeqH(DataType.NumericInteger, 'taskAsId', true, false, {width: 80});
    this.addColumn(DataType.String, 'idTask', 'ID_TASK', true, false,
      {width: 300, translateValues: TranslateValue.NORMAL});
  }

  /**
   * Called when the dialog is shown. Initializes the task filter items from the enum
   * and restores previous selections from LocalStorage.
   */
  onShow(): void {
    this.initializeTaskFilterItems();
    this.restoreSelectionFromStorage();
    this.translateHeadersAndColumns();
  }

  /**
   * Called when the dialog is hidden without applying. Emits null to indicate no change.
   */
  onHide(): void {
    this.closeDialog.emit(null);
  }

  /**
   * Checks if at least one task type is selected.
   * The Apply button is disabled when no selections are made.
   */
  hasSelection(): boolean {
    return this.selectedItems.length > 0;
  }

  /**
   * Applies the current filter selection. Saves to LocalStorage and emits the selected task IDs.
   */
  applyFilter(): void {
    const selectedTaskIds = this.selectedItems.map(item => item.taskAsId);
    this.saveSelectionToStorage(selectedTaskIds);
    this.closeDialog.emit(selectedTaskIds);
    this.visibleDialog = false;
  }

  /**
   * Initializes the task filter items from the injected task type enum.
   * Creates a TaskFilterItem for each enum value.
   */
  private initializeTaskFilterItems(): void {
    this.taskFilterItems = [];
    const enumKeys = Object.keys(this.taskTypeEnum).filter(key => isNaN(Number(key)));

    for (const key of enumKeys) {
      const value = this.taskTypeEnum[key];
      if (typeof value === 'number') {
        this.taskFilterItems.push({
          idTask: key,
          taskAsId: value
        });
      }
    }


    // Sort by taskAsId
    this.taskFilterItems.sort((a, b) => a.taskAsId - b.taskAsId);
    this.createTranslatedValueStore(this.taskFilterItems);
  }

  /**
   * Restores the selection state from LocalStorage.
   * If no saved state exists, all items are selected by default.
   */
  private restoreSelectionFromStorage(): void {
    const savedTaskIds = this.getStoredTaskIds();
    if (savedTaskIds === null) {
      // No stored preference - select all
      this.selectedItems = [...this.taskFilterItems];
    } else {
      // Restore saved selection
      this.selectedItems = this.taskFilterItems.filter(item =>
        savedTaskIds.includes(item.taskAsId)
      );
    }
  }

  /**
   * Saves the selected task IDs to LocalStorage.
   */
  private saveSelectionToStorage(taskIds: number[]): void {
    localStorage.setItem(TaskFilterDialogComponent.STORAGE_KEY, JSON.stringify(taskIds));
  }

  /**
   * Retrieves the stored task IDs from LocalStorage.
   * @returns Array of task IDs or null if no stored preference exists.
   */
  private getStoredTaskIds(): number[] | null {
    const stored = localStorage.getItem(TaskFilterDialogComponent.STORAGE_KEY);
    return stored ? JSON.parse(stored) : null;
  }

  /** LocalStorage key for persisting task filter selections */
  static readonly STORAGE_KEY = 'taskDataChangeFilter';

  /**
   * Static utility method to get stored task IDs without instantiating the component.
   * Used by TaskDataChangeService to retrieve filter values.
   * @returns Array of task IDs or null if no stored preference exists.
   */
  static getStoredTaskIdsStatic(): number[] | null {
    const stored = localStorage.getItem(TaskFilterDialogComponent.STORAGE_KEY);
    return stored ? JSON.parse(stored) : null;
  }
}
