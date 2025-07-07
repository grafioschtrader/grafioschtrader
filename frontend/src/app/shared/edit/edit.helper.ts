import {ProposeTransientTransfer} from '../../lib/entities/propose.transient.transfer';
import {ProposeChangeEntityWithEntity} from '../../lib/proposechange/model/propose.change.entity.whit.entity';
import {AuditHelper} from '../../lib/helper/audit.helper';
import {DynamicFormComponent} from '../../dynamic-form/containers/dynamic-form/dynamic-form.component';
import {FormBase} from './form.base';
import {BaseID} from '../../lib/entities/base.id';

export abstract class EditHelper {

  public static  copyFormToPrivateBusinessObject<T>(targetEntity: T, existingEntity: T, form: DynamicFormComponent): T {
    if (existingEntity) {
      Object.assign(targetEntity, existingEntity);
    }
    form.cleanMaskAndTransferValuesToBusinessObject(targetEntity);
    return targetEntity;
  }

  public static copyFormToPublicBusinessObject<T>(targetEntity: ProposeTransientTransfer, existingEntity: T,
    proposeChangeEntityWithEntity: ProposeChangeEntityWithEntity, form: DynamicFormComponent, formBase: FormBase): void {
    if (existingEntity) {
      Object.assign(targetEntity, existingEntity);
    }
    AuditHelper.copyProposeChangeEntityToEntityAfterEdit(formBase, targetEntity, proposeChangeEntityWithEntity);
    form.cleanMaskAndTransferValuesToBusinessObject(targetEntity);
  }
}
