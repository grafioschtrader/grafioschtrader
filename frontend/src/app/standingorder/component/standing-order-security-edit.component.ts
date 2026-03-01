import {Component, OnInit} from '@angular/core';
import {TranslateModule, TranslateService} from '@ngx-translate/core';
import {GlobalparameterService} from '../../lib/services/globalparameter.service';
import {MessageToastService} from '../../lib/message/message.toast.service';
import {AppHelper} from '../../lib/helper/app.helper';
import {DynamicFieldHelper} from '../../lib/helper/dynamic.field.helper';
import {TranslateHelper} from '../../lib/helper/translate.helper';
import {StandingOrderSecurity} from '../../entities/standing.order';
import {Cashaccount} from '../../entities/cashaccount';
import {Security} from '../../entities/security';
import {CurrencypairWatchlist} from '../../entities/view/currencypair.watchlist';
import {StandingOrderService} from '../service/standing.order.service';
import {PortfolioService} from '../../portfolio/service/portfolio.service';
import {SelectOptionsHelper} from '../../lib/helper/select.options.helper';
import {ValueKeyHtmlSelectOptions} from '../../lib/dynamic-form/models/value.key.html.select.options';
import {ProcessedActionData} from '../../lib/types/processed.action.data';
import {DialogModule} from 'primeng/dialog';
import {DynamicFormModule} from '../../lib/dynamic-form/dynamic-form.module';
import {AppSettings} from '../../shared/app.settings';
import {DataType} from '../../lib/dynamic-form/models/data.type';
import {TransactionType} from '../../shared/types/transaction.type';
import {SupplementCriteria} from '../../securitycurrency/model/supplement.criteria';
import {
  AfterSetSecurity,
  CallBackSetSecurityWithAfter,
  SecuritycurrencySearchAndSetComponent
} from '../../securitycurrency/component/securitycurrency-search-and-set.component';
import {FieldConfig} from '../../lib/dynamic-form/models/field.config';
import {Portfolio} from '../../entities/portfolio';
import {StandingOrderEditBase} from './standing-order-edit-base';
import {AppHelpIds} from '../../shared/help/help.ids';

/** Invest mode: 0 = fixed units, 1 = fixed amount. Not persisted — derived from which field is non-null. */
enum InvestMode {
  INVEST_MODE_UNITS = 0,
  INVEST_MODE_AMOUNT = 1
}

/**
 * Dialog component for creating and editing security standing orders (ACCUMULATE/REDUCE).
 * Provides an investMode select to toggle between unit-based and amount-based mode.
 * When taxCost/transactionCost has a value, the corresponding formula field is disabled.
 * When opened from the table (no transaction context), includes a security search dialog.
 */
@Component({
  selector: 'standing-order-security-edit',
  template: `
    <p-dialog header="{{'STANDING_ORDER_SECURITY' | translate}}" [visible]="visibleDialog"
              [style]="{width: '600px'}"
              (onShow)="onShow($event)" (onHide)="onHide($event)" [modal]="true">
      <dynamic-form [config]="config" [formConfig]="formConfig" [translateService]="translateService"
                    #form="dynamicForm" (submitBt)="submit($event)">
      </dynamic-form>

      @if (visibleSetSecurityDialog) {
        <securitycurrency-search-and-set
          [visibleDialog]="visibleSetSecurityDialog"
          [supplementCriteria]="supplementCriteria"
          [callBackSetSecurityWithAfter]="this"
          (closeDialog)="handleOnCloseSetDialog($event)">
        </securitycurrency-search-and-set>
      }
    </p-dialog>
  `,
  standalone: true,
  imports: [DialogModule, DynamicFormModule, TranslateModule, SecuritycurrencySearchAndSetComponent]
})
export class StandingOrderSecurityEditComponent extends StandingOrderEditBase implements OnInit, CallBackSetSecurityWithAfter {

  visibleSetSecurityDialog = false;
  supplementCriteria = new SupplementCriteria(true, false);

  /** Security selected via the search dialog. */
  private selectedSecurity: Security | null = null;

  constructor(
    portfolioService: PortfolioService,
    translateService: TranslateService,
    gps: GlobalparameterService,
    messageToastService: MessageToastService,
    standingOrderService: StandingOrderService
  ) {
    super(portfolioService, AppHelpIds.HELP_STANDING_ORDER_SECURITY, translateService, gps, messageToastService, standingOrderService);
  }

