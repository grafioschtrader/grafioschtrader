import { Component, computed, effect, inject, input, OnDestroy, signal, untracked } from '@angular/core';
import { rxResource, takeUntilDestroyed } from '@angular/core/rxjs-interop';
import { TranslateModule } from '@ngx-translate/core';
import { MenuItem } from '@openng/optimus-ui/api';
import { DialogService } from '@openng/optimus-ui/dynamicdialog';
import { TooltipModule } from '@openng/optimus-ui/tooltip';
import { forkJoin, of } from 'rxjs';
import { BaseSettings } from '../../base.settings';
import { ColumnConfig, TranslateValue } from '../../datashowbase/column.config';
import { ConfigurableTableComponent } from '../../datashowbase/configurable-table.component';
import { FilterType } from '../../datashowbase/filter.type';
import { CrudMenuOptions, TableCrudSupportMenu } from '../../datashowbase/table.crud.support.menu';
import { DataType } from '../../dynamic-form/models/data.type';
import { GlobalSessionNames } from '../../global.session.names';
import { HelpIds } from '../../help/help.ids';
import { TranslateHelper } from '../../helper/translate.helper';
import { reportResourceErrors } from '../../login/service/resource.error.report';
import { InfoLevelType } from '../../message/info.leve.type';
import { GlobalparameterService } from '../../services/globalparameter.service';
import { TaskDataChangeService } from '../service/task.data.change.service';
import { TASK_EXTENDED_SERVICE } from '../service/task.extend.service.token';
import { TASK_TYPE_ENUM } from '../service/task.type.enum.token';
import {
  MAIN_TASK_QUEUE,
  ProgressStateType,
  TaskDataChange,
  TaskDataChangeFormConstraints
} from '../types/task.data.change';
import { ITaskExtendService } from './itask.extend.service';
import { TaskDataChangeEditComponent } from './task-data-change-edit.component';
import { TaskDataChangeTabMenuComponent } from './task-data-change-tab-menu.component';
import { TaskFilterDialogComponent } from './task-filter-dialog.component';

/**
 * Shows the batch jobs of one queue in a table. The queue comes from the child route of the tab menu; without one, the
 * main queue is shown. Each queue keeps its own column settings, the task filter applies to all of them.
 */
@Component({
  template: `
    <configurable-table
      [data]="entityList()"
      [loading]="tasksResource.isLoading()"
      [fields]="fields"
      [fieldsRevision]="fieldsRevision()"
      [dataKey]="'idTaskDataChange'"
      [selectionMode]="'single'"
      [(selection)]="selectedEntity"
      [multiSortMeta]="multiSortMeta"
      [customSortFn]="customSorter"
      [scrollable]="true"
      [scrollHeight]="'flex'"
      [frozenColumnCount]="frozenColumnCount"
      [stripedRows]="true"
      [showGridlines]="true"
      [expandable]="true"
      [canExpandFn]="canExpand"
      [expandedRowTemplate]="expandedContent"
      [hasFilter]="hasFilter"
      [customMatchModeOptions]="customMatchModeOptions"
      [minDate]="minDate"
      [maxDate]="maxDate"
      [baseLocale]="baseLocale"
      [formLocale]="formLocale"
      [containerClass]="{
        'data-container-full': true,
        'active-border': isActivated(),
        'passiv-border': !isActivated()
      }"
      [showContextMenu]="!!contextMenuItems()"
      [contextMenuAppendTo]="'body'"
      [contextMenuItems]="contextMenuItems()"
      [valueGetterFn]="valueGetter"
      (componentClick)="onComponentClick($event)">
      <div caption>
        <h4>{{ queueLabelKey() | translate }}</h4>
        <p>{{ 'NO_DATA_REFRESH' | translate }}</p>
      </div>

      <!-- Custom cell template with tooltip support -->
      <ng-template #customCell let-row let-field="field">
        @switch (field.templateName) {
          @case ('check') {
            <span
              ><i
                [class.fa]="getValueByPath(row, field)"
                [class.fa-check]="getValueByPath(row, field)"
                aria-hidden="true"></i
            ></span>
          }
          @default {
            <span [pTooltip]="getTooltipValueByPath(row, field)" tooltipPosition="top">
              {{ getValueByPath(row, field) }}
            </span>
          }
        }
      </ng-template>
    </configurable-table>

    <!-- Expanded row content template for error details -->
    <ng-template #expandedContent let-tdc>
      <h4>{{ tdc.failedMessageCode | translate }}</h4>
      @if (tdc.failedStackTrace) {
        <textarea [rows]="getShowLines(tdc.failedStackTrace)" style="width:100%;">{{ tdc.failedStackTrace }}</textarea>
      }
    </ng-template>

    @if (visibleDialog()) {
      <task-data-change-edit
        [visibleDialog]="visibleDialog()"
        [callParam]="callParam"
        [tdcFormConstraints]="tdcFormConstraints()"
        [taskTypeEnum]="taskTypeEnum"
        [queue]="queue()"
        (closeDialog)="handleCloseDialog($event)" />
    }

    @if (visibleFilterDialog()) {
      <task-filter-dialog [visibleDialog]="visibleFilterDialog()" (closeDialog)="handleCloseFilterDialog($event)" />
    }
  `,
  // The table takes the height the tab menu leaves it, so it scrolls in its own viewport below fixed column headers.
  styles: [
    `
      :host {
        display: block;
        height: 100%;
      }
    `
  ],
  providers: [DialogService],
  imports: [
    ConfigurableTableComponent,
    TooltipModule,
    TranslateModule,
    TaskDataChangeEditComponent,
    TaskFilterDialogComponent
  ]
})
export class TaskDataChangeTableComponent extends TableCrudSupportMenu<TaskDataChange> implements OnDestroy {
  readonly queueInput = input<string>(undefined, { alias: 'queue' });
  private readonly queueReady = signal(false);

