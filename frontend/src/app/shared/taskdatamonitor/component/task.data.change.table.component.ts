import {Component, Inject} from '@angular/core';
import {ConfirmationService, FilterService, MenuItem} from 'primeng/api';
import {ProgressStateType, TaskDataChange, TaskDataChangeFormConstraints} from '../../../entities/task.data.change';
import {ActivePanelService} from '../../../lib/mainmenubar/service/active.panel.service';
import {TranslateService} from '@ngx-translate/core';
import {GlobalparameterService} from '../../../lib/services/globalparameter.service';
import {UserSettingsService} from '../../../lib/services/user.settings.service';
import {HelpIds} from '../../../lib/help/help.ids';
import {DataType} from '../../../lib/dynamic-form/models/data.type';
import {ColumnConfig, TranslateValue} from '../../../lib/datashowbase/column.config';
import {TaskDataChangeService} from '../service/task.data.change.service';
import {CrudMenuOptions, TableCrudSupportMenu} from '../../../lib/datashowbase/table.crud.support.menu';
import {MessageToastService} from '../../../lib/message/message.toast.service';
import {DialogService} from 'primeng/dynamicdialog';
import {AppSettings} from '../../app.settings';
import {FilterType} from '../../../lib/datashowbase/filter.type';
import {combineLatest, of} from 'rxjs';
import {InfoLevelType} from '../../../lib/message/info.leve.type';
import {ITaskExtendService} from './itask.extend.service';
import {TASK_EXTENDED_SERVICE} from '../../../app.component';
import {BaseSettings} from '../../../lib/base.settings';

/**
 * Shows the batch Jobs in a table.
 */
@Component({
  template: `
    <div class="data-container" (click)="onComponentClick($event)" #cmDiv
         [ngClass]="{'active-border': isActivated(), 'passiv-border': !isActivated()}">
      <p-table #table [columns]="fields" [value]="taskDataChangeList"
               selectionMode="single" [(selection)]="selectedEntity" dataKey="idTaskDataChange"
               sortMode="multiple" [multiSortMeta]="multiSortMeta"
               (sortFunction)="customSort($event)" [customSort]="true"
               stripedRows showGridlines>
        <ng-template #caption>
          <h4>{{ 'TASK_DATA_MONITOR' | translate }}</h4>
          <p>{{ 'NO_DATA_REFRESH' | translate }}</p>
        </ng-template>
        <ng-template #header let-fields>
          <tr>
            <th style="width:24px"></th>
            @for (field of fields; track field) {
              <th [pSortableColumn]="field.field"
                  [pTooltip]="field.headerTooltipTranslated"
                  [style.max-width.px]="field.width"
                  [ngStyle]="field.width? {'flex-basis': '0 0 ' + field.width + 'px'}: {}">
                {{ field.headerTranslated }}
                <p-sortIcon [field]="field.field"></p-sortIcon>
              </th>
            }
          </tr>
          @if (hasFilter) {
            <tr>
              <th style="width:24px"></th>
              @for (field of fields; track field) {
                <th style="overflow:visible;">
                  @switch (field.filterType) {
                    @case (FilterType.likeDataType) {
                      @switch (field.dataType) {
                        @case (field.dataType === DataType.DateString || field.dataType === DataType.DateNumeric ? field.dataType : '') {
                          <p-columnFilter [field]="field.field" display="menu" [showOperator]="true"
                                          [matchModeOptions]="customMatchModeOptions"
                                          [matchMode]="'gtNoFilter'">
                            <ng-template pTemplate="filter" let-value let-filter="filterCallback">
                              <p-datepicker #cal [ngModel]="value" [dateFormat]="baseLocale.dateFormat"
                                            (onSelect)="filter($event)"
                                            [minDate]="minDate" [maxDate]="maxDate"
                                            (onInput)="filter(cal.value)">
                              </p-datepicker>
                            </ng-template>
                          </p-columnFilter>
                        }
                        @case (DataType.NumericShowZero) {
                          <p-columnFilter type="numeric"
                                          [field]="field.field"
                                          [locale]="formLocale"
                                          minFractionDigits="0" display="menu"></p-columnFilter>
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
            </tr>
          }
        </ng-template>
        <ng-template #body let-expanded="expanded" let-el let-columns="fields">
          <tr [pSelectableRow]="el">
            <td>
              @if (ProgressStateType[el.progressStateType] === ProgressStateType.PROG_FAILED) {
                <a href="#" [pRowToggler]="el">
                  <i [ngClass]="expanded ? 'fa fa-fw fa-chevron-circle-down' : 'fa fa-fw fa-chevron-circle-right'"></i>
                </a>
              }
            </td>
            @for (field of fields; track field) {
              <td [ngClass]="(field.dataType===DataType.NumericShowZero || field.dataType===DataType.DateTimeNumeric
                  || field.dataType===DataType.NumericInteger)? 'text-right': ''" [style.max-width.px]="field.width"
                  [ngStyle]="field.width? {'flex-basis': '0 0 ' + field.width + 'px'}: {}">
                @switch (field.templateName) {
                  @case ('check') {
                    <span><i [ngClass]="{'fa fa-check': getValueByPath(el, field)}"
                             aria-hidden="true"></i></span>
                  }
                  @default {
                    <span [pTooltip]="getTooltipValueByPath(el, field)"
                          tooltipPosition="top">{{ getValueByPath(el, field) }}</span>
                  }
                }
              </td>
            }
          </tr>
        </ng-template>
        <ng-template #expandedrow let-tdc let-columns="fields">
          <tr>
            <td [attr.colspan]="numberOfVisibleColumns + 1" style="overflow:visible;">
              <h4>{{ tdc.failedMessageCode | translate }}</h4>
              @if (tdc.failedStackTrace) {
                <textarea [rows]="getShowLines(tdc.failedStackTrace)">
                {{tdc.failedStackTrace}}
                </textarea>
              }
            </td>
          </tr>
        </ng-template>
      </p-table>
      @if (contextMenuItems) {
        <p-contextMenu [target]="cmDiv" [model]="contextMenuItems"></p-contextMenu>
      }
    </div>
    @if (visibleDialog) {
      <task-data-change-edit [visibleDialog]="visibleDialog"
                             [callParam]="callParam"
                             [tdcFormConstraints]="tdcFormConstraints"
                             (closeDialog)="handleCloseDialog($event)">
      </task-data-change-edit>
    }
  `,
  styles: ['textarea { width:100%; }'],
  providers: [DialogService],
  standalone: false
})
export class TaskDataChangeTableComponent extends TableCrudSupportMenu<TaskDataChange> {

