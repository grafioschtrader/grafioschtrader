import {Auditable} from '../entities/auditable';
import {DynamicFormComponent} from '../dynamic-form/containers/dynamic-form/dynamic-form.component';
import {FieldConfig} from '../dynamic-form/models/field.config';
import {AppSettings} from '../../shared/app.settings';
import {GlobalparameterService} from '../../shared/service/globalparameter.service';
import {FormHelper} from '../dynamic-form/components/FormHelper';
import {TranslateService} from '@ngx-translate/core';
import {ProcessedAction} from '../types/processed.action';
import {ProcessedActionData} from '../types/processed.action.data';
import {EventEmitter} from '@angular/core';
import {ProposeChangeEntityWithEntity} from '../proposechange/model/propose.change.entity.whit.entity';
import {FormBase} from '../edit/form.base';
import {ProposeTransientTransfer} from '../entities/propose.transient.transfer';
import {DynamicFieldHelper} from './dynamic.field.helper';
import {User} from '../entities/user';
import {Helper} from './helper';
import {InputType} from '../dynamic-form/models/input.type';
import {ProposeUserTask} from '../entities/propose.user.task';

/**
 * Utility class for handling audit trails and proposal workflows in dynamic forms.
 * Manages role-based access control, change request workflows, and form configuration
 * based on user permissions and entity audit states. Supports both direct editing
 * and proposal-based editing where changes require approval.
 *
 * Key features:
 * - Role-based form configuration (admin, limited edit, proposal workflow)
 * - Change request management with note handling
 * - Audit trail support for entity modifications
 * - Form field visibility and state management based on user rights
 * - Proposal acceptance/rejection workflow integration
 */
export class AuditHelper {

  /**
   * Custom ownership check function that can be configured by the application.
   * Should return true if the entity has special ownership rules that grant edit rights.
   */
  private static customOwnershipCheck: (entity: any, userId: number) => boolean = null;

  /** Field name constant for note request input */
  public static readonly NOTE_REQUEST_INPUT = 'noteRequest';

  /** Field name constant for note accept/reject input */
  public static readonly NOTE_ACCEPT_REJECT_INPUT = 'noteAcceptReject';

  /** Field name constant for submit button */
  public static readonly SUBMIT_FIELD_BUTTON = 'submit';

  /** Field name constant for reject button */
  public static readonly REJECT_FIELD_BUTTON = 'rejectDataChange';

  /** Label key constant for save change request button */
  public static readonly SAVE_CHANGE_REQUEST = 'SAVE_CHANGE_REQUEST';

