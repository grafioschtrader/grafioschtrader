import {Component, OnInit} from '@angular/core';
import {FormBase} from '../../edit/form.base';
import {DynamicFieldHelper} from '../../helper/dynamic.field.helper';
import {TranslateHelper} from '../../helper/translate.helper';
import {TranslateService} from '@ngx-translate/core';
import {AppHelper} from '../../helper/app.helper';
import {HelpIds} from '../../help/help.ids';
import {ProposeUserTaskService} from '../service/propose.user.task.service';
import {ProposeUserTask} from '../../entities/propose.user.task';
import {UserTaskType} from '../../types/user.task.type';
import {ProposeChangeField} from '../../entities/propose.change.field';
import {InfoLevelType} from '../../message/info.leve.type';
import {MessageToastService} from '../../message/message.toast.service';
import {FieldDescriptorInputAndShow} from '../../dynamicfield/field.descriptor.input.and.show';
import {Helper} from '../../helper/helper';
import {DialogService, DynamicDialogConfig, DynamicDialogRef} from 'primeng/dynamicdialog';
import {DynamicFieldModelHelper} from '../../helper/dynamic.field.model.helper';
import {BaseSettings} from '../../base.settings';
import {GlobalparameterService} from '../../services/globalparameter.service';
import {DynamicFormModule} from '../../dynamic-form/dynamic-form.module';

/**
 * The daily limit of changing public data was passed. The user can apply for a different daily limit.
 */
@Component({
  template: `
    {{ dialogTitle }}
    <dynamic-form [config]="config" [formConfig]="formConfig" [translateService]="translateService" #form="dynamicForm"
                  (submitBt)="submit($event)">
    </dynamic-form>`,
  standalone: true,
  imports: [DynamicFormModule]
})
export class LimitTransactionRequestDynamicComponent extends FormBase implements OnInit {
  dialogTitle: string;

  private readonly ENTITY_NAME = 'entity';
  private readonly NOTE_REQUEST = 'noteRequest';

  constructor(public translateService: TranslateService,
    public gps: GlobalparameterService,
    private messageToastService: MessageToastService,
    private proposeUserTaskService: ProposeUserTaskService,
    private dialogService: DialogService,
    private dynamicDialogRef: DynamicDialogRef,
    private dynamicDialogConfig: DynamicDialogConfig) {
    super();
  }

  ngOnInit(): void {
    this.formConfig = AppHelper.getDefaultFormConfig(this.gps,
      4, this.helpLink.bind(this));
    this.dialogTitle = this.dynamicDialogConfig.header;
    this.proposeUserTaskService.getFormDefinitionsByUserTaskType(UserTaskType.LIMIT_CUD_CHANGE).subscribe(
      (fDIaSs: FieldDescriptorInputAndShow[]) => {
        this.config = DynamicFieldModelHelper.createConfigFieldsFromDescriptor(this.translateService, fDIaSs, '', true, 'SEND');
        this.config.splice(this.config.length - 1, 0,
          DynamicFieldHelper.createFieldTextareaInputStringHeqF(this.NOTE_REQUEST, BaseSettings.FID_MAX_LETTERS, true));
        this.configObject = TranslateHelper.prepareFieldsAndErrors(this.translateService, this.config);
        this.configObject[this.ENTITY_NAME].readonly = true;
        this.configObject[this.ENTITY_NAME].defaultValue = this.dynamicDialogConfig.data.entityName;
      });
  }

  submit(value: { [name: string]: any }): void {
    const proposeUserTask = new ProposeUserTask(UserTaskType.LIMIT_CUD_CHANGE);

    proposeUserTask.noteRequest = value[this.NOTE_REQUEST];
    for (let i = 0; i < 3; i++) {
      const fieldObject = {};
      Helper.copyFormSingleFormConfigToBusinessObject(this.formConfig, this.config[i], fieldObject, true);
      proposeUserTask.addProposeChangeField((new ProposeChangeField(this.config[i].field, fieldObject[this.config[i].field])));
    }

    this.proposeUserTaskService.update(proposeUserTask).subscribe({
      next: returnEntity => {
        this.messageToastService.showMessageI18n(InfoLevelType.SUCCESS, 'MSG_PROPOSE_SAVED');
        this.dynamicDialogRef.close();
      }, error: error1 => {
        this.configObject.submit.disabled = false;
      }
    });
  }

  helpLink(): void {
    this.gps.toExternalHelpWebpage(this.gps.getUserLang(), HelpIds.HELP_USER);
  }

}
