import {Component, Input, OnInit} from '@angular/core';
import {TransactionType} from '../../shared/types/transaction.type';
import {FieldConfig} from '../../lib/dynamic-form/models/field.config';
import {Portfolio} from '../../entities/portfolio';
import {ProcessedActionData} from '../../lib/types/processed.action.data';
import {ProcessedAction} from '../../lib/types/processed.action';
import {ValidatorFn, Validators} from '@angular/forms';
import {TransactionService} from '../service/transaction.service';
import {TranslateService} from '@ngx-translate/core';
import {Cashaccount} from '../../entities/cashaccount';
import {gteWithMask, gteWithMaskIncludeNegative} from '../../lib/validator/validator';
import {Subscription} from 'rxjs';
import {Transaction} from '../../entities/transaction';
import {RuleEvent} from '../../lib/dynamic-form/error/error.message.rules';
import {TransactionCashaccountBaseOperations} from './transaction.cashaccount.base.operations';
import {PortfolioService} from '../../portfolio/service/portfolio.service';
import {AppHelper} from '../../lib/helper/app.helper';
import {GlobalparameterService} from '../../shared/service/globalparameter.service';
import {FormConfig} from '../../lib/dynamic-form/models/form.config';
import {InfoLevelType} from '../../lib/message/info.leve.type';
import {MessageToastService} from '../../lib/message/message.toast.service';
import {HelpIds} from '../../shared/help/help.ids';
import {FormDefinitionHelper} from '../../shared/edit/form.definition.helper';
import {DynamicFieldHelper} from '../../lib/helper/dynamic.field.helper';
import {BusinessHelper} from '../../shared/helper/business.helper';
import {SelectOptionsHelper} from '../../lib/helper/select.options.helper';
import {TranslateHelper} from '../../lib/helper/translate.helper';
import {AppSettings} from '../../shared/app.settings';

/**
 * Component for editing single cash account transactions where only one cash account is involved.
 * Supports deposit, withdrawal, fee, and interest transactions with dynamic form validation.
 */
@Component({
    selector: 'transaction-cashaccount-editsingle',
    template: `
    <p-dialog header="{{'SINGLE_ACCOUNT_TRANSACTION' | translate}}"
              [(visible)]="visibleCashaccountTransactionSingleDialog"
              [style]="{width: '400px'}"
              (onShow)="onShow($event)" (onHide)="onHide($event)" [modal]="true">

      <dynamic-form [config]="config" [formConfig]="formConfig" [translateService]="translateService"
                    #form="dynamicForm"
                    (submitBt)="submit($event)">
      </dynamic-form>
    </p-dialog>
  `,
    standalone: false
})
export class TransactionCashaccountEditSingleComponent extends TransactionCashaccountBaseOperations implements OnInit {

  /** Controls visibility of the cash account transaction dialog */
  @Input() visibleCashaccountTransactionSingleDialog: boolean;

  /** Form configuration object containing locale and validation settings */
  formConfig: FormConfig;

  /** Minimum allowed transaction amount */
  readonly minAmount = 0.00000001;

  /** Currency symbol for the selected cash account */
  cashaccountCurrency = ' ';

  /** Subscription for transaction type changes */
  private transactionTypeChangedSub: Subscription;

  /** Subscription for cash account changes */
  private chashaccountChangedSub: Subscription;

  /** Standard required field error configuration */
  private errorRequired = {name: 'required', keyi18n: 'required', rules: [RuleEvent.TOUCHED]};

  /**
   * Creates an instance of TransactionCashaccountEditSingleComponent.
   * @param portfolioService Service for portfolio operations
   * @param transactionService Service for transaction operations
   * @param messageToastService Service for displaying user messages
   * @param translateService Angular translation service
   * @param gps Global parameter service for application settings
   */
  constructor(private portfolioService: PortfolioService,
              private transactionService: TransactionService,
              messageToastService: MessageToastService,
              translateService: TranslateService,
              gps: GlobalparameterService) {
    super(messageToastService, null, null, translateService, gps);
  }

