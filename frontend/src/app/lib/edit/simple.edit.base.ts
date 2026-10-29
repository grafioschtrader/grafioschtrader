import { ProcessedActionData } from '../types/processed.action.data';
import { ProcessedAction } from '../types/processed.action';
import { GlobalparameterService } from '../services/globalparameter.service';
import {
  ChangeDetectorRef,
  DestroyRef,
  Directive,
  afterNextRender,
  AfterRenderRef,
  inject,
  input,
  Injector,
  output,
  viewChild
} from '@angular/core';
import { MonoTypeOperatorFunction, Subscription } from 'rxjs';
import { DynamicFormComponent } from '../dynamic-form/containers/dynamic-form/dynamic-form.component';
import { FormBase } from './form.base';
import { untilSessionEnds } from './dialog.session';

/**
 * Base class for simple editing fields of object in a dialog.
 */
@Directive()
export abstract class SimpleEditBase extends FormBase {
  public gps: GlobalparameterService = inject(GlobalparameterService);

  // Input from parent view
  readonly visibleDialog = input(false);

  // Output for parent view
  readonly closeDialog = output<ProcessedActionData>();

  // Access child components
  readonly form = viewChild(DynamicFormComponent);

  /** Lifetime of the component, for subscriptions created outside an injection context */
  protected readonly destroyRef = inject(DestroyRef);

  protected readonly changeDetectorRef = inject(ChangeDetectorRef);
  private readonly dialogRenderInjector = inject(Injector);
  private pendingInitialize?: AfterRenderRef;

  /**
   * Subscriptions of the current dialog session, from one onShow to the following onHide. Replaced at the start of
   * every onShow and in onHide, closed when the component is destroyed.
   */
  private dialogSession = new Subscription();

  /**
   * Creates a dialog with the help page chosen by its concrete editor.
   *
   * @param helpId - Context-sensitive help page of this dialog
   */
  constructor(protected helpId: string | null) {
    super();
    this.destroyRef.onDestroy(() => this.dialogSession.unsubscribe());
  }

  public onShow(event: unknown) {
    this.renewDialogSession();
    this.pendingInitialize = afterNextRender(
      () => {
        this.initialize();
        this.viewChanged();
      },
      { injector: this.dialogRenderInjector }
    );
  }

  helpLink(): void {
    this.gps.toExternalHelpWebpage(this.gps.getUserLang(), this.helpId);
  }

  onHide(event: unknown): void {
    this.renewDialogSession();
    // Only emit when visibleDialog is still true (user initiated close).
    // Prevents double emission when parent sets visibleDialog to false.
    if (this.visibleDialog()) {
      this.closeDialog.emit(new ProcessedActionData(ProcessedAction.NO_CHANGE));
    }
  }

  /**
   * Ties an observable to the current dialog session: it completes when the dialog is hidden, shown anew or destroyed.
   * Used for valueChanges subscriptions created in initialize(), which would otherwise pile up with every opening.
   */
  protected untilDialogClosed<T>(): MonoTypeOperatorFunction<T> {
    return untilSessionEnds<T>(this.dialogSession);
  }

  /**
   * Marks this dialog for check. Needed by a dialog that writes its own template fields from an asynchronous callback
   * and has not moved them to signals yet; changes of a FieldConfig notify the form by themselves.
   */
  protected viewChanged(): void {
    this.changeDetectorRef.markForCheck();
  }

  /** Ends the subscriptions of the current dialog session and opens a new one. */
  protected renewDialogSession(): void {
    this.pendingInitialize?.destroy();
    this.dialogSession.unsubscribe();
    this.dialogSession = new Subscription();
  }

  protected abstract initialize(): void;
}
