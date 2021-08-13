import {Auditable} from '../../entities/auditable';
import {DynamicFormComponent} from '../../dynamic-form/containers/dynamic-form/dynamic-form.component';
import {FieldConfig} from '../../dynamic-form/models/field.config';
import {AppSettings} from '../app.settings';
import {GlobalparameterService} from '../service/globalparameter.service';
import {FormHelper} from '../../dynamic-form/components/FormHelper';
import {TranslateService} from '@ngx-translate/core';
import {ProcessedAction} from '../types/processed.action';
import {ProcessedActionData} from '../types/processed.action.data';
import {EventEmitter} from '@angular/core';
import {ProposeChangeEntityWithEntity} from '../../entities/proposechange/propose.change.entity.whit.entity';
import {FormBase} from '../edit/form.base';
import {Security} from '../../entities/security';
import {ProposeTransientTransfer} from '../../entities/propose.transient.transfer';
import {DynamicFieldHelper} from './dynamic.field.helper';
import {User} from '../../entities/user';
import {Helper} from '../../helper/helper';
import {InputType} from '../../dynamic-form/models/input.type';
import {ProposeUserTask} from '../../entities/propose.user.task';

export class AuditHelper {

  public static readonly NOTE_REQUEST_INPUT = 'noteRequest';
  public static readonly NOTE_ACCEPT_REJECT_INPUT = 'noteAcceptReject';
  public static readonly SUBMIT_FIELD_BUTTON = 'submit';
  public static readonly REJECT_FIELD_BUTTON = 'rejectDataChange';
  public static readonly SAVE_CHANGE_REQUEST = 'SAVE_CHANGE_REQUEST';

  /**
   * Only used for proposal editing.
   */
  public static transferToFormAndChangeButtonForProposaleEdit(translateService: TranslateService,
                                                              gps: GlobalparameterService, entityAuditable: Auditable,
                                                              form: DynamicFormComponent, configObject: { [name: string]: FieldConfig },
                                                              proposeChangeEntityWithEntity: ProposeChangeEntityWithEntity): void {
    if (entityAuditable) {
      // Existing entity
      AuditHelper.configureFormFromAuditableRights(translateService, gps, entityAuditable, form, configObject,
        proposeChangeEntityWithEntity, true);
    } else {
      // New entity
      AuditHelper.editWithoutProposalInForm(form, configObject);
    }
  }

  public static convertToProposeChangeEntityWithEntity(entity: any, proposeUserTask: ProposeUserTask): ProposeChangeEntityWithEntity {
    let pewe: ProposeChangeEntityWithEntity;
    if (proposeUserTask) {
      pewe = new ProposeChangeEntityWithEntity();
      pewe.entity = entity;
      pewe.proposedEntity = JSON.parse(JSON.stringify(entity));
      proposeUserTask.proposeChangeFieldList.forEach(put => pewe.proposedEntity[put.field] = put.valueDesarialized);
      pewe.proposeChangeEntity = proposeUserTask;
      (<Auditable>pewe.entity).idProposeRequest = proposeUserTask.idProposeRequest;
    }
    return pewe;
  }

