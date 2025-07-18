import {ProposeTransientTransfer} from '../entities/propose.transient.transfer';
import {ProposeChangeEntityWithEntity} from '../proposechange/model/propose.change.entity.whit.entity';
import {AuditHelper} from '../helper/audit.helper';
import {DynamicFormComponent} from '../../dynamic-form/containers/dynamic-form/dynamic-form.component';
import {FormBase} from './form.base';

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
   * @param {DynamicFormComponent} form - The form component containing user input
   * @returns {T} The updated target entity with form data applied
   */
  public static copyFormToPrivateBusinessObject<T>(targetEntity: T, existingEntity: T, form: DynamicFormComponent): T {
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
   * @param {DynamicFormComponent} form - The form component containing user input
   * @param {FormBase} formBase - The form base instance for audit operations
   * @returns {void}
   */
  public static copyFormToPublicBusinessObject<T>(targetEntity: ProposeTransientTransfer, existingEntity: T,
    proposeChangeEntityWithEntity: ProposeChangeEntityWithEntity, form: DynamicFormComponent, formBase: FormBase): void {
    if (existingEntity) {
      Object.assign(targetEntity, existingEntity);
    }
    AuditHelper.copyProposeChangeEntityToEntityAfterEdit(formBase, targetEntity, proposeChangeEntityWithEntity);
    form.cleanMaskAndTransferValuesToBusinessObject(targetEntity);
  }
}