  ngOnInit(): void {
    this.formConfig = AppHelper.getDefaultFormConfig(this.gps, 5, this.helpLink.bind(this));
    const fs = StandingOrderEditBase.FS_TRANSACTION;
    this.config = [
      DynamicFieldHelper.createFieldSelectStringHeqF('transactionType', true, {fieldsetName: fs}),
      DynamicFieldHelper.createFieldSelectNumber('idSecurityaccount', AppSettings.SECURITYACCOUNT.toUpperCase(), true,
        {fieldsetName: fs}),
      DynamicFieldHelper.createFieldSelectNumber('idCashaccount', AppSettings.CASHACCOUNT.toUpperCase(), true,
        {dataproperty: 'cashaccount.idSecuritycashAccount', fieldsetName: fs}),
      DynamicFieldHelper.createFieldInputButtonHeqF(DataType.String, 'securityName',
        this.handleSecuritySearchClick.bind(this), true, {fieldsetName: fs}),
      DynamicFieldHelper.createFieldSelectNumberHeqF('investMode', true, {fieldsetName: fs}),
      DynamicFieldHelper.createFieldInputNumberHeqF('units', false,
        AppSettings.FID_STANDARD_INTEGER_DIGITS, AppSettings.FID_MAX_DIGITS - AppSettings.FID_STANDARD_INTEGER_DIGITS, false,
        {fieldsetName: fs}),
      DynamicFieldHelper.createFieldInputNumberHeqF('investAmount', false,
        AppSettings.FID_STANDARD_INTEGER_DIGITS, AppSettings.FID_MAX_DIGITS - AppSettings.FID_STANDARD_INTEGER_DIGITS, false,
        {fieldsetName: fs}),
      DynamicFieldHelper.createFieldCheckboxHeqF('amountIncludesCosts', {fieldsetName: fs}),
      DynamicFieldHelper.createFieldCheckboxHeqF('fractionalUnits', {fieldsetName: fs}),
      DynamicFieldHelper.createFieldInputNumberHeqF('taxCost', false,
        AppSettings.FID_STANDARD_INTEGER_DIGITS, AppSettings.FID_MAX_DIGITS - AppSettings.FID_STANDARD_INTEGER_DIGITS, false,
        {fieldsetName: fs}),
      DynamicFieldHelper.createFieldInputStringHeqF('taxCostFormula', 200, false,
        {fieldsetName: fs, labelHelpText: 'TAX_COST_FORMULA_TOOLTIP'}),
      DynamicFieldHelper.createFieldInputNumberHeqF('transactionCost', false,
        AppSettings.FID_STANDARD_INTEGER_DIGITS, AppSettings.FID_MAX_DIGITS - AppSettings.FID_STANDARD_INTEGER_DIGITS, false,
        {fieldsetName: fs}),
      DynamicFieldHelper.createFieldInputStringHeqF('transactionCostFormula', 200, false,
        {fieldsetName: fs, labelHelpText: 'TRANSACTION_COST_FORMULA_TOOLTIP'}),
      ...StandingOrderEditBase.createSchedulingFields()
    ];
    this.configObject = TranslateHelper.prepareFieldsAndErrors(this.translateService, this.config);
  }

  /** Populates the securityaccount select from loaded portfolios. Auto-selects if only one exists. */
  protected override afterPortfoliosLoaded(): void {
    const securityaccountsHtmlOptions: ValueKeyHtmlSelectOptions[] = [];
    for (const portfolio of this.portfolios) {
      if (portfolio.securityaccountList) {
        portfolio.securityaccountList.forEach(sa => securityaccountsHtmlOptions.push(
          new ValueKeyHtmlSelectOptions(sa.idSecuritycashAccount,
            `${sa.name} / ${portfolio.name}`)));
      }
    }
    this.configObject.idSecurityaccount.valueKeyHtmlOptions = securityaccountsHtmlOptions;
    if (securityaccountsHtmlOptions.length === 1) {
      this.configObject.idSecurityaccount.formControl.setValue(securityaccountsHtmlOptions[0].key);
      this.configObject.idSecurityaccount.formControl.disable();
    }
    this.setupSecurityaccountChangeListener();
  }

  protected override initializeTransactionTypeOptions(): void {
    this.configObject.transactionType.valueKeyHtmlOptions = SelectOptionsHelper.createHtmlOptionsFromEnum(
      this.translateService, TransactionType, [TransactionType.ACCUMULATE, TransactionType.REDUCE]);
    this.configObject.investMode.valueKeyHtmlOptions = SelectOptionsHelper.createHtmlOptionsFromEnum(
      this.translateService, InvestMode);
    this.setupInvestModeListener();
    this.setupCostFormulaListeners();
  }

