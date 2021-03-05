import {ProcessedActionData} from '../types/processed.action.data';
import {ProcessedAction} from '../types/processed.action';
import {AppHelper} from '../helper/app.helper';
import {GlobalparameterService} from '../service/globalparameter.service';
import { EventEmitter, Input, Output, ViewChild, Directive } from '@angular/core';
import {DynamicFormComponent} from '../../dynamic-form/containers/dynamic-form/dynamic-form.component';
import {HelpIds} from '../help/help.ids';
import {FormBase} from './form.base';
import {BusinessHelper} from '../helper/business.helper';

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

  constructor(protected helpId: HelpIds,
              public globalparameterService: GlobalparameterService) {
    super();
  }

  public onShow(event) {
    setTimeout(() => this.initialize());
  }


  helpLink() {
    BusinessHelper.toExternalHelpWebpage(location, this.globalparameterService.getUserLang(), this.helpId);
  }

  onHide(event): void {
    this.closeDialog.emit(new ProcessedActionData(ProcessedAction.NO_CHANGE));
  }

  protected abstract initialize(): void;
}
