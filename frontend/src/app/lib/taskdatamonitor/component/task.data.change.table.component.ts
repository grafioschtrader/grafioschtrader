import {Component, Inject} from '@angular/core';
import {ConfirmationService, FilterService, MenuItem} from 'primeng/api';
import {ProgressStateType, TaskDataChange, TaskDataChangeFormConstraints} from '../types/task.data.change';
import {ActivePanelService} from '../../mainmenubar/service/active.panel.service';
import {TranslateModule, TranslateService} from '@ngx-translate/core';
import {GlobalparameterService} from '../../services/globalparameter.service';
import {UserSettingsService} from '../../services/user.settings.service';
import {HelpIds} from '../../help/help.ids';
import {DataType} from '../../dynamic-form/models/data.type';
import {ColumnConfig, TranslateValue} from '../../datashowbase/column.config';
import {TaskDataChangeService} from '../service/task.data.change.service';
import {CrudMenuOptions, TableCrudSupportMenu} from '../../datashowbase/table.crud.support.menu';
import {MessageToastService} from '../../message/message.toast.service';
import {DialogService} from 'primeng/dynamicdialog';
import {FilterType} from '../../datashowbase/filter.type';
import {combineLatest, of} from 'rxjs';
import {InfoLevelType} from '../../message/info.leve.type';
import {ITaskExtendService} from './itask.extend.service';
import {TASK_EXTENDED_SERVICE} from '../service/task.extend.service.token';
import {TASK_TYPE_ENUM} from '../service/task.type.enum.token';
import {BaseSettings} from '../../base.settings';
import {CommonModule} from '@angular/common';
import {ConfigurableTableComponent} from '../../datashowbase/configurable-table.component';
import {TooltipModule} from 'primeng/tooltip';
import {TaskDataChangeEditComponent} from './task-data-change-edit.component';
import {TaskFilterDialogComponent} from './task-filter-dialog.component';
import {TranslateHelper} from '../../helper/translate.helper';

/**
 * Shows the batch Jobs in a table.
 */
@Component({
  template: `
    <configurable-table
      [data]="taskDataChangeList"
      [fields]="fields"
      [dataKey]="'idTaskDataChange'"
      [selectionMode]="'single'"
      [(selection)]="selectedEntity"
      [multiSortMeta]="multiSortMeta"
      [customSortFn]="customSort.bind(this)"
      [scrollable]="false"
      [stripedRows]="true"
      [showGridlines]="true"
      [expandable]="true"
      [canExpandFn]="canExpandRow.bind(this)"
      [expandedRowTemplate]="expandedContent"
      [hasFilter]="hasFilter"
      [customMatchModeOptions]="customMatchModeOptions"
      [minDate]="minDate"
      [maxDate]="maxDate"
      [baseLocale]="baseLocale"
      [formLocale]="formLocale"
      [containerClass]="{'data-container': true, 'active-border': isActivated(), 'passiv-border': !isActivated()}"
      [showContextMenu]="!!contextMenuItems"
      [contextMenuItems]="contextMenuItems"
      [valueGetterFn]="getValueByPath.bind(this)"
      (componentClick)="onComponentClick($event)">

      <div caption>
        <h4>{{ 'TASK_DATA_MONITOR' | translate }}</h4>
        <p>{{ 'NO_DATA_REFRESH' | translate }}</p>
      </div>

      <!-- Custom cell template with tooltip support -->
      <ng-template #customCell let-row let-field="field">
        @switch (field.templateName) {
          @case ('check') {
            <span><i [ngClass]="{'fa fa-check': getValueByPath(row, field)}" aria-hidden="true"></i></span>
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
        <textarea [rows]="getShowLines(tdc.failedStackTrace)" style="width:100%;">{{tdc.failedStackTrace}}</textarea>
      }
    </ng-template>

    @if (visibleDialog) {
      <task-data-change-edit [visibleDialog]="visibleDialog"
                             [callParam]="callParam"
                             [tdcFormConstraints]="tdcFormConstraints"
                             [taskTypeEnum]="taskTypeEnum"
                             (closeDialog)="handleCloseDialog($event)">
      </task-data-change-edit>
    }

    @if (visibleFilterDialog) {
      <task-filter-dialog [visibleDialog]="visibleFilterDialog"
                          (closeDialog)="handleCloseFilterDialog($event)">
      </task-filter-dialog>
    }
  `,
  providers: [DialogService],
  standalone: true,
  imports: [CommonModule, ConfigurableTableComponent, TooltipModule, TranslateModule, TaskDataChangeEditComponent,
    TaskFilterDialogComponent]
})
export class TaskDataChangeTableComponent extends TableCrudSupportMenu<TaskDataChange> {

