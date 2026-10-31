import { requireFormControl } from '../../dynamic-form/models/form-control-access';
import { Component, OnInit, input, inject } from '@angular/core';
import { SimpleEntityEditBase } from '../../edit/simple.entity.edit.base';
import { TranslateModule } from '@ngx-translate/core';
import { HelpIds } from '../../help/help.ids';
import { TaskDataChangeService } from '../service/task.data.change.service';
import {
  TaskDataChange,
  type TaskDataChangeFormConstraints,
  EntityIdOption,
  MAIN_TASK_QUEUE
} from '../types/task.data.change';
import { AppHelper } from '../../helper/app.helper';
import { DynamicFieldHelper } from '../../helper/dynamic.field.helper';
import { TranslateHelper } from '../../helper/translate.helper';
import { SelectOptionsHelper } from '../../helper/select.options.helper';
import { map, Observable, of, switchMap } from 'rxjs';
import { FormHelper } from '../../dynamic-form/components/FormHelper';
import { DataType } from '../../dynamic-form/models/data.type';
import { GroupItem, ValueKeyHtmlSelectOptions } from '../../dynamic-form/models/value.key.html.select.options';
import moment from 'moment';
import { Validators } from '@angular/forms';
import { BaseSettings } from '../../base.settings';
import { TaskTypeBase } from '../types/task.type.base';
import { getTaskTypeHelpId } from '../task.type.help';

import { DialogModule } from '@openng/optimus-ui/dialog';
import { DYNAMIC_FORM_IMPORTS } from '../../dynamic-form/dynamic-form.imports';

@Component({
  selector: 'task-data-change-edit',
  template: `
    <p-dialog
      header="{{ i18nRecord | translate }}"
      [visible]="visibleDialog()"
      [style]="{ width: '500px' }"
      (onShow)="onShow($event)"
      (onHide)="onHide($event)"
      [modal]="true">
      <dynamic-form
        [config]="config"
        [formConfig]="formConfig"
        [translateService]="translateService"
        #form="dynamicForm"
        (submitBt)="submit($event)" />
    </p-dialog>
  `,
  imports: [DialogModule, ...DYNAMIC_FORM_IMPORTS, TranslateModule]
})
export class TaskDataChangeEditComponent extends SimpleEntityEditBase<TaskDataChange> implements OnInit {
  private taskDataChangeService: TaskDataChangeService = inject(TaskDataChangeService);

  readonly callParam = input<TaskDataChange | null>();
  readonly tdcFormConstraints = input.required<TaskDataChangeFormConstraints>();
  readonly taskTypeEnum = input<any>(TaskTypeBase); // Application can provide extended enum
  /** The queue of the monitor tab; only its task types can be created there */
  readonly queue = input<string>(MAIN_TASK_QUEUE);

  constructor() {
    super(
      HelpIds.HELP_TASK_DATA_CHANGE_MONITOR,
      AppHelper.toUpperCaseWithUnderscore(BaseSettings.TASK_DATE_CHANGE),
      inject(TaskDataChangeService)
    );
  }

  ngOnInit(): void {
    this.formConfig = AppHelper.getDefaultFormConfig(this.gps, 4, this.helpLink.bind(this));

    this.config = [
      DynamicFieldHelper.createFieldDropdownStringHeqF('idTask', true, {
        filter: true
      }),
      DynamicFieldHelper.createFieldSelectStringHeqF('entity', true),
      DynamicFieldHelper.createFieldSelectStringHeqF('idEntity', true),
      DynamicFieldHelper.createFieldInputNumber('idEntityNum', 'ID_ENTITY', true, 10, 0, false),
      DynamicFieldHelper.createFieldPcalendarHeqF(DataType.DateTimeNumeric, 'earliestStartTime', true, {
        calendarConfig: {
          minDate: new Date(),
          maxDate: moment(new Date()).add(this.tdcFormConstraints().maxDaysInFuture, 'd').toDate()
        }
      }),
      DynamicFieldHelper.createSubmitButton()
    ];
    this.configObject = TranslateHelper.prepareFieldsAndErrors(this.translateService, this.config);
  }

