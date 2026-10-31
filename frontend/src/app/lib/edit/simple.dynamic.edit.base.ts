import { Directive } from '@angular/core';
import { takeUntilDestroyed } from '@angular/core/rxjs-interop';
import { plainToClassFromExist } from 'class-transformer';
import { ProposeTransientTransfer } from '../entities/propose.transient.transfer';
import { AuditHelper } from '../helper/audit.helper';
import { LimitEntityTransactionError } from '../login/service/limit.entity.transaction.error';
import { TransformedError } from '../login/service/transformed.error';
import { InfoLevelType } from '../message/info.leve.type';
import type { ProposeChangeEntityWithEntity } from '../proposechange/model/propose.change.entity.whit.entity';
import { ProcessedAction } from '../types/processed.action';
import { ProcessedActionData } from '../types/processed.action.data';
import { EditHelper } from './edit.helper';
import { ServiceEntityUpdate } from './service.entity.update';
import { SimpleDynamicFormBase } from './simple.dynamic.form.base';

/** Dynamic entity editor with the shared form session and the standard save/error flow. */
@Directive()
export abstract class SimpleDynamicEditBase<T extends object> extends SimpleDynamicFormBase {
  protected constructor(
    helpId: string,
    public serviceEntityUpdate: ServiceEntityUpdate<T>
  ) {
    super(helpId);
  }

  override submit(value: { [name: string]: any }): void {
    const entityNew: T = this.getNewOrExistingInstanceBeforeSave(value);
    AuditHelper.disableRejectFieldButton(this.configObject, true);
    this.serviceEntityUpdate
      .update(entityNew)
      .pipe(takeUntilDestroyed(this.destroyRef))
      .subscribe({
        next: (returnEntity) => {
          this.messageToastService.showMessageI18n(InfoLevelType.SUCCESS, 'MSG_RECORD_SAVED', {
            i18nRecord: this.dynamicDialogConfig.header
          });
          this.dynamicDialogRef.close(
            new ProcessedActionData(ProcessedAction.CREATED, plainToClassFromExist(entityNew, returnEntity))
          );
          this.viewChanged();
        },
        error: (transformedError: TransformedError) => {
          if (transformedError.errorClass && transformedError.errorClass instanceof LimitEntityTransactionError) {
            this.dynamicDialogRef.close(new ProcessedActionData(ProcessedAction.NO_CHANGE, null, transformedError));
          }
          this.configObject.submit.disabled = false;
          AuditHelper.disableRejectFieldButton(this.configObject, false);
          this.viewChanged();
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
    existingEntity: T,
    proposeChangeEntityWithEntity: ProposeChangeEntityWithEntity
  ): void {
    EditHelper.copyFormToPublicBusinessObject(
      targetEntity,
      existingEntity,
      proposeChangeEntityWithEntity,
      this.form(),
      this
    );
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
