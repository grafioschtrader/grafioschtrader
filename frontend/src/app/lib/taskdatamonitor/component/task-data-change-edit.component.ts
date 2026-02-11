import {Component, Input, OnInit} from '@angular/core';
import {SimpleEntityEditBase} from '../../edit/simple.entity.edit.base';
import {TranslateModule, TranslateService} from '@ngx-translate/core';
import {GlobalparameterService} from '../../services/globalparameter.service';
import {MessageToastService} from '../../message/message.toast.service';
import {HelpIds} from '../../help/help.ids';
import {TaskDataChangeService} from '../service/task.data.change.service';
import {TaskDataChange, TaskDataChangeFormConstraints, EntityIdOption} from '../types/task.data.change';
import {AppHelper} from '../../helper/app.helper';
import {DynamicFieldHelper} from '../../helper/dynamic.field.helper';
import {TranslateHelper} from '../../helper/translate.helper';
import {SelectOptionsHelper} from '../../helper/select.options.helper';
import {Subscription} from 'rxjs';
import {FormHelper} from '../../dynamic-form/components/FormHelper';
import {DataType} from '../../dynamic-form/models/data.type';
import {ValueKeyHtmlSelectOptions} from '../../dynamic-form/models/value.key.html.select.options';
import moment from 'moment';
import {Validators} from '@angular/forms';
import {BaseSettings} from '../../base.settings';
import {TaskTypeBase} from '../types/task.type.base';

import {DialogModule} from 'primeng/dialog';
import {DynamicFormModule} from '../../dynamic-form/dynamic-form.module';

@Component({
    selector: 'task-data-change-edit',
    template: `
    <p-dialog header="{{i18nRecord | translate}}" [visible]="visibleDialog"
              [style]="{width: '500px'}" (onShow)="onShow($event)"
              (onHide)="onHide($event)" [modal]="true">
      <dynamic-form [config]="config" [formConfig]="formConfig" [translateService]="translateService"
                    #form="dynamicForm"
                    (submitBt)="submit($event)">
      </dynamic-form>
    </p-dialog>
  `,
    standalone: true,
    imports: [DialogModule, DynamicFormModule, TranslateModule]
})
export class TaskDataChangeEditComponent extends SimpleEntityEditBase<TaskDataChange> implements OnInit {
  @Input() callParam: TaskDataChange;
  @Input() tdcFormConstraints: TaskDataChangeFormConstraints;
  @Input() taskTypeEnum: any = TaskTypeBase; // Application can provide extended enum

  private idTaskSubscribe: Subscription;
  private entitySubscribe: Subscription;

  constructor(translateService: TranslateService,
              gps: GlobalparameterService,
              messageToastService: MessageToastService,
              private taskDataChangeService: TaskDataChangeService) {
    super(HelpIds.HELP_TASK_DATA_CHANGE_MONITOR, AppHelper.toUpperCaseWithUnderscore(BaseSettings.TASK_DATE_CHANGE),
      translateService, gps, messageToastService, taskDataChangeService);
  }

  ngOnInit(): void {
    this.formConfig = AppHelper.getDefaultFormConfig(this.gps,
      4, this.helpLink.bind(this));

    this.config = [
      DynamicFieldHelper.createFieldSelectStringHeqF('idTask', true),
      DynamicFieldHelper.createFieldSelectStringHeqF('entity', true),
      DynamicFieldHelper.createFieldSelectStringHeqF('idEntity', true),
      DynamicFieldHelper.createFieldInputNumber('idEntityNum', 'ID_ENTITY', true, 10, 0, false),
      DynamicFieldHelper.createFieldPcalendarHeqF(DataType.DateTimeNumeric, 'earliestStartTime', true,
        {
          calendarConfig: {
            minDate: new Date(), maxDate: moment(new Date()).add(
              this.tdcFormConstraints.maxDaysInFuture, 'd').toDate()
          }
        }),
      DynamicFieldHelper.createSubmitButton()
    ];
    this.configObject = TranslateHelper.prepareFieldsAndErrors(this.translateService, this.config);
  }