  /**
   * Configures form for proposal editing based on entity existence and user rights.
   * For existing entities, applies audit-based configuration; for new entities, enables direct editing.
   * Only used in proposal editing scenarios.
   *
   * @param translateService Translation service for internationalization
   * @param gps Global parameter service for user context and permissions
   * @param entityAuditable The auditable entity being edited (null for new entities)
   * @param form Dynamic form component to configure
   * @param configObject Map of field configurations keyed by field name
   * @param proposeChangeEntityWithEntity Proposal change context with original and proposed entities
   */
  public static transferToFormAndChangeButtonForProposaleEdit(translateService: TranslateService,
    gps: GlobalparameterService, entityAuditable: Auditable,
    form: DynamicFormComponent,
    configObject: { [name: string]: FieldConfig },
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

  /**
   * Configures a custom ownership check function for application-specific entities.
   * This allows different applications to define their own special ownership rules
   * without hard-coding entity types in the AuditHelper.
   *
   * @param checkFunction Function that takes an entity and user ID, returns true if user owns entity
   */
  public static setCustomOwnershipCheck(checkFunction: (entity: any, userId: number) => boolean): void {
    AuditHelper.customOwnershipCheck = checkFunction;
  }

  /**
   * Converts entity and user task into proposal change entity structure.
   * Creates deep copy of entity for proposed changes and applies proposed field values.
   * Sets up the proposal request relationship between entities.
   *
   * @param entity Original entity to be modified
   * @param proposeUserTask User task containing proposed field changes
   * @returns ProposeChangeEntityWithEntity with original entity, proposed entity, and change metadata
   */
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

  /**
   * Configures form fields and buttons based on user rights and proposal state.
   * Handles three scenarios: change request submission, proposal review, and direct editing.
   * Sets field visibility, button labels, and form headers appropriately.
   *
   * @param translateService Translation service for form headers and labels
   * @param gps Global parameter service for user permissions
   * @param entityAuditable Auditable entity being edited
   * @param form Dynamic form component to configure
   * @param configObject Map of field configurations
   * @param proposeChangeEntityWithEntity Proposal context (null for direct editing)
   * @param transferToForm Whether to populate form with entity data
   */
  public static configureFormFromAuditableRights(translateService: TranslateService,
    gps: GlobalparameterService, entityAuditable: Auditable,
    form: DynamicFormComponent,
    configObject: { [name: string]: FieldConfig },
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

  /**
   * Creates complete field configuration array for note request workflow.
   * Includes note request input, accept/reject input, submit button, and reject button
   * with proper event handling for proposal workflow.
   *
   * @param closed Event emitter for workflow completion events
   * @param formBase Form base containing configuration objects
   * @param acceptRejectRequired Whether accept/reject note is mandatory
   * @returns Array of FieldConfig objects for complete note workflow
   */
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

  /**
   * Creates field configuration for note request input.
   * Creates textarea input field for change request notes with maximum character limit.
   *
   * @param required Whether the note request field is mandatory
   * @returns FieldConfig for note request textarea input
   */
  public static getNoteRequestInputDefinition(required = false): FieldConfig {
    return DynamicFieldHelper.createFieldTextareaInputString(AuditHelper.NOTE_REQUEST_INPUT, 'NOTE_REQUEST',
      AppSettings.FID_MAX_LETTERS, required);
  }

  /**
   * Copies note request value from form to target entity.
   * Only copies if note request field is visible and enabled. Used for
   * transferring user-entered notes to request objects.
   *
   * @param formBase Form base containing field configurations
   * @param targetEntity Target entity to receive the note value
   */
  public static copyNoteRequestToEntity(formBase: FormBase, targetEntity: ProposeTransientTransfer): void {
    if (!formBase.configObject[AuditHelper.NOTE_REQUEST_INPUT].invisible
      && !formBase.configObject[AuditHelper.NOTE_REQUEST_INPUT].formControl.disabled) {
      targetEntity.noteRequestOrReject = formBase.configObject[AuditHelper.NOTE_REQUEST_INPUT].formControl.value;
    }
  }

  /**
   * Copies accept/reject note value from form to target entity.
   * Only copies if propose fields are visible and enabled. Used for
   * transferring reviewer notes to response objects.
   *
   * @param formBase Form base containing field configurations
   * @param targetEntity Target entity to receive the accept/reject note
   */
  public static copyNoteAcceptRejectToEntity(formBase: FormBase, targetEntity: ProposeTransientTransfer): void {
    if (this.isProposeVisible(formBase)) {
      targetEntity.noteRequestOrReject = formBase.configObject[AuditHelper.NOTE_ACCEPT_REJECT_INPUT].formControl.value;
    }
  }

  /**
   * Checks if proposal accept/reject fields are visible and enabled.
   * Used to determine if user is in proposal review mode and can provide feedback.
   *
   * @param formBase Form base containing field configurations
   * @returns True if accept/reject note field is visible and enabled
   */
  public static isProposeVisible(formBase: FormBase): boolean {
    return !formBase.configObject[AuditHelper.NOTE_ACCEPT_REJECT_INPUT].invisible
      && !formBase.configObject[AuditHelper.NOTE_ACCEPT_REJECT_INPUT].formControl.disabled
  }

  /**
   * Enables or disables the reject field button based on form state.
   * Only affects button if it exists and is visible. Used for controlling
   * workflow actions during proposal review.
   *
   * @param configObject Map of field configurations
   * @param disable Whether to disable the reject button
   */
  public static disableRejectFieldButton(configObject: { [name: string]: FieldConfig }, disable: boolean): void {
    if (configObject[AuditHelper.REJECT_FIELD_BUTTON] && !configObject[AuditHelper.REJECT_FIELD_BUTTON].invisible) {
      configObject[AuditHelper.REJECT_FIELD_BUTTON].disabled = disable;
    }
  }

  /**
   * Copies proposal change data to entity after editing operations.
   * Handles both proposal ID transfer and note copying based on proposal context.
   * Consolidates proposal and request note handling.
   *
   * @param formBase Form base containing field configurations
   * @param targetEntity Target entity to receive proposal data
   * @param proposeChangeEntityWithEntity Proposal context (null if no proposal)
   */
  public static copyProposeChangeEntityToEntityAfterEdit(formBase: FormBase, targetEntity: ProposeTransientTransfer,
    proposeChangeEntityWithEntity: ProposeChangeEntityWithEntity): void {
    if (proposeChangeEntityWithEntity) {
      targetEntity.idProposeRequest = proposeChangeEntityWithEntity.proposeChangeEntity.idProposeRequest;
      this.copyNoteAcceptRejectToEntity(formBase, targetEntity);
    }
    this.copyNoteRequestToEntity(formBase, targetEntity);
  }

  /**
   * Checks if user has rights to edit or delete an entity.
   * For non-auditable entities, always returns true. For auditable entities,
   * delegates to auditable-specific rights checking.
   *
   * @param gps Global parameter service for user context
   * @param entity Entity to check permissions for (any type)
   * @returns True if user can edit/delete the entity
   */
  public static hasRightsForEditingOrDeleteEntity<T>(gps: GlobalparameterService, entity: any): boolean {
    return !(entity instanceof Auditable) || AuditHelper.hasRightsForEditingOrDeleteAuditable(gps, <Auditable>entity);
  }

  /**
   * Checks if user has rights to edit or delete an auditable entity.
   * Grants access if user has higher privileges, created the entity, or owns private security.
   * Core method for auditable entity permission checking.
   *
   * @param gps Global parameter service for user roles and ID
   * @param entityAuditable Auditable entity to check permissions for
   * @returns True if user can edit/delete the auditable entity
   */
  public static hasRightsForEditingOrDeleteAuditable(gps: GlobalparameterService,
    entityAuditable: Auditable): boolean {
    return AuditHelper.hasHigherPrivileges(gps)
      || gps.isEntityCreatedByUser(entityAuditable)
      || (AuditHelper.customOwnershipCheck && AuditHelper.customOwnershipCheck(entityAuditable, gps.getIdUser()));
  }

  /**
   * Checks if user has higher privileges for data editing.
   * Users with ADMIN or ALL_EDIT roles can edit most data with few exceptions.
   * Used for bypassing normal ownership restrictions.
   *
   * @param gps Global parameter service for role checking
   * @returns True if user has ADMIN or ALL_EDIT role
   */
  public static hasHigherPrivileges(gps: GlobalparameterService): boolean {
    return gps.hasRole(AppSettings.ROLE_ADMIN)
      || gps.hasRole(AppSettings.ROLE_ALL_EDIT);
  }

  /**
   * Checks if user has administrative role.
   * Admin users have highest level of system access and permissions.
   *
   * @param gps Global parameter service for role checking
   * @returns True if user has ADMIN role
   */
  public static hasAdminRole(gps: GlobalparameterService): boolean {
    return gps.hasRole(AppSettings.ROLE_ADMIN);
  }

  /**
   * Checks if user has limited editing privileges.
   * Limited edit users have restricted permissions and may require approval workflows.
   *
   * @param user User object containing role information
   * @returns True if user's most privileged role is LIMIT_EDIT
   */
  public static isLimitedEditUser(user: User): boolean {
    return user.mostPrivilegedRole === AppSettings.ROLE_LIMIT_EDIT;
  }

  /**
   * Creates visual indicators for changed fields in proposal review.
   * Sets labelTitle with old values for fields that differ between original and proposed entities.
   * Handles special formatting for select options to show human-readable old values.
   *
   * @param configObject Map of field configurations to enhance with old value tooltips
   * @param proposeChangeEntityWithEntity Proposal context containing original and proposed entities
   */
  private static createDifferenceAsLabelTitle(configObject: { [name: string]: FieldConfig },
    proposeChangeEntityWithEntity: ProposeChangeEntityWithEntity): void {
    Object.keys(configObject).forEach((key) => {
      const fieldConfig: FieldConfig = configObject[key];
      const field = fieldConfig.dataproperty ? fieldConfig.dataproperty : key;
      let oldValue = Helper.getValueByPath(proposeChangeEntityWithEntity.entity, field);
      if (oldValue !== Helper.getValueByPath(proposeChangeEntityWithEntity.proposedEntity, field)) {
        if (oldValue != null && fieldConfig.inputType === InputType.Select && fieldConfig.valueKeyHtmlOptions) {
          oldValue = fieldConfig.valueKeyHtmlOptions.find(v => v.key === oldValue).value;
        }
        configObject[key].labelTitle = oldValue;
      }
    });
  }

  /**
   * Configures form for direct editing without proposal workflow.
   * Hides proposal-related fields, sets save button label, removes form headers,
   * and resets validation on accept/reject fields.
   *
   * @param form Dynamic form component to configure
   * @param configObject Map of field configurations to modify
   */
  private static editWithoutProposalInForm(form: DynamicFormComponent,
    configObject: { [name: string]: FieldConfig }): void {
    AuditHelper.setHeaderChangeRequest(null, form);
    FormHelper.hideVisibleFieldConfigs(true, [configObject[AuditHelper.NOTE_REQUEST_INPUT],
      configObject[AuditHelper.NOTE_ACCEPT_REJECT_INPUT], configObject[AuditHelper.REJECT_FIELD_BUTTON]]);
    configObject[AuditHelper.SUBMIT_FIELD_BUTTON].labelKey = 'SAVE';
    DynamicFieldHelper.resetValidator(configObject[AuditHelper.NOTE_ACCEPT_REJECT_INPUT], null, null);
  }

  /**
   * Sets or removes form header text for change requests.
   * When textKey provided, translates and sets header for note request field.
   * When null, removes existing header. Used for contextual form labeling.
   *
   * @param translateService Translation service for header text (null to remove header)
   * @param form Dynamic form component to configure
   * @param textKey Translation key for header text (null to remove)
   */
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
