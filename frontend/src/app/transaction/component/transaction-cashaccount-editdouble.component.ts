import {Component, Input, OnInit} from '@angular/core';
import {FieldConfig} from '../../lib/dynamic-form/models/field.config';
import {TranslateModule, TranslateService} from '@ngx-translate/core';
import {CashAccountTransfer, TransactionService} from '../service/transaction.service';
import {ProcessedActionData} from '../../lib/types/processed.action.data';
import {ProcessedAction} from '../../lib/types/processed.action';
import {TransactionCashaccountBaseOperations} from './transaction.cashaccount.base.operations';
import {GlobalparameterService} from '../../lib/services/globalparameter.service';
import {Portfolio} from '../../entities/portfolio';
import {PortfolioService} from '../../portfolio/service/portfolio.service';
import {ValueKeyHtmlSelectOptions} from '../../lib/dynamic-form/models/value.key.html.select.options';
import {Subscription} from 'rxjs';
import {Cashaccount} from '../../entities/cashaccount';
import {Transaction} from '../../entities/transaction';
import {TransactionType} from '../../shared/types/transaction.type';
import {AppHelper} from '../../lib/helper/app.helper';
import {Helper} from '../../lib/helper/helper';
import {CurrencypairService} from '../../securitycurrency/service/currencypair.service';
import {InfoLevelType} from '../../lib/message/info.leve.type';
import {MessageToastService} from '../../lib/message/message.toast.service';
import {FormConfig} from '../../lib/dynamic-form/models/form.config';
import {HistoryquoteService} from '../../historyquote/service/historyquote.service';
import {HelpIds} from '../../lib/help/help.ids';
import {FormDefinitionHelper} from '../../shared/edit/form.definition.helper';
import {DynamicFieldHelper} from '../../lib/helper/dynamic.field.helper';
import {BusinessHelper} from '../../shared/helper/business.helper';
import {TranslateHelper} from '../../lib/helper/translate.helper';
import {AppSettings} from '../../shared/app.settings';
import {BaseSettings} from '../../lib/base.settings';
import {GlobalparameterGTService} from '../../gtservice/globalparameter.gt.service';
import {gtDate} from '../../lib/validator/validator';
import {Validators} from '@angular/forms';
import {RuleEvent} from '../../lib/dynamic-form/error/error.message.rules';
import moment from 'moment';
import {DialogModule} from 'primeng/dialog';
import {DynamicFormModule} from '../../lib/dynamic-form/dynamic-form.module';

/**
 * Cash transfer between two cash accounts which are managed by this application.
 */
@Component({
  selector: 'transaction-cashaccount-editdouble',
  template: `
    <p-dialog header="{{'ACCOUNT_TRANSFER' | translate}}" [(visible)]="visibleCashaccountTransactionDoubleDialog"
              [style]="{width: '550px'}"
              (onShow)="onShow($event)" (onHide)="onHide($event)" [modal]="true">

      @if (transactionLocked) {
        <div class="alert alert-warning alert-dialog-wrap">{{ transactionLockedMessage }}</div>
      }

      <dynamic-form [config]="config" [formConfig]="formConfig" [translateService]="translateService"
                    #form="dynamicForm"
                    (submitBt)="submit($event)">
      </dynamic-form>
    </p-dialog>
  `,
  standalone: true,
  imports: [
    DialogModule,
    DynamicFormModule,
    TranslateModule
  ]
})
export class TransactionCashaccountEditDoubleComponent extends TransactionCashaccountBaseOperations implements OnInit {

  // InputMask from parent view
  @Input() visibleCashaccountTransactionDoubleDialog: boolean;
  // @Input() transactionCallParam: TransactionCallParam;

  // Access the form
//  @ViewChild(DynamicFormComponent) form: DynamicFormComponent;

  formConfig: FormConfig;

  // The first is the withdrawal(debit) and the 2nd is the deposit(credit)
  transactions: Transaction[] = [];