  minDate: Date = new Date('2000-01-01');
  maxDate: Date = new Date('2099-12-31');
  taskDataChangeList: TaskDataChange[];
  additionalData: any;
  callParam: TaskDataChange;
  ProgressStateType: typeof ProgressStateType = ProgressStateType;
  editMenu: MenuItem;
  visibleFilterDialog = false;
  /** Current task ID filter. Null means no filter (load all). */
  currentTaskFilter: number[] | null = null;

  tdcFormConstraints: TaskDataChangeFormConstraints;
  taskTypeEnum: any;

  constructor(private taskDataChangeService: TaskDataChangeService,
    @Inject(TASK_EXTENDED_SERVICE) private taskExtendService: ITaskExtendService,
    @Inject(TASK_TYPE_ENUM) taskTypeEnum: any,
    confirmationService: ConfirmationService,
    messageToastService: MessageToastService,
    activePanelService: ActivePanelService,
    dialogService: DialogService,
    filterService: FilterService,
    translateService: TranslateService,
    gps: GlobalparameterService,
    usersettingsService: UserSettingsService) {
    super(BaseSettings.TASK_DATE_CHANGE, taskDataChangeService, confirmationService, messageToastService, activePanelService,
      dialogService, filterService, translateService, gps, usersettingsService,
      gps.hasRole(BaseSettings.ROLE_ADMIN) ? [CrudMenuOptions.Allow_Create,
        CrudMenuOptions.Allow_Delete] : []);
    this.taskTypeEnum = taskTypeEnum;

    this.addColumnFeqH(DataType.NumericInteger, 'idTaskDataChange', true, false);
    this.addColumnFeqH(DataType.DateTimeSecondString, 'creationTime', true, false);
    this.addColumnFeqH(DataType.NumericShowZero, 'taskAsId', true, false,
      {width: 40, maxFractionDigits: 0, filterType: FilterType.likeDataType});
    this.addColumnFeqH(DataType.String, 'idTask', true, false,
      {translateValues: TranslateValue.NORMAL, width: 300, filterType: FilterType.likeDataType});
    this.addColumnFeqH(DataType.DateTimeSecondString, 'earliestStartTime', true, false);
    this.addColumnFeqH(DataType.String, 'entity', true, false,
      {translateValues: TranslateValue.UPPER_CASE});
    this.addColumnFeqH(DataType.NumericInteger, 'idEntity', true, false);
    this.addColumnFeqH(DataType.DateTimeSecondString, 'execStartTime', true, false);
    this.addColumnFeqH(DataType.String, 'executionPriority', true, false,
      {translateValues: TranslateValue.NORMAL, filterType: FilterType.withOptions});
    this.addColumnFeqH(DataType.DateTimeSecondString, 'execEndTime', true, false);
    this.addColumnFeqH(DataType.String, 'progressStateType', true, false,
      {translateValues: TranslateValue.NORMAL, filterType: FilterType.withOptions});
    this.addColumnFeqH(DataType.NumericShowZero, 'executionDurationInSeconds', true, false,
      {maxFractionDigits: 0});

    this.fields.filter(f => f.dataType === DataType.DateTimeSecondString).map(f => f.width = 100);
    this.multiSortMeta.push({field: 'creationTime', order: -1});
    this.multiSortMeta.push({field: 'taskAsId', order: 1});
  }

