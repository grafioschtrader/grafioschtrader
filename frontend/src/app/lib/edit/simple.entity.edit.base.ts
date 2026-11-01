import { MessageToastService } from '../message/message.toast.service';
import { TranslateService } from '@ngx-translate/core';
import { ProcessedActionData } from '../types/processed.action.data';
import { ProcessedAction } from '../types/processed.action';
import { InfoLevelType } from '../message/info.leve.type';
import { ServiceEntityUpdate } from './service.entity.update';
import { SimpleFormEditBase } from './simple.form.edit.base';
import type { ProposeChangeEntityWithEntity } from '../proposechange/model/propose.change.entity.whit.entity';
import { AuditHelper } from '../helper/audit.helper';
import { ProposeTransientTransfer } from '../entities/propose.transient.transfer';
import { TransformedError } from '../login/service/transformed.error';
import { LimitEntityTransactionError } from '../login/service/limit.entity.transaction.error';
import { plainToClassFromExist } from 'class-transformer';
import { Directive, inject } from '@angular/core';
import { takeUntilDestroyed } from '@angular/core/rxjs-interop';
import { EditHelper } from './edit.helper';
import { DynamicFormComponent } from '../dynamic-form/containers/dynamic-form/dynamic-form.component';
import { DynamicFormApi } from '../dynamic-form/models/dynamic-form-api';
import { ValidationError } from '../login/service/validation.error';

/**
 * Abstract base class for simple entity editing in dialogs with service-based updates.
 * Provides form submission, validation, error handling, and dialog management for entity editing.
 *
 * @template T - The type of entity being edited
 * @template F - Form component the dialog renders
 */
@Directive()
export abstract class SimpleEntityEditBase<
  T extends object,
  F extends DynamicFormApi = DynamicFormComponent
> extends SimpleFormEditBase<F> {
  public translateService: TranslateService = inject(TranslateService);
  public messageToastService: MessageToastService = inject(MessageToastService);

  /**
   * Constructor for SimpleEntityEditBase.
   *
   * @param {HelpIds} helpId - Help system identifier for context-sensitive help
   * @param {string} i18nRecord - Internationalization key for record type
   * @param {ServiceEntityUpdate<T>} serviceEntityUpdate - Service for updating entities
   */
  protected constructor(
    helpId: string,
    public i18nRecord: string,
    public serviceEntityUpdate: ServiceEntityUpdate<T>
  ) {
    super(helpId);
  }

  /**
   * Handles form submission by validating, updating the entity, and emitting dialog close events.
   * Manages UI state during processing and handles success/error scenarios.
   *
   * @param {Object} value - Form values as key-value pairs
   * @returns {void}
   */
  submit(value: { [name: string]: any }): void {
    const entityNew: T = this.getNewOrExistingInstanceBeforeSave(value);
    AuditHelper.disableRejectFieldButton(this.form(), true);
    this.activateWaitStateInButton();
    // Bound to the component, not to the dialog session: closing the dialog does not cancel a save in progress.
    this.serviceEntityUpdate
      .update(entityNew)
      .pipe(takeUntilDestroyed(this.destroyRef))
      .subscribe({
        next: (returnEntity) => {
          this.messageToastService.showMessageI18n(InfoLevelType.SUCCESS, 'MSG_RECORD_SAVED', {
            i18nRecord: this.i18nRecord
          });
          this.closeDialog.emit(
            new ProcessedActionData(ProcessedAction.CREATED, plainToClassFromExist(entityNew, returnEntity))
          );
          this.viewChanged();
        },
        error: (transformedError: TransformedError) => {
          if (transformedError.errorClass && transformedError.errorClass instanceof LimitEntityTransactionError) {
            this.closeDialog.emit(new ProcessedActionData(ProcessedAction.NO_CHANGE, null, transformedError));
          }
          if (transformedError.errorClass instanceof ValidationError) {
            this.form().showServerErrors?.(transformedError.errorClass.fieldErrors ?? []);
          }
          this.configObject.submit.disabled = false;
          this.form().enableSubmit?.();
          AuditHelper.disableRejectFieldButton(this.form(), false);
          this.viewChanged();
        }
      });
  }

  /**
   * Copies form data to a private business object by merging existing entity data with form values.
   *
   * @param {T} targetEntity - The target entity to receive form data
   * @param {T} existingEntity - The existing entity data to merge
   * @returns {T} The updated target entity with form data applied
   */
  public copyFormToPrivateBusinessObject(targetEntity: T, existingEntity: T | null | undefined): T {
    return EditHelper.copyFormToPrivateBusinessObject(targetEntity, existingEntity, this.form());
  }

  /**
   * Copies form data to a public business object with audit trail support for proposed changes.
   *
   * @param {ProposeTransientTransfer} targetEntity - The target propose transient transfer entity
   * @param {T} existingEntity - The existing entity data to merge
   * @param {ProposeChangeEntityWithEntity} proposeChangeEntityWithEntity - The propose change entity wrapper
   * @returns {void}
   */
  public copyFormToPublicBusinessObject(
    targetEntity: ProposeTransientTransfer,
    existingEntity: T | null | undefined,
    proposeChangeEntityWithEntity: ProposeChangeEntityWithEntity | null | undefined
  ): void {
    EditHelper.copyFormToPublicBusinessObject(targetEntity, existingEntity, proposeChangeEntityWithEntity, this.form());
  }

  /**
   * Activates waiting state in submit button during processing. Override to implement custom wait state logic.
   *
   * @returns {void}
   */
  protected activateWaitStateInButton(): void {}

  /**
   * Deactivates waiting state in submit button after processing. Override to implement custom wait state logic.
   *
   * @returns {void}
   */
  protected deactivateWaitStateInButton(): void {}

  /**
   * Abstract method to create or retrieve entity instance before saving.
   * Must be implemented by subclasses to provide specific entity creation logic.
   *
   * @abstract
   * @param {Object} value - Form values as key-value pairs
   * @returns {T} New or existing entity instance ready for saving
   */
  protected abstract getNewOrExistingInstanceBeforeSave(value: { [name: string]: any }): T;
}