  readonly WithIndex: number = 0;
  readonly DepIndex: number = 1;

  debitCashaccount: Cashaccount;
  creditCashaccount: Cashaccount;

  // Portfolio references for closedUntil calculation
  private debitPortfolio: Portfolio;
  private creditPortfolio: Portfolio;

  // Observer subscribe
  private transactionTimeChangeSub: Subscription;
  private debitChashaccountChangedSub: Subscription;
  private creditChashaccountChangedSub: Subscription;

  constructor(private portfolioService: PortfolioService,
    private transactionService: TransactionService,
    private gpsGT: GlobalparameterGTService,
    messageToastService: MessageToastService,
    currencypairService: CurrencypairService,
    historyquoteService: HistoryquoteService,
    translateService: TranslateService,
    gps: GlobalparameterService) {
    super(messageToastService, currencypairService, historyquoteService, translateService, gps);
  }

  ngOnInit(): void {
    this.formConfig = AppHelper.getDefaultFormConfig(this.gps,
      4, this.helpLink.bind(this));

    // When the input on the following group changes, some calculations must be executed
    const calcGroupConfig: FieldConfig[] = [
      DynamicFieldHelper.createFieldInputNumber('currencyExRate', 'EXCHANGE_RATE', true,
        this.gps.getMaxFractionDigits() - 2, this.gps.getMaxFractionDigits(), false,
        {usedLayoutColumns: 8}),
      ...this.createExRateButtons(),
      DynamicFieldHelper.createFieldInputNumberHeqF('creditAmount', true,
        AppSettings.FID_MAX_INT_REAL_DOUBLE, this.gps.getMaxFractionDigits(), false),
      this.getTransactionCostFieldDefinition(this.gps),
    ];

    this.config = [
      FormDefinitionHelper.getTransactionTime(),
      DynamicFieldHelper.createFieldSelectNumber('idDebitCashaccount', 'DEBIT_ACCOUNT', true),
      DynamicFieldHelper.createFieldSelectNumber('idCreditCashaccount', 'CREDIT_ACCOUNT', true),
      {formGroupName: 'calcGroup', fieldConfig: calcGroupConfig},
      this.getDebitAmountFieldDefinition(this.gps),
      DynamicFieldHelper.createFieldTextareaInputStringHeqF('note', BaseSettings.FID_MAX_LETTERS, false),
      DynamicFieldHelper.createSubmitButton()
    ];
    this.configObject = TranslateHelper.prepareFieldsAndErrors(this.translateService, this.config);
  }

  isVisibleDialog(): boolean {
    return this.visibleCashaccountTransactionDoubleDialog;
  }

  ////////////////////////////////////////////////
  onHide(event): void {
    this.debitChashaccountChangedSub && this.debitChashaccountChangedSub.unsubscribe();
    this.creditChashaccountChangedSub && this.creditChashaccountChangedSub.unsubscribe();
    this.transactionTimeChangeSub && this.transactionTimeChangeSub.unsubscribe();
    super.close();
  }

  valueChangedOnCalcFields(): void {
    this.valueChangedOnValueCalcFieldsSub = this.configObject.calcGroup.formControl.valueChanges.subscribe(data => {
      this.setCalculatedDebitAmount();
    });
  }

  setCalculatedDebitAmount(): number {
    const values: any = {};
    this.form.cleanMaskAndTransferValuesToBusinessObject(values, true);

    if (this.currencypair) {
      const debitAmount = this.calcDebitAmount(values);
      this.configObject.debitAmount.formControl.setValue(debitAmount);

    } else {
      this.configObject.debitAmount.formControl.setValue(values.creditAmount + (values.transactionCost
        ? values.transactionCost : 0));
    }
    return values.currencyExRate;
  }

