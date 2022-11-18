import {HelpIds} from '../help/help.ids';
import {GlobalparameterService} from '../service/globalparameter.service';
import {MessageToastService} from '../message/message.toast.service';
import {TranslateService} from '@ngx-translate/core';
import {ProcessedActionData} from '../types/processed.action.data';
import {ProcessedAction} from '../types/processed.action';
import {InfoLevelType} from '../message/info.leve.type';
import {ServiceEntityUpdate} from './service.entity.update';
import {SimpleEditBase} from './simple.edit.base';
import {ProposeChangeEntityWithEntity} from '../../entities/proposechange/propose.change.entity.whit.entity';
import {AuditHelper} from '../helper/audit.helper';
import {ProposeTransientTransfer} from '../../entities/propose.transient.transfer';
import {TransformedError} from '../login/service/transformed.error';
import {LimitEntityTransactionError} from '../login/service/limit.entity.transaction.error';
import {plainToClassFromExist} from 'class-transformer';
import {Directive} from '@angular/core';

/**
 * Base class for simple editing fields of an entity in a dialog. It will call a service for updating the
 * entity.
 */
@Directive()
export abstract class SimpleEntityEditBase<T> extends SimpleEditBase {

  constructor(helpId: HelpIds,
              public i18nRecord: string,
              public translateService: TranslateService,
              gps: GlobalparameterService,
              public messageToastService: MessageToastService,
              public serviceEntityUpdate: ServiceEntityUpdate<T>) {
    super(helpId, gps);
  }

  submit(value: { [name: string]: any }): void {
    const entityNew: T = this.getNewOrExistingInstanceBeforeSave(value);
    this.serviceEntityUpdate.update(entityNew).subscribe({ next: returnEntity => {
      this.messageToastService.showMessageI18n(InfoLevelType.SUCCESS, 'MSG_RECORD_SAVED', {i18nRecord: this.i18nRecord});
      this.closeDialog.emit(new ProcessedActionData(ProcessedAction.CREATED,
        plainToClassFromExist(entityNew, returnEntity)));
    }, error: (transformedError: TransformedError) => {
      if (transformedError.errorClass && transformedError.errorClass instanceof LimitEntityTransactionError) {
        this.closeDialog.emit(new ProcessedActionData(ProcessedAction.NO_CHANGE, null, transformedError));
      }
      this.configObject.submit.disabled = false;
    }});
  }

  public copyFormToPrivateBusinessObject(targetEntity: T, existingEntity: T): T {
    if (existingEntity) {
      Object.assign(targetEntity, existingEntity);
    }
    this.form.cleanMaskAndTransferValuesToBusinessObject(targetEntity);
    return targetEntity;
  }

  public copyFormToPublicBusinessObject(targetEntity: ProposeTransientTransfer, existingEntity: T,
                                        proposeChangeEntityWithEntity: ProposeChangeEntityWithEntity): void {
    if (existingEntity) {
      Object.assign(targetEntity, existingEntity);
    }
    AuditHelper.copyProposeChangeEntityToEntityAfterEdit(this, targetEntity, proposeChangeEntityWithEntity);
    this.form.cleanMaskAndTransferValuesToBusinessObject(targetEntity);
  }

  protected abstract getNewOrExistingInstanceBeforeSave(value: { [name: string]: any }): T;
}
