import { Component, Inject, Injector, OnDestroy, computed, inject, signal } from '@angular/core';
import { takeUntilDestroyed } from '@angular/core/rxjs-interop';
import { ActivatedRoute, ParamMap } from '@angular/router';
import { ConfirmationService, FilterService, MenuItem } from '@openng/optimus-ui/api';
import {
  MAIN_TASK_QUEUE,
  ProgressStateType,
  TaskDataChange,
  TaskDataChangeFormConstraints
} from '../types/task.data.change';
import { ActivePanelService } from '../../mainmenubar/service/active.panel.service';
import { TranslateModule, TranslateService } from '@ngx-translate/core';
import { GlobalparameterService } from '../../services/globalparameter.service';
import { UserSettingsService } from '../../services/user.settings.service';
import { HelpIds } from '../../help/help.ids';
import { DataType } from '../../dynamic-form/models/data.type';
import { ColumnConfig, TranslateValue } from '../../datashowbase/column.config';
import { TaskDataChangeService } from '../service/task.data.change.service';
import { CrudMenuOptions } from '../../datashowbase/table.crud.support.menu.helper';
import { TableCrudSupportMenuSignal } from '../../datashowbase/table.crud.support.menu.signal';
import { MessageToastService } from '../../message/message.toast.service';
import { DialogService } from '@openng/optimus-ui/dynamicdialog';
import { FilterType } from '../../datashowbase/filter.type';
import { forkJoin, of, Subscription, switchMap } from 'rxjs';
import { InfoLevelType } from '../../message/info.leve.type';
import { ITaskExtendService } from './itask.extend.service';
import { TASK_EXTENDED_SERVICE } from '../service/task.extend.service.token';
import { TASK_TYPE_ENUM } from '../service/task.type.enum.token';
import { BaseSettings } from '../../base.settings';
import { CommonModule } from '@angular/common';
import { ConfigurableTableComponent } from '../../datashowbase/configurable-table.component';
import { TooltipModule } from '@openng/optimus-ui/tooltip';
import { TaskDataChangeEditComponent } from './task-data-change-edit.component';
import { TaskFilterDialogComponent } from './task-filter-dialog.component';
import { TaskDataChangeTabMenuComponent } from './task-data-change-tab-menu.component';
import { TranslateHelper } from '../../helper/translate.helper';
import { GlobalSessionNames } from '../../global.session.names';

/**
 * Shows the batch jobs of one queue in a table. The queue comes from the child route of the tab menu; without one, the
 * main queue is shown. Each queue keeps its own column settings, the task filter applies to all of them.
 */
@Component({
  template: `
    <configurable-table
      [data]="entityList()"
      [fields]="fields"
      [fieldsRevision]="fieldsRevision()"
      [dataKey]="'idTaskDataChange'"
      [selectionMode]="'single'"
      [(selection)]="selectedEntity"
      [multiSortMeta]="multiSortMeta"
      [customSortFn]="customSorter"
      [scrollable]="false"
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
        'data-container': true,
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
            <span><i [ngClass]="{ 'fa fa-check': getValueByPath(row, field) }" aria-hidden="true"></i></span>
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
        (closeDialog)="handleCloseDialog($event)">
      </task-data-change-edit>
    }

    @if (visibleFilterDialog()) {
      <task-filter-dialog [visibleDialog]="visibleFilterDialog()" (closeDialog)="handleCloseFilterDialog($event)">
      </task-filter-dialog>
    }
  `,
  providers: [DialogService],
  standalone: true,
  imports: [
    CommonModule,
    ConfigurableTableComponent,
    TooltipModule,
    TranslateModule,
    TaskDataChangeEditComponent,
    TaskFilterDialogComponent
  ]
})
export class TaskDataChangeTableComponent extends TableCrudSupportMenuSignal<TaskDataChange> implements OnDestroy {
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
  taskTypeEnum: any;

  /** The queue whose tasks are shown */
  readonly queue = signal(MAIN_TASK_QUEUE);
  /** Text key of the caption, the label of the queue */
  readonly queueLabelKey = computed(
    () => this.tdcFormConstraints()?.queues?.find((q) => q.name === this.queue())?.labelKey ?? 'TASK_DATA_MONITOR'
  );
  readonly canExpand = (task: TaskDataChange): boolean => this.canExpandRow(task);

