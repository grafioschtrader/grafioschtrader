import {Component, OnInit} from '@angular/core';
import {FormBase} from '../../lib/edit/form.base';
import {DynamicFieldHelper} from '../../lib/helper/dynamic.field.helper';
import {TranslateHelper} from '../../lib/helper/translate.helper';
import {TranslateService} from '@ngx-translate/core';
import {GlobalparameterService} from '../../lib/services/globalparameter.service';
import {DynamicDialogRef} from 'primeng/dynamicdialog';
import {AppSettings} from '../../shared/app.settings';
import {AppHelper} from '../../lib/helper/app.helper';
import {DynamicFormModule} from '../../lib/dynamic-form/dynamic-form.module';

/**
 * Minimal dynamic dialog for entering the cash account amount (credited/debited total).
 * The entered amount is used to reverse-calculate the exchange rate in the transaction form.
 * Opened via DialogService.open() and returns the entered amount on submit.
 */
@Component({
  template: `
    <dynamic-form [config]="config" [formConfig]="formConfig" [translateService]="translateService"
                  #form="dynamicForm" (submitBt)="submit($event)">
    </dynamic-form>`,
  standalone: true,
  imports: [DynamicFormModule]
})
export class CalcExRateDialogComponent extends FormBase implements OnInit {

  constructor(
    public translateService: TranslateService,
    public gps: GlobalparameterService,
    private dynamicDialogRef: DynamicDialogRef
  ) {
    super();
  }

  ngOnInit(): void {
    this.formConfig = AppHelper.getDefaultFormConfig(this.gps, 4, null, true);
    this.config = [
      DynamicFieldHelper.createFieldInputNumberHeqF('cashaccountAmount', true,
        AppSettings.FID_MAX_DIGITS - this.gps.getMaxFractionDigits(), this.gps.getMaxFractionDigits(), true),
      DynamicFieldHelper.createSubmitButton('APPLY')
    ];
    this.configObject = TranslateHelper.prepareFieldsAndErrors(this.translateService, this.config);
  }

  submit(value: { [name: string]: any }): void {
    this.dynamicDialogRef.close(value.cashaccountAmount);
  }
}
