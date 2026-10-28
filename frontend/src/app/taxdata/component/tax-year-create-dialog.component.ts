import { afterNextRender, Component, inject, Injector, OnInit, viewChild } from '@angular/core';
import { FormBase } from '../../lib/edit/form.base';
import { DynamicFieldHelper } from '../../lib/helper/dynamic.field.helper';
import { TranslateHelper } from '../../lib/helper/translate.helper';
import { TranslateService } from '@ngx-translate/core';
import { GlobalparameterService } from '../../lib/services/globalparameter.service';
import { DynamicDialogConfig, DynamicDialogRef } from '@openng/optimus-ui/dynamicdialog';
import { AppHelper } from '../../lib/helper/app.helper';
import { DYNAMIC_FORM_IMPORTS } from '../../lib/dynamic-form/dynamic-form.imports';
import { DynamicFormComponent } from '../../lib/dynamic-form/containers/dynamic-form/dynamic-form.component';
import { HelpIds } from '../../lib/help/help.ids';
import { ValueKeyHtmlSelectOptions } from '../../lib/dynamic-form/models/value.key.html.select.options';
import { GlobalSessionNames } from '../../lib/global.session.names';
import { BaseSettings } from '../../lib/base.settings';

/**
 * Dynamic dialog for selecting a tax year when creating a new tax year node.
 * Year range: from the oldest trading day year (from backend) to the current year.
 * Opened via DialogService.open() and returns the selected year number on submit.
 */
@Component({
  template: ` <dynamic-form
    [config]="config"
    [formConfig]="formConfig"
    [translateService]="translateService"
    #form="dynamicForm"
    (submitBt)="submit($event)" />`,
  imports: [...DYNAMIC_FORM_IMPORTS]
})
export class TaxYearCreateDialogComponent extends FormBase implements OnInit {
  public translateService: TranslateService = inject(TranslateService);
  public gps: GlobalparameterService = inject(GlobalparameterService);
  private dynamicDialogConfig: DynamicDialogConfig = inject(DynamicDialogConfig);
  private dynamicDialogRef: DynamicDialogRef = inject(DynamicDialogRef);

  private readonly injector = inject(Injector);
  readonly form = viewChild(DynamicFormComponent);

  constructor() {
    super();
  }

  ngOnInit(): void {
    this.formConfig = AppHelper.getDefaultFormConfig(this.gps, 4, this.helpLink.bind(this));
    this.config = [
      DynamicFieldHelper.createFieldSelectNumberHeqF('taxYear', true),
      DynamicFieldHelper.createSubmitButton()
    ];
    this.configObject = TranslateHelper.prepareFieldsAndErrors(this.translateService, this.config);
    afterNextRender(() => this.initialize(), { injector: this.injector });
  }

  private initialize(): void {
    const oldestDate =
      sessionStorage.getItem(GlobalSessionNames.OLDEST_TRADING_DAY) ?? BaseSettings.OLDEST_TRADING_DAY_FALLBACK;
    const minYear = new Date(oldestDate).getFullYear();
    const maxYear = new Date().getFullYear();
    const yearOptions: ValueKeyHtmlSelectOptions[] = [];
    for (let y = maxYear; y >= minYear; y--) {
      yearOptions.push(new ValueKeyHtmlSelectOptions(y, String(y)));
    }
    this.configObject.taxYear.valueKeyHtmlOptions = yearOptions;
    this.form().setDefaultValuesAndEnableSubmit();
  }

  helpLink(): void {
    this.gps.toExternalHelpWebpage(this.gps.getUserLang(), HelpIds.HELP_TAX_DATA);
  }

  submit(value: { [name: string]: any }): void {
    this.dynamicDialogRef.close(value.taxYear);
  }
}