  canExpandRow(taskDataChange: TaskDataChange): boolean {
    return ProgressStateType[taskDataChange.progressStateType]
      === ProgressStateType[ProgressStateType[ProgressStateType.PROG_FAILED]];
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

  protected override initialize(): void {
    // Load stored filter from LocalStorage (null means load all)
    this.currentTaskFilter = TaskFilterDialogComponent.getStoredTaskIdsStatic();
    this.taskDataChangeService.getFormConstraints().subscribe((tdcFormConstraints: TaskDataChangeFormConstraints) => {
      // Only needs to be read once, as this is configuration data.
      this.tdcFormConstraints = tdcFormConstraints
      this.readData();
    });
  }

  protected override readData(): void {
    combineLatest([this.taskDataChangeService.getAllTaskDataChange(this.currentTaskFilter),
      this.taskExtendService.supportAdditionalToolTipData()
        ? this.taskExtendService.getAdditionalData() : of([])]).subscribe(([taskDataChanges, additionalData]: [TaskDataChange[], any]) => {
      this.taskDataChangeList = taskDataChanges;
      if (this.taskExtendService.supportAdditionalToolTipData()) {
        this.additionalData = additionalData;
      }
      this.prepareTableAndTranslate();
      this.createTranslatedValueStoreAndFilterField(this.taskDataChangeList);
      this.prepareFilter(this.taskDataChangeList);
    });
  }

  protected override prepareShowMenu(): MenuItem[] {
    const menuItems: MenuItem[] = [];
    menuItems.push({
      label: 'TASK_FILTER' + BaseSettings.DIALOG_MENU_SUFFIX,
      command: () => this.openFilterDialog()
    });
    menuItems.push({separator: true});
    const columnMenuItems = this.getMenuShowOptions();
    if (columnMenuItems) {
      menuItems.push(...columnMenuItems);
    }
    TranslateHelper.translateMenuItems(menuItems, this.translateService);
    return menuItems;
  }

  /**
   * Opens the task filter dialog.
   */
  openFilterDialog(): void {
    this.visibleFilterDialog = true;
  }

  /**
   * Handles the close event from the filter dialog.
   * @param selectedTaskIds Array of selected task IDs or null if cancelled
   */
  handleCloseFilterDialog(selectedTaskIds: number[] | null): void {
    this.visibleFilterDialog = false;
    if (selectedTaskIds !== null) {
      this.currentTaskFilter = selectedTaskIds;
      this.readData();
    }
  }

  protected override addCustomMenusToSelectedEntity(taskDataChange: TaskDataChange, menuItems: MenuItem[]): void {
    if (this.gps.hasRole(BaseSettings.ROLE_ADMIN)) {
      menuItems.push({
        label: 'COPY_RECORD|' + this.entityNameUpper,
        command: (event) => this.copyTaskDataChange(taskDataChange),
        disabled: taskDataChange[taskDataChange.idTask] > this.tdcFormConstraints.maxUserCreateTask
      });

      menuItems.push({
        label: 'TASK_INTERRUPT|' + this.entityNameUpper,
        command: (event) => this.interruptingRunningJob(taskDataChange.idTaskDataChange),
        disabled: !(typeof taskDataChange.idTask === 'string' && this.tdcFormConstraints.canBeInterruptedList.includes
          (taskDataChange.idTask) && ProgressStateType[taskDataChange.progressStateType]
          === ProgressStateType[ProgressStateType[ProgressStateType.PROG_RUNNING]])
      });
    }
  }

  getTooltipValueByPath(dataobject: any, field: ColumnConfig) {
    let toolTip = null;
    if (this.taskExtendService.supportAdditionalToolTipData()) {
      toolTip = this.taskExtendService.getToolTipByPath(dataobject, field, this.additionalData);
    }
    if (toolTip === null) {
      toolTip = this.getValueByPathWithField(dataobject, field, field.fieldTranslated || field.field);
    }
    return toolTip;
  }

  private copyTaskDataChange(taskDataChange: TaskDataChange): void {
    const taskDataChangeNew = Object.assign(new TaskDataChange(), {
      idTask: taskDataChange.idTask,
      entity: taskDataChange.entity, idEntity: taskDataChange.idEntity
    });
    this.prepareCallParam(taskDataChangeNew);
    this.visibleDialog = true;
  }

  private interruptingRunningJob(idTaskDataChange: number): void {
    this.taskDataChangeService.interruptingRunningJob(idTaskDataChange).subscribe(interrupted => {
      if (interrupted) {
        this.messageToastService.showMessageI18n(InfoLevelType.SUCCESS, 'TASK_INTERRUPTED', null);
      } else {
        this.messageToastService.showMessageI18n(InfoLevelType.WARNING, 'TASK_NOT_INTERRUPTED', null);
      }
      this.readData();
    });
  }

  protected override hasRightsForDeleteEntity(entity: TaskDataChange): boolean {
    return ProgressStateType[entity.progressStateType] !== ProgressStateType[ProgressStateType[ProgressStateType.PROG_RUNNING]];
  }

}
