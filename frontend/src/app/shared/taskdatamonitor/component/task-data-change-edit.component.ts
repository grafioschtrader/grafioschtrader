import {Component, Input, OnInit} from '@angular/core';
import {SimpleEntityEditBase} from '../../edit/simple.entity.edit.base';
import {TranslateService} from '@ngx-translate/core';
import {GlobalparameterService} from '../../service/globalparameter.service';
import {MessageToastService} from '../../message/message.toast.service';
import {HelpIds} from '../../help/help.ids';
import {AppSettings} from '../../app.settings';
import {TaskDataChangeService} from '../service/task.data.change.service';
import {TaskDataChange, TaskDataChangeFormConstraints, TaskType} from '../../../entities/task.data.change';
import {AppHelper} from '../../helper/app.helper';
import {DynamicFieldHelper} from '../../helper/dynamic.field.helper';
import {TranslateHelper} from '../../helper/translate.helper';
import {SelectOptionsHelper} from '../../helper/select.options.helper';
import {Subscription} from 'rxjs';
import {FormHelper} from '../../../dynamic-form/components/FormHelper';
import {DataType} from '../../../dynamic-form/models/data.type';
import * as moment from 'moment';
import {Validators} from '@angular/forms';

@Component({
  selector: 'task-data-change-edit',
  template: `
    <p-dialog header="{{i18nRecord | translate}}" [(visible)]="visibleDialog"
              [responsive]="true" [style]="{width: '500px'}"
              (onShow)="onShow($event)" (onHide)="onHide($event)" [modal]="true">

      <dynamic-form [config]="config" [formConfig]="formConfig" [translateService]="translateService"
                    #form="dynamicForm"
                    (submitBt)="submit($event)">
      </dynamic-form>
    </p-dialog>
  `
})
export class TaskDataChangeEditComponent extends SimpleEntityEditBase<TaskDataChange> implements OnInit {
  @Input() callParam: TaskDataChange;
  @Input() tdcFormConstraints: TaskDataChangeFormConstraints;

  private idTaskSubscribe: Subscription;
  private entitySubscribe: Subscription;

  constructor(translateService: TranslateService,
    gps: GlobalparameterService,
    messageToastService: MessageToastService,
    private taskDataChangeService: TaskDataChangeService) {
    super(HelpIds.HELP_TASK_DATA_CHANGE_MONITOR, AppSettings.TASK_DATE_CHANGE.toUpperCase(), translateService, gps,
      messageToastService, taskDataChangeService);
  }

  ngOnInit(): void {
    this.formConfig = AppHelper.getDefaultFormConfig(this.gps,
      4, this.helpLink.bind(this));

    this.config = [
      DynamicFieldHelper.createFieldSelectStringHeqF('idTask', true),
      DynamicFieldHelper.createFieldSelectStringHeqF('entity', true),
      DynamicFieldHelper.createFieldMinMaxNumberHeqF(DataType.NumericInteger, 'idEntity', true, 1,
        Math.pow(2, 32 - 1)),
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
      this.configObject.entity.valueKeyHtmlOptions = this.tdcFormConstraints.taskTypeConfig[idTask] ?
        SelectOptionsHelper.translateExistingValueKeyHtmlSelectOptions(this.translateService,
          SelectOptionsHelper.createHtmlOptionsFromStringArray(
            this.tdcFormConstraints.taskTypeConfig[idTask], true), false) : null;
      FormHelper.disableEnableFieldConfigs(!this.configObject.entity.valueKeyHtmlOptions, [this.configObject.entity,
        this.configObject.idEntity]);
      const allowEmpty = this.tdcFormConstraints.taskTypeConfig[idTask] && this.tdcFormConstraints.taskTypeConfig[idTask].includes;
      DynamicFieldHelper.resetValidator(this.configObject.entity, allowEmpty ? null : [Validators.required],
        allowEmpty ? null : [DynamicFieldHelper.RULE_REQUIRED_DIRTY]);
    });
  }

  valueChangedOnEntity(): void {
    this.entitySubscribe = this.configObject.entity.formControl.valueChanges.subscribe(entity => {
      FormHelper.disableEnableFieldConfigs(!entity || entity.length === 0, [this.configObject.idEntity]);
    });
  }

  onHide(event): void {
    this.idTaskSubscribe && this.idTaskSubscribe.unsubscribe();
    this.entitySubscribe && this.entitySubscribe.unsubscribe();
    super.onHide(event);
  }

  protected initialize(): void {
    this.valueChangedOnIdTask();
    this.valueChangedOnEntity();
    this.configObject.idTask.valueKeyHtmlOptions = SelectOptionsHelper.createHtmlOptionsFromEnum(
      this.translateService, TaskType, Object.keys(TaskType).filter(
        key => TaskType[key] <= this.tdcFormConstraints.maxUserCreateTask).map(key => TaskType[key]));
    if (this.callParam) {
      this.form.transferBusinessObjectToForm(this.callParam);
    }
    this.configObject.earliestStartTime.formControl.setValue(moment().add(1, 'm').toDate());
    this.configObject.idTask.elementRef.nativeElement.focus();
  }

  protected getNewOrExistingInstanceBeforeSave(value: { [name: string]: any }): TaskDataChange {
    const taskDataChange = this.copyFormToPrivateBusinessObject(new TaskDataChange(), null);
    taskDataChange.earliestStartTime = moment(taskDataChange.earliestStartTime).add(moment().utcOffset() * -1,
      'm').format('yyyy-MM-DD HH:mm:ss');
    return taskDataChange;
  }
}
