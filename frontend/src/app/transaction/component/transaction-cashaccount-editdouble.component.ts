import { requireFormControl } from '../../lib/dynamic-form/models/form-control-access';
import { Component, OnInit, input, inject } from '@angular/core';
import { FieldConfig } from '../../lib/dynamic-form/models/field.config';
import { TranslateModule } from '@ngx-translate/core';
import { CashAccountTransfer, TransactionService } from '../service/transaction.service';
import { ProcessedActionData } from '../../lib/types/processed.action.data';
import { ProcessedAction } from '../../lib/types/processed.action';
import { TransactionCashaccountBaseOperations } from './transaction.cashaccount.base.operations';
import { Portfolio } from '../../entities/portfolio';
import { PortfolioService } from '../../portfolio/service/portfolio.service';
import { ValueKeyHtmlSelectOptions } from '../../lib/dynamic-form/models/value.key.html.select.options';
import { Subscription } from 'rxjs';
import { Cashaccount } from '../../entities/cashaccount';
import { Transaction } from '../../entities/transaction';
import { TransactionType } from '../../shared/types/transaction.type';
import { AppHelper } from '../../lib/helper/app.helper';
import { InfoLevelType } from '../../lib/message/info.leve.type';
import { HelpIds } from '../../lib/help/help.ids';
import { FormDefinitionHelper } from '../../shared/edit/form.definition.helper';
import { DynamicFieldHelper } from '../../lib/helper/dynamic.field.helper';
import { BusinessHelper } from '../../shared/helper/business.helper';
import { BusinessSelectOptionsHelper } from '../../shared/securitycurrency/business.select.options.helper';
import { TranslateHelper } from '../../lib/helper/translate.helper';
import { AppSettings } from '../../shared/app.settings';
import { BaseSettings } from '../../lib/base.settings';
import { GlobalparameterGTService } from '../../gtservice/globalparameter.gt.service';
import { gtDate } from '../../lib/validator/validator';
import { Validators } from '@angular/forms';
import { RuleEvent } from '../../lib/dynamic-form/error/error.message.rules';
import moment from 'moment';
import { DialogModule } from '@openng/optimus-ui/dialog';
import { DYNAMIC_FORM_IMPORTS } from '../../lib/dynamic-form/dynamic-form.imports';

/**
 * Cash transfer between two cash accounts which are managed by this application.
 */
@Component({
  selector: 'transaction-cashaccount-editdouble',
  template: `
    <p-dialog
      header="{{ 'ACCOUNT_TRANSFER' | translate }}"
      [visible]="visibleCashaccountTransactionDoubleDialog()"
      [style]="{ width: '550px' }"
      (onShow)="onShow($event)"
      (onHide)="onHide($event)"
      [modal]="true">
      @if (transactionLocked()) {
        <div class="alert alert-warning alert-dialog-wrap">
          {{ transactionLockedMessage() }}
        </div>
      }

      <dynamic-form
        [config]="config"
        [formConfig]="formConfig"
        [translateService]="translateService"
        #form="dynamicForm"
        (submitBt)="submit($event)" />
    </p-dialog>
  `,
  imports: [DialogModule, ...DYNAMIC_FORM_IMPORTS, TranslateModule]
})
export class TransactionCashaccountEditDoubleComponent extends TransactionCashaccountBaseOperations implements OnInit {
  private portfolioService: PortfolioService = inject(PortfolioService);
  private transactionService: TransactionService = inject(TransactionService);
  private gpsGT: GlobalparameterGTService = inject(GlobalparameterGTService);

  // InputMask from parent view
  readonly visibleCashaccountTransactionDoubleDialog = input(false);
  // @Input() transactionCallParam: TransactionCallParam;

  // Access the form
  //  @ViewChild(DynamicFormComponent) form: DynamicFormComponent;

  // The first is the withdrawal(debit) and the 2nd is the deposit(credit)
  transactions: Transaction[] = [];

  readonly WithIndex: number = 0;
  readonly DepIndex: number = 1;

  debitCashaccount: Cashaccount | null = null;
  creditCashaccount: Cashaccount | null = null;

  // Portfolio references for closedUntil calculation
  private debitPortfolio: Portfolio | null = null;
  private creditPortfolio: Portfolio | null = null;

  // Observer subscribe
  private transactionTimeChangeSub?: Subscription;
  private debitChashaccountChangedSub?: Subscription;
  private creditChashaccountChangedSub?: Subscription;

