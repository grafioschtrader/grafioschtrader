import {FormBase} from './form.base';
import {BusinessHelper} from '../helper/business.helper';
import {HelpIds} from '../help/help.ids';
import {GlobalparameterService} from '../service/globalparameter.service';
import {EditHelper} from './edit.helper';
import {ProposeTransientTransfer} from '../../lib/entities/propose.transient.transfer';
import {ProposeChangeEntityWithEntity} from '../../lib/proposechange/model/propose.change.entity.whit.entity';
import {DynamicFormComponent} from '../../dynamic-form/containers/dynamic-form/dynamic-form.component';
import {TranslateService} from '@ngx-translate/core';
import {Directive, ViewChild} from '@angular/core';
import {AuditHelper} from '../../lib/helper/audit.helper';
import {InfoLevelType} from '../message/info.leve.type';
import {ProcessedActionData} from '../types/processed.action.data';
import {ProcessedAction} from '../types/processed.action';
import {plainToClassFromExist} from 'class-transformer';
import {TransformedError} from '../login/service/transformed.error';
import {LimitEntityTransactionError} from '../login/service/limit.entity.transaction.error';
import {MessageToastService} from '../message/message.toast.service';
import {ServiceEntityUpdate} from './service.entity.update';
import {Tenant} from '../../entities/tenant';
import {DynamicDialogConfig, DynamicDialogRef} from 'primeng/dynamicdialog';

/**
 * Intended for a dynamically created PrimeNG editing dialog, whereby the dialog is dynamic, not the input fields.
 */
@Directive()
export abstract class SimpleDynamicEditBase<T> extends FormBase {
  // Access child components
  @ViewChild(DynamicFormComponent) form: DynamicFormComponent;

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

  public copyFormToPrivateBusinessObject(targetEntity: T, existingEntity: T): T {
    return EditHelper.copyFormToPrivateBusinessObject(targetEntity, existingEntity, this.form);
  }

  public copyFormToPublicBusinessObject(targetEntity: ProposeTransientTransfer, existingEntity: T,
    proposeChangeEntityWithEntity: ProposeChangeEntityWithEntity): void {
    EditHelper.copyFormToPublicBusinessObject(targetEntity, existingEntity, proposeChangeEntityWithEntity, this.form, this);
  }

  helpLink(): void {
    BusinessHelper.toExternalHelpWebpage(this.gps.getUserLang(), this.helpId);
  }

  protected abstract getNewOrExistingInstanceBeforeSave(value: { [name: string]: any }): T;
}
