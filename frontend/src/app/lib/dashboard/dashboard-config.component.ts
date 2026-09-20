import { Component, Input, OnInit } from '@angular/core';
import { TranslateModule, TranslateService } from '@ngx-translate/core';
import { DialogModule } from '@openng/optimus-ui/dialog';
import { SimpleEditBase } from '../edit/simple.edit.base';
import { GlobalparameterService } from '../services/globalparameter.service';
import { DynamicFormModule } from '../dynamic-form/dynamic-form.module';
import { DynamicFieldModelHelper } from '../helper/dynamic.field.model.helper';
import { DynamicFieldHelper } from '../helper/dynamic.field.helper';
import { TranslateHelper } from '../helper/translate.helper';
import { AppHelper } from '../helper/app.helper';
import { HelpIds } from '../help/help.ids';
import { DashboardConfig, DashboardDescriptor, DashboardWidget } from './dashboard.types';
import { ProcessedActionData } from '../types/processed.action.data';
import { ProcessedAction } from '../types/processed.action';
import { FieldConfig } from '../dynamic-form/models/field.config';

/** Descriptor is fetched before mounting this dialog so the dynamic form initializes synchronously. */
@Component({
  selector: 'dashboard-config',
  standalone: true,
  imports: [TranslateModule, DialogModule, DynamicFormModule],
  template: `<p-dialog
    [header]="descriptor.titleKey | translate"
    [visible]="visibleDialog"
    [modal]="true"
    [closable]="true"
    (onShow)="onShow($event)"
    (onHide)="onHide($event)">
    <p>{{ descriptor.descriptionKey | translate }}</p>
    <dynamic-form
      [config]="config"
      [formConfig]="formConfig"
      [translateService]="translateService"
      #form="dynamicForm"
      (submitBt)="submit()" />
  </p-dialog>`
})
export class DashboardConfigComponent extends SimpleEditBase implements OnInit {
  @Input() descriptor: DashboardDescriptor;
  @Input() widget: DashboardWidget;
  constructor(
    public translateService: TranslateService,
    gps: GlobalparameterService
  ) {
    super(HelpIds.HELP_DASHBOARD, gps);
  }
  ngOnInit(): void {
    this.formConfig = AppHelper.getDefaultFormConfig(this.gps, 5, this.helpLink.bind(this));
    this.config = [
      ...DynamicFieldModelHelper.createFieldsFromClassDescriptorInputAndShow(
        this.translateService,
        this.descriptor.formDefinition,
        '',
        false
      ),
      DynamicFieldHelper.createSubmitButton()
    ] as FieldConfig[];
    this.configObject = TranslateHelper.prepareFieldsAndErrors(this.translateService, this.config);
  }
  protected initialize(): void {
    this.form.transferBusinessObjectToForm(this.widget.config);
  }
  /**
   * Emits exactly the settings the type declares. The names come from the descriptor rather than from this dialog, so a
   * widget with settings of its own needs no change here; anything else the form carries, such as the submit button, is
   * left behind. The standard form transfer converts numeric input strings to JSON numbers for backend validation.
   */
  submit(): void {
    const config: DashboardConfig = { ...this.descriptor.defaultConfig };
    this.form.cleanMaskAndTransferValuesToBusinessObject(config);
    this.closeDialog.emit(new ProcessedActionData(ProcessedAction.UPDATED, config));
  }
}