  constructor() {
    super();
  }

  ngOnInit(): void {
    this.formConfig = AppHelper.getDefaultFormConfig(this.gps, 4, this.helpLink.bind(this));

    // When the input on the following group changes, some calculations must be executed
    const calcGroupConfig: FieldConfig[] = [
      DynamicFieldHelper.createFieldInputNumber(
        'currencyExRate',
        'EXCHANGE_RATE',
        true,
        this.gps.getMaxFractionDigits() - 2,
        this.gps.getMaxFractionDigits(),
        false,
        { usedLayoutColumns: 8 }
      ),
      ...this.createExRateButtons(),
      DynamicFieldHelper.createFieldInputNumberHeqF(
        'creditAmount',
        true,
        AppSettings.FID_MAX_INT_REAL_DOUBLE,
        this.gps.getMaxFractionDigits(),
        false
      ),
      this.getTransactionCostFieldDefinition(this.gps)
    ];

    this.config = [
      FormDefinitionHelper.getTransactionTime(),
      DynamicFieldHelper.createFieldSelectNumber('idDebitCashaccount', 'DEBIT_ACCOUNT', true),
      DynamicFieldHelper.createFieldSelectNumber('idCreditCashaccount', 'CREDIT_ACCOUNT', true),
      { formGroupName: 'calcGroup', fieldConfig: calcGroupConfig },
      this.getDebitAmountFieldDefinition(this.gps),
      DynamicFieldHelper.createFieldTextareaInputStringHeqF('note', BaseSettings.FID_MAX_LETTERS, false),
      DynamicFieldHelper.createSubmitButton()
    ];
    this.configObject = TranslateHelper.prepareFieldsAndErrors(this.translateService, this.config);
  }

  isVisibleDialog(): boolean {
    return this.visibleCashaccountTransactionDoubleDialog() ?? false;
  }

  ////////////////////////////////////////////////
  onHide(event: unknown): void {
    this.debitChashaccountChangedSub?.unsubscribe();
    this.creditChashaccountChangedSub?.unsubscribe();
    this.transactionTimeChangeSub?.unsubscribe();
    super.close();
  }

  valueChangedOnCalcFields(): void {
    this.valueChangedOnValueCalcFieldsSub = requireFormControl(this.configObject.calcGroup)
      .valueChanges.pipe(this.untilDialogClosed())
      .subscribe((data) => {
        this.setCalculatedDebitAmount();
      });
  }

  setCalculatedDebitAmount(): number {
    const values: any = {};
    this.form().cleanMaskAndTransferValuesToBusinessObject(values, true);
    if (!this.debitCashaccount || !this.creditCashaccount || values.creditAmount == null) {
      requireFormControl(this.configObject.debitAmount).setValue(null);
      return values.currencyExRate;
    }
    if (!this.currencypair && this.debitCashaccount.currency !== this.creditCashaccount.currency) {
      requireFormControl(this.configObject.debitAmount).setValue(null);
      return values.currencyExRate;
    }

    if (this.currencypair) {
      const debitAmount = this.calcDebitAmount(values);
      requireFormControl(this.configObject.debitAmount).setValue(debitAmount);
    } else {
      requireFormControl(this.configObject.debitAmount).setValue(
        values.creditAmount + (values.transactionCost ? values.transactionCost : 0)
      );
    }
    return values.currencyExRate;
  }

  calcDebitAmount(values: any): number | null {
    if (!this.debitCashaccount?.currency || !this.creditCashaccount?.currency || values.creditAmount == null)
      return null;
    if (!this.currencypair && this.debitCashaccount.currency !== this.creditCashaccount.currency) return null;
    if (this.currencypair && values.currencyExRate == null) return null;
    return (
      BusinessHelper.divideMultiplyExchangeRate(
        values.creditAmount,
        values.currencyExRate,
        this.debitCashaccount.currency,
        this.currencypair
      ) + (values.transactionCost ? values.transactionCost : 0)
    );
  }

  valueChangedOnTransactionTime() {
    this.transactionTimeChangeSub = requireFormControl(this.configObject.transactionTime)
      .valueChanges.pipe(this.untilDialogClosed())
      .subscribe(() => {
        this.disableEnableExchangeRateButtons();
        this.toggleDebitCashaccountOptionsByTransactionTime();
      });
  }

