import { disabled, hidden, metadata, SchemaPathTree } from '@angular/forms/signals';
import { Auditable } from '../entities/auditable';
import { DynamicFormComponent } from '../dynamic-form/containers/dynamic-form/dynamic-form.component';
import { DynamicFormApi } from '../dynamic-form/models/dynamic-form-api';
import { FieldConfig } from '../dynamic-form/models/field.config';
import { FieldFormGroup } from '../dynamic-form/models/form.group.definition';
import { FormHelper } from '../dynamic-form/components/FormHelper';
import { FIELD_DISPLAY, SignalFormModel } from '../dynamic-form/signal/signal-form.model';
import { fieldPath, signalFieldEntries } from '../dynamic-form/signal/signal-form.schema';
import { ProcessedAction } from '../types/processed.action';
import { ProcessedActionData } from '../types/processed.action.data';
import type { ProposeChangeEntityWithEntity } from '../proposechange/model/propose.change.entity.whit.entity';
import { ProposeTransientTransfer } from '../entities/propose.transient.transfer';
import { DynamicFieldHelper } from './dynamic.field.helper';
import { User } from '../entities/user';
import { Helper } from './helper';
import { InputType } from '../dynamic-form/models/input.type';
import { ProposeUserTask } from '../entities/propose.user.task';
import { BaseSettings } from '../base.settings';
import { GlobalparameterService } from '../services/globalparameter.service';

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
  private static customOwnershipCheck: ((entity: any, userId: number) => boolean) | null = null;

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
   * Configures a reactive form for proposal editing based on entity existence and user rights. For an existing
   * entity the audit edit mode decides; a new entity is always edited directly.
   *
   * @param gps Global parameter service for user context and permissions
   * @param entityAuditable The auditable entity being edited (null for new entities)
   * @param form The reactive dynamic form of the dialog
   * @param proposeChangeEntityWithEntity Proposal change context with original and proposed entities
   */
  public static transferToFormAndChangeButtonForProposaleEdit(
    gps: GlobalparameterService,
    entityAuditable: Auditable | null | undefined,
    form: DynamicFormComponent,
    proposeChangeEntityWithEntity: ProposeChangeEntityWithEntity | null | undefined
  ): void {
    if (entityAuditable) {
      AuditHelper.configureFormFromAuditableRights(gps, entityAuditable, form, proposeChangeEntityWithEntity, true);
    } else {
      AuditHelper.applyReactiveAuditEditMode(form, AuditEditMode.DIRECT_EDIT, proposeChangeEntityWithEntity);
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
  public static convertToProposeChangeEntityWithEntity(
    entity: any,
    proposeUserTask: ProposeUserTask
  ): ProposeChangeEntityWithEntity;
  public static convertToProposeChangeEntityWithEntity(entity: any, proposeUserTask: null | undefined): undefined;
  public static convertToProposeChangeEntityWithEntity(
    entity: any,
    proposeUserTask: ProposeUserTask | null | undefined
  ): ProposeChangeEntityWithEntity | undefined;
  public static convertToProposeChangeEntityWithEntity(
    entity: any,
    proposeUserTask: ProposeUserTask | null | undefined
  ): ProposeChangeEntityWithEntity | undefined {
    if (proposeUserTask) {
      const pewe: ProposeChangeEntityWithEntity = {
        entity,
        proposedEntity: JSON.parse(JSON.stringify(entity)),
        proposeChangeEntity: proposeUserTask
      };
      proposeUserTask.proposeChangeFieldList.forEach((put) => (pewe.proposedEntity[put.field] = put.valueDesarialized));
      (<Auditable>pewe.entity).idProposeRequest = proposeUserTask.idProposeRequest;
      return pewe;
    }
    return undefined;
  }

  /**
   * Determines how a dialog edits a shared entity. A user without editing rights proposes a change; a user with
   * rights reviews a pending change request or edits the entity directly.
   *
   * @param gps Global parameter service for user permissions
   * @param entityAuditable Auditable entity being edited, absent for a new entity
   * @param proposeChangeEntityWithEntity Pending change request, absent when there is none
   * @returns The edit mode the note fields and buttons of the dialog follow
   */
  public static auditEditMode(
    gps: GlobalparameterService,
    entityAuditable: Auditable | null | undefined,
    proposeChangeEntityWithEntity: ProposeChangeEntityWithEntity | null | undefined
  ): AuditEditMode {
    if (!entityAuditable) {
      return AuditEditMode.DIRECT_EDIT;
    }
    if (!AuditHelper.hasRightsForEditingOrDeleteAuditable(gps, entityAuditable)) {
      return AuditEditMode.CHANGE_REQUEST;
    }
    return proposeChangeEntityWithEntity ? AuditEditMode.PROPOSAL_REVIEW : AuditEditMode.DIRECT_EDIT;
  }

  /**
   * Returns the display state of the note fields and buttons for an edit mode. Both forms follow it: the reactive
   * form through {@link configureFormFromAuditableRights}, the signal form through {@link auditRules} and
   * {@link applyAuditButtons}.
   *
   * @param mode The audit edit mode of the dialog
   * @returns Visibility and lock of the note fields, visibility of the reject button, submit label and header
   */
  public static auditFormState(mode: AuditEditMode): AuditFormState {
    switch (mode) {
      case AuditEditMode.CHANGE_REQUEST:
        return {
          noteRequestHidden: false,
          noteRequestDisabled: false,
          noteAcceptRejectHidden: true,
          rejectButtonHidden: true,
          submitLabelKey: AuditHelper.SAVE_CHANGE_REQUEST,
          headerKey: 'YOUR_CHANGE_REQUEST'
        };
      case AuditEditMode.PROPOSAL_REVIEW:
        return {
          noteRequestHidden: false,
          noteRequestDisabled: true,
          noteAcceptRejectHidden: false,
          rejectButtonHidden: false,
          submitLabelKey: 'SAVE',
          headerKey: 'CHANGE_REQUEST_FOR_YOU'
        };
      case AuditEditMode.DIRECT_EDIT:
        return {
          noteRequestHidden: true,
          noteRequestDisabled: false,
          noteAcceptRejectHidden: true,
          rejectButtonHidden: true,
          submitLabelKey: 'SAVE',
          headerKey: null
        };
    }
  }

  /**
   * Configures a reactive form from the user rights and the proposal state: field visibility, button labels and the
   * header of the change request, and the old values of a reviewed proposal as label titles.
   *
   * @param gps Global parameter service for user permissions
   * @param entityAuditable Auditable entity being edited
   * @param form The reactive dynamic form of the dialog
   * @param proposeChangeEntityWithEntity Proposal context (null for direct editing)
   * @param transferToForm Whether to populate form with entity data
   */
  public static configureFormFromAuditableRights(
    gps: GlobalparameterService,
    entityAuditable: Auditable,
    form: DynamicFormComponent,
    proposeChangeEntityWithEntity: ProposeChangeEntityWithEntity | null | undefined,
    transferToForm: boolean
  ): void {
    const mode = AuditHelper.auditEditMode(gps, entityAuditable, proposeChangeEntityWithEntity);
    AuditHelper.applyReactiveAuditEditMode(form, mode, proposeChangeEntityWithEntity);
    if (transferToForm) {
      form.transferBusinessObjectToForm(entityAuditable);
    }
    proposeChangeEntityWithEntity &&
      form.setValue(AuditHelper.NOTE_REQUEST_INPUT, proposeChangeEntityWithEntity.proposeChangeEntity.noteRequest);
  }

  /**
   * Sets the submit label and the visibility of the reject button for an edit mode. A signal form dialog calls it
   * when it initializes, because buttons are no schema fields.
   *
   * @param form The dynamic form of the dialog, reactive or signal
   * @param mode The audit edit mode of the dialog
   */
  public static applyAuditButtons(form: DynamicFormApi, mode: AuditEditMode): void {
    const state = AuditHelper.auditFormState(mode);
    form.setButtonState(AuditHelper.SUBMIT_FIELD_BUTTON, { labelKey: state.submitLabelKey });
    form.setButtonState(AuditHelper.REJECT_FIELD_BUTTON, { invisible: state.rejectButtonHidden });
  }

  /**
   * Schema rules of a signal form dialog with the note fields of {@link getFullNoteRequestInputDefinition}: they
   * hide and lock the note fields, show the header of the change request and, while a proposal is reviewed, the old
   * value of every changed field as label title. A hidden field is not validated, so a required accept/reject note
   * needs no condition of its own.
   *
   * @param p Root path of the dialog's schema
   * @param config The configuration of the form, groups included
   * @param audit The edit mode and the reviewed proposal; absent while the dialog has no context, which means
   *   direct editing
   */
  public static auditRules<TModel extends object>(
    p: SchemaPathTree<TModel>,
    config: FieldFormGroup[],
    audit: () => AuditContext | undefined
  ): void {
    const root = p as unknown as SchemaPathTree<SignalFormModel>;
    const state = () => AuditHelper.auditFormState(audit()?.mode ?? AuditEditMode.DIRECT_EDIT);
    for (const entry of signalFieldEntries(config)) {
      const path = fieldPath<unknown>(root, entry);
      if (entry.config.field === AuditHelper.NOTE_REQUEST_INPUT) {
        hidden(path, { when: () => state().noteRequestHidden });
        disabled(path, { when: () => state().noteRequestDisabled });
        metadata(path, FIELD_DISPLAY, () => ({ fieldHeader: state().headerKey }));
      } else if (entry.config.field === AuditHelper.NOTE_ACCEPT_REJECT_INPUT) {
        hidden(path, { when: () => state().noteAcceptRejectHidden });
      } else {
        metadata(path, FIELD_DISPLAY, () => {
          const context = audit();
          const proposal =
            context?.mode === AuditEditMode.PROPOSAL_REVIEW ? context.proposeChangeEntityWithEntity : null;
          const labelTitle = proposal ? AuditHelper.oldValueLabel(entry.config, proposal) : undefined;
          return labelTitle === undefined ? {} : { labelTitle };
        });
      }
    }
  }

  /**
   * Start value of the change request note of a signal form dialog, for its `[values]` input.
   *
   * @param proposeChangeEntityWithEntity The reviewed proposal, absent when there is none
   * @returns The note of the proposal, or no value
   */
  public static noteStartValues(
    proposeChangeEntityWithEntity: ProposeChangeEntityWithEntity | null | undefined
  ): Partial<AuditNoteModel> {
    return proposeChangeEntityWithEntity
      ? { noteRequest: proposeChangeEntityWithEntity.proposeChangeEntity.noteRequest }
      : {};
  }

  /**
   * Creates complete field configuration array for note request workflow.
   * Includes note request input, accept/reject input, submit button, and reject button
   * with proper event handling for proposal workflow.
   *
   * @param closed Event emitter for workflow completion events
   * @param form The form of the dialog, read when the change request is rejected; supplies the accept/reject note
   * @param acceptRejectRequired Whether accept/reject note is mandatory
   * @returns Array of FieldConfig objects for complete note workflow
   */
  public static getFullNoteRequestInputDefinition(
    closed: { emit(value: ProcessedActionData): void },
    form: () => DynamicFormApi | undefined,
    acceptRejectRequired = false
  ): FieldConfig[] {
    return [
      this.getNoteRequestInputDefinition(),
      DynamicFieldHelper.createFieldTextareaInputString(
        AuditHelper.NOTE_ACCEPT_REJECT_INPUT,
        'PROPOSEACCEPTREJECT',
        BaseSettings.FID_MAX_LETTERS,
        acceptRejectRequired
      ),
      DynamicFieldHelper.createSubmitButtonFieldName(AuditHelper.SUBMIT_FIELD_BUTTON),
      DynamicFieldHelper.createFunctionButtonFieldName(AuditHelper.REJECT_FIELD_BUTTON, 'REJECT_DATA_CHANGE', (e) =>
        closed.emit(
          new ProcessedActionData(
            ProcessedAction.REJECT_DATA_CHANGE,
            form()?.value(AuditHelper.NOTE_ACCEPT_REJECT_INPUT)
          )
        )
      )
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
    return DynamicFieldHelper.createFieldTextareaInputString(
      AuditHelper.NOTE_REQUEST_INPUT,
      'NOTE_REQUEST',
      BaseSettings.FID_MAX_LETTERS,
      required
    );
  }

  /**
   * Copies note request value from form to target entity.
   * Only copies if note request field is visible and enabled. Used for
   * transferring user-entered notes to request objects.
   *
   * @param form The dynamic form of the dialog
   * @param targetEntity Target entity to receive the note value
   */
  public static copyNoteRequestToEntity(form: DynamicFormApi, targetEntity: ProposeTransientTransfer): void {
    if (form.isFieldActive(AuditHelper.NOTE_REQUEST_INPUT)) {
      targetEntity.noteRequestOrReject = <string | null>form.value(AuditHelper.NOTE_REQUEST_INPUT);
    }
  }

  /**
   * Copies accept/reject note value from form to target entity.
   * Only copies if propose fields are visible and enabled. Used for
   * transferring reviewer notes to response objects.
   *
   * @param form The dynamic form of the dialog
   * @param targetEntity Target entity to receive the accept/reject note
   */
  public static copyNoteAcceptRejectToEntity(form: DynamicFormApi, targetEntity: ProposeTransientTransfer): void {
    if (this.isProposeVisible(form)) {
      targetEntity.noteRequestOrReject = <string | null>form.value(AuditHelper.NOTE_ACCEPT_REJECT_INPUT);
    }
  }

  /**
   * Checks if proposal accept/reject fields are visible and enabled.
   * Used to determine if user is in proposal review mode and can provide feedback.
   *
   * @param form The dynamic form of the dialog
   * @returns True if accept/reject note field is visible and enabled
   */
  public static isProposeVisible(form: DynamicFormApi): boolean {
    return form.isFieldActive(AuditHelper.NOTE_ACCEPT_REJECT_INPUT);
  }

  /**
   * Enables or disables the reject field button while a save runs. A form without the button ignores it.
   *
   * @param form The dynamic form of the dialog
   * @param disable Whether to disable the reject button
   */
  public static disableRejectFieldButton(form: DynamicFormApi, disable: boolean): void {
    form.setButtonState(AuditHelper.REJECT_FIELD_BUTTON, { disabled: disable });
  }

  /**
   * Copies proposal change data to entity after editing operations.
   * Handles both proposal ID transfer and note copying based on proposal context.
   * Consolidates proposal and request note handling.
   *
   * @param form The dynamic form of the dialog
   * @param targetEntity Target entity to receive proposal data
   * @param proposeChangeEntityWithEntity Proposal context (null if no proposal)
   */
  public static copyProposeChangeEntityToEntityAfterEdit(
    form: DynamicFormApi,
    targetEntity: ProposeTransientTransfer,
    proposeChangeEntityWithEntity: ProposeChangeEntityWithEntity | null | undefined
  ): void {
    if (proposeChangeEntityWithEntity) {
      targetEntity.idProposeRequest = proposeChangeEntityWithEntity.proposeChangeEntity.idProposeRequest;
      this.copyNoteAcceptRejectToEntity(form, targetEntity);
    }
    this.copyNoteRequestToEntity(form, targetEntity);
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
  public static hasRightsForEditingOrDeleteAuditable(gps: GlobalparameterService, entityAuditable: Auditable): boolean {
    return (
      AuditHelper.hasHigherPrivileges(gps) ||
      gps.isEntityCreatedByUser(entityAuditable) ||
      (AuditHelper.customOwnershipCheck?.(entityAuditable, gps.getIdUser()) ?? false)
    );
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
    return gps.hasRole(BaseSettings.ROLE_ADMIN) || gps.hasRole(BaseSettings.ROLE_ALL_EDIT);
  }

  /**
   * Checks if user has administrative role.
   * Admin users have highest level of system access and permissions.
   *
   * @param gps Global parameter service for role checking
   * @returns True if user has ADMIN role
   */
  public static hasAdminRole(gps: GlobalparameterService): boolean {
    return gps.hasRole(BaseSettings.ROLE_ADMIN);
  }

  /**
   * Checks if user has limited editing privileges.
   * Limited edit users have restricted permissions and may require approval workflows.
   *
   * @param user User object containing role information
   * @returns True if user's most privileged role is LIMIT_EDIT
   */
  public static isLimitedEditUser(user: User): boolean {
    return user.mostPrivilegedRole === BaseSettings.ROLE_LIMIT_EDIT;
  }

  /**
   * Returns the old value of a field that a reviewed proposal changes, for its label title. A select shows the
   * label of its old option.
   *
   * @param fieldConfig The field
   * @param proposeChangeEntityWithEntity The original and the proposed entity
   * @returns The old value as text, empty for an absent old value; undefined when the proposal leaves it unchanged
   */
  public static oldValueLabel(
    fieldConfig: FieldConfig,
    proposeChangeEntityWithEntity: ProposeChangeEntityWithEntity
  ): string | undefined {
    const field = fieldConfig.dataproperty ? fieldConfig.dataproperty : fieldConfig.field;
    if (!field) {
      return undefined;
    }
    let oldValue = Helper.getValueByPath(proposeChangeEntityWithEntity.entity, field);
    if (oldValue === Helper.getValueByPath(proposeChangeEntityWithEntity.proposedEntity, field)) {
      return undefined;
    }
    if (oldValue != null && fieldConfig.inputType === InputType.Select && fieldConfig.valueKeyHtmlOptions) {
      oldValue = fieldConfig.valueKeyHtmlOptions.find((v) => v.key === oldValue)?.value ?? oldValue;
    }
    return oldValue == null ? '' : String(oldValue);
  }

  /**
   * Applies an edit mode to the fields and buttons of a reactive form. Its FieldConfigs are instrumented, so each
   * assignment re-renders the form.
   *
   * @param form The reactive dynamic form of the dialog
   * @param mode The audit edit mode
   * @param proposeChangeEntityWithEntity The reviewed proposal, whose old values become label titles
   */
  private static applyReactiveAuditEditMode(
    form: DynamicFormComponent,
    mode: AuditEditMode,
    proposeChangeEntityWithEntity: ProposeChangeEntityWithEntity | null | undefined
  ): void {
    const state = AuditHelper.auditFormState(mode);
    AuditHelper.applyAuditButtons(form, mode);
    const noteRequest = form.fieldConfig(AuditHelper.NOTE_REQUEST_INPUT);
    if (noteRequest) {
      noteRequest.fieldHeader = state.headerKey;
      if (state.noteRequestHidden) {
        noteRequest.invisible = true;
      } else {
        FormHelper.disableEnableFieldConfigs(state.noteRequestDisabled, [noteRequest]);
      }
    }
    const noteAcceptReject = form.fieldConfig(AuditHelper.NOTE_ACCEPT_REJECT_INPUT);
    if (noteAcceptReject) {
      noteAcceptReject.invisible = state.noteAcceptRejectHidden;
      if (mode === AuditEditMode.DIRECT_EDIT) {
        DynamicFieldHelper.resetValidator(noteAcceptReject, null, null);
      }
    }
    if (mode === AuditEditMode.PROPOSAL_REVIEW && proposeChangeEntityWithEntity) {
      FormHelper.flattenConfigMap(form.config()).forEach((fieldConfig) => {
        const labelTitle = AuditHelper.oldValueLabel(fieldConfig, proposeChangeEntityWithEntity);
        if (labelTitle !== undefined) {
          fieldConfig.labelTitle = labelTitle;
        }
      });
    }
  }
}

/** How a dialog of a shared entity edits it; decides the state of its note fields and buttons. */
export enum AuditEditMode {
  /** The user edits the entity directly, or creates a new one; the note fields are hidden. */
  DIRECT_EDIT = 'DIRECT_EDIT',
  /** The user lacks editing rights and proposes a change with a note. */
  CHANGE_REQUEST = 'CHANGE_REQUEST',
  /** The user reviews a change request of another user and accepts or rejects it with a note. */
  PROPOSAL_REVIEW = 'PROPOSAL_REVIEW'
}

/** Display state of the note fields and buttons in one audit edit mode. */
export interface AuditFormState {
  noteRequestHidden: boolean;
  noteRequestDisabled: boolean;
  noteAcceptRejectHidden: boolean;
  rejectButtonHidden: boolean;
  /** Translation key of the submit button label. */
  submitLabelKey: string;
  /** Translation key of the header above the change request note, null for none. */
  headerKey: string | null;
}

/** What the audit rules of a signal form dialog follow; the dialog keeps it in a signal. */
export interface AuditContext {
  mode: AuditEditMode;
  /** The reviewed proposal, whose old values become label titles. */
  proposeChangeEntityWithEntity?: ProposeChangeEntityWithEntity | null;
}

/** The note fields of a signal form dialog in the audit flow, for the model interface of the dialog. */
export interface AuditNoteModel {
  noteRequest: string | null;
  noteAcceptReject: string | null;
}