  /**
   * Initializes the component and sets up form configuration with dynamic field definitions.
   */
  ngOnInit(): void {
    this.formConfig = AppHelper.getDefaultFormConfig(this.gps,
      5, this.helpLink.bind(this));
    const calcGroupConfig: FieldConfig[] = [
      // Validator for amount is set dynamically
      DynamicFieldHelper.createFieldCurrencyNumberHeqF('cashaccountAmount', true,
        AppSettings.FID_MAX_INT_REAL_DOUBLE, AppSettings.FID_MAX_FRACTION_DIGITS, true, {
          ...this.gps.getNumberCurrencyMask(),
          allowZero: false
        }, true),
      this.getTransactionCostFieldDefinition()
    ];
    this.config = [
      DynamicFieldHelper.createFieldSelectStringHeqF('transactionType', true),
      FormDefinitionHelper.getTransactionTime(),
      DynamicFieldHelper.createFieldSelectNumber('idCashaccount', AppSettings.CASHACCOUNT.toUpperCase(), true,
        {dataproperty: 'cashaccount.idSecuritycashAccount'}),
      DynamicFieldHelper.createFieldSelectNumber('idSecurityaccount', AppSettings.SECURITYACCOUNT.toUpperCase(), false,
        {invisible: true}),

      DynamicFieldHelper.createFieldCurrencyNumberHeqF('taxCost', false,
        AppSettings.FID_STANDARD_INTEGER_DIGITS, AppSettings.FID_STANDARD_FRACTION_DIGITS, false,
        {...this.gps.getNumberCurrencyMask(), allowNegative: false}, true,
        {invisible: true}),
      {formGroupName: 'calcGroup', fieldConfig: calcGroupConfig},
      this.getDebitAmountFieldDefinition(),
      DynamicFieldHelper.createFieldTextareaInputStringHeqF('note', AppSettings.FID_MAX_LETTERS, false),
      DynamicFieldHelper.createSubmitButton()
    ];
    this.configObject = TranslateHelper.prepareFieldsAndErrors(this.translateService, this.config);
  }

  /**
   * Returns the visibility state of the transaction dialog.
   * @returns True if dialog should be visible
   */
  isVisibleDialog(): boolean {
    return this.visibleCashaccountTransactionSingleDialog;
  }

  /**
   * Selects the first available option for fields with single or forced selection.
   * @param fieldConfig Field configuration to potentially auto-select
   * @param force Whether to force selection even with multiple options
   */
  selectSingleOptions(fieldConfig: FieldConfig, force: boolean) {
    if (fieldConfig.valueKeyHtmlOptions.length === 1 || force && fieldConfig.valueKeyHtmlOptions.length > 0) {
      fieldConfig.formControl.setValue(fieldConfig.valueKeyHtmlOptions[0].key);
    }
  }

  /**
   * Sets up subscription for transaction type changes to adjust form validation and visibility.
   */
  valueChangedOnTransactionType() {
    this.transactionTypeChangedSub = this.configObject.transactionType.formControl.valueChanges.subscribe(
      (data: string) => this.changeTransactionType(data)
    );
  }

  /**
   * Adjusts form fields and validation based on the selected transaction type.
   * @param transactionType The selected transaction type string
   */
  private changeTransactionType(transactionType: string): void {
    switch (TransactionType[transactionType]) {
      case TransactionType.FEE:
        AppHelper.enableAndVisibleInput(this.configObject.idSecurityaccount);
        if (this.configObject.idCashaccount.formControl.value) {
          const cp: { cashaccount: Cashaccount; portfolio: Portfolio } = this.getCashaccountByIdCashaccountFromPortfolios(
            this.portfolios, +this.configObject.idCashaccount.formControl.value);
          this.prepareSecurityaccount(cp.portfolio);
        }
        this.setAmountValidator(true, gteWithMaskIncludeNegative(this.minAmount), 'gteWithMaskIncludeNegative');
        AppHelper.disableAndHideInput(this.configObject.taxCost);
        break;
      case TransactionType.INTEREST_CASHACCOUNT:
        AppHelper.disableAndHideInput(this.configObject.idSecurityaccount);
        this.setAmountValidator(true, gteWithMaskIncludeNegative(this.minAmount), 'gteWithMaskIncludeNegative');
        AppHelper.enableAndVisibleInput(this.configObject.taxCost);
        break;
      default:
        // Deposit and withdrawal
        AppHelper.disableAndHideInput(this.configObject.idSecurityaccount);
        AppHelper.disableAndHideInput(this.configObject.taxCost);
        this.setAmountValidator(false, gteWithMask(this.minAmount), 'gte');
    }
    this.configObject.cashaccountAmount.labelKey = this.configObject.transactionType.valueKeyHtmlOptions
      .find(vkho => vkho.key === transactionType).value;
    const invisibleWithdrawal = TransactionType[transactionType] !== TransactionType.WITHDRAWAL;
    AppHelper.invisibleAndHide(this.configObject.debitAmount, invisibleWithdrawal);
    AppHelper.invisibleAndHide(this.configObject.transactionCost, invisibleWithdrawal);
  }