  calcDebitAmount(values: any): number {
    return BusinessHelper.divideMultiplyExchangeRate(values.creditAmount, values.currencyExRate,
      this.debitCashaccount.currency, this.currencypair) + ((values.transactionCost) ? values.transactionCost : 0);
  }

  valueChangedOnTransactionTime() {
    this.transactionTimeChangeSub = this.configObject.transactionTime.formControl.valueChanges.subscribe(() =>
      this.disableEnableExchangeRateButtons());
  }

  valueChangedOnDebitCashaccount(): void {
    this.debitChashaccountChangedSub = this.configObject.idDebitCashaccount.formControl.valueChanges.subscribe((data: string) => {
      if (Helper.hasValue(data)) {
        const cp: { cashaccount: Cashaccount; portfolio: Portfolio } = this.getCashaccountByIdCashaccountFromPortfolios(
          this.portfolios, +data);
        this.debitCashaccount = cp.cashaccount;
        this.debitPortfolio = cp.portfolio;
        this.updateTransactionTimeMinDateFromBothPortfolios();
        this.filterCreditHtmlOptions(this.configObject.idDebitCashaccount.valueKeyHtmlOptions,
          cp.cashaccount.idSecuritycashAccount);
        this.configObject.debitAmount.inputNumberSettings.currency = this.debitCashaccount.currency;
        this.configObject.transactionCost.inputNumberSettings.currency = this.debitCashaccount.currency;
        this.changeCurrencyExRateState();
      }
    });
  }

  /**
   * When cash credit account changes the currency of the must be changed as well
   */
  valueChangedOnCreditCashaccount(): void {
    this.creditChashaccountChangedSub = this.configObject.idCreditCashaccount.formControl.valueChanges.subscribe((data: string) => {
      if (data) {
        const cp: { cashaccount: Cashaccount; portfolio: Portfolio } = this.getCashaccountByIdCashaccountFromPortfolios(
          this.portfolios, +data);
        this.creditCashaccount = cp.cashaccount;
        this.creditPortfolio = cp.portfolio;
        this.updateTransactionTimeMinDateFromBothPortfolios();
        // this.configObject.creditAmount.currencyMaskConfig.prefix = AppHelper.addSpaceToCurrency(this.creditCashaccount.currency);
        this.configObject.creditAmount.inputNumberSettings.currency = this.creditCashaccount.currency;
        this.changeCurrencyExRateState();
      }
    });
  }

  helpLink(): void {
    this.gps.toExternalHelpWebpage(this.gps.getUserLang(), HelpIds.HELP_TRANSACTION_ACCOUNT);
  }

  submit(value: { [name: string]: any }) {
    let cashAccountTransfer: CashAccountTransfer = this.createOrCopyExistingTransaction();
    cashAccountTransfer = this.viewToBusinessModel(cashAccountTransfer);
    this.saveTransaction(cashAccountTransfer);
  }

  saveTransaction(cashAccountTransfer: CashAccountTransfer) {
    this.transactionService.updateCreateDoubleTransaction(cashAccountTransfer).subscribe({
      next: (newCashAccountTransfer: CashAccountTransfer) => {
        this.messageToastService.showMessageI18n(InfoLevelType.SUCCESS, 'MSG_RECORD_SAVED',
          {i18nRecord: AppSettings.TRANSACTION.toUpperCase()});
        this.closeDialog.emit(new ProcessedActionData(cashAccountTransfer.depositTransaction.idTransaction ? ProcessedAction.UPDATED
          : ProcessedAction.CREATED, [newCashAccountTransfer.withdrawalTransaction, newCashAccountTransfer.depositTransaction]));
      }, error: () => this.configObject.submit.disabled = false
    });
  }