  /**
   * Opens the description of the selected task type at its own section, or the overview of all task types when
   * none is selected yet.
   */
  override helpLink(): void {
    const idTaskName = requireFormControl(this.configObject.idTask).value;
    this.gps.toExternalHelpWebpage(
      this.gps.getUserLang(),
      getTaskTypeHelpId(idTaskName ? this.taskTypeEnum()[idTaskName] : null)
    );
  }

  valueChangedOnIdTask(): void {
    requireFormControl(this.configObject.idTask)
      .valueChanges.pipe(
        switchMap((idTask) => {
          const taskConfig = this.tdcFormConstraints().taskTypeConfig[idTask];
          this.configObject.entity.valueKeyHtmlOptions = [];
          // An empty entity in the list is the "no entity" choice, not a selectable one. A configuration that holds
          // nothing else offers the user no choice at all, so it is treated like a task without entity configuration.
          if (taskConfig && taskConfig.some((entity: string) => entity !== '')) {
            // Scenario B/C: task has entity configuration - show entity field
            FormHelper.disableEnableFieldConfigs(false, [this.configObject.entity]);
            const allowEmpty = taskConfig.includes('');
            DynamicFieldHelper.resetValidator(
              this.configObject.entity,
              allowEmpty ? null : [Validators.required],
              allowEmpty ? null : [DynamicFieldHelper.RULE_REQUIRED_DIRTY]
            );
            requireFormControl(this.configObject.entity).setValue(null);
            return this.translateOptions(SelectOptionsHelper.createHtmlOptionsFromStringArray(taskConfig, true));
          } else {
            // Scenario A: no entity configuration - hide all entity fields
            requireFormControl(this.configObject.entity).setValue(null);
            this.hideIdEntityFields();
            DynamicFieldHelper.resetValidator(this.configObject.entity, null, null);
            requireFormControl(this.configObject.entity).disable();
            FormHelper.hideVisibleFieldConfigs(true, [this.configObject.entity]);
            return of([]);
          }
        }),
        this.untilDialogClosed()
      )
      .subscribe((options) => (this.configObject.entity.valueKeyHtmlOptions = options));
  }

  valueChangedOnEntity(): void {
    requireFormControl(this.configObject.entity)
      .valueChanges.pipe(
        switchMap((entity) => {
          this.configObject.idEntity.valueKeyHtmlOptions = [];
          // Options are task-scoped: pick the option map of the currently selected task first.
          const tdcFormConstraints = this.tdcFormConstraints();
          const optionsForTask =
            tdcFormConstraints.entityIdOptions &&
            tdcFormConstraints.entityIdOptions[requireFormControl(this.configObject.idTask).value];
          if (!entity || entity.length === 0) {
            // No entity selected: hide both idEntity fields
            this.hideIdEntityFields();
            return of([]);
          } else if (optionsForTask && optionsForTask[entity]) {
            // Entity with predefined options: show select dropdown, hide number input
            requireFormControl(this.configObject.idEntity).setValue(null);
            FormHelper.disableEnableFieldConfigs(false, [this.configObject.idEntity]);
            DynamicFieldHelper.resetValidator(
              this.configObject.idEntity,
              [Validators.required],
              [DynamicFieldHelper.RULE_REQUIRED_DIRTY]
            );
            this.hideField(this.configObject.idEntityNum);
            return this.translateOptions(
              optionsForTask[entity].map((opt: EntityIdOption) => new ValueKeyHtmlSelectOptions(opt.key, opt.value))
            );
          } else {
            // Entity without predefined options: show number input, hide select
            requireFormControl(this.configObject.idEntityNum).setValue(null);
            FormHelper.disableEnableFieldConfigs(false, [this.configObject.idEntityNum]);
            DynamicFieldHelper.resetValidator(
              this.configObject.idEntityNum,
              [Validators.required],
              [DynamicFieldHelper.RULE_REQUIRED_DIRTY]
            );
            this.hideField(this.configObject.idEntity);
            return of([]);
          }
        }),
        this.untilDialogClosed()
      )
      .subscribe((options) => (this.configObject.idEntity.valueKeyHtmlOptions = options));
  }

