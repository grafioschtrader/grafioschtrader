import { requireFormControl } from '../../lib/dynamic-form/models/form-control-access';
import { afterNextRender, Component, OnInit, inject } from '@angular/core';
import { PortfolioService } from '../service/portfolio.service';
import { TranslateModule } from '@ngx-translate/core';
import { AppHelper } from '../../lib/helper/app.helper';
import { Portfolio } from '../../entities/portfolio';
import { CallParam } from '../../shared/maintree/types/dialog.visible';
import { HelpIds } from '../../lib/help/help.ids';
import { Tenant } from '../../entities/tenant';
import { DataType } from '../../lib/dynamic-form/models/data.type';
import { DynamicFieldHelper } from '../../lib/helper/dynamic.field.helper';
import { SelectOptionsHelper } from '../../lib/helper/select.options.helper';
import { TranslateHelper } from '../../lib/helper/translate.helper';
import { GlobalparameterGTService } from '../../gtservice/globalparameter.gt.service';
import { SimpleDynamicEditBase } from '../../lib/edit/simple.dynamic.edit.base';
import { DYNAMIC_FORM_IMPORTS } from '../../lib/dynamic-form/dynamic-form.imports';
import { ValueKeyHtmlSelectOptions } from '../../lib/dynamic-form/models/value.key.html.select.options';
import { forkJoin } from 'rxjs';

/**
 * Component for editing the portfolio.
 */
@Component({
  template: `
    <dynamic-form
      [config]="config"
      [formConfig]="formConfig"
      [translateService]="translateService"
      #form="dynamicForm"
      (submitBt)="submit($event)" />
  `,
  imports: [...DYNAMIC_FORM_IMPORTS, TranslateModule]
})
export class PortfolioEditDynamicComponent extends SimpleDynamicEditBase<Portfolio> implements OnInit {
  private gpsGT: GlobalparameterGTService = inject(GlobalparameterGTService);

  readonly callParam: CallParam = this.dynamicDialogConfig.data.callParam;

  constructor() {
    super(HelpIds.HELP_PORTFOLIO, inject(PortfolioService));
    afterNextRender(() => this.initializeForm());
  }

  ngOnInit(): void {
    this.formConfig = AppHelper.getDefaultFormConfig(this.gps, 6, this.helpLink.bind(this));
    this.config = [
      DynamicFieldHelper.createFieldInputString('name', 'PORTFOLIO_NAME', 25, true),
      DynamicFieldHelper.createFieldSelectStringHeqF('currency', true),
      DynamicFieldHelper.createFieldPcalendarHeqF(DataType.DateString, 'closedUntil', false, {
        calendarConfig: { minDate: new Date(2000, 0, 1) }
      }),
      DynamicFieldHelper.createFieldSelectStringHeqF('costBasisMethod', false),
      // Only a client that reports in euro and has Germany as its country uses the German tax help
      ...(this.hasGermanTaxHelp() ? [DynamicFieldHelper.createFieldSelectStringHeqF('taxWithholding', false)] : []),
      DynamicFieldHelper.createSubmitButton()
    ];
    this.configObject = TranslateHelper.prepareFieldsAndErrors(this.translateService, this.config);
  }

  /** Initializes the rendered form once; arriving currency options must not reset values already entered. */
  private initializeForm(): void {
    this.form().setDefaultValuesAndEnableSubmit();
    if (this.callParam.thisObject != null) {
      this.form().transferBusinessObjectToForm(this.callParam.thisObject);
    } else {
      requireFormControl(this.configObject.currency).setValue((<Tenant>this.callParam.parentObject).currency);
    }
    const costBasisMethod = requireFormControl(this.configObject.costBasisMethod);
    if (costBasisMethod.value == null) {
      // The empty option stands for the method of the tenant
      costBasisMethod.setValue('');
    }
    this.configObject.name.elementRef?.nativeElement.focus();
    this.loadTaxWithholdingOptions();
    forkJoin([this.gpsGT.getCostBasisMethods(), this.translateService.get('COST_BASIS_METHOD_INHERIT')])
      .pipe(this.untilDialogClosed())
      .subscribe(([methods, inherit]) => {
        // Translated in place: the helper for translated options sorts by label, the methods keep their enum order.
        this.configObject.costBasisMethod.valueKeyHtmlOptions = [
          new ValueKeyHtmlSelectOptions('', inherit),
          ...methods.map(
            (option) => new ValueKeyHtmlSelectOptions(option.key, this.translateService.instant(option.value))
          )
        ];
      });
    this.gpsGT
      .getCurrencies()
      .pipe(this.untilDialogClosed())
      .subscribe((data) => {
        this.configObject.currency.valueKeyHtmlOptions = SelectOptionsHelper.createValueKeyHtmlSelectOptionsFromArray(
          'key',
          'value',
          data,
          false
        );
      });
  }

  private hasGermanTaxHelp(): boolean {
    const tenant = <Tenant | undefined>this.callParam.parentObject;
    return tenant?.currency === 'EUR' && tenant.country === 'DE';
  }

  /** The empty option stands for a portfolio that is not assigned yet. */
  private loadTaxWithholdingOptions(): void {
    if (!this.configObject.taxWithholding) {
      return;
    }
    requireFormControl(this.configObject.taxWithholding).setValue(
      (<Portfolio | undefined>this.callParam.thisObject)?.taxWithholding ?? ''
    );
    forkJoin([this.gpsGT.getTaxWithholdings(), this.translateService.get('GERMAN_TAX_PORTFOLIO_NOT_ASSIGNED')])
      .pipe(this.untilDialogClosed())
      .subscribe(([withholdings, notAssigned]) => {
        this.configObject.taxWithholding.valueKeyHtmlOptions = [
          new ValueKeyHtmlSelectOptions('', notAssigned),
          ...withholdings.map(
            (option) => new ValueKeyHtmlSelectOptions(option.key, this.translateService.instant(option.value))
          )
        ];
      });
  }

  protected override getNewOrExistingInstanceBeforeSave(value: { [name: string]: any }): Portfolio {
    const portfolio = this.copyFormToPrivateBusinessObject(new Portfolio(), <Portfolio>this.callParam.thisObject);
    // The empty option is sent as null: the portfolio then follows the method of the tenant.
    portfolio.costBasisMethod = portfolio.costBasisMethod || null;
    portfolio.taxWithholding = portfolio.taxWithholding || null;
    return portfolio;
  }
}