  protected override setExistingValues(): void {
    const so = this.callParam?.standingOrder as StandingOrderSecurity;
    if (so) {
      if (so.idSecurityaccount) {
        this.configObject.idSecurityaccount.formControl.setValue(so.idSecurityaccount);
      }
      this.form.transferBusinessObjectToForm(so);
      if (so.security) {
        this.selectedSecurity = so.security as Security;
        this.configObject.securityName.formControl.setValue(so.security.name);
      }
      // Derive investMode from existing data
      const mode = so.investAmount != null ? InvestMode.INVEST_MODE_AMOUNT : InvestMode.INVEST_MODE_UNITS;
      this.configObject.investMode.formControl.setValue(InvestMode[mode]);
      this.applyInvestMode(mode);
    } else if (this.callParam?.transaction) {
      const t = this.callParam.transaction;
      this.configObject.transactionType.formControl.setValue(t.transactionType);
      if (t.idSecurityaccount) {
        this.configObject.idSecurityaccount.formControl.setValue(t.idSecurityaccount);
      }
      if (t.cashaccount) {
        this.configObject.idCashaccount.formControl.setValue(t.cashaccount.idSecuritycashAccount);
      }
      if (t.security) {
        this.selectedSecurity = t.security;
        this.configObject.securityName.formControl.setValue(t.security.name);
      }
      if (t.units != null) {
        this.configObject.investMode.formControl.setValue(InvestMode[InvestMode.INVEST_MODE_UNITS]);
        this.configObject.units.formControl.setValue(Math.abs(t.units));
      }
      const investModeNumeric = StandingOrderEditBase.enumFormValueToNumeric(InvestMode,
        this.configObject.investMode.formControl.value);
      this.applyInvestMode(investModeNumeric ?? InvestMode.INVEST_MODE_UNITS);
    } else {
      // Default to units mode for new entries
      this.configObject.investMode.formControl.setValue(InvestMode[InvestMode.INVEST_MODE_UNITS]);
      this.applyInvestMode(InvestMode.INVEST_MODE_UNITS);
    }
    this.applyCostFormulaState();
    // Apply field locking LAST — after all state adjustments, so nothing re-enables locked fields
    if (so) {
      this.disableFieldsForEdit(so);
    }
  }

  /** Opens the security search dialog when the security name button is clicked. */
  handleSecuritySearchClick(fieldConfig: FieldConfig): void {
    this.visibleSetSecurityDialog = true;
  }

  /** Called by SecuritycurrencySearchAndSetComponent when a security is selected. */
  setSecurity(security: Security | CurrencypairWatchlist, afterSetSecurity: AfterSetSecurity): void {
    afterSetSecurity.afterSetSecurity();
    this.selectedSecurity = security as Security;
    this.configObject.securityName.formControl.setValue(security.name);
  }

  /** Called when the security search dialog is closed without selection. */
  handleOnCloseSetDialog(processedActionData: ProcessedActionData): void {
    this.visibleSetSecurityDialog = false;
  }

  protected override getNewOrExistingInstanceBeforeSave(value: { [name: string]: any }): StandingOrderSecurity {
    const so = new StandingOrderSecurity();
    const existing = this.callParam?.standingOrder as StandingOrderSecurity;
    this.copyFormToPrivateBusinessObject(so, existing);
    so.dtype = 'S';
    // Clear the field not selected by investMode
    const mode = StandingOrderEditBase.enumFormValueToNumeric(InvestMode,
      this.configObject.investMode.formControl.value);
    if (mode === InvestMode.INVEST_MODE_UNITS) {
      so.investAmount = null;
      so.amountIncludesCosts = false;
    } else {
      so.units = null;
    }
    // Clear formula when fixed cost is set
    if (so.taxCost != null) {
      so.taxCostFormula = null;
    }
    if (so.transactionCost != null) {
      so.transactionCostFormula = null;
    }
    // Remove non-entity field
    delete (so as any).investMode;
    delete (so as any).securityName;
    // Wrap cashaccount ID into Cashaccount object for Jackson deserialization
    so.cashaccount = {idSecuritycashAccount: +this.configObject.idCashaccount.formControl.value} as Cashaccount;
    // Set security from search dialog selection, existing entity, or transaction
    if (this.selectedSecurity) {
      so.security = this.selectedSecurity;
    }
    // idSecurityaccount comes from the form select (which may be disabled with a single value)
    so.idSecurityaccount = +this.configObject.idSecurityaccount.formControl.value;
    return so;
  }

