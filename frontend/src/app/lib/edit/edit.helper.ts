import { ProposeTransientTransfer } from '../entities/propose.transient.transfer';
import type { ProposeChangeEntityWithEntity } from '../proposechange/model/propose.change.entity.whit.entity';
import { AuditHelper } from '../helper/audit.helper';
import { DynamicFormApi } from '../dynamic-form/models/dynamic-form-api';

/**
 * Static helper class for copying form data to business objects.
 * Provides utility methods for transferring form values to entity instances.
 */
export abstract class EditHelper {
  /**
   * Copies form data to a private business object by merging existing entity data with form values.
   *
   * @template T
   * @static
   * @param {T} targetEntity - The target entity to receive form data
   * @param {T} existingEntity - The existing entity data to merge (optional)
   * @param {DynamicFormApi} form - The form component containing user input
   * @returns {T} The updated target entity with form data applied
   */
  public static copyFormToPrivateBusinessObject<T extends object>(
    targetEntity: T,
    existingEntity: T | null | undefined,
    form: DynamicFormApi
  ): T {
    if (existingEntity) {
      Object.assign(targetEntity, existingEntity);
    }
    form.cleanMaskAndTransferValuesToBusinessObject(targetEntity);
    return targetEntity;
  }

  /**
   * Copies form data to a public business object with audit trail support for proposed changes.
   *
   * @template T
   * @static
   * @param {ProposeTransientTransfer} targetEntity - The target propose transient transfer entity
   * @param {T} existingEntity - The existing entity data to merge (optional)
   * @param {ProposeChangeEntityWithEntity} proposeChangeEntityWithEntity - The propose change entity wrapper
   * @param {DynamicFormApi} form - The form component containing user input and the audit notes
   * @returns {void}
   */
  public static copyFormToPublicBusinessObject<T>(
    targetEntity: ProposeTransientTransfer,
    existingEntity: T | null | undefined,
    proposeChangeEntityWithEntity: ProposeChangeEntityWithEntity | null | undefined,
    form: DynamicFormApi
  ): void {
    if (existingEntity) {
      Object.assign(targetEntity, existingEntity);
    }
    AuditHelper.copyProposeChangeEntityToEntityAfterEdit(form, targetEntity, proposeChangeEntityWithEntity);
    form.cleanMaskAndTransferValuesToBusinessObject(targetEntity);
  }
}