  /**
   * Disables debit cash account options that are no longer active at the selected transaction time. The credit
   * options are re-toggled separately by {@link filterCreditHtmlOptions} since they are rebuilt on every debit change.
   */
  private toggleDebitCashaccountOptionsByTransactionTime(): void {
    const transactionTime = requireFormControl(this.configObject.transactionTime).value;
    if (!transactionTime || !this.portfoliosIfInitialized) {
      return;
    }
    BusinessSelectOptionsHelper.accountsEnableDisableOptionsByActiveDate(
      this.portfolios.flatMap((p) => p.cashaccountList || []),
      this.configObject.idDebitCashaccount,
      +transactionTime
    );
  }

  valueChangedOnDebitCashaccount(): void {
    this.debitChashaccountChangedSub = requireFormControl(this.configObject.idDebitCashaccount)
      .valueChanges.pipe(this.untilDialogClosed())
      .subscribe((data: string | number | null) => {
        const cp =
          data == null || data === '' ? null : this.getCashaccountByIdCashaccountFromPortfolios(this.portfolios, +data);
        this.debitCashaccount = cp?.cashaccount ?? null;
        this.debitPortfolio = cp?.portfolio ?? null;
        this.updateTransactionTimeMinDateFromBothPortfolios();
        this.filterCreditHtmlOptions(
          this.configObject.idDebitCashaccount.valueKeyHtmlOptions ?? [],
          cp?.cashaccount.idSecuritycashAccount
        );
        if (cp) {
          DynamicFieldHelper.setCurrency(this.configObject.debitAmount, cp.cashaccount.currency);
          DynamicFieldHelper.setCurrency(this.configObject.transactionCost, cp.cashaccount.currency);
        }
        this.changeCurrencyExRateState();
      });
  }

  /**
   * When cash credit account changes the currency of the must be changed as well
   */
  valueChangedOnCreditCashaccount(): void {
    this.creditChashaccountChangedSub = requireFormControl(this.configObject.idCreditCashaccount)
      .valueChanges.pipe(this.untilDialogClosed())
      .subscribe((data: string | number | null) => {
        const cp =
          data == null || data === '' ? null : this.getCashaccountByIdCashaccountFromPortfolios(this.portfolios, +data);
        this.creditCashaccount = cp?.cashaccount ?? null;
        this.creditPortfolio = cp?.portfolio ?? null;
        this.updateTransactionTimeMinDateFromBothPortfolios();
        if (cp) DynamicFieldHelper.setCurrency(this.configObject.creditAmount, cp.cashaccount.currency);
        this.changeCurrencyExRateState();
      });
  }

  helpLink(): void {
    this.gps.toExternalHelpWebpage(this.gps.getUserLang(), HelpIds.HELP_TRANSACTION_ACCOUNT);
  }

  submit(value: { [name: string]: any }) {
    const cashAccountTransfer = this.viewToBusinessModel(this.createOrCopyExistingTransaction());
    if (!cashAccountTransfer) return;
    this.saveTransaction(cashAccountTransfer);
  }

  saveTransaction(cashAccountTransfer: CashAccountTransfer) {
    this.transactionService
      .updateCreateDoubleTransaction(cashAccountTransfer)
      .pipe(this.untilDialogClosed())
      .subscribe({
        next: (newCashAccountTransfer: CashAccountTransfer) => {
          this.messageToastService.showMessageI18n(InfoLevelType.SUCCESS, 'MSG_RECORD_SAVED', {
            i18nRecord: AppSettings.TRANSACTION.toUpperCase()
          });
          this.closeDialog.emit(
            new ProcessedActionData(
              cashAccountTransfer.depositTransaction.idTransaction ? ProcessedAction.UPDATED : ProcessedAction.CREATED,
              [newCashAccountTransfer.withdrawalTransaction, newCashAccountTransfer.depositTransaction]
            )
          );
        },
        error: () => (this.configObject.submit.disabled = false)
      });
  }

  protected initialize(): void {
    this.debitCashaccount = null;
    this.creditCashaccount = null;
    this.debitPortfolio = null;
    this.creditPortfolio = null;
    this.currencypair = null;
    this.valueChangedOnTransactionTime();
    this.portfolioService
      .getPortfoliosForTenantOrderByName()
      .pipe(this.untilDialogClosed())
      .subscribe((data: Portfolio[]) => {
        this.portfolios = data;
        this.disableCurrencyExRateState();

        const transactionCallParam = this.transactionCallParam();
        if (transactionCallParam.transaction) {
          this.setupModifyExistingTransaction(transactionCallParam.transaction);
        } else {
          this.setupCashaccountSelect(transactionCallParam.cashaccount?.idSecuritycashAccount, null);
        }
      });
  }

