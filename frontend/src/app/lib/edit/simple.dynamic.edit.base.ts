import { FormBase } from './form.base';
import { GlobalparameterService } from '../services/globalparameter.service';
import { EditHelper } from './edit.helper';
import { ProposeTransientTransfer } from '../entities/propose.transient.transfer';
import { ProposeChangeEntityWithEntity } from '../proposechange/model/propose.change.entity.whit.entity';
import { DynamicFormComponent } from '../dynamic-form/containers/dynamic-form/dynamic-form.component';
import { TranslateService } from '@ngx-translate/core';
import { ChangeDetectorRef, DestroyRef, Directive, inject, viewChild } from '@angular/core';
import { takeUntilDestroyed } from '@angular/core/rxjs-interop';
import { MonoTypeOperatorFunction, Subscription } from 'rxjs';
import { untilSessionEnds } from './dialog.session';
import { AuditHelper } from '../helper/audit.helper';
import { InfoLevelType } from '../message/info.leve.type';
import { ProcessedActionData } from '../types/processed.action.data';
import { ProcessedAction } from '../types/processed.action';
import { plainToClassFromExist } from 'class-transformer';
import { TransformedError } from '../login/service/transformed.error';
import { LimitEntityTransactionError } from '../login/service/limit.entity.transaction.error';
import { MessageToastService } from '../message/message.toast.service';
import { ServiceEntityUpdate } from './service.entity.update';
import { DynamicDialogConfig, DynamicDialogRef } from '@openng/optimus-ui/dynamicdialog';

/**
 * Abstract base class for dynamically created Optimus editing dialogs.
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
  protected dynamicDialogConfig: DynamicDialogConfig = inject(DynamicDialogConfig);
  protected dynamicDialogRef: DynamicDialogRef = inject(DynamicDialogRef);
  protected translateService: TranslateService = inject(TranslateService);
  public gps: GlobalparameterService = inject(GlobalparameterService);
  public messageToastService: MessageToastService = inject(MessageToastService);

  /**
   * Reference to the dynamic form component for accessing form controls and validation.
   */
  readonly form = viewChild.required(DynamicFormComponent);

  /** Lifetime of the component, for subscriptions created outside an injection context */
  protected readonly destroyRef = inject(DestroyRef);

  protected readonly changeDetectorRef = inject(ChangeDetectorRef);

  /**
   * Subscriptions of the dialog session. A dynamic dialog is created for every opening, so the session lasts until
   * the component is destroyed unless a subclass renews it.
   */
  private dialogSession = new Subscription();

  /**
   * Constructor for SimpleDynamicEditBase.
   *
   * @protected
   * @param {HelpIds} helpId - Help system identifier for context-sensitive help
   * @param {ServiceEntityUpdate<T>} serviceEntityUpdate - Service for updating entities
   */
  protected constructor(
    protected helpId: string,
    public serviceEntityUpdate: ServiceEntityUpdate<T>
  ) {
    super();
    this.destroyRef.onDestroy(() => this.dialogSession.unsubscribe());
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
   * Ties an observable to the dialog session: it completes when the session is renewed or the dialog is destroyed.
   */
  protected untilDialogClosed<T>(): MonoTypeOperatorFunction<T> {
    return untilSessionEnds<T>(this.dialogSession);
  }

  /** Ends the subscriptions of the current dialog session and opens a new one, for a dialog that re-initializes. */
  protected renewDialogSession(): void {
    this.dialogSession.unsubscribe();
    this.dialogSession = new Subscription();
  }

  /**
   * Marks this dialog for check. Needed by a dialog that writes its own template fields from an asynchronous callback
   * and has not moved them to signals yet; changes of a FieldConfig notify the form by themselves.
   */
  protected viewChanged(): void {
    this.changeDetectorRef.markForCheck();
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
   * Opens external help webpage using the current user's language and help identifier.
   */
  helpLink(): void {
    this.gps.toExternalHelpWebpage(this.gps.getUserLang(), this.helpId);
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