  private taskDataChangeService: TaskDataChangeService = inject(TaskDataChangeService);
  private taskExtendService: ITaskExtendService = inject(TASK_EXTENDED_SERVICE);

  private static readonly STORE_KEY = 'u_taskdatachange';

  minDate: Date = new Date(
    sessionStorage.getItem(GlobalSessionNames.OLDEST_TRADING_DAY) ?? BaseSettings.OLDEST_TRADING_DAY_FALLBACK
  );
  maxDate: Date = new Date('2099-12-31');
  private readonly additionalData = signal<any>([]);
  callParam: TaskDataChange;
  ProgressStateType: typeof ProgressStateType = ProgressStateType;
  readonly visibleFilterDialog = signal(false);
  /** Current task ID filter. Null means no filter (load all). */
  readonly currentTaskFilter = signal<number[] | null>(null);

  readonly tdcFormConstraints = signal<TaskDataChangeFormConstraints>(null);
  taskTypeEnum: any = inject(TASK_TYPE_ENUM);

  /** The queue whose tasks are shown */
  readonly queue = signal(MAIN_TASK_QUEUE);
  /** Text key of the caption, the label of the queue */
  readonly queueLabelKey = computed(
    () => this.tdcFormConstraints()?.queues?.find((q) => q.name === this.queue())?.labelKey ?? 'TASK_DATA_MONITOR'
  );
  readonly canExpand = (task: TaskDataChange): boolean => this.canExpandRow(task);
  private queueInitialized = false;
  private readonly showMenu = signal<MenuItem[]>([]);

