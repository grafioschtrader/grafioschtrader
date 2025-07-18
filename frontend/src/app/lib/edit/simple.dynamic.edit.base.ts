import {FormBase} from './form.base';
import {BusinessHelper} from '../../shared/helper/business.helper';
import {HelpIds} from '../../shared/help/help.ids';
import {GlobalparameterService} from '../../shared/service/globalparameter.service';
import {EditHelper} from './edit.helper';
import {ProposeTransientTransfer} from '../entities/propose.transient.transfer';
import {ProposeChangeEntityWithEntity} from '../proposechange/model/propose.change.entity.whit.entity';
import {DynamicFormComponent} from '../../dynamic-form/containers/dynamic-form/dynamic-form.component';
import {TranslateService} from '@ngx-translate/core';
import {Directive, ViewChild} from '@angular/core';
import {AuditHelper} from '../helper/audit.helper';
import {InfoLevelType} from '../message/info.leve.type';
import {ProcessedActionData} from '../types/processed.action.data';
import {ProcessedAction} from '../types/processed.action';
import {plainToClassFromExist} from 'class-transformer';
import {TransformedError} from '../../shared/login/service/transformed.error';
import {LimitEntityTransactionError} from '../../shared/login/service/limit.entity.transaction.error';
import {MessageToastService} from '../message/message.toast.service';
import {ServiceEntityUpdate} from './service.entity.update';
import {DynamicDialogConfig, DynamicDialogRef} from 'primeng/dynamicdialog';

/**
 * Abstract base class for dynamically created PrimeNG editing dialogs.
 * Provides common functionality for entity editing with form validation, submission, and dialog management.
 * The dialog itself is dynamic, not the input fields.
 *
 * @abstract
 * @class SimpleDynamicEditBase
 * @extends {FormBase}
 * @template T - The type of entity being edited
 */
@Directive()
export abstract class SimpleDynamicEditBase<T> extends FormBase {
  /**
   * Reference to the dynamic form component for accessing form controls and validation.
   */
  @ViewChild(DynamicFormComponent) form: DynamicFormComponent;

  /**
   * Constructor for SimpleDynamicEditBase.
   *
   * @protected
   * @param {DynamicDialogConfig} dynamicDialogConfig - Configuration for the dynamic dialog
   * @param {DynamicDialogRef} dynamicDialogRef - Reference to the dynamic dialog instance
   * @param {HelpIds} helpId - Help system identifier for context-sensitive help
   * @param {TranslateService} translateService - Service for internationalization
   * @param {GlobalparameterService} gps - Service for accessing global parameters
   * @param {MessageToastService} messageToastService - Service for displaying toast messages
   * @param {ServiceEntityUpdate<T>} serviceEntityUpdate - Service for updating entities
   */
  protected constructor(
    protected dynamicDialogConfig: DynamicDialogConfig,
    protected dynamicDialogRef: DynamicDialogRef,
    protected helpId: HelpIds,
    protected translateService: TranslateService,
    public gps: GlobalparameterService,
    public messageToastService: MessageToastService,
    public serviceEntityUpdate: ServiceEntityUpdate<T>) {
    super();
  }

  /**
   * Handles form submission by validating, updating the entity, and closing the dialog.
   * Disables UI elements during processing and handles success/error scenarios.
   *
   * @param {Object} value - Form values as key-value pairs
   * @returns {void}
   */
  submit(value: { [name: string]: any }): void {
    const entityNew: T = this.getNewOrExistingInstanceBeforeSave(value);
    AuditHelper.disableRejectFieldButton(this.configObject, true);
    this.serviceEntityUpdate.update(entityNew).subscribe({
      next: returnEntity => {
        this.messageToastService.showMessageI18n(InfoLevelType.SUCCESS, 'MSG_RECORD_SAVED',
          {i18nRecord: this.dynamicDialogConfig.header});
        this.dynamicDialogRef.close(new ProcessedActionData(ProcessedAction.CREATED,
          plainToClassFromExist(entityNew, returnEntity)));
      }, error: (transformedError: TransformedError) => {
        if (transformedError.errorClass && transformedError.errorClass instanceof LimitEntityTransactionError) {
          this.dynamicDialogRef.close(new ProcessedActionData(ProcessedAction.NO_CHANGE, null, transformedError));
        }
        this.configObject.submit.disabled = false;
        AuditHelper.disableRejectFieldButton(this.configObject, false);
      }
    });
  }

  /**
   * Copies form data to a private business object by merging existing entity data with form values.
   *
   * @public
   * @param {T} targetEntity - The target entity to receive form data
   * @param {T} existingEntity - The existing entity data to merge
   * @returns {T} The updated target entity with form data applied
   */
  public copyFormToPrivateBusinessObject(targetEntity: T, existingEntity: T): T {
    return EditHelper.copyFormToPrivateBusinessObject(targetEntity, existingEntity, this.form);
  }

  /**
   * Copies form data to a public business object with audit trail support for proposed changes.
   *
   * @param {ProposeTransientTransfer} targetEntity - The target propose transient transfer entity
   * @param {T} existingEntity - The existing entity data to merge
   * @param {ProposeChangeEntityWithEntity} proposeChangeEntityWithEntity - The propose change entity wrapper
   * @returns {void}
   */
  public copyFormToPublicBusinessObject(targetEntity: ProposeTransientTransfer, existingEntity: T,
    proposeChangeEntityWithEntity: ProposeChangeEntityWithEntity): void {
    EditHelper.copyFormToPublicBusinessObject(targetEntity, existingEntity, proposeChangeEntityWithEntity, this.form, this);
  }

  /**
   * Opens external help webpage using the current user's language and help identifier.
   */
  helpLink(): void {
    BusinessHelper.toExternalHelpWebpage(this.gps.getUserLang(), this.helpId);
  }

  /**
   * Abstract method to create or retrieve entity instance before saving.
   * Must be implemented by subclasses to provide specific entity creation logic.
   *
   * @param {Object} value - Form values as key-value pairs
   * @returns {T} New or existing entity instance ready for saving
   */
  protected abstract getNewOrExistingInstanceBeforeSave(value: { [name: string]: any }): T;
}
