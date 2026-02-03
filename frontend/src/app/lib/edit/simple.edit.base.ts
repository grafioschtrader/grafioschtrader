import {ProcessedActionData} from '../types/processed.action.data';
import {ProcessedAction} from '../types/processed.action';
import {GlobalparameterService} from '../services/globalparameter.service';
import {Directive, EventEmitter, Input, Output, ViewChild} from '@angular/core';
import {DynamicFormComponent} from '../dynamic-form/containers/dynamic-form/dynamic-form.component';
import {FormBase} from './form.base';

/**
 * Base class for simple editing fields of object in a dialog.
 */
@Directive()
export abstract class SimpleEditBase extends FormBase {

  // Input from parent view
  @Input() visibleDialog: boolean;

  // Output for parent view
  @Output() closeDialog = new EventEmitter<ProcessedActionData>();

  // Access child components
  @ViewChild(DynamicFormComponent) form: DynamicFormComponent;

  constructor(protected helpId: string,
    public gps: GlobalparameterService) {
    super();
  }

  public onShow(event) {
    setTimeout(() => this.initialize());
  }


  helpLink(): void {
    this.gps.toExternalHelpWebpage(this.gps.getUserLang(), this.helpId);
  }

  onHide(event): void {
    // Only emit when visibleDialog is still true (user initiated close).
    // Prevents double emission when parent sets visibleDialog to false.
    if (this.visibleDialog) {
      this.closeDialog.emit(new ProcessedActionData(ProcessedAction.NO_CHANGE));
    }
  }

  protected abstract initialize(): void;
}