  readonly tasksResource = rxResource({
    params: () => {
      if (!this.queueReady()) return undefined;
      const queue = TaskDataChangeTabMenuComponent.queueOf(this.queueInput());
      return { queue, idTasks: this.taskIdsOfQueue(queue) };
    },
    stream: ({ params }) =>
      params.idTasks?.length === 0
        ? of([[], []] as [TaskDataChange[], any])
        : forkJoin([
            this.taskDataChangeService.getAllTaskDataChange(params.idTasks),
            this.hasAdditionalToolTipData() ? this.taskExtendService.getAdditionalData() : of([])
          ])
  });
  constructor() {
    super(
      BaseSettings.TASK_DATE_CHANGE,
      inject(TaskDataChangeService),
      inject(GlobalparameterService).hasRole(BaseSettings.ROLE_ADMIN)
        ? [CrudMenuOptions.Allow_Create, CrudMenuOptions.Allow_Delete]
        : []
    );

    this.frozenColumnsSelectable = true;

    this.addColumnFeqH(DataType.NumericInteger, 'idTaskDataChange', true, false);
    this.addColumnFeqH(DataType.DateTimeSecondString, 'creationTime', true, true);
    this.addColumnFeqH(DataType.NumericShowZero, 'taskAsId', true, false, {
      width: 40,
      maxFractionDigits: 0,
      filterType: FilterType.likeDataType
    });
    this.addColumnFeqH(DataType.String, 'idTask', true, false, {
      translateValues: TranslateValue.NORMAL,
      width: 300,
      filterType: FilterType.likeDataType
    });
    this.addColumnFeqH(DataType.DateTimeSecondString, 'earliestStartTime', true, true);
    this.addColumnFeqH(DataType.String, 'entity', true, false, {
      translateValues: TranslateValue.UPPER_CASE
    });
    this.addColumnFeqH(DataType.NumericInteger, 'idEntity', true, false);
    this.addColumnFeqH(DataType.DateTimeSecondString, 'execStartTime', true, true);
    this.addColumnFeqH(DataType.String, 'executionPriority', true, true, {
      translateValues: TranslateValue.NORMAL,
      filterType: FilterType.withOptions
    });
    this.addColumnFeqH(DataType.DateTimeSecondString, 'execEndTime', true, false);
    this.addColumnFeqH(DataType.String, 'progressStateType', true, false, {
      translateValues: TranslateValue.NORMAL,
      filterType: FilterType.withOptions
    });
    this.addColumnFeqH(DataType.NumericShowZero, 'executionDurationInSeconds', true, false, { maxFractionDigits: 0 });

    this.fields.filter((f) => f.dataType === DataType.DateTimeSecondString).map((f) => (f.width = 100));
    this.multiSortMeta.push({ field: 'creationTime', order: -1 });
    this.multiSortMeta.push({ field: 'taskAsId', order: 1 });

    effect(() => {
      const queue = TaskDataChangeTabMenuComponent.queueOf(this.queueInput());
      if (this.queueReady()) untracked(() => this.showQueue(queue));
    });

    reportResourceErrors(this.tasksResource, this.taskDataChangeService);
    effect(() => {
      if (this.tasksResource.isLoading() || !this.queueReady()) return;
      const [tasks, extra] = this.tasksResource.hasValue() ? this.tasksResource.value() : [[], []];
      untracked(() => {
        this.entityList.set(tasks);
        this.additionalData.set(extra);
        this.prepareTableAndTranslate();
        this.createTranslatedValueStoreAndFilterField(tasks);
        this.prepareFilter(tasks);
        this.refreshSelectedEntity();
      });
    });
  }

  canExpandRow(taskDataChange: TaskDataChange): boolean {
    return (
      (ProgressStateType[taskDataChange.progressStateType] as string | number) ===
      ProgressStateType[ProgressStateType[ProgressStateType.PROG_FAILED] as keyof typeof ProgressStateType]
    );
  }

  getShowLines(text: string): number {
    return Math.min((text.match(/\n/g) || '').length + 1, 15);
  }

  override prepareCallParam(entity: TaskDataChange): void {
    this.callParam = entity;
  }

  override getHelpContextId(): string {
    return HelpIds.HELP_TASK_DATA_CHANGE_MONITOR;
  }

  ngOnDestroy(): void {
    if (this.queueInitialized) this.writeTableDefinition(this.storeKey());
  }

  /** The key of the stored column settings; the main queue keeps the key it always had. */
  private storeKey(): string {
    return this.queue() === MAIN_TASK_QUEUE
      ? TaskDataChangeTableComponent.STORE_KEY
      : TaskDataChangeTableComponent.STORE_KEY + '_' + this.queue();
  }

  protected override initialize(): void {
    // Load stored filter from LocalStorage (null means load all). The stored selection is validated
    // against the current set of task types, so a stale filter (e.g. after task-type renumbering or a
    // newly added task) is discarded and all tasks are shown.
    this.currentTaskFilter.set(TaskFilterDialogComponent.getValidatedStoredTaskIds(this.taskTypeEnum));
    forkJoin({
      constraints: this.taskDataChangeService.getFormConstraints(),
      showMenu: TranslateHelper.translateMenuItems$(this.createShowMenu(), this.translateService)
    })
      .pipe(takeUntilDestroyed(this.destroyRef))
      .subscribe(({ constraints, showMenu }) => {
        this.tdcFormConstraints.set(constraints);
        this.showMenu.set(showMenu);
        this.queueReady.set(true);
      });
  }

  /**
   * Switches the table to a queue, keeping the column settings of the previous one.
   *
   * @param queue - The queue to show
   */
  private showQueue(queue: string): void {
    if (this.queueInitialized) {
      this.writeTableDefinition(this.storeKey());
    }
    this.queueInitialized = true;
    this.queue.set(queue);
    this.visibleDialog.set(false);
    this.visibleFilterDialog.set(false);
    this.entityList.set([]);
    this.selectedEntity.set(null);
    if (this.isActivated()) this.resetMenu(null);
    this.readTableDefinition(this.storeKey());
    this.readData();
  }

