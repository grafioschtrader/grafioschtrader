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
    <p-dialog header="{{ 'TASK_FILTER' | translate }}" [visible]="visibleDialog"
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
   * If no saved state exists or the stored preference is stale (the set of task types changed since it
   * was saved), all items are selected by default.
   */
  private restoreSelectionFromStorage(): void {
    const savedTaskIds = TaskFilterDialogComponent.getValidatedStoredTaskIds(this.taskTypeEnum);
    if (savedTaskIds === null) {
      // No (valid) stored preference - select all
      this.selectedItems = [...this.taskFilterItems];
    } else {
      // Restore saved selection
      this.selectedItems = this.taskFilterItems.filter(item =>
        savedTaskIds.includes(item.taskAsId)
      );
    }
  }

  /**
   * Saves the selected task IDs to LocalStorage together with a checksum of the currently known set of
   * task types. The checksum lets {@link getValidatedStoredTaskIds} detect when the available task
   * types have changed (renumbering, additions, removals) and discard the now-stale selection.
   */
  private saveSelectionToStorage(taskIds: number[]): void {
    const payload: StoredTaskFilter = {
      checksum: TaskFilterDialogComponent.computeTaskSetChecksum(this.taskTypeEnum),
      taskIds
    };
    localStorage.setItem(TaskFilterDialogComponent.STORAGE_KEY, JSON.stringify(payload));
  }

  /** LocalStorage key for persisting task filter selections */
  static readonly STORAGE_KEY = 'taskDataChangeFilter';

  /**
   * Extracts the numeric task ids of an injected task type enum, sorted ascending. Mirrors the value
   * set enumerated by {@link initializeTaskFilterItems}.
   *
   * @param taskTypeEnum the merged task type enum (e.g. TaskType)
   * @returns the sorted numeric task ids
   */
  static extractTaskIds(taskTypeEnum: any): number[] {
    return Object.keys(taskTypeEnum)
      .filter(key => isNaN(Number(key)))
      .map(key => taskTypeEnum[key])
      .filter((value): value is number => typeof value === 'number')
      .sort((a, b) => a - b);
  }

  /**
   * Computes a small deterministic checksum over the current set of possible task ids. Any change to
   * the set (a renumbered, added or removed task type) yields a different checksum.
   *
   * @param taskTypeEnum the merged task type enum
   * @returns a short checksum string
   */
  static computeTaskSetChecksum(taskTypeEnum: any): string {
    const key = TaskFilterDialogComponent.extractTaskIds(taskTypeEnum).join(',');
    let hash = 5381;
    for (let i = 0; i < key.length; i++) {
      hash = ((hash << 5) + hash + key.charCodeAt(i)) | 0; // djb2, kept in 32-bit range
    }
    return (hash >>> 0).toString(36);
  }

  /**
   * Validates a raw LocalStorage value against the current task-set checksum. Pure helper (no
   * LocalStorage access) so the validation logic can be unit tested.
   *
   * @param raw the raw string read from LocalStorage, or null
   * @param currentChecksum the checksum of the currently known task set
   * @returns the stored task ids when the value is the current format and the checksum matches,
   *          otherwise null (missing, legacy bare-array format, corrupt JSON, or checksum mismatch)
   */
  static parseStoredFilter(raw: string | null, currentChecksum: string): number[] | null {
    if (!raw) {
      return null;
    }
    try {
      const parsed = JSON.parse(raw);
      if (!parsed || Array.isArray(parsed) || typeof parsed !== 'object'
        || !Array.isArray(parsed.taskIds) || parsed.checksum !== currentChecksum) {
        return null;
      }
      return parsed.taskIds as number[];
    } catch {
      return null;
    }
  }

  /**
   * Static utility method to get the stored task ids without instantiating the component, validated
   * against the current set of task types. Used by TaskDataChangeTableComponent to retrieve the filter.
   * When the stored value is missing, in the legacy format, corrupt, or no longer matches the current
   * task set, the stale key is cleared and null is returned (meaning "show all tasks").
   *
   * @param taskTypeEnum the merged task type enum
   * @returns the stored task ids, or null when there is no valid stored preference
   */
  static getValidatedStoredTaskIds(taskTypeEnum: any): number[] | null {
    const currentChecksum = TaskFilterDialogComponent.computeTaskSetChecksum(taskTypeEnum);
    const raw = localStorage.getItem(TaskFilterDialogComponent.STORAGE_KEY);
    const taskIds = TaskFilterDialogComponent.parseStoredFilter(raw, currentChecksum);
    if (taskIds === null && raw !== null) {
      // Drop a stale or legacy value so the next applied filter is written in the current format.
      localStorage.removeItem(TaskFilterDialogComponent.STORAGE_KEY);
    }
    return taskIds;
  }
}

/**
 * Persisted shape of the task filter in LocalStorage: the selected numeric task ids together with a
 * checksum of the task-type set they were chosen from.
 */
interface StoredTaskFilter {
  checksum: string;
  taskIds: number[];
}
