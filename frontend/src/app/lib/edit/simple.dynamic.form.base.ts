import { ChangeDetectorRef, DestroyRef, Directive, inject, viewChild } from '@angular/core';
import { TranslateService } from '@ngx-translate/core';
import { DynamicDialogConfig, DynamicDialogRef } from '@openng/optimus-ui/dynamicdialog';
import { MonoTypeOperatorFunction, Subscription } from 'rxjs';
import { DynamicFormComponent } from '../dynamic-form/containers/dynamic-form/dynamic-form.component';
import { DynamicFormApi, dynamicFormToken } from '../dynamic-form/models/dynamic-form-api';
import { MessageToastService } from '../message/message.toast.service';
import { GlobalparameterService } from '../services/globalparameter.service';
import { untilSessionEnds } from './dialog.session';
import { FormBase } from './form.base';

/**
 * Shared form and session of a dynamically opened dialog. Action dialogs implement submit themselves;
 * SimpleDynamicEditBase adds the entity-save flow for dialogs backed by ServiceEntityUpdate.
 *
 * @template F - Form component the dialog renders; the reactive DynamicFormComponent unless it uses the signal form
 */
@Directive()
export abstract class SimpleDynamicFormBase<F extends DynamicFormApi = DynamicFormComponent> extends FormBase {
  protected readonly dynamicDialogConfig = inject(DynamicDialogConfig);
  protected readonly dynamicDialogRef = inject(DynamicDialogRef);
  protected readonly translateService = inject(TranslateService);
  readonly gps = inject(GlobalparameterService);
  readonly messageToastService = inject(MessageToastService);
  readonly form = viewChild.required(dynamicFormToken<F>());
  protected readonly destroyRef = inject(DestroyRef);
  protected readonly changeDetectorRef = inject(ChangeDetectorRef);

  /** A dynamic dialog is created for every opening, so its session normally lasts until destruction. */
  private dialogSession = new Subscription();

  protected constructor(protected helpId: string) {
    super();
    this.destroyRef.onDestroy(() => this.dialogSession.unsubscribe());
  }

  abstract submit(value: { [name: string]: any }): void | Promise<void>;

  /** Ends a stream when this dialog is destroyed or explicitly renews its session. */
  protected untilDialogClosed<T>(): MonoTypeOperatorFunction<T> {
    return untilSessionEnds<T>(this.dialogSession);
  }

  protected renewDialogSession(): void {
    this.dialogSession.unsubscribe();
    this.dialogSession = new Subscription();
  }

  /** Notifies the view after asynchronous changes to plain template fields. */
  protected viewChanged(): void {
    this.changeDetectorRef.markForCheck();
  }

  helpLink(): void {
    this.gps.toExternalHelpWebpage(this.gps.getUserLang(), this.helpId);
  }
}