  protected override initialize(): void {
    this.form().setDefaultValuesAndEnableSubmit();
    this.valueChangedOnIdTask();
    this.valueChangedOnEntity();
    // Initially hide entity and idEntity fields until a task is selected
    this.hideField(this.configObject.entity);
    this.hideIdEntityFields();
    this.loadTaskTypeOptions();
    const callParam = this.callParam();
    if (callParam) {
      this.form().transferBusinessObjectToForm(callParam);
      // For number input case, transferBusinessObjectToForm sets idEntity on the select field.
      // Move the value to the number input if it's now visible (no task-scoped options for this entity).
      const tdcFormConstraints = this.tdcFormConstraints();
      const optionsForTask =
        tdcFormConstraints.entityIdOptions && tdcFormConstraints.entityIdOptions[callParam.idTask as string];
      if (callParam.idEntity != null && callParam.entity && !(optionsForTask && optionsForTask[callParam.entity])) {
        requireFormControl(this.configObject.idEntityNum).setValue(callParam.idEntity);
      }
    }
    requireFormControl(this.configObject.earliestStartTime).setValue(moment().add(1, 'm').toDate());
    this.configObject.idTask.baseInputComponent?.focus();
  }

  /**
   * Builds the selectable task types, restricted to those the user may create.
   *
   * The label carries the numeric task type as a suffix, because the task monitor table and the server log
   * identify a job by that number; without it the user cannot relate a listed job to an entry of this list.
   * The suffixed text is written to both 'value' (shown in the closed control) and 'optionsText' (shown in
   * the overlay list and searched by the filter box).
   *
   * Translation belongs to this dialog session, including when it closes before the texts arrive.
   */
  private loadTaskTypeOptions(): void {
    const names = Object.keys(this.taskTypeEnum()).filter(
      (key) =>
        this.taskTypeEnum()[key] <= this.tdcFormConstraints().maxUserCreateTask &&
        (this.tdcFormConstraints().taskTypeQueue?.[key] ?? MAIN_TASK_QUEUE) === this.queue()
    );
    this.translateOptions(names.map((name) => new ValueKeyHtmlSelectOptions(name, name)))
      .pipe(this.untilDialogClosed())
      .subscribe((options) => {
        this.configObject.idTask.groupItem = options
          .flatMap((option) => {
            if (option.key == null) {
              return [];
            }
            const label = `${option.value} - ${this.taskTypeEnum()[option.key]}`;
            return [new GroupItem(option.key, label, label, null)];
          })
          .sort((a, b) => a.optionsText.localeCompare(b.optionsText));
      });
  }

  /** Publishes new option objects instead of mutating labels after the select received its array. */
  private translateOptions(options: ValueKeyHtmlSelectOptions[]): Observable<ValueKeyHtmlSelectOptions[]> {
    const keys = options.map((option) => option.value).filter(Boolean);
    return (keys.length ? this.translateService.get(keys) : of({})).pipe(
      map((texts) =>
        options.map((option) => new ValueKeyHtmlSelectOptions(option.key, texts[option.value] ?? option.value))
      )
    );
  }

  protected override getNewOrExistingInstanceBeforeSave(value: { [name: string]: any }): TaskDataChange {
    const taskDataChange = this.copyFormToPrivateBusinessObject(new TaskDataChange(), null);
    taskDataChange.earliestStartTime = moment(taskDataChange.earliestStartTime).utc().format('YYYY-MM-DD HH:mm:ss');
    // Use number input value if present (idEntityNum is not on TaskDataChange, so read from form)
    const idEntityNumValue = requireFormControl(this.configObject.idEntityNum).value;
    if (idEntityNumValue != null) {
      taskDataChange.idEntity = idEntityNumValue;
    }
    // Convert idEntity to number if it was selected from dropdown (string key)
    if (taskDataChange.idEntity && typeof taskDataChange.idEntity === 'string') {
      taskDataChange.idEntity = parseInt(taskDataChange.idEntity, 10);
    }
    return taskDataChange;
  }

  private hideIdEntityFields(): void {
    this.hideField(this.configObject.idEntity);
    this.hideField(this.configObject.idEntityNum);
  }

  private hideField(fieldConfig: any): void {
    fieldConfig.formControl.setValue(null);
    DynamicFieldHelper.resetValidator(fieldConfig, null, null);
    fieldConfig.formControl.disable();
    FormHelper.hideVisibleFieldConfigs(true, [fieldConfig]);
  }
}