  /**
   * Change observer on cash account is needed for the currency determination
   */
  valueChangedOnCashaccount(): void {
    this.chashaccountChangedSub = this.configObject.idCashaccount.formControl.valueChanges.subscribe((data: string) => {
      const cp: { cashaccount: Cashaccount; portfolio: Portfolio } = this.getCashaccountByIdCashaccountFromPortfolios(
        this.portfolios, +data);
      this.prepareSecurityaccount(cp.portfolio);
      this.cashaccountCurrency = cp.cashaccount.currency;
      const precision = this.gps.getCurrencyPrecision(this.cashaccountCurrency);
      this.adjustNumberInputFractions(this.configObject.cashaccountAmount, AppSettings.FID_MAX_INT_REAL_DOUBLE, precision);
      this.adjustNumberInputFractions(this.configObject.debitAmount, AppSettings.FID_MAX_INT_REAL_DOUBLE, precision);
      this.adjustNumberInputFractions(this.configObject.transactionCost, AppSettings.FID_MAX_INT_REAL_DOUBLE, precision);
      this.adjustNumberInputFractions(this.configObject.taxCost, AppSettings.FID_MAX_INT_REAL_DOUBLE, precision);
    });
  }

  /**
   * Sets up subscription for calculation field changes to update debit amount for withdrawal transactions.
   */
  valueChangedOnCalcFields(): void {
    this.valueChangedOnValueCalcFieldsSub = this.configObject.calcGroup.formControl.valueChanges.subscribe(data => {
      if (!this.configObject.transactionCost.invisible) {
        const values: any = {};
        this.form.cleanMaskAndTransferValuesToBusinessObject(values, true);
        this.configObject.debitAmount.formControl.setValue((values.cashaccountAmount + (values.transactionCost
          ? values.transactionCost : 0)));
      }
    });
  }

  /**
   * Handles dialog hide event and cleans up subscriptions.
   * @param event Dialog hide event
   */
  onHide(event) {
    this.transactionTypeChangedSub && this.transactionTypeChangedSub.unsubscribe();
    this.chashaccountChangedSub && this.chashaccountChangedSub.unsubscribe();
    super.close();
  }

  /**
   * Submits the transaction form and processes the transaction creation or update.
   * @param value Form values object
   */
  submit(value: { [name: string]: any }) {
    const transaction: Transaction = new Transaction();
    if (this.transactionCallParam.transaction) {
      Object.assign(transaction, this.transactionCallParam.transaction);
    }
    this.form.cleanMaskAndTransferValuesToBusinessObject(transaction);
    transaction.cashaccount = this.getCashaccountByIdCashaccountFromPortfolios(this.portfolios,
      transaction.idCashaccount).cashaccount;
    if (TransactionType[transaction.transactionType] === TransactionType.WITHDRAWAL) {
      transaction.cashaccountAmount = transaction.cashaccountAmount + transaction.transactionCost;
    }
    this.transactionService.updateCreateSingleCashTrans(transaction).subscribe({next: newTransaction => {
      this.messageToastService.showMessageI18n(InfoLevelType.SUCCESS, 'MSG_RECORD_SAVED',
        {i18nRecord: AppSettings.TRANSACTION.toUpperCase()});
      this.closeDialog.emit(new ProcessedActionData(transaction.idTransaction ? ProcessedAction.UPDATED
        : ProcessedAction.CREATED, newTransaction));
    }, error: () => this.configObject.submit.disabled = false});
  }

  helpLink(): void {
    BusinessHelper.toExternalHelpWebpage(this.gps.getUserLang(), HelpIds.HELP_TRANSACTION_ACCOUNT);
  }

  /**
   * Initializes component data and sets up form options and subscriptions.
   */
  protected override initialize(): void {
    this.configObject.transactionType.valueKeyHtmlOptions = SelectOptionsHelper.createHtmlOptionsFromEnum(this.translateService,
      TransactionType, [TransactionType.DEPOSIT, TransactionType.INTEREST_CASHACCOUNT,
        TransactionType.WITHDRAWAL, TransactionType.FEE]);
    this.selectSingleOptions(this.configObject.transactionType, true);
    if (this.transactionCallParam.portfolio) {
      this.getSinglePortfolioByIdPortfolio();
    } else {
      this.getAllPortfolios();
    }
  }