  private readonly activatedRoute = inject(ActivatedRoute);
  private readDataSub?: Subscription;
  private queueInitialized = false;
  private readonly showMenu = signal<MenuItem[]>([]);

  constructor(
    private taskDataChangeService: TaskDataChangeService,
    @Inject(TASK_EXTENDED_SERVICE)
    private taskExtendService: ITaskExtendService,
    @Inject(TASK_TYPE_ENUM) taskTypeEnum: any,
    confirmationService: ConfirmationService,
    messageToastService: MessageToastService,
    activePanelService: ActivePanelService,
    dialogService: DialogService,
    filterService: FilterService,
    translateService: TranslateService,
    gps: GlobalparameterService,
    usersettingsService: UserSettingsService,
    injector: Injector
  ) {
    super(
      BaseSettings.TASK_DATE_CHANGE,
      taskDataChangeService,
      confirmationService,
      messageToastService,
      activePanelService,
      dialogService,
      filterService,
      translateService,
      gps,
      usersettingsService,
      injector,
      gps.hasRole(BaseSettings.ROLE_ADMIN) ? [CrudMenuOptions.Allow_Create, CrudMenuOptions.Allow_Delete] : []
    );
    this.taskTypeEnum = taskTypeEnum;

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
  }

  canExpandRow(taskDataChange: TaskDataChange): boolean {
    return (
      ProgressStateType[taskDataChange.progressStateType] ===
      ProgressStateType[ProgressStateType[ProgressStateType.PROG_FAILED]]
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
      .pipe(
        switchMap(({ constraints, showMenu }) => {
          this.tdcFormConstraints.set(constraints);
          this.showMenu.set(showMenu);
          return this.activatedRoute.paramMap;
        }),
        takeUntilDestroyed(this.destroyRef)
      )
      .subscribe((params: ParamMap) => this.showQueue(TaskDataChangeTabMenuComponent.queueOf(params.get('queue'))));
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
  private taskIdsOfQueue(): number[] | null {
    const taskTypeQueue = this.tdcFormConstraints()?.taskTypeQueue;
    if (!taskTypeQueue) {
      return this.currentTaskFilter();
    }
    const ids = Object.keys(taskTypeQueue)
      .filter((name) => taskTypeQueue[name] === this.queue())
      .map((name) => this.taskTypeEnum[name])
      .filter((id) => typeof id === 'number');
    return this.currentTaskFilter() ? ids.filter((id) => this.currentTaskFilter().includes(id)) : ids;
  }

  protected override readData(): void {
    this.readDataSub?.unsubscribe();
    const idTasks = this.taskIdsOfQueue();
    if (idTasks && idTasks.length === 0) {
      // An empty list would load every task
      this.entityList.set([]);
      this.additionalData.set([]);
      this.prepareTableAndTranslate();
      this.createTranslatedValueStoreAndFilterField([]);
      this.prepareFilter([]);
      this.refreshSelectedEntity();
      return;
    }
    this.readDataSub = forkJoin([
      this.taskDataChangeService.getAllTaskDataChange(idTasks),
      this.hasAdditionalToolTipData() ? this.taskExtendService.getAdditionalData() : of([])
    ])
      .pipe(takeUntilDestroyed(this.destroyRef))
      .subscribe(([taskDataChanges, additionalData]: [TaskDataChange[], any]) => {
        this.entityList.set(taskDataChanges);
        this.additionalData.set(additionalData);
        this.prepareTableAndTranslate();
        this.createTranslatedValueStoreAndFilterField(taskDataChanges);
        this.prepareFilter(taskDataChanges);
        this.refreshSelectedEntity();
      });
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
          ProgressStateType[taskDataChange.progressStateType] ===
            ProgressStateType[ProgressStateType[ProgressStateType.PROG_RUNNING]]
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
      ProgressStateType[entity.progressStateType] !==
      ProgressStateType[ProgressStateType[ProgressStateType.PROG_RUNNING]]
    );
  }
}