  private adjustNumberInputFractions(): void {
    if (!this.creditCashaccount || !this.debitCashaccount) return;
    const fromCurrencyFraction = this.gps.getCurrencyPrecision(
      this.currencypair ? this.currencypair.fromCurrency : this.creditCashaccount.currency
    );
    const toCurrencyFraction = this.gps.getCurrencyPrecision(
      this.currencypair ? this.currencypair.toCurrency : this.debitCashaccount.currency
    );
    DynamicFieldHelper.adjustNumberFraction(
      this.configObject.creditAmount,
      AppSettings.FID_MAX_INT_REAL_DOUBLE,
      toCurrencyFraction
    );
    DynamicFieldHelper.adjustNumberFraction(
      this.configObject.transactionCost,
      AppSettings.FID_SMALL_INTEGER_LIMIT,
      fromCurrencyFraction
    );
    DynamicFieldHelper.adjustNumberFraction(
      this.configObject.debitAmount,
      AppSettings.FID_MAX_INT_REAL_DOUBLE,
      fromCurrencyFraction
    );
  }

  private getCurrencypair() {
    const pair = this.currencypair;
    const fromCurrency = pair?.fromCurrency;
    const toCurrency = pair?.toCurrency;
    if (!fromCurrency || !toCurrency) return;
    this.replaceDialogRequest('currencypair', () =>
      this.currencypairService
        .findOrCreateCurrencypairByFromAndToCurrency(fromCurrency, toCurrency)
        .pipe(this.untilDialogClosed())
        .subscribe((currencypair) => {
          this.currencypair = currencypair;
          if (this.hasChangedOnExistingTransaction()) {
            this.replaceDialogRequest('exchangeRate', () =>
              BusinessHelper.getAndSetQuotationCurrencypair(
                this.currencypairService,
                currencypair,
                requireFormControl(this.configObject.transactionTime).value,
                requireFormControl(this.configObject.currencyExRate)
              )
            );
          }
        })
    );
  }

  private setupCashaccountSelect(
    debitIdCashaccount: number | null | undefined,
    creditIdCashaccount: number | null | undefined
  ) {
    this.configObject.idDebitCashaccount.valueKeyHtmlOptions = this.prepareCashaccountOptions(this.portfolios);
    this.toggleDebitCashaccountOptionsByTransactionTime();
    this.filterCreditHtmlOptions(this.configObject.idDebitCashaccount.valueKeyHtmlOptions, creditIdCashaccount);
    this.valueChangedOnDebitCashaccount();
    this.valueChangedOnCreditCashaccount();
    // Debit account is always set
    requireFormControl(this.configObject.idDebitCashaccount).setValue(debitIdCashaccount);
    if (creditIdCashaccount != null) {
      requireFormControl(this.configObject.idCreditCashaccount).setValue(creditIdCashaccount);
    }
    this.valueChangedOnCalcFields();
  }

  /**
   * Loads the counterpart of an existing transfer and puts the two sides into the withdrawal/deposit order the form
   * expects.
   *
   * A transaction whose connectedIdTransaction points at its own row would otherwise be pushed into both slots, and
   * saving the dialog would post the same idTransaction twice and write that self reference back. Such a row cannot be
   * edited as a transfer, so the dialog is closed instead of offering a form that corrupts it further.
   *
   * @param transaction1 the transfer side the user opened
   */
  private setupModifyExistingTransaction(transaction1: Transaction) {
    const connectedId = transaction1.connectedIdTransaction;
    if (connectedId == null) return;
    if (transaction1.connectedIdTransaction === transaction1.idTransaction) {
      this.messageToastService.showMessageI18n(InfoLevelType.WARNING, 'GT_TRANSFER_SELF_CONNECTED', {
        idTransaction: transaction1.idTransaction
      });
      this.closeDialog.emit(new ProcessedActionData(ProcessedAction.NO_CHANGE));
      return;
    }
    this.transactionService
      .getTransactionByIdTransaction(connectedId)
      .pipe(this.untilDialogClosed())
      .subscribe((data) => {
        this.transactions = [];
        this.transactions.push(transaction1);
        if (TransactionType[data.transactionType as keyof typeof TransactionType] === TransactionType.WITHDRAWAL) {
          this.transactions.unshift(data);
        } else {
          this.transactions.push(data);
        }
        this.setFormValues();
      });
  }