  /**
   * Subscribes to investMode changes and toggles between units and investAmount+fractionalUnits fields.
   */
  private setupInvestModeListener(): void {
    this.configObject.investMode.formControl.valueChanges.subscribe((mode: any) => {
      const numericMode = StandingOrderEditBase.enumFormValueToNumeric(InvestMode, mode);
      this.applyInvestMode(numericMode ?? InvestMode.INVEST_MODE_UNITS);
    });
  }

  /**
   * Enables/disables fields based on the selected invest mode.
   * Units mode: units enabled, investAmount/amountIncludesCosts/fractionalUnits disabled.
   * Amount mode: investAmount/amountIncludesCosts/fractionalUnits enabled, units disabled.
   */
  private applyInvestMode(mode: number): void {
    if (mode === InvestMode.INVEST_MODE_UNITS) {
      this.configObject.units.formControl.enable();
      this.configObject.investAmount.formControl.disable();
      this.configObject.amountIncludesCosts.formControl.disable();
      this.configObject.fractionalUnits.formControl.disable();
    } else {
      this.configObject.units.formControl.disable();
      this.configObject.investAmount.formControl.enable();
      this.configObject.amountIncludesCosts.formControl.enable();
      this.configObject.fractionalUnits.formControl.enable();
    }
  }

  /**
   * Subscribes to taxCost and transactionCost value changes to disable the corresponding formula field
   * when a fixed cost value is entered.
   */
  private setupCostFormulaListeners(): void {
    this.configObject.taxCost.formControl.valueChanges.subscribe(() => this.applyCostFormulaState());
    this.configObject.transactionCost.formControl.valueChanges.subscribe(() => this.applyCostFormulaState());
  }

  /**
   * Subscribes to idSecurityaccount changes and updates the cashaccount options
   * to show only cash accounts from the same portfolio as the selected security account.
   */
  private setupSecurityaccountChangeListener(): void {
    this.configObject.idSecurityaccount.formControl.valueChanges.subscribe((idSecurityaccount: number) => {
      this.updateCashaccountOptions(+idSecurityaccount);
    });
    const currentValue = this.configObject.idSecurityaccount.formControl.value;
    if (currentValue != null) {
      this.updateCashaccountOptions(+currentValue);
    } else {
      this.configObject.idCashaccount.valueKeyHtmlOptions = [];
    }
  }

  /** Updates cashaccount options based on the selected security account's portfolio. */
  private updateCashaccountOptions(idSecurityaccount: number): void {
    const portfolio = this.getPortfolioByIdSecurityaccount(idSecurityaccount);
    if (portfolio?.cashaccountList) {
      const cashaccountsHtmlOptions: ValueKeyHtmlSelectOptions[] = portfolio.cashaccountList.map(ca =>
        new ValueKeyHtmlSelectOptions(ca.idSecuritycashAccount,
          `${ca.name} / ${ca.currency} / ${portfolio.name}`)
      );
      this.configObject.idCashaccount.valueKeyHtmlOptions = cashaccountsHtmlOptions;
      if (cashaccountsHtmlOptions.length === 1) {
        this.configObject.idCashaccount.formControl.setValue(cashaccountsHtmlOptions[0].key);
      }
    } else {
      this.configObject.idCashaccount.valueKeyHtmlOptions = [];
      this.configObject.idCashaccount.formControl.setValue(null);
    }
  }

  /** Finds the portfolio containing the specified security account. */
  private getPortfolioByIdSecurityaccount(idSecurityaccount: number): Portfolio | null {
    for (const portfolio of this.portfolios) {
      if (portfolio.securityaccountList) {
        for (const sa of portfolio.securityaccountList) {
          if (sa.idSecuritycashAccount === idSecurityaccount) {
            return portfolio;
          }
        }
      }
    }
    return null;
  }

  /**
   * Disables the formula field when the corresponding fixed cost field has a value, and vice versa.
   */
  private applyCostFormulaState(): void {
    const taxCostValue = this.configObject.taxCost.formControl.value;
    if (taxCostValue != null && taxCostValue !== '' && taxCostValue !== 0) {
      this.configObject.taxCostFormula.formControl.disable();
    } else {
      this.configObject.taxCostFormula.formControl.enable();
    }
    const txnCostValue = this.configObject.transactionCost.formControl.value;
    if (txnCostValue != null && txnCostValue !== '' && txnCostValue !== 0) {
      this.configObject.transactionCostFormula.formControl.disable();
    } else {
      this.configObject.transactionCostFormula.formControl.enable();
    }
  }
}