  protected initialize(): void {
    this.valueChangedOnTransactionTime();
    this.portfolioService.getPortfoliosForTenantOrderByName()
      .subscribe((data: Portfolio[]) => {
        this.portfolios = data;
        this.disableCurrencyExRateState();

        if (this.transactionCallParam.transaction) {
          this.setupModifyExistingTransaction(this.transactionCallParam.transaction);
        } else {
          this.setupCashaccountSelect(this.transactionCallParam.cashaccount.idSecuritycashAccount, null);
        }
      });
  }

  private adjustNumberInputFractions(): void {
    const fromCurrencyFraction = this.gpsGT.getCurrencyPrecision(this.currencypair ?
      this.currencypair.fromCurrency : this.creditCashaccount.currency);
    const toCurrencyFraction = this.gpsGT.getCurrencyPrecision(this.currencypair ? this.currencypair.toCurrency :
      this.debitCashaccount.currency);
    DynamicFieldHelper.adjustNumberFraction(this.configObject.creditAmount, AppSettings.FID_MAX_INT_REAL_DOUBLE,
      toCurrencyFraction);
    DynamicFieldHelper.adjustNumberFraction(this.configObject.transactionCost, AppSettings.FID_SMALL_INTEGER_LIMIT,
      fromCurrencyFraction);
    DynamicFieldHelper.adjustNumberFraction(this.configObject.debitAmount, AppSettings.FID_MAX_INT_REAL_DOUBLE,
      fromCurrencyFraction);
  }

  private getCurrencypair() {
    this.currencypairService.findOrCreateCurrencypairByFromAndToCurrency(this.currencypair.fromCurrency,
      this.currencypair.toCurrency).subscribe(currencypair => {
      this.currencypair = currencypair;
      if (this.hasChangedOnExistingTransaction()) {
        BusinessHelper.getAndSetQuotationCurrencypair(this.currencypairService, this.currencypair,
          +this.configObject.transactionTime.formControl.value, this.configObject.currencyExRate.formControl);
      }
    });
  }

  private setupCashaccountSelect(debitIdCashaccount: number, creditIdCashaccount: number) {
    this.configObject.idDebitCashaccount.valueKeyHtmlOptions = this.prepareCashaccountOptions(this.portfolios);
    this.filterCreditHtmlOptions(this.configObject.idDebitCashaccount.valueKeyHtmlOptions,
      creditIdCashaccount);
    this.valueChangedOnDebitCashaccount();
    this.valueChangedOnCreditCashaccount();
    // Debit account is always set
    this.configObject.idDebitCashaccount.formControl.setValue(debitIdCashaccount);
    if (creditIdCashaccount) {
      this.configObject.idCreditCashaccount.formControl.setValue(creditIdCashaccount);
    }
    this.valueChangedOnCalcFields();
  }

  private setupModifyExistingTransaction(transaction1: Transaction) {
    this.transactionService.getTransactionByIdTransaction(transaction1.connectedIdTransaction).subscribe(data => {
      this.transactions = [];
      this.transactions.push(transaction1);
      if (TransactionType[data.transactionType] === TransactionType.WITHDRAWAL) {
        this.transactions.unshift(data);
      } else {
        this.transactions.push(data);
      }
      this.setFormValues();
    });
  }

  private setFormValues(): void {
    this.configObject.transactionTime.formControl.setValue(new Date(this.transactions[this.WithIndex].transactionTime));
    this.configObject.currencyExRate.formControl.setValue(this.transactions[this.WithIndex].currencyExRate);
    this.configObject.transactionCost.formControl.setValue(this.transactions[this.WithIndex].transactionCost);
    this.configObject.creditAmount.formControl.setValue(this.transactions[this.DepIndex].cashaccountAmount);
    this.configObject.note.formControl.setValue(this.transactions[this.WithIndex].note);

    this.setupCashaccountSelect(this.transactions[this.WithIndex].cashaccount.idSecuritycashAccount,
      this.transactions[this.DepIndex].cashaccount.idSecuritycashAccount);
    this.setCalculatedDebitAmount();
  }

