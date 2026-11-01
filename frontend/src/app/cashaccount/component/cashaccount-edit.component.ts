import { requireInitialized } from '../../lib/helper/required-state';
import { Component, OnInit, computed, input, inject, signal } from '@angular/core';
import { disabled, hidden, metadata, SchemaPathTree } from '@angular/forms/signals';
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
import { SignalDynamicFormComponent } from '../../lib/dynamic-form/signal/signal-dynamic-form.component';
import { FIELD_DISPLAY } from '../../lib/dynamic-form/signal/signal-form.model';
import { ValueKeyHtmlSelectOptions } from '../../lib/dynamic-form/models/value.key.html.select.options';

/** The fields of the cash account form its dependency rules refer to. */
interface CashaccountFormModel {
  currency: string | null;
  connectIdSecurityaccount: number | string | null;
}

/**
 * Edit a cash account the currency of a cash account can only be changed when there is no transaction for it.
 *
 * Prototype dialog of the signal dynamic form: the currency is disabled and the security account connection is
 * hidden through schema rules, and both option lists reach their selects through FIELD_DISPLAY.
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
    <signal-dynamic-form
      [config]="config"
      [formConfig]="formConfig"
      [rules]="rules"
      [source]="callParam().thisObject"
      #form="signalDynamicForm"
      (submitBt)="submit($event)" />
  </p-dialog>`,
  imports: [DialogModule, SignalDynamicFormComponent, TranslateModule]
})
export class CashaccountEditComponent
  extends SimpleEntityEditBase<Cashaccount, SignalDynamicFormComponent<CashaccountFormModel>>
  implements OnInit
{
  private portfolioService: PortfolioService = inject(PortfolioService);
  private gpsGT: GlobalparameterGTService = inject(GlobalparameterGTService);

  readonly callParam = input.required<CallParam>();

  private initializedPortfolio?: Portfolio;

  /** Currencies, loaded asynchronously; the currency select shows them as soon as they arrive. */
  private readonly currencies = signal<ValueKeyHtmlSelectOptions[]>([]);
  /** The currency can only be changed while the cash account has no transaction. */
  private readonly hasTransaction = computed(() => !!this.callParam().optParam?.hasTransaction);
  /** Security accounts of the portfolio; a connection is only offered when there are at least two. */
  private readonly securityaccountOptions = computed(() => {
    const accounts = (<Portfolio>this.callParam().parentObject).securityaccountList;
    return accounts && accounts.length >= 2
      ? SelectOptionsHelper.createValueKeyHtmlSelectOptionsFromArray('idSecuritycashAccount', 'name', accounts, true)
      : null;
  });

  /** Dependency rules of the form; they replace the imperative disable/hide calls of the reactive form. */
  readonly rules = (p: SchemaPathTree<CashaccountFormModel>): void => {
    disabled(p.currency, { when: () => this.hasTransaction() });
    metadata(p.currency, FIELD_DISPLAY, () => ({ valueKeyHtmlOptions: this.currencies() }));
    hidden(p.connectIdSecurityaccount, { when: () => !this.securityaccountOptions() });
    metadata(p.connectIdSecurityaccount, FIELD_DISPLAY, () => ({
      valueKeyHtmlOptions: this.securityaccountOptions() ?? []
    }));
  };

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
    this.configObject = TranslateHelper.prepareFieldsAndErrors(this.translateService, this.config, false);
    this.configObject.currency.inputWidth = 5;
  }

  protected override initialize(): void {
    this.portfolio = <Portfolio>this.callParam().parentObject;
    // The form shows the edited account from its first rendering ([source]); nothing is reset here.
    this.configObject.name.elementRef?.nativeElement.focus();
    // Currency options load asynchronously and only populate the dropdown.
    this.gpsGT
      .getCurrencies()
      .pipe(this.untilDialogClosed())
      .subscribe((data) => this.currencies.set(data));
  }

  protected override getNewOrExistingInstanceBeforeSave(value: { [name: string]: any }): Cashaccount {
    const cashaccount: Cashaccount = this.copyFormToPrivateBusinessObject(
      new Cashaccount(),
      <Cashaccount>this.callParam().thisObject
    );
    cashaccount.portfolio = this.portfolio;
    return cashaccount;
  }
}
