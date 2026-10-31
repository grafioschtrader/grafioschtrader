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
    this.configObject.name.elementRef?.nativeElement.focus();
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

  protected override getNewOrExistingInstanceBeforeSave(value: { [name: string]: any }): Portfolio {
    return this.copyFormToPrivateBusinessObject(new Portfolio(), <Portfolio>this.callParam.thisObject);
  }
}