  /**
   * Enable or disable input for currency exchange rate.
   */
  private changeCurrencyExRateState(): void {
    if (this.debitCashaccount && this.creditCashaccount) {
      this.setCurrencypair();
      if (this.debitCashaccount.currency === this.creditCashaccount.currency) {
        this.configObject.currencyExRate.formControl.setValue(1);
        this.disableCurrencyExRateState();
      } else {
        // this.configObject.currencyExRate.labelSuffix = this.currencypair.toStringFN();
        this.configObject.currencyExRate.labelSuffix = this.currencypair.name;
        this.configObject.currencyExRate.formControl.enable();
        this.getCurrencypair();
      }
      this.adjustNumberInputFractions();
    }
    this.disableEnableExchangeRateButtons();
  }

  private setCurrencypair(): void {
    if (this.transactionCallParam.transaction
      && this.transactions[this.DepIndex].cashaccount.currency === this.debitCashaccount.currency
      && this.transactions[this.WithIndex].cashaccount.currency === this.creditCashaccount.currency) {
      this.currencypair = this.transactionCallParam.transaction.currencypair;
    } else {
      this.currencypair = BusinessHelper.getCurrencypairWithSetOfFromAndTo(this.creditCashaccount.currency,
        this.debitCashaccount.currency);
    }
  }

  /**
   * Return true when exchange rate can be adjusted
   */
  private hasChangedOnExistingTransaction(): boolean {
    const t = this.transactionCallParam.transaction;
    return t === null || t !== null &&
      (this.configObject.idCreditCashaccount.formControl.value &&
        this.transactions[this.DepIndex].cashaccount.idSecuritycashAccount !== this.configObject.idCreditCashaccount.formControl.value
        || this.transactions[this.WithIndex].cashaccount.idSecuritycashAccount &&
        this.transactions[this.WithIndex].cashaccount.idSecuritycashAccount !== this.configObject.idDebitCashaccount.formControl.value);
  }

  private disableCurrencyExRateState(): void {
    // this.configObject.currencyExRate.labelSuffix = '';
    this.configObject.currencyExRate.formControl.disable();
  }

  private filterCreditHtmlOptions(debitHtmlOptions: ValueKeyHtmlSelectOptions[], idCashaccountCredit: number): void {
    const valueKeyHtmlOptions = debitHtmlOptions.filter(debitHtmlOption =>
      debitHtmlOption.key !== idCashaccountCredit);

    this.configObject.idCreditCashaccount.valueKeyHtmlOptions = valueKeyHtmlOptions;
    this.configObject.idCreditCashaccount.valueKeyHtmlOptions.unshift(new ValueKeyHtmlSelectOptions('', ''));
    if (valueKeyHtmlOptions.length === 2) {
      this.configObject.idCreditCashaccount.formControl.setValue(valueKeyHtmlOptions[1].key);
    } else {
      this.configObject.idCreditCashaccount.formControl.setValue('');
    }
  }

  private createOrCopyExistingTransaction(): CashAccountTransfer {
    const newTransactions: Transaction[] = [];
    for (let i = 0; i < 2; i++) {
      newTransactions.push(new Transaction());
      if (this.transactionCallParam.transaction) {
        Object.assign(newTransactions[i], this.transactions[i]);
      } else {
        newTransactions[i].transactionType = TransactionType[(i === this.WithIndex)
          ? TransactionType.WITHDRAWAL : TransactionType.DEPOSIT];
      }
      this.form.cleanMaskAndTransferValuesToBusinessObject(newTransactions[i]);
    }
    return new CashAccountTransfer(newTransactions[0], newTransactions[1]);
  }

