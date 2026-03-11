import {AfterViewInit, Component, OnInit, ViewChild} from '@angular/core';
import {FormBase} from '../../lib/edit/form.base';
import {DynamicFieldHelper} from '../../lib/helper/dynamic.field.helper';
import {TranslateHelper} from '../../lib/helper/translate.helper';
import {TranslateService} from '@ngx-translate/core';
import {GlobalparameterService} from '../../lib/services/globalparameter.service';
import {DynamicDialogRef} from 'primeng/dynamicdialog';
import {AppHelper} from '../../lib/helper/app.helper';
import {DynamicFormModule} from '../../lib/dynamic-form/dynamic-form.module';
import {DynamicFormComponent} from '../../lib/dynamic-form/containers/dynamic-form/dynamic-form.component';
import {HelpIds} from '../../lib/help/help.ids';

/**
 * Dynamic dialog for selecting a country code when creating a new tax country node.
 * Uses the global countries list from the backend to populate the dropdown.
 * Opened via DialogService.open() and returns the selected country code on submit.
 */
@Component({
  template: `
    <dynamic-form [config]="config" [formConfig]="formConfig" [translateService]="translateService"
                  #form="dynamicForm" (submitBt)="submit($event)">
    </dynamic-form>`,
  standalone: true,
  imports: [DynamicFormModule]
})
export class TaxCountryCreateComponent extends FormBase implements OnInit, AfterViewInit {

  @ViewChild(DynamicFormComponent) form: DynamicFormComponent;

  constructor(
    public translateService: TranslateService,
    public gps: GlobalparameterService,
    private dynamicDialogRef: DynamicDialogRef
  ) {
    super();
  }

  ngOnInit(): void {
    this.formConfig = AppHelper.getDefaultFormConfig(this.gps, 4, this.helpLink.bind(this));
    this.config = [
      DynamicFieldHelper.createFieldSelectStringHeqF('countryCode', true),
      DynamicFieldHelper.createSubmitButton()
    ];
    this.configObject = TranslateHelper.prepareFieldsAndErrors(this.translateService, this.config);
  }

  ngAfterViewInit(): void {
    this.gps.getCountriesForSelectBox().subscribe(countries => {
      this.configObject.countryCode.valueKeyHtmlOptions = countries;
      this.form.setDefaultValuesAndEnableSubmit();
    });
  }

  helpLink(): void {
    this.gps.toExternalHelpWebpage(this.gps.getUserLang(), HelpIds.HELP_TAX_DATA);
  }

  submit(value: { [name: string]: any }): void {
    this.dynamicDialogRef.close(value.countryCode);
  }
}
