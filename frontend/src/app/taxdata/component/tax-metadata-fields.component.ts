import { Component, Input, OnInit, ViewChild, ChangeDetectionStrategy } from '@angular/core';
import { forkJoin } from 'rxjs';
import { TranslateService, TranslateModule } from '@ngx-translate/core';
import { FormBase } from '../../lib/edit/form.base';
import { DynamicFormComponent } from '../../lib/dynamic-form/containers/dynamic-form/dynamic-form.component';
import { GlobalparameterService } from '../../lib/services/globalparameter.service';
import { DynamicFieldModelHelper } from '../../lib/helper/dynamic.field.model.helper';
import { TranslateHelper } from '../../lib/helper/translate.helper';
import { AppHelper } from '../../lib/helper/app.helper';
import { FieldConfig } from '../../lib/dynamic-form/models/field.config';
import { InputType } from '../../lib/dynamic-form/models/input.type';

/** Generated metadata fields saved atomically by the enclosing existing entity editor. */
@Component({
  selector: 'tax-metadata-fields',
  standalone: true,
  changeDetection: ChangeDetectionStrategy.Eager,
  imports: [DynamicFormComponent, TranslateModule],
  template: ` <details open>
    <summary>{{ 'SIMULATION_TAX_METADATA' | translate }}</summary>
    @if (ready) {
      <dynamic-form [config]="config" [formConfig]="formConfig" [translateService]="translateService" />
    }
  </details>`
})
export class TaxMetadataFieldsComponent extends FormBase implements OnInit {
  @Input() entityName: string;
  @Input() entity: object;
  ready = false;
  private dynamicForm: DynamicFormComponent;

  @ViewChild(DynamicFormComponent) set form(form: DynamicFormComponent) {
    this.dynamicForm = form;
    if (form)
      queueMicrotask(() => {
        form.setDefaultValuesAndEnableSubmit();
        if (this.entity) form.transferBusinessObjectToForm(this.entity);
      });
  }

  constructor(
    public translateService: TranslateService,
    private gps: GlobalparameterService
  ) {
    super();
  }

  ngOnInit(): void {
    this.formConfig = AppHelper.getDefaultFormConfig(this.gps, 4, null);
    forkJoin([this.gps.getEntityFormDefinition(this.entityName, 2), this.gps.getCountriesForSelectBox()]).subscribe(
      ([descriptor, countries]) => {
        this.config = DynamicFieldModelHelper.createFieldsFromClassDescriptorInputAndShow(
          this.translateService,
          descriptor,
          '',
          false
        ) as FieldConfig[];
        this.configObject = TranslateHelper.prepareFieldsAndErrors(this.translateService, this.config);
        if (this.configObject.countryCode) {
          this.configObject.countryCode.inputType = InputType.Select;
          this.configObject.countryCode.valueKeyHtmlOptions = countries;
        }
        if (this.configObject.taxExemptInvestor)
          this.configObject.taxExemptInvestor.inputType = InputType.TriStateCheckbox;
        this.ready = true;
      }
    );
  }

  /** Enclosing editors must call this before saving so metadata validation cannot be skipped. */
  transfer(target: object): boolean {
    if (!this.dynamicForm) return false;
    this.dynamicForm.form.markAllAsTouched();
    if (this.dynamicForm.form.invalid) return false;
    this.dynamicForm.cleanMaskAndTransferValuesToBusinessObject(target);
    if (Object.prototype.hasOwnProperty.call(target, 'countryCode') && target['countryCode'] === '') {
      target['countryCode'] = null;
    }
    return true;
  }
}