  private viewToBusinessModel(cashAccountTransfer: CashAccountTransfer): CashAccountTransfer {
    const values: any = {};
    this.form.cleanMaskAndTransferValuesToBusinessObject(values, true);
    cashAccountTransfer.withdrawalTransaction.cashaccount = this.getCashaccountByIdCashaccountFormPortfolios(this.portfolios,
      values.idDebitCashaccount).cashaccount;
    cashAccountTransfer.withdrawalTransaction.transactionCost = values.transactionCost;
    cashAccountTransfer.depositTransaction.cashaccount = this.getCashaccountByIdCashaccountFormPortfolios(this.portfolios,
      values.idCreditCashaccount).cashaccount;
    cashAccountTransfer.depositTransaction.cashaccountAmount = values.creditAmount;
    if (this.currencypair != null) {
      cashAccountTransfer.withdrawalTransaction.idCurrencypair = this.currencypair.idSecuritycurrency;
      cashAccountTransfer.depositTransaction.idCurrencypair = this.currencypair.idSecuritycurrency;
    }
    cashAccountTransfer.withdrawalTransaction.cashaccountAmount = this.calcDebitAmount(values);
    return cashAccountTransfer;
  }

  /**
   * Updates the transaction time minDate using the most restrictive closedUntil from both portfolios.
   * For double account transfers, transactions must respect both portfolios' closed periods.
   * Also checks if existing transaction is within the closed period and updates the date validator.
   */
  private updateTransactionTimeMinDateFromBothPortfolios(): void {
    const debitClosedUntil = FormDefinitionHelper.getEffectiveClosedUntil(this.debitPortfolio, this.gpsGT);
    const creditClosedUntil = FormDefinitionHelper.getEffectiveClosedUntil(this.creditPortfolio, this.gpsGT);

    // Use the later (more restrictive) closedUntil date
    let effectiveClosedUntil: Date | null = null;
    if (debitClosedUntil && creditClosedUntil) {
      effectiveClosedUntil = debitClosedUntil > creditClosedUntil ? debitClosedUntil : creditClosedUntil;
    } else {
      effectiveClosedUntil = debitClosedUntil || creditClosedUntil;
    }

    FormDefinitionHelper.updateTransactionTimeMinDate(this.configObject.transactionTime, effectiveClosedUntil);

    // Update validator to enforce date restriction even for already-entered dates
    this.updateTransactionTimeValidator(effectiveClosedUntil);

    // Check if existing transaction is within closed period
    if (this.transactionCallParam.transaction) {
      this.checkTransactionLocked(effectiveClosedUntil, this.transactionCallParam.transaction.transactionTime);
    }
  }

  /**
   * Updates the transactionTime form control validators to include the gtDate validator
   * with the current effectiveClosedUntil date. Also updates the error message rule with
   * the formatted date for proper error display.
   */
  private updateTransactionTimeValidator(effectiveClosedUntil: Date | null): void {
    const formControl = this.configObject.transactionTime.formControl;
    const fieldConfig = this.configObject.transactionTime;

    // Remove existing gtDate error rule if present
    if (fieldConfig.errors) {
      fieldConfig.errors = fieldConfig.errors.filter(e => e.name !== 'gtDate');
    } else {
      fieldConfig.errors = [];
    }

    if (effectiveClosedUntil) {
      // Set required validator plus gtDate validator
      formControl.setValidators([Validators.required, gtDate(effectiveClosedUntil)]);

      // Add gtDate error rule with formatted date
      const formattedDate = moment(effectiveClosedUntil).format(this.gps.getDateFormat());
      const gtDateError = {
        name: 'gtDate',
        keyi18n: 'gtDate',
        param1: formattedDate,
        rules: [RuleEvent.DIRTY, RuleEvent.TOUCHED],
        text: null
      };
      fieldConfig.errors.push(gtDateError);

      // Translate the new error message
      this.translateService.get('gtDate', {param1: formattedDate}).subscribe(
        text => gtDateError.text = text
      );
    } else {
      // Only required validator when no closedUntil restriction
      formControl.setValidators([Validators.required]);
    }

    // Re-validate the current value with the new validators
    formControl.updateValueAndValidity();
  }
}