  private setFormValues(): void {
    const withdrawal = this.transactions[this.WithIndex];
    const deposit = this.transactions[this.DepIndex];
    requireFormControl(this.configObject.transactionTime).setValue(
      withdrawal.transactionTime == null ? null : new Date(withdrawal.transactionTime)
    );
    requireFormControl(this.configObject.currencyExRate).setValue(this.transactions[this.WithIndex].currencyExRate);
    requireFormControl(this.configObject.transactionCost).setValue(this.transactions[this.WithIndex].transactionCost);
    requireFormControl(this.configObject.creditAmount).setValue(this.transactions[this.DepIndex].cashaccountAmount);
    requireFormControl(this.configObject.note).setValue(this.transactions[this.WithIndex].note);

    this.setupCashaccountSelect(
      withdrawal.cashaccount?.idSecuritycashAccount ?? withdrawal.idCashaccount,
      deposit.cashaccount?.idSecuritycashAccount ?? deposit.idCashaccount
    );
    this.setCalculatedDebitAmount();
  }

  /**
   * Enable or disable input for currency exchange rate.
   */
  private changeCurrencyExRateState(): void {
    this.cancelDialogRequest('currencypair');
    this.cancelDialogRequest('exchangeRate');
    if (this.debitCashaccount?.currency && this.creditCashaccount?.currency) {
      this.setCurrencypair();
      if (this.debitCashaccount.currency === this.creditCashaccount.currency) {
        requireFormControl(this.configObject.currencyExRate).setValue(1);
        this.disableCurrencyExRateState();
      } else {
        // this.configObject.currencyExRate.labelSuffix = this.currencypair.toStringFN();
        this.configObject.currencyExRate.labelSuffix = this.currencypair?.name ?? undefined;
        requireFormControl(this.configObject.currencyExRate).enable();
        this.getCurrencypair();
      }
      this.adjustNumberInputFractions();
    } else {
      this.currencypair = null;
      this.disableCurrencyExRateState();
      requireFormControl(this.configObject.debitAmount).setValue(null);
    }
    this.disableEnableExchangeRateButtons();
  }

  private setCurrencypair(): void {
    if (!this.debitCashaccount || !this.creditCashaccount) {
      this.currencypair = null;
      return;
    }
    const transactionCallParam = this.transactionCallParam();
    if (
      transactionCallParam.transaction &&
      this.transactions[this.DepIndex].cashaccount?.currency === this.debitCashaccount.currency &&
      this.transactions[this.WithIndex].cashaccount?.currency === this.creditCashaccount.currency
    ) {
      this.currencypair = transactionCallParam.transaction.currencypair;
    } else {
      this.currencypair = BusinessHelper.getCurrencypairWithSetOfFromAndTo(
        this.creditCashaccount.currency,
        this.debitCashaccount.currency
      );
    }
  }

  /**
   * Return true when exchange rate can be adjusted
   */
  private hasChangedOnExistingTransaction(): boolean {
    const t = this.transactionCallParam().transaction;
    return (
      t === null ||
      (t !== null &&
        ((requireFormControl(this.configObject.idCreditCashaccount).value &&
          this.transactions[this.DepIndex].cashaccount?.idSecuritycashAccount !==
            requireFormControl(this.configObject.idCreditCashaccount).value) ||
          (this.transactions[this.WithIndex].cashaccount?.idSecuritycashAccount != null &&
            this.transactions[this.WithIndex].cashaccount?.idSecuritycashAccount !==
              requireFormControl(this.configObject.idDebitCashaccount).value)))
    );
  }

  private disableCurrencyExRateState(): void {
    // this.configObject.currencyExRate.labelSuffix = '';
    requireFormControl(this.configObject.currencyExRate).disable();
  }

