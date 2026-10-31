import { requireInitialized } from '../../lib/helper/required-state';
import { requireFormControl } from '../../lib/dynamic-form/models/form-control-access';
import { Component, OnInit, input, inject } from '@angular/core';
import { TranslateModule } from '@ngx-translate/core';
import { AppHelper } from '../../lib/helper/app.helper';
import { CashaccountService } from '../service/cashaccount.service';
import { CallParam } from '../../shared/maintree/types/dialog.visible';
import { Cashaccount } from '../../entities/cashaccount';
import { Portfolio } from '../../entities/portfolio';
import { PortfolioService } from '../../portfolio/service/portfolio.service';
import { HelpIds } from '../../lib/help/help.ids';
import { SimpleEntityEditBase } from '../../lib/edit/simple.entity.edit.base';
import { DynamicFieldModelHelper } from '../../lib/helper/dynamic.field.model.helper';
import { FieldConfig } from '../../lib/dynamic-form/models/field.config';
import { SelectOptionsHelper } from '../../lib/helper/select.options.helper';
import { TranslateHelper } from '../../lib/helper/translate.helper';
import { AppSettings } from '../../shared/app.settings';
import { GlobalparameterGTService } from '../../gtservice/globalparameter.gt.service';
import { DialogModule } from '@openng/optimus-ui/dialog';
import { DYNAMIC_FORM_IMPORTS } from '../../lib/dynamic-form/dynamic-form.imports';

/**
 * Edit a cash account the currency of a cash account can only be changed when there is no transaction for it.
 */
@Component({
  selector: 'cashaccount-edit',
  template: ` <p-dialog
    header="{{ i18nRecord | translate }}"
    [visible]="visibleDialog()"
    [style]="{ width: '400px', minWidth: '350px', minHeight: '180px' }"
    (onShow)="onShow($event)"
    (onHide)="onHide($event)"
    [modal]="true">
    <dynamic-form
      [config]="config"
      [formConfig]="formConfig"
      [translateService]="translateService"
      #form="dynamicForm"
      (submitBt)="submit($event)" />
  </p-dialog>`,
  imports: [DialogModule, ...DYNAMIC_FORM_IMPORTS, TranslateModule]
})
export class CashaccountEditComponent extends SimpleEntityEditBase<Cashaccount> implements OnInit {
  private portfolioService: PortfolioService = inject(PortfolioService);
  private gpsGT: GlobalparameterGTService = inject(GlobalparameterGTService);

  readonly callParam = input.required<CallParam>();

  private initializedPortfolio?: Portfolio;

  get portfolio(): Portfolio {
    return requireInitialized(this.initializedPortfolio, 'CashaccountEditComponent.portfolio');
  }

  set portfolio(value: Portfolio) {
    this.initializedPortfolio = value;
  }

  constructor() {
    super(
      HelpIds.HELP_PORTFOLIO_ACCOUNT,
      AppHelper.toUpperCaseWithUnderscore(AppSettings.CASHACCOUNT),
      inject(CashaccountService)
    );
  }

  ngOnInit(): void {
    this.formConfig = AppHelper.getDefaultFormConfig(this.gps, 4, this.helpLink.bind(this));
    // The whole form (fields, order, constraints, labels) is generated from the Cashaccount entity's
    // @DynamicFormField + Bean Validation annotations. The definition is pre-fetched by the opener and
    // passed via callParam, so the form is built synchronously here and edit values transfer reliably.
    const formDefinition = this.callParam().optParam?.formDefinition;
    if (!formDefinition) {
      throw new Error('Cash account editor requires its form definition');
    }
    this.config = <FieldConfig[]>(
      DynamicFieldModelHelper.createFieldsFromClassDescriptorInputAndShow(
        this.translateService,
        formDefinition,
        '',
        true
      )
    );
    this.configObject = TranslateHelper.prepareFieldsAndErrors(this.translateService, this.config);
  }

  protected override initialize(): void {
    this.portfolio = <Portfolio>this.callParam().parentObject;
    // Initialise and transfer synchronously (config is ready from ngOnInit). Doing this inside the async
    // getCurrencies() subscription would let a later form.reset() wipe fields the user already entered
    // before the currency dropdown was populated (e.g. activeToDate).
    this.configObject.currency.inputWidth = 5;
    const callParam = this.callParam();
    if (callParam.optParam && callParam.optParam.hasTransaction) {
      requireFormControl(this.configObject.currency).disable();
    }
    this.prepareSecurityaccountOption();
    this.form().setDefaultValuesAndEnableSubmit();
    if (callParam.thisObject != null) {
      this.form().transferBusinessObjectToForm(callParam.thisObject);
    }
    this.configObject.name.elementRef?.nativeElement.focus();
    // Currency options load asynchronously and only populate the dropdown; they must not reset the form.
    this.gpsGT
      .getCurrencies()
      .pipe(this.untilDialogClosed())
      .subscribe((data) => {
        this.configObject.currency.valueKeyHtmlOptions = data;
      });
  }

  protected override getNewOrExistingInstanceBeforeSave(value: { [name: string]: any }): Cashaccount {
    const cashaccount: Cashaccount = this.copyFormToPrivateBusinessObject(
      new Cashaccount(),
      <Cashaccount>this.callParam().thisObject
    );
    cashaccount.portfolio = this.portfolio;
    return cashaccount;
  }

  private prepareSecurityaccountOption() {
    const accounts = this.portfolio.securityaccountList;
    if (accounts && accounts.length >= 2) {
      AppHelper.enableAndVisibleInput(this.configObject.connectIdSecurityaccount);
      this.configObject.connectIdSecurityaccount.valueKeyHtmlOptions =
        SelectOptionsHelper.createValueKeyHtmlSelectOptionsFromArray('idSecuritycashAccount', 'name', accounts, true);
    } else {
      AppHelper.disableAndHideInput(this.configObject.connectIdSecurityaccount);
    }
  }
}