  valueChangedOnIdTask(): void {
    this.idTaskSubscribe = this.configObject.idTask.formControl.valueChanges.subscribe(idTask => {
      const taskConfig = this.tdcFormConstraints.taskTypeConfig[idTask];
      if (taskConfig) {
        // Scenario B/C: task has entity configuration - show entity field
        this.configObject.entity.valueKeyHtmlOptions =
          SelectOptionsHelper.translateExistingValueKeyHtmlSelectOptions(this.translateService,
            SelectOptionsHelper.createHtmlOptionsFromStringArray(taskConfig, true), false);
        FormHelper.disableEnableFieldConfigs(false, [this.configObject.entity]);
        const allowEmpty = taskConfig.includes('');
        DynamicFieldHelper.resetValidator(this.configObject.entity, allowEmpty ? null : [Validators.required],
          allowEmpty ? null : [DynamicFieldHelper.RULE_REQUIRED_DIRTY]);
        this.configObject.entity.formControl.setValue(null);
      } else {
        // Scenario A: no entity configuration - hide all entity fields
        this.configObject.entity.formControl.setValue(null);
        this.hideIdEntityFields();
        DynamicFieldHelper.resetValidator(this.configObject.entity, null, null);
        this.configObject.entity.formControl.disable();
        FormHelper.hideVisibleFieldConfigs(true, [this.configObject.entity]);
      }
    });
  }

  valueChangedOnEntity(): void {
    this.entitySubscribe = this.configObject.entity.formControl.valueChanges.subscribe(entity => {
      if (!entity || entity.length === 0) {
        // No entity selected: hide both idEntity fields
        this.hideIdEntityFields();
      } else if (this.tdcFormConstraints.entityIdOptions && this.tdcFormConstraints.entityIdOptions[entity]) {
        // Entity with predefined options: show select dropdown, hide number input
        this.configObject.idEntity.valueKeyHtmlOptions = SelectOptionsHelper.translateExistingValueKeyHtmlSelectOptions(
          this.translateService, this.tdcFormConstraints.entityIdOptions[entity]
            .map((opt: EntityIdOption) => new ValueKeyHtmlSelectOptions(opt.key, opt.value)), false);
        this.configObject.idEntity.formControl.setValue(null);
        FormHelper.disableEnableFieldConfigs(false, [this.configObject.idEntity]);
        DynamicFieldHelper.resetValidator(this.configObject.idEntity, [Validators.required],
          [DynamicFieldHelper.RULE_REQUIRED_DIRTY]);
        this.hideField(this.configObject.idEntityNum);
      } else {
        // Entity without predefined options: show number input, hide select
        this.configObject.idEntityNum.formControl.setValue(null);
        FormHelper.disableEnableFieldConfigs(false, [this.configObject.idEntityNum]);
        DynamicFieldHelper.resetValidator(this.configObject.idEntityNum, [Validators.required],
          [DynamicFieldHelper.RULE_REQUIRED_DIRTY]);
        this.hideField(this.configObject.idEntity);
      }
    });
  }

  override onHide(event): void {
    this.idTaskSubscribe && this.idTaskSubscribe.unsubscribe();
    this.entitySubscribe && this.entitySubscribe.unsubscribe();
    super.onHide(event);
  }

  protected override initialize(): void {
    this.valueChangedOnIdTask();
    this.valueChangedOnEntity();
    // Initially hide entity and idEntity fields until a task is selected
    this.hideField(this.configObject.entity);
    this.hideIdEntityFields();
    this.configObject.idTask.valueKeyHtmlOptions = SelectOptionsHelper.createHtmlOptionsFromEnum(
      this.translateService, this.taskTypeEnum, Object.keys(this.taskTypeEnum).filter(
        key => this.taskTypeEnum[key] <= this.tdcFormConstraints.maxUserCreateTask).map(key => this.taskTypeEnum[key]));
    if (this.callParam) {
      this.form.transferBusinessObjectToForm(this.callParam);
      // For number input case, transferBusinessObjectToForm sets idEntity on the select field.
      // Move the value to the number input if it's now visible.
      if (this.callParam.idEntity != null && this.callParam.entity
        && !(this.tdcFormConstraints.entityIdOptions && this.tdcFormConstraints.entityIdOptions[this.callParam.entity])) {
        this.configObject.idEntityNum.formControl.setValue(this.callParam.idEntity);
      }
    }
    this.configObject.earliestStartTime.formControl.setValue(moment().add(1, 'm').toDate());
    this.configObject.idTask.elementRef.nativeElement.focus();
  }

  protected override getNewOrExistingInstanceBeforeSave(value: { [name: string]: any }): TaskDataChange {
    const taskDataChange = this.copyFormToPrivateBusinessObject(new TaskDataChange(), null);
    taskDataChange.earliestStartTime = moment(taskDataChange.earliestStartTime).add(moment().utcOffset() * -1,
      'm').format('yyyy-MM-DD HH:mm:ss');
    // Use number input value if present (idEntityNum is not on TaskDataChange, so read from form)
    const idEntityNumValue = this.configObject.idEntityNum.formControl.value;
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