  minDate: Date = new Date('2000-01-01');
  maxDate: Date = new Date('2099-12-31');
  taskDataChangeList: TaskDataChange[];
  additionalData: any;
  callParam: TaskDataChange;
  ProgressStateType: typeof ProgressStateType = ProgressStateType;
  editMenu: MenuItem;

  tdcFormConstraints: TaskDataChangeFormConstraints;

  constructor(private taskDataChangeService: TaskDataChangeService,
    @Inject(TASK_EXTENDED_SERVICE) private taskExtendService: ITaskExtendService,
    confirmationService: ConfirmationService,
    messageToastService: MessageToastService,
    activePanelService: ActivePanelService,
    dialogService: DialogService,
    filterService: FilterService,
    translateService: TranslateService,
    gps: GlobalparameterService,
    usersettingsService: UserSettingsService) {
    super(AppSettings.TASK_DATE_CHANGE, taskDataChangeService, confirmationService, messageToastService, activePanelService,
      dialogService, filterService, translateService, gps, usersettingsService,
      gps.hasRole(BaseSettings.ROLE_ADMIN) ? [CrudMenuOptions.Allow_Create,
        CrudMenuOptions.Allow_Delete] : []);

    this.addColumnFeqH(DataType.NumericInteger, 'idTaskDataChange', true, false,
      {});
    this.addColumnFeqH(DataType.DateTimeSecondString, 'creationTime', true, false,
      {});
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
    this.taskDataChangeService.getFormConstraints().subscribe((tdcFormConstraints: TaskDataChangeFormConstraints) => {
      // Only needs to be read once, as this is configuration data.
      this.tdcFormConstraints = tdcFormConstraints
      this.readData();
    });
  }

  protected override readData(): void {
    combineLatest([this.taskDataChangeService.getAllTaskDataChange(), this.taskExtendService.supportAdditionalToolTipData()
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