  private filterCreditHtmlOptions(
    debitHtmlOptions: ValueKeyHtmlSelectOptions[],
    idCashaccountCredit: number | null | undefined
  ): void {
    const valueKeyHtmlOptions = debitHtmlOptions.filter(
      (debitHtmlOption) => debitHtmlOption.key !== idCashaccountCredit
    );

    this.configObject.idCreditCashaccount.valueKeyHtmlOptions = valueKeyHtmlOptions;
    if (this.portfoliosIfInitialized && requireFormControl(this.configObject.transactionTime).value) {
      BusinessSelectOptionsHelper.accountsEnableDisableOptionsByActiveDate(
        this.portfolios.flatMap((p) => p.cashaccountList || []),
        this.configObject.idCreditCashaccount,
        +requireFormControl(this.configObject.transactionTime).value
      );
    }
    this.configObject.idCreditCashaccount.valueKeyHtmlOptions = [
      new ValueKeyHtmlSelectOptions('', ''),
      ...this.configObject.idCreditCashaccount.valueKeyHtmlOptions
    ];
    if (valueKeyHtmlOptions.length === 1) {
      requireFormControl(this.configObject.idCreditCashaccount).setValue(valueKeyHtmlOptions[0].key);
    } else {
      requireFormControl(this.configObject.idCreditCashaccount).setValue('');
    }
  }

  private createOrCopyExistingTransaction(): CashAccountTransfer {
    const newTransactions: Transaction[] = [];
    for (let i = 0; i < 2; i++) {
      newTransactions.push(new Transaction());
      if (this.transactionCallParam().transaction) {
        Object.assign(newTransactions[i], this.transactions[i]);
      } else {
        newTransactions[i].transactionType =
          TransactionType[i === this.WithIndex ? TransactionType.WITHDRAWAL : TransactionType.DEPOSIT];
      }
      this.form().cleanMaskAndTransferValuesToBusinessObject(newTransactions[i]);
    }
    return new CashAccountTransfer(newTransactions[0], newTransactions[1]);
  }

  private viewToBusinessModel(cashAccountTransfer: CashAccountTransfer): CashAccountTransfer | null {
    const values: any = {};
    this.form().cleanMaskAndTransferValuesToBusinessObject(values, true);
    const debit = this.getCashaccountByIdCashaccountFormPortfolios(this.portfolios, values.idDebitCashaccount);
    const credit = this.getCashaccountByIdCashaccountFormPortfolios(this.portfolios, values.idCreditCashaccount);
    if (!debit || !credit) return null;
    cashAccountTransfer.withdrawalTransaction.cashaccount = debit.cashaccount;
    cashAccountTransfer.withdrawalTransaction.transactionCost = values.transactionCost;
    cashAccountTransfer.depositTransaction.cashaccount = credit.cashaccount;
    cashAccountTransfer.depositTransaction.cashaccountAmount = values.creditAmount;
    if (this.currencypair != null) {
      cashAccountTransfer.withdrawalTransaction.idCurrencypair = this.currencypair.idSecuritycurrency;
      cashAccountTransfer.depositTransaction.idCurrencypair = this.currencypair.idSecuritycurrency;
    }
    const debitAmount = this.calcDebitAmount(values);
    if (debitAmount == null) return null;
    cashAccountTransfer.withdrawalTransaction.cashaccountAmount = debitAmount;
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
    const transactionCallParam = this.transactionCallParam();
    if (transactionCallParam.transaction) {
      this.checkTransactionLocked(effectiveClosedUntil, transactionCallParam.transaction.transactionTime);
    }
  }

  /**
   * Updates the transactionTime form control validators to include the gtDate validator
   * with the current effectiveClosedUntil date. Also updates the error message rule with
   * the formatted date for proper error display.
   */
  private updateTransactionTimeValidator(effectiveClosedUntil: Date | null): void {
    const formControl = requireFormControl(this.configObject.transactionTime);
    const fieldConfig = this.configObject.transactionTime;

    // Remove existing gtDate error rule if present
    if (fieldConfig.errors) {
      fieldConfig.errors = fieldConfig.errors.filter((e) => e.name !== 'gtDate');
    } else {
      fieldConfig.errors = [];
    }

    if (effectiveClosedUntil) {
      // Set required validator plus gtDate validator
      formControl.setValidators([Validators.required, gtDate(effectiveClosedUntil)]);

      // Add gtDate error rule with formatted date
      const formattedDate = moment(effectiveClosedUntil).format(this.gps.getDateFormat());
      DynamicFieldHelper.addErrorRule(this.translateService, fieldConfig, {
        name: 'gtDate',
        keyi18n: 'gtDate',
        param1: formattedDate,
        rules: [RuleEvent.DIRTY, RuleEvent.TOUCHED]
      });
    } else {
      // Only required validator when no closedUntil restriction
      formControl.setValidators([Validators.required]);
    }

    // Re-validate the current value with the new validators
    formControl.updateValueAndValidity();
  }
}