  /**
   * Adjusts number input field precision and currency prefix.
   * @param fieldConfig Field configuration to adjust
   * @param integerDigits Maximum number of integer digits
   * @param precision Number of decimal places
   */
  private adjustNumberInputFractions(fieldConfig: FieldConfig, integerDigits: number, precision: number): void {
    DynamicFieldHelper.setCurrency(fieldConfig, this.cashaccountCurrency);
    DynamicFieldHelper.adjustNumberFraction(fieldConfig, integerDigits,
      precision);
  }

  /**
   * Sets up all value change subscriptions for form fields.
   */
  private setValueChanged(): void {
    this.valueChangedOnCashaccount();
    this.valueChangedOnTransactionType();
    this.valueChangedOnCalcFields();
  }

  /**
   * Loads portfolio data for a single portfolio and initializes form options.
   */
  private getSinglePortfolioByIdPortfolio(): void {
    // Portfolio maybe out of date
    this.portfolioService.getPortfolioByIdPortfolio(this.transactionCallParam.portfolio.idPortfolio).subscribe((portfolio: Portfolio) => {
        // this.transactionCallParam.portfolio = portfolio;
        this.portfolios = [portfolio];
        this.configObject.idCashaccount.valueKeyHtmlOptions = this.prepareCashaccountOptions(this.portfolios);
        this.setValueChanged();
        this.setExistingTransactionToView();
      }
    );
  }

  /**
   * Loads all portfolios for the tenant and initializes form options.
   */
  private getAllPortfolios(): void {
    this.portfolioService.getPortfoliosForTenantOrderByName()
      .subscribe((data: Portfolio[]) => {
        this.portfolios = data;
        this.configObject.idCashaccount.valueKeyHtmlOptions = this.prepareCashaccountOptions(this.portfolios);
        this.setValueChanged();
        this.setExistingTransactionToView();
      });
  }

  /**
   * Sets existing transaction data to form fields or sets default values for new transactions.
   */
  private setExistingTransactionToView(): void {
    if (this.transactionCallParam.transaction === null) {
      this.configObject.idCashaccount.formControl.setValue(this.transactionCallParam.cashaccount.idSecuritycashAccount);
      this.changeTransactionType(this.configObject.transactionType.formControl.value);
    } else {
      this.form.transferBusinessObjectToForm(this.transactionCallParam.transaction);
      this.configObject.cashaccountAmount.formControl.setValue(BusinessHelper.getTotalAmountFromTransaction(
        this.transactionCallParam.transaction) - (this.transactionCallParam.transaction.transactionCost === null ? 0
        : this.transactionCallParam.transaction.transactionCost));
    }
  }

  /**
   * Sets amount field validator based on transaction type requirements.
   * @param allowNegative Whether negative values are allowed
   * @param validator Validator function to apply
   * @param validatorKey Validation error key for error messages
   */
  private setAmountValidator(allowNegative: boolean, validator: ValidatorFn, validatorKey: string) {
    this.configObject.cashaccountAmount.currencyMaskConfig.allowNegative = allowNegative;
    this.configObject.cashaccountAmount.validation = [Validators.required, validator];
    this.configObject.cashaccountAmount.formControl.setValidators(this.configObject.cashaccountAmount.validation);
    this.configObject.cashaccountAmount.errors = [this.errorRequired,
      {name: validatorKey, keyi18n: validatorKey, param1: this.minAmount, rules: ['dirty']}];
    TranslateHelper.translateMessageError(this.translateService, this.configObject.cashaccountAmount);
    this.configObject.cashaccountAmount.formControl.updateValueAndValidity();
    this.configObject.cashaccountAmount.baseInputComponent.reEvaluateRequired();
  }

  /**
   * Prepares security account options for the given portfolio.
   * @param portfolio Portfolio containing security accounts
   */
  private prepareSecurityaccount(portfolio: Portfolio) {
    if (!this.configObject.idSecurityaccount.invisible) {
      this.configObject.idSecurityaccount.valueKeyHtmlOptions =
        SelectOptionsHelper.createValueKeyHtmlSelectOptionsFromArray('idSecuritycashAccount', 'name',
          portfolio.securityaccountList, true);
      this.selectSingleOptions(this.configObject.idSecurityaccount, true);
    }

  }
}