  public static configureFormFromAuditableRights(translateService: TranslateService,
                                                 gps: GlobalparameterService, entityAuditable: Auditable,
                                                 form: DynamicFormComponent, configObject: { [name: string]: FieldConfig },
                                                 proposeChangeEntityWithEntity: ProposeChangeEntityWithEntity,
                                                 transferToForm: boolean): void {
    if (!AuditHelper.hasRightsForEditingOrDeleteAuditable(gps, entityAuditable)) {
      // User can not change entity directly but propose one
      AuditHelper.setHeaderChangeRequest(translateService, form, 'YOUR_CHANGE_REQUEST');
      FormHelper.hideVisibleFieldConfigs(true, [
        configObject[AuditHelper.NOTE_ACCEPT_REJECT_INPUT], configObject[AuditHelper.REJECT_FIELD_BUTTON]]);
      FormHelper.disableEnableFieldConfigs(false, [configObject[AuditHelper.NOTE_REQUEST_INPUT]]);
      configObject[AuditHelper.SUBMIT_FIELD_BUTTON].labelKey = AuditHelper.SAVE_CHANGE_REQUEST;

    } else {
      // User can change entity
      if (proposeChangeEntityWithEntity) {
        // it is a proposal edit
        AuditHelper.setHeaderChangeRequest(translateService, form, 'CHANGE_REQUEST_FOR_YOU');
        FormHelper.disableEnableFieldConfigs(true, [configObject[AuditHelper.NOTE_REQUEST_INPUT]]);
        FormHelper.hideVisibleFieldConfigs(false, [configObject[AuditHelper.REJECT_FIELD_BUTTON],
          configObject[AuditHelper.NOTE_ACCEPT_REJECT_INPUT]]);
        configObject[AuditHelper.SUBMIT_FIELD_BUTTON].labelKey = 'SAVE';
        AuditHelper.createDifferenceAsLabelTitle(configObject, proposeChangeEntityWithEntity);
      } else {
        // User can directly edit the entity
        AuditHelper.editWithoutProposalInForm(form, configObject);
      }
    }
    if (transferToForm) {
      form.transferBusinessObjectToForm(entityAuditable);
    }
    proposeChangeEntityWithEntity && configObject[AuditHelper.NOTE_REQUEST_INPUT].formControl.setValue(
      proposeChangeEntityWithEntity.proposeChangeEntity.noteRequest);
  }

  public static getFullNoteRequestInputDefinition(closed: EventEmitter<ProcessedActionData>, formBase: FormBase,
                                                  acceptRejectRequired = false): FieldConfig[] {
    return [
      this.getNoteRequestInputDefinition(),
      DynamicFieldHelper.createFieldTextareaInputString(AuditHelper.NOTE_ACCEPT_REJECT_INPUT, 'PROPOSEACCEPTREJECT',
        AppSettings.FID_MAX_LETTERS, acceptRejectRequired),
      DynamicFieldHelper.createSubmitButtonFieldName(AuditHelper.SUBMIT_FIELD_BUTTON),
      DynamicFieldHelper.createFunctionButtonFieldName(AuditHelper.REJECT_FIELD_BUTTON, 'REJECT_DATA_CHANGE',
        (e) => closed.emit(new ProcessedActionData(ProcessedAction.REJECT_DATA_CHANGE,
          formBase.configObject[AuditHelper.NOTE_ACCEPT_REJECT_INPUT].formControl.value))),
    ];
  }

  public static getNoteRequestInputDefinition(required = false): FieldConfig {
    return DynamicFieldHelper.createFieldTextareaInputString(AuditHelper.NOTE_REQUEST_INPUT, 'NOTE_REQUEST',
      AppSettings.FID_MAX_LETTERS, required);
  }

  public static copyNoteRequestToEntity(formBase: FormBase, targetEntity: ProposeTransientTransfer) {
    targetEntity.noteRequestOrReject = formBase.configObject[AuditHelper.NOTE_REQUEST_INPUT].formControl.value;
  }

  public static copyProposeChangeEntityToEntityAfterEdit(formBase: FormBase, targetEntity: ProposeTransientTransfer,
                                                         proposeChangeEntityWithEntity: ProposeChangeEntityWithEntity): void {
    if (proposeChangeEntityWithEntity) {
      targetEntity.idProposeRequest = proposeChangeEntityWithEntity.proposeChangeEntity.idProposeRequest;
      targetEntity.noteRequestOrReject = formBase.configObject[AuditHelper.NOTE_ACCEPT_REJECT_INPUT].formControl.value;
    }

    if (!formBase.configObject[AuditHelper.NOTE_REQUEST_INPUT].invisible
      && !formBase.configObject[AuditHelper.NOTE_REQUEST_INPUT].formControl.disabled) {
      this.copyNoteRequestToEntity(formBase, targetEntity);
    }
  }

