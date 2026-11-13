import { Component, OnInit } from '@angular/core';
import { DataType } from '../../lib/dynamic-form/models/data.type';
import { SimpleDynamicFormBase } from '../../lib/edit/simple.dynamic.form.base';
import { SignalDynamicFormComponent } from '../../lib/dynamic-form/signal/signal-dynamic-form.component';
import { AppHelper } from '../../lib/helper/app.helper';
import { DynamicFieldHelper } from '../../lib/helper/dynamic.field.helper';
import { TranslateHelper } from '../../lib/helper/translate.helper';
import { HelpIds } from '../../lib/help/help.ids';

/** The only field of the dialog. */
interface FundBaseRateModel {
  fundBaseRate: number | null;
}

/**
 * Dynamic dialog in which an administrator sets the base interest rate of a tax year of Germany, in percent. The advance
 * lump sum of the investment funds needs it, and the Federal Ministry of Finance publishes it each January. The dialog
 * closes with {@code { fundBaseRate }}, where an empty value removes the rate.
 */
@Component({
  template: `
    <signal-dynamic-form
      [config]="config"
      [formConfig]="formConfig"
      [values]="startValues"
      #form="signalDynamicForm"
      (submitBt)="submit($event)" />
  `,
  imports: [SignalDynamicFormComponent]
})
export class TaxYearFundBaseRateDialogComponent
  extends SimpleDynamicFormBase<SignalDynamicFormComponent<FundBaseRateModel>>
  implements OnInit
{
  readonly startValues: Partial<FundBaseRateModel> = {
    fundBaseRate: this.dynamicDialogConfig.data?.fundBaseRate ?? null
  };

  constructor() {
    super(HelpIds.HELP_TAX_DATA);
  }

  ngOnInit(): void {
    this.formConfig = AppHelper.getDefaultFormConfig(this.gps, 4, this.helpLink.bind(this));
    this.config = [
      DynamicFieldHelper.createFieldMinMaxNumberHeqF(DataType.Numeric, 'fundBaseRate', false, -10, 20),
      DynamicFieldHelper.createSubmitButton()
    ];
    this.configObject = TranslateHelper.prepareFieldsAndErrors(this.translateService, this.config, false);
  }

  submit(value: { [name: string]: unknown }): void {
    this.dynamicDialogRef.close({ fundBaseRate: (value['fundBaseRate'] as number | null | undefined) ?? null });
  }
}