  /**
   * The task types to load: those of the shown queue, narrowed by the task filter. Null loads every task, which only
   * happens with a server that reports no queues.
   */
  private taskIdsOfQueue(queue = this.queue()): number[] | null {
    const taskTypeQueue = this.tdcFormConstraints()?.taskTypeQueue;
    if (!taskTypeQueue) {
      return this.currentTaskFilter();
    }
    const ids = Object.keys(taskTypeQueue)
      .filter((name) => taskTypeQueue[name] === queue)
      .map((name) => this.taskTypeEnum[name])
      .filter((id) => typeof id === 'number');
    return this.currentTaskFilter() ? ids.filter((id) => this.currentTaskFilter().includes(id)) : ids;
  }

  protected override readData(): void {
    this.tasksResource.reload();
  }

  protected override prepareShowMenu(): MenuItem[] {
    return this.showMenu();
  }

  private createShowMenu(): MenuItem[] {
    const menuItems: MenuItem[] = [];
    menuItems.push({
      label: 'TASK_FILTER' + BaseSettings.DIALOG_MENU_SUFFIX,
      command: () => this.openFilterDialog()
    });
    menuItems.push({ separator: true });
    const columnMenuItems = this.getMenuShowOptions();
    if (columnMenuItems) {
      menuItems.push(...columnMenuItems);
    }
    return menuItems;
  }

  /**
   * Opens the task filter dialog.
   */
  openFilterDialog(): void {
    this.visibleFilterDialog.set(true);
  }

  /**
   * Handles the close event from the filter dialog.
   * @param selectedTaskIds Array of selected task IDs or null if cancelled
   */
  handleCloseFilterDialog(selectedTaskIds: number[] | null): void {
    this.visibleFilterDialog.set(false);
    if (selectedTaskIds !== null) {
      this.currentTaskFilter.set(selectedTaskIds);
      this.readData();
    }
  }

  protected override addCustomMenusToSelectedEntity(taskDataChange: TaskDataChange, menuItems: MenuItem[]): void {
    if (this.gps.hasRole(BaseSettings.ROLE_ADMIN)) {
      menuItems.push({
        label: 'COPY_RECORD|' + this.entityNameUpper,
        command: (event) => this.copyTaskDataChange(taskDataChange),
        disabled: this.taskTypeEnum[taskDataChange.idTask] > this.tdcFormConstraints().maxUserCreateTask
      });

      menuItems.push({
        label: 'TASK_INTERRUPT|' + this.entityNameUpper,
        command: (event) => this.interruptingRunningJob(taskDataChange.idTaskDataChange),
        disabled: !(
          typeof taskDataChange.idTask === 'string' &&
          this.tdcFormConstraints().canBeInterruptedList.includes(taskDataChange.idTask) &&
          (ProgressStateType[taskDataChange.progressStateType] as string | number) ===
            ProgressStateType[ProgressStateType[ProgressStateType.PROG_RUNNING] as keyof typeof ProgressStateType]
        )
      });
    }
  }

  getTooltipValueByPath(dataobject: any, field: ColumnConfig) {
    let toolTip = null;
    if (this.hasAdditionalToolTipData()) {
      toolTip = this.taskExtendService.getToolTipByPath(dataobject, field, this.additionalData());
    }
    if (toolTip === null) {
      toolTip = this.getValueByPathWithField(dataobject, field, field.fieldTranslated || field.field);
    }
    return toolTip;
  }

  /** A host without task extensions provides TASK_EXTENDED_SERVICE as null, as grafiosch-host does. */
  private hasAdditionalToolTipData(): boolean {
    return !!this.taskExtendService?.supportAdditionalToolTipData();
  }

  private copyTaskDataChange(taskDataChange: TaskDataChange): void {
    const taskDataChangeNew = Object.assign(new TaskDataChange(), {
      idTask: taskDataChange.idTask,
      entity: taskDataChange.entity,
      idEntity: taskDataChange.idEntity
    });
    this.prepareCallParam(taskDataChangeNew);
    this.visibleDialog.set(true);
  }

  private interruptingRunningJob(idTaskDataChange: number): void {
    this.taskDataChangeService
      .interruptingRunningJob(idTaskDataChange)
      .pipe(takeUntilDestroyed(this.destroyRef))
      .subscribe((interrupted) => {
        if (interrupted) {
          this.messageToastService.showMessageI18n(InfoLevelType.SUCCESS, 'TASK_INTERRUPTED', null);
        } else {
          this.messageToastService.showMessageI18n(InfoLevelType.WARNING, 'TASK_NOT_INTERRUPTED', null);
        }
        this.readData();
      });
  }

  protected override hasRightsForDeleteEntity(entity: TaskDataChange): boolean {
    return (
      (ProgressStateType[entity.progressStateType] as string | number) !==
      ProgressStateType[ProgressStateType[ProgressStateType.PROG_RUNNING] as keyof typeof ProgressStateType]
    );
  }
}