  /**
   * Users created this entity and can modify and delete it.
   */
  public static hasRightsForEditingOrDeleteEntity<T>(gps: GlobalparameterService, entity: any): boolean {
    return !(entity instanceof Auditable) || AuditHelper.hasRightsForEditingOrDeleteAuditable(gps, <Auditable>entity);
  }

  public static hasRightsForEditingOrDeleteAuditable(gps: GlobalparameterService,
                                                     entityAuditable: Auditable): boolean {
    return AuditHelper.hasHigherPrivileges(gps)
      || gps.isEntityCreatedByUser(entityAuditable)
      || entityAuditable instanceof Security && (<Security>entityAuditable).idTenantPrivate
      && (<Security>entityAuditable).idTenantPrivate === gps.getIdUser();
  }

  /**
   * Can edit all data with some few exceptions.
   */
  public static hasHigherPrivileges(gps: GlobalparameterService): boolean {
    return gps.hasRole(AppSettings.ROLE_ADMIN)
      || gps.hasRole(AppSettings.ROLE_ALL_EDIT);
  }

  public static hasAdminRole(gps: GlobalparameterService): boolean {
    return gps.hasRole(AppSettings.ROLE_ADMIN);
  }

  public static isLimitedEditUser(user: User): boolean {
    return user.mostPrivilegedRole === AppSettings.ROLE_LIMIT_EDIT;
  }

  private static createDifferenceAsLabelTitle(configObject: { [name: string]: FieldConfig },
                                              proposeChangeEntityWithEntity: ProposeChangeEntityWithEntity): void {
    Object.keys(configObject).forEach((key) => {
      const fieldConfig: FieldConfig = configObject[key];
      const field = fieldConfig.dataproperty ? fieldConfig.dataproperty : key;
      let oldValue = Helper.getValueByPath(proposeChangeEntityWithEntity.entity, field);
      if (oldValue !== Helper.getValueByPath(proposeChangeEntityWithEntity.proposedEntity, field)) {
        if (fieldConfig.inputType === InputType.Select && fieldConfig.valueKeyHtmlOptions) {
          oldValue = fieldConfig.valueKeyHtmlOptions.find(v => v.key === oldValue).value;
        }
        configObject[key].labelTitle = oldValue;
      }
    });
  }

  private static editWithoutProposalInForm(form: DynamicFormComponent, configObject: { [name: string]: FieldConfig }): void {
    AuditHelper.setHeaderChangeRequest(null, form);
    FormHelper.hideVisibleFieldConfigs(true, [configObject[AuditHelper.NOTE_REQUEST_INPUT],
      configObject[AuditHelper.NOTE_ACCEPT_REJECT_INPUT], configObject[AuditHelper.REJECT_FIELD_BUTTON]]);
    configObject[AuditHelper.SUBMIT_FIELD_BUTTON].labelKey = 'SAVE';
    DynamicFieldHelper.resetValidator(configObject[AuditHelper.NOTE_ACCEPT_REJECT_INPUT], null, null);
  }

  private static setHeaderChangeRequest(translateService: TranslateService, form: DynamicFormComponent, textKey: string = null) {
    if (textKey) {
      form.formConfig.fieldHeaders = form.formConfig.fieldHeaders || {};
      translateService.get(textKey).subscribe(text => form.formConfig.fieldHeaders[AuditHelper.NOTE_REQUEST_INPUT] = text);
    } else {
      if (form.formConfig.fieldHeaders && form.formConfig.fieldHeaders[AuditHelper.NOTE_REQUEST_INPUT]) {
        form.formConfig.fieldHeaders[AuditHelper.NOTE_REQUEST_INPUT] = null;
      }
    }
  }
}
