import { requireInitialized } from '../../lib/helper/required-state';
import { switchMap } from 'rxjs';
import { requireFormControl } from '../../lib/dynamic-form/models/form-control-access';
import { Component, OnInit, signal, output, viewChild, input, inject } from '@angular/core';

import { PortfolioService } from '../../portfolio/service/portfolio.service';
import { GlobalparameterGTService } from '../../gtservice/globalparameter.gt.service';
import { Portfolio } from '../../entities/portfolio';
import { Cashaccount } from '../../entities/cashaccount';
import { Securityaccount } from '../../entities/securityaccount';
import { Security } from '../../entities/security';
import { TransactionType } from '../../shared/types/transaction.type';
import { AppHelper } from '../../lib/helper/app.helper';
import { SecurityService } from '../../securitycurrency/service/security.service';
import { Transaction } from '../../entities/transaction';
import { TransactionService } from '../service/transaction.service';
import { AlgoAlertService } from '../../algo/service/algo-alert.service';
import { SecurityOpenPositionPerSecurityaccount } from '../../entities/view/security.open.position.per.securityaccount';
import { ITransactionEditType } from './i.transaction.edit.type';
import { TransactionSecurityEditAccumulate } from './transaction.security.edit.accumulate';
import {
  TransactionSecurityEditDividendReduce,
  TransactionSecurityEditFinanceCost
} from './transaction.security.edit.reduce';
import { SecurityaccountOpenPositionUnits } from '../../entities/view/securityaccount.open.position.units';
import moment from 'moment';
import { ActivatedRoute } from '@angular/router';
import { ProcessedActionData } from '../../lib/types/processed.action.data';
import { ProcessedAction } from '../../lib/types/processed.action';
import { TransactionCallParam } from './transaction.call.parm';
import { TranslateModule } from '@ngx-translate/core';
import { ValueKeyHtmlSelectOptions } from '../../lib/dynamic-form/models/value.key.html.select.options';
import { Helper } from '../../lib/helper/helper';
import { InfoLevelType } from '../../lib/message/info.leve.type';
import { merge, Observable, Subscription } from 'rxjs';

import { FieldConfig } from '../../lib/dynamic-form/models/field.config';
import { DataType } from '../../lib/dynamic-form/models/data.type';
import { TransactionBaseOperations } from './transaction.base.operations';
import { DynamicFormComponent } from '../../lib/dynamic-form/containers/dynamic-form/dynamic-form.component';
import { HelpIds } from '../../lib/help/help.ids';
import { map } from 'rxjs/operators';
import { FormDefinitionHelper } from '../../shared/edit/form.definition.helper';
import { DynamicFieldHelper } from '../../lib/helper/dynamic.field.helper';
import { SelectOptionsHelper } from '../../lib/helper/select.options.helper';
import { BusinessHelper } from '../../shared/helper/business.helper';
import { TranslateHelper } from '../../lib/helper/translate.helper';
import { FormHelper } from '../../lib/dynamic-form/components/FormHelper';
import { ClosedMarginPosition } from '../model/closed.margin.position';
import { AbstractControl, Validators } from '@angular/forms';
import { gtDate } from '../../lib/validator/validator';
import { RuleEvent } from '../../lib/dynamic-form/error/error.message.rules';
import { AppSettings } from '../../shared/app.settings';
import { BusinessSelectOptionsHelper } from '../../shared/securitycurrency/business.select.options.helper';
import { BaseSettings } from '../../lib/base.settings';
import { DialogModule } from '@openng/optimus-ui/dialog';
import { DYNAMIC_FORM_IMPORTS } from '../../lib/dynamic-form/dynamic-form.imports';
import { DialogService } from '@openng/optimus-ui/dynamicdialog';
import { CalcExRateDialogComponent } from './calc-ex-rate-dialog.component';
import { DynamicDialogHelper } from '../../lib/dynamicdialog/component/dynamicDialogHelper';
import { takeUntilDestroyed } from '@angular/core/rxjs-interop';

/**
 * Edit transaction for an investment product, also margin product. The transaction type (buy, dividend, sell,
 * finance cost) can not be changed in this form, it has to be set in advance.
 */
@Component({
  selector: 'transaction-security-edit',
  template: `
    <p-dialog
      [visible]="visibleSecurityTransactionDialog()"
      [style]="{ width: '720px' }"
      [resizable]="false"
      (onShow)="onShow($event)"
      (onHide)="onHide($event)"
      [modal]="true">
      <ng-template #header>
        <div>
          <h4>{{ 'INVESTMENT_TRANSACTION' | translate }}</h4>
          @if (isOpenMarginInstrument() && !transactionCallParam().transaction) {
            <p class="big-size">{{ 'MARGIN_BUY_SELL' | translate }}</p>
          }
        </div>
      </ng-template>

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
  imports: [TranslateModule, DialogModule, ...DYNAMIC_FORM_IMPORTS]
})
export class TransactionSecurityEditComponent extends TransactionBaseOperations implements OnInit {
  private transactionService: TransactionService = inject(TransactionService);
  private algoAlertService: AlgoAlertService = inject(AlgoAlertService);
  private portfolioService: PortfolioService = inject(PortfolioService);
  private securityService: SecurityService = inject(SecurityService);
  private activeRoute: ActivatedRoute = inject(ActivatedRoute);
  private gpsGT: GlobalparameterGTService = inject(GlobalparameterGTService);
  private dialogService: DialogService = inject(DialogService);

  // InputMask from parent view
  readonly visibleSecurityTransactionDialog = input(false);
  readonly transactionCallParam = input.required<TransactionCallParam>();

  // Access the form
  readonly form = viewChild.required(DynamicFormComponent);

  readonly closeDialog = output<ProcessedActionData>();

  // Form configuration
  readonly isOpenMarginInstrument = signal(false);
  // Transaction depentable
  private initializedTransactionEditType?: ITransactionEditType;

  get transactionEditType(): ITransactionEditType {
    return requireInitialized(
      this.initializedTransactionEditType,
      'TransactionSecurityEditComponent.transactionEditType'
    );
  }

  set transactionEditType(value: ITransactionEditType) {
    this.initializedTransactionEditType = value;
  }
  // Data from Server
  private securities: Security[] = [];
  private portfolios: Portfolio[] | null = null;
  private initializedSecurityOpenPositionPerSecurityaccount?: SecurityOpenPositionPerSecurityaccount;

  private get securityOpenPositionPerSecurityaccount(): SecurityOpenPositionPerSecurityaccount {
    return requireInitialized(
      this.initializedSecurityOpenPositionPerSecurityaccount,
      'TransactionSecurityEditComponent.securityOpenPositionPerSecurityaccount'
    );
  }

  private set securityOpenPositionPerSecurityaccount(value: SecurityOpenPositionPerSecurityaccount) {
    this.initializedSecurityOpenPositionPerSecurityaccount = value;
  }
  // Form content variables

  private selectedSecurity: Security | null | undefined;
  private isAccumaulteOrReduce = false;
  private volumei18n: string | null = null;
  private currencyCashaccount: string | null | undefined;
  private maxTransactionDate = new Date();
  private oldCurrencyExChange: number | null = null;
  // Subscribe
  private subObj: { [key: string]: Subscription } = {};

  private readonly cashaccountSecurity = 'A';
  private readonly valueChangedOnValueCalcFields = 'B';
  private readonly valueChangedOnIdSecurityaccount = 'C';
  private readonly valueChangedOnTransactionTime = 'D';
  private readonly valueChangedOnExDate = 'E';
  private readonly valueChangedOnquotationCA = 'F';
  private readonly valueChangedOnTaxCostCA = 'G';
  private readonly valueChangedOnTransactionCostCA = 'H';

  private isMarginInstrument = false;
  private closedMarginPosition: ClosedMarginPosition | null = null;

  private isCloseMarginInstrument = false;

  // When this changes, the holdings must be reloaded
  private holdingCheck = new ChangedIdSecurityAndTime();
  // When this changes, the price of historyquote will be reloaded
  private historyquoteCheck = new ChangedIdSecurityAndTime();

  constructor() {
    super();
  }

  ngOnInit(): void {
    const transactionCallParam = this.transactionCallParam();
    if (transactionCallParam.transaction) {
      const security = transactionCallParam.security;
      const id = security?.idSecuritycurrency;
      if (!security || id == null) throw new Error('Editing a security transaction requires its persisted security.');
      transactionCallParam.transactionType =
        TransactionType[transactionCallParam.transaction.transactionType as keyof typeof TransactionType];
      transactionCallParam.idSecuritycurrency = id;
      transactionCallParam.transaction.security = security;
      this.historyquoteCheck.hasChanged(
        transactionCallParam.idSecuritycurrency,
        transactionCallParam.transaction.transactionTime
      );
    }

    this.formConfig = AppHelper.getDefaultFormConfig(this.gps, 4, this.helpLink.bind(this));

    const calcGroupConfig: FieldConfig[] = [
      DynamicFieldHelper.createFieldInputNumber(
        'currencyExRate',
        'EXCHANGE_RATE',
        true,
        AppSettings.FID_MAX_CURRENCY_EX_RATE_PRECISION - AppSettings.FID_MAX_CURRENCY_EX_RATE_FRACTION,
        AppSettings.FID_MAX_CURRENCY_EX_RATE_FRACTION,
        false,
        { usedLayoutColumns: 8 }
      ),
      ...this.createExRateButtons(),
      this.createQuotationField(),
      DynamicFieldHelper.createFieldInputNumber(
        'quotation',
        'QUOTATION_DIV',
        true,
        AppSettings.FID_MAX_INT_REAL_DOUBLE,
        this.gps.getMaxFractionDigits(),
        transactionCallParam.transactionType === TransactionType.DIVIDEND ||
          transactionCallParam.transactionType === TransactionType.FINANCE_COST,
        { userDefinedValue: 'S', usedLayoutColumns: 8 }
      ),
      DynamicFieldHelper.createFieldInputNumber(
        'quotationCA',
        '_CA',
        false,
        AppSettings.FID_MAX_INT_REAL_DOUBLE,
        this.gps.getMaxFractionDigits(),
        true,
        { userDefinedValue: 'C', usedLayoutColumns: 4 }
      ),

      DynamicFieldHelper.createFieldInputNumberHeqF(
        'taxCost',
        false,
        AppSettings.FID_STANDARD_INTEGER_DIGITS,
        this.gps.getStandardFractionDigits(),
        false,
        { userDefinedValue: 'S', usedLayoutColumns: 8 }
      ),

      DynamicFieldHelper.createFieldInputNumber(
        'taxCostCA',
        '_CA',
        false,
        AppSettings.FID_STANDARD_INTEGER_DIGITS,
        this.gps.getStandardFractionDigits(),
        false,
        { userDefinedValue: 'C', usedLayoutColumns: 4 }
      ),

      DynamicFieldHelper.createFieldInputNumberHeqF(
        'transactionCost',
        false,
        AppSettings.FID_STANDARD_INTEGER_DIGITS,
        this.gps.getStandardFractionDigits(),
        false,
        { userDefinedValue: 'S', usedLayoutColumns: 8 }
      ),

      DynamicFieldHelper.createFieldInputNumber(
        'transactionCostCA',
        '_CA',
        false,
        AppSettings.FID_STANDARD_INTEGER_DIGITS,
        this.gps.getStandardFractionDigits(),
        false,
        { userDefinedValue: 'C', usedLayoutColumns: 4 }
      ),

      // Used for accrued interest and daily finance cost for margin instrument
      DynamicFieldHelper.createFieldInputNumber(
        'assetInvestmentValue1',
        'ACCRUED_INTEREST',
        false,
        AppSettings.FID_STANDARD_INTEGER_DIGITS,
        this.gps.getMaxFractionDigits() - 3,
        true,
        { userDefinedValue: 'S' }
      ),

      DynamicFieldHelper.createFieldInputNumber(
        'assetInvestmentValue2',
        'VALUE_PER_POINT',
        false,
        AppSettings.FID_STANDARD_INTEGER_DIGITS - 3,
        0,
        true
      )
    ];

    this.config = [
      DynamicFieldHelper.createFieldSelectNumberHeqF('transactionType', true),
      DynamicFieldHelper.createFieldSelectNumber('idAlgoStrategy', 'STRATEGY_ASSIGNMENT', false),
      FormDefinitionHelper.getTransactionTime(),
      DynamicFieldHelper.createFieldPcalendarHeqF(DataType.DateNumeric, 'exDate', false, {
        calendarConfig: { disabledDays: [0, 6] }
      }),
      DynamicFieldHelper.createFieldSelectNumber('idSecuritycurrency', AppSettings.SECURITY.toUpperCase(), true, {
        dataproperty: 'security.idSecuritycurrency'
      }),
      DynamicFieldHelper.createFieldSelectNumber('idSecurityaccount', AppSettings.SECURITYACCOUNT.toUpperCase(), true),
      DynamicFieldHelper.createFieldSelectNumber('idCashaccount', AppSettings.CASHACCOUNT.toUpperCase(), true, {
        dataproperty: 'cashaccount.idSecuritycashAccount'
      }),
      DynamicFieldHelper.createFieldCheckboxHeqF('taxableInterest', {
        defaultValue: true
      }),
      { formGroupName: 'calcGroup', fieldConfig: calcGroupConfig },

      DynamicFieldHelper.createFieldInputNumberHeqF(
        'securityRisk',
        false,
        AppSettings.FID_MAX_DIGITS - this.gps.getMaxFractionDigits(),
        this.gps.getMaxFractionDigits(),
        true,
        { readonly: true, userDefinedValue: 'C' }
      ),

      DynamicFieldHelper.createFieldInputNumberHeqF(
        'cashaccountAmount',
        false,
        AppSettings.FID_MAX_DIGITS - this.gps.getMaxFractionDigits(),
        this.gps.getMaxFractionDigits(),
        true,
        { readonly: true, userDefinedValue: 'C', usedLayoutColumns: 8 }
      ),
      /*
       DynamicFieldHelper.createFieldInputString('cashaccountAmountExact', '_exact',
         15, false, { dataproperty: 'cashaccountAmount', readonly: true,  usedLayoutColumns: 4}),
 */
      DynamicFieldHelper.createFieldTextareaInputStringHeqF('note', BaseSettings.FID_MAX_LETTERS, false),
      DynamicFieldHelper.createSubmitButton()
    ];

    this.configObject = TranslateHelper.prepareFieldsAndErrors(this.translateService, this.config);
    this.translateService
      .get('VOLUME')
      .pipe(takeUntilDestroyed(this.destroyRef))
      .subscribe((text) => {
        this.volumei18n = text;
        if (this.portfolios && this.initializedSecurityOpenPositionPerSecurityaccount) {
          this.createSecurityaccountHtmlSelect();
        }
      });
    this.setCurrencyPrefixOnFields(null, transactionCallParam.transaction?.cashaccount?.currency);
  }

  isVisibleDialog(): boolean {
    return this.visibleSecurityTransactionDialog() ?? false;
  }

  valueChangedOnCalcFields(): void {
    this.subObj[this.valueChangedOnValueCalcFields] = requireFormControl(this.configObject.calcGroup)
      .valueChanges.pipe(this.untilDialogClosed())
      .subscribe((data) => {
        this.calcPosTotalOnChanges(data);
      });
  }

  /**
   * Probably a currency pair is introduced when cash account or Security changes. Because currency of cash account
   * and Security may be different.
   */
  valueChangedOnCashaccountOrSecurity(): void {
    this.clearCurrencyExRate();
    const obs1 = requireFormControl(this.configObject.idCashaccount).valueChanges.pipe(
      map((v) => ({
        control: this.configObject.idCashaccount.formControl,
        value: v
      }))
    );

    const obs2 = requireFormControl(this.configObject.idSecuritycurrency).valueChanges.pipe(
      map((v) => ({
        control: this.configObject.idSecuritycurrency.formControl,
        value: v
      }))
    );

    this.subObj[this.cashaccountSecurity] = merge(obs1, obs2)
      .pipe(this.untilDialogClosed())
      .subscribe((controlValue) => {
        if (requireFormControl(this.configObject.idSecuritycurrency).value != null) {
          this.selectedSecurity = this.getSecurityById(+requireFormControl(this.configObject.idSecuritycurrency).value);
          if (this.selectedSecurity?.idSecuritycurrency != null) {
            const transactionCallParam = this.transactionCallParam();
            transactionCallParam.idSecuritycurrency = this.selectedSecurity.idSecuritycurrency;
            if (
              !transactionCallParam.transaction &&
              transactionCallParam.transactionType !== TransactionType.DIVIDEND
            ) {
              this.getAndSetQuotationSecurity(this.selectedSecurity);
            }
            if (controlValue.control === this.configObject.idSecuritycurrency.formControl) {
              this.loadStrategyAssignments();
              this.readSecurityaccountAndHoldings();
            }
            this.setCurrencyOnSecurityAndCashaccount(this.selectedSecurity);
            this.setMarginFlags();
            this.setVisibilityOnFields();
            this.calcPosTotalOnChanges(this.getTransactionByForm());
            this.setCurrencyPrefixOnFields(this.selectedSecurity.currency, this.currencyCashaccount);
          }
        }
      });
  }

  /**
   * When transaction time changes, possible the available securities and possible holdings need a update.
   */
  valueChangedOnTransactionTimeAndExDate(): void {
    this.timeChangedSubscribe(
      this.valueChangedOnTransactionTime,
      requireFormControl(this.configObject.transactionTime),
      false
    );
    this.timeChangedSubscribe(this.valueChangedOnExDate, requireFormControl(this.configObject.exDate), true);
  }

  valueChangedOnSecurityaccount(): void {
    this.subObj[this.valueChangedOnIdSecurityaccount] = requireFormControl(this.configObject.idSecurityaccount)
      .valueChanges.pipe(this.untilDialogClosed())
      .subscribe((value) => {
        // Change cashaccount when securityaccount was changed
        const portfolio = this.getPortfolioByIdSecurityaccount(+value);
        if (portfolio != null) {
          this.configObject.idCashaccount.valueKeyHtmlOptions = this.createHtmlSelectKeyValue(
            portfolio.name,
            portfolio.cashaccountList ?? []
          );
          this.toggleAccountOptionsByActiveDate(this.configObject.idCashaccount, portfolio.cashaccountList || []);

          // Update transaction time minDate and validator based on portfolio's closedUntil
          const effectiveClosedUntil = FormDefinitionHelper.getEffectiveClosedUntil(portfolio, this.gpsGT);
          FormDefinitionHelper.updateTransactionTimeMinDate(this.configObject.transactionTime, effectiveClosedUntil);
          this.updateTransactionTimeValidator(effectiveClosedUntil);

          // Check if existing transaction is within closed period
          const transactionCallParam = this.transactionCallParam();
          if (transactionCallParam.transaction) {
            this.checkTransactionLocked(effectiveClosedUntil, transactionCallParam.transaction.transactionTime);
          }

          this.selectedSecurity = this.getSecurityById(requireFormControl(this.configObject.idSecuritycurrency).value);

          const selectedSecurity = this.selectedSecurity;
          if (selectedSecurity && !transactionCallParam.transaction) {
            // select default cashaccount depending on selected currency of the security
            const cashaccounts: Cashaccount[] = (portfolio.cashaccountList ?? []).filter(
              (cashaccount) => cashaccount.currency === selectedSecurity.currency
            );
            if (cashaccounts.length > 0) {
              this.setValueToControl(this.configObject.idCashaccount, cashaccounts[0].idSecuritycashAccount);
              this.currencyCashaccount = selectedSecurity.currency;
              this.setCurrencyPrefixOnFields(null, this.currencyCashaccount);
            }
          }
        }
      });
  }

  /**
   * Prepare all security accounts and its position holdings per security. When the security changes, this
   * method should be called.
   */
  readSecurityaccountAndHoldings(): void {
    if (this.portfolios) {
      let checkTime: number = +requireFormControl(this.configObject.transactionTime).value;
      if (this.isCloseMarginInstrument) {
        this.setHoldingsForTime(checkTime);
        // this.createSecurityaccountHtmlSelect();
      } else {
        const exDate = this.getExDate();
        checkTime = exDate
          ? moment(+exDate)
              .set({
                hour: 23,
                minute: 59,
                second: 59
              })
              .toDate()
              .getTime()
          : checkTime;
        this.setHoldingsForTime(checkTime);
      }
    }
  }

  prepareSecurityOptions(): void {
    const transactionCallParam = this.transactionCallParam();
    if (transactionCallParam.security?.idSecuritycurrency != null) {
      const securitiesHtmlSelect: ValueKeyHtmlSelectOptions[] = [];
      securitiesHtmlSelect.push(
        new ValueKeyHtmlSelectOptions(
          transactionCallParam.security.idSecuritycurrency,
          transactionCallParam.security.name + ' / ' + transactionCallParam.security.currency
        )
      );
      transactionCallParam.idSecuritycurrency = transactionCallParam.security.idSecuritycurrency;
      this.securities.push(transactionCallParam.security);
      this.configObject.idSecuritycurrency.valueKeyHtmlOptions = securitiesHtmlSelect;
      this.selectSecurity();
    } else {
      this.prepareAsyncActiveSecurityOptions();
    }
  }

  selectSecurity(): void {
    this.setValueToControl(this.configObject.idSecuritycurrency, this.transactionCallParam().idSecuritycurrency);
    this.selectedSecurity = this.getSecurityById(requireFormControl(this.configObject.idSecuritycurrency).value);
    this.loadStrategyAssignments();
    this.setMarginFlags();
    if (this.configObject.idSecuritycurrency.valueKeyHtmlOptions?.length === 1) {
      requireFormControl(this.configObject.idSecuritycurrency).disable();
    } else {
      requireFormControl(this.configObject.idSecuritycurrency).enable();
    }
  }

  public onShow(event: unknown) {
    this.clearControlSubscriptions();
    this.initializeAfterRender(() => this.initialize());
  }

  onHide(event: unknown) {
    this.endDialogSession();
    this.clearControlSubscriptions();
    this.closeDialog.emit(new ProcessedActionData(ProcessedAction.NO_CHANGE));
  }

  private clearControlSubscriptions(): void {
    Object.values(this.subObj).forEach((subscription) => subscription?.unsubscribe());
    this.subObj = {};
  }

  submit(value: { [name: string]: any }) {
    const transaction = this.getTransactionByForm();
    if (!transaction.cashaccount || !transaction.security) return;
    if (this.isMarginInstrument) {
      const risk = this.calcPosTotal(transaction, true);
      transaction.securityRisk = risk == null ? null : -risk;
      transaction.cashaccountAmount = this.calcPosTotal(transaction, false);
    } else {
      transaction.cashaccountAmount = this.calcPosTotal(transaction, false);
    }
    if (transaction.cashaccountAmount == null) return;

    if (this.currencypair != null) {
      const { fromCurrency, toCurrency } = this.currencypair;
      if (!fromCurrency || !toCurrency) return;
      this.currencypairService
        .findOrCreateCurrencypairByFromAndToCurrency(fromCurrency, toCurrency)
        .pipe(this.untilDialogClosed())
        .subscribe((currencypair) => {
          transaction.idCurrencypair = currencypair.idSecuritycurrency;
          this.saveTransaction(transaction);
        });
    } else {
      this.saveTransaction(transaction);
    }
  }

  saveTransaction(transaction: Transaction) {
    this.transactionService
      .updateCreateSecurityTrans(transaction)
      .pipe(this.untilDialogClosed())
      .subscribe({
        next: (newTransaction) => {
          this.messageToastService.showMessageI18n(InfoLevelType.SUCCESS, 'MSG_RECORD_SAVED', {
            i18nRecord: AppSettings.TRANSACTION.toUpperCase()
          });
          this.closeDialog.emit(
            new ProcessedActionData(
              transaction.idTransaction ? ProcessedAction.UPDATED : ProcessedAction.CREATED,
              newTransaction
            )
          );
        },
        error: () => (this.configObject.submit.disabled = false)
      });
  }

  helpLink(): void {
    this.gps.toExternalHelpWebpage(
      this.gps.getUserLang(),
      this.isMarginInstrument ? HelpIds.HELP_TRANSACTION_MARGIN_BASED : HelpIds.HELP_TRANSACTION_CASH_BASED
    );
  }

  private createQuotationField(): FieldConfig {
    if (this.transactionCallParam().transactionType === TransactionType.FINANCE_COST) {
      return DynamicFieldHelper.createFieldInputNumber('units', 'NUMBER_OF_DAYS', true, 4, 0, false);
    } else {
      return DynamicFieldHelper.createFieldInputNumber('units', 'QUANTITY', true, 9, 3, false);
    }
  }

  protected override createExRateButtons(): FieldConfig[] {
    return [
      ...super.createExRateButtons(),
      DynamicFieldHelper.createFunctionButtonFieldName(
        'calcExRateButton',
        'CALC_EX_RATE',
        (e) => this.openCalcExRateDialog(),
        {
          buttonInForm: true,
          usedLayoutColumns: 1
        }
      )
    ];
  }

  protected override disableEnableExchangeRateButtons(): void {
    super.disableEnableExchangeRateButtons();
    this.configObject.calcExRateButton.disabled = requireFormControl(this.configObject.currencyExRate).disabled;
  }

  /**
   * Opens a dialog where the user enters the total credited/debited amount.
   * On close, the entered amount is used to reverse-calculate the exchange rate.
   */
  private openCalcExRateDialog(): void {
    const title = this.configObject.calcExRateButton.labelKey;
    if (!title) return;
    const ddh = new DynamicDialogHelper(this.translateService, this.dialogService, CalcExRateDialogComponent, title);
    const opened = ddh.openDynamicDialog(400, {
      cashaccountCurrency: this.currencyCashaccount
    });
    opened
      .pipe(
        switchMap((ref) => ref.onClose),
        this.untilDialogClosed()
      )
      .subscribe((amount: number) => {
        if (amount != null) {
          this.calcExRateFromAmount(amount);
        }
      });
  }

  /**
   * Reverse-calculates the exchange rate from the given cash account amount.
   * Uses the same quotation and cost logic as calcPosTotal(data, false) but with exRate=1.0
   * to obtain rawTotal, then derives exRate from: cashaccountAmount = divideMultiplyExchangeRate(rawTotal, exRate, ...).
   *
   * @param cashaccountAmount The cash account amount entered by the user (can be negative)
   */
  private calcExRateFromAmount(cashaccountAmount: number): void {
    const pair = this.currencypair;
    if (cashaccountAmount === 0 || !pair) {
      return;
    }
    const data = this.getTransactionByForm();
    const closePosition = this.transactionCallParam().closeMarginPosition;
    if (!data.units || !data.quotation) {
      return;
    }
    // Apply the same quotation adjustments as calcPosTotal(data, false)
    let quotation = data.quotation;
    if (this.isOpenMarginInstrument()) {
      quotation = data.quotation * -1;
    } else if (this.isCloseMarginInstrument) {
      if (!closePosition) return;
      if (this.initializedSecurityOpenPositionPerSecurityaccount) {
        quotation -=
          closePosition.quotationOpenPosition /
          this.securityOpenPositionPerSecurityaccount.securityPositionSummary.splitFactorFromBaseTransaction;
      }
    }
    const taxCost = data.taxCost ? data.taxCost : 0;
    const transactionCost = data.transactionCost ? data.transactionCost : 0;
    const accruedInterest =
      !this.configObject.assetInvestmentValue1.invisible && !this.isMarginInstrument && data.assetInvestmentValue1
        ? data.assetInvestmentValue1
        : 0;
    let valuePerPoint = 1.0;
    if (this.isMarginInstrument) {
      const marginValue = data.assetInvestmentValue2 ?? this.transactionCallParam().transaction?.assetInvestmentValue2;
      if (marginValue == null) return;
      valuePerPoint = marginValue;
    }
    const rawTotal = this.transactionEditType.calcPosTotal(
      quotation,
      data.units,
      taxCost,
      transactionCost,
      accruedInterest,
      valuePerPoint
    );
    if (rawTotal === 0) {
      return;
    }
    // Reverse of divideMultiplyExchangeRate:
    // if cashCurrency == fromCurrency: cashAmount = rawTotal / exRate => exRate = rawTotal / cashAmount
    // else:                            cashAmount = rawTotal * exRate => exRate = cashAmount / rawTotal
    let exRate: number;
    if (this.currencyCashaccount === pair.fromCurrency) {
      exRate = Math.abs(rawTotal) / Math.abs(cashaccountAmount);
    } else {
      exRate = Math.abs(cashaccountAmount) / Math.abs(rawTotal);
    }
    requireFormControl(this.configObject.currencyExRate).setValue(exRate);
  }

  private calcPosTotalOnChanges(data: any): void {
    this.currencyExRateHasChanged();
    if (data.units && data.quotation) {
      if (!this.configObject.securityRisk.invisible) {
        const risk = this.calcPosTotal(data, true);
        this.setValueToControl(this.configObject.securityRisk, risk == null ? null : -risk);
      }
      const amount = this.calcPosTotal(data, false);
      this.setValueToControl(this.configObject.cashaccountAmount, amount);
      // this.setValueToControl(this.configObject.cashaccountAmountExact, this.gps.getNumberFormatRaw().format(amount));
    }
  }

  private calcPosTotal(data: any, calcSecurityRisk: boolean): number | null {
    const currencyCashaccount = this.currencyCashaccount;
    if (currencyCashaccount == null) return null;
    const closePosition = this.transactionCallParam().closeMarginPosition;
    const currencyExRate =
      !requireFormControl(this.configObject.currencyExRate).disabled && Helper.hasValue(data.currencyExRate)
        ? data.currencyExRate
        : 1.0;
    const units = data.units;

    let quotation = data.quotation;
    if (!calcSecurityRisk) {
      if (this.isOpenMarginInstrument()) {
        quotation = 0;
      } else if (this.isCloseMarginInstrument) {
        if (!closePosition) return null;
        // Calc the difference
        if (this.initializedSecurityOpenPositionPerSecurityaccount) {
          quotation -=
            closePosition.quotationOpenPosition /
            this.securityOpenPositionPerSecurityaccount.securityPositionSummary.splitFactorFromBaseTransaction;
        }
      }
    }

    const taxCost = data.taxCost ? data.taxCost : 0;
    const transactionCost = data.transactionCost ? data.transactionCost : 0;
    const accruedInterest =
      !this.configObject.assetInvestmentValue1.invisible && !this.isMarginInstrument && data.assetInvestmentValue1
        ? data.assetInvestmentValue1
        : 0;

    let valuePerPoint = 1.0;
    if (this.isMarginInstrument) {
      const marginValue = data.assetInvestmentValue2 ?? this.transactionCallParam().transaction?.assetInvestmentValue2;
      if (marginValue == null) return null;
      valuePerPoint = marginValue;
    }

    return BusinessHelper.divideMultiplyExchangeRate(
      this.transactionEditType.calcPosTotal(quotation, units, taxCost, transactionCost, accruedInterest, valuePerPoint),
      currencyExRate,
      currencyCashaccount,
      this.currencypair
    );
  }

  private setCurrencyPrefixOnFields(
    currencySecurity: string | null | undefined,
    currencyCashaccount: string | null | undefined
  ): void {
    currencySecurity &&
      Object.values(this.configObject)
        .filter((fieldConfig) => fieldConfig.userDefinedValue === 'S')
        .map((fc) => {
          DynamicFieldHelper.setCurrency(fc, currencySecurity);
        });
    currencyCashaccount &&
      Object.values(this.configObject)
        .filter((fieldConfig) => fieldConfig.userDefinedValue === 'C')
        .map((fc) => {
          DynamicFieldHelper.setCurrency(fc, currencyCashaccount);
        });
    this.adjustAmountFieldFractions(currencySecurity, currencyCashaccount);
  }

  /**
   * Applies the currency-specific decimal precision (e.g. BTC=8, JPY=0) to the settled cash amount fields.
   * Quotation fields keep the maximum fraction digits because unit prices need more digits than the currency's
   * cash precision. For open margin instruments assetInvestmentValue1 holds the daily finance cost and keeps its
   * maximum fraction digits as set by setVisibilityOnFields().
   *
   * @param currencySecurity Currency of the selected security, or null when unchanged/unknown
   * @param currencyCashaccount Currency of the selected cash account, or null when unchanged/unknown
   */
  private adjustAmountFieldFractions(
    currencySecurity: string | null | undefined,
    currencyCashaccount: string | null | undefined
  ): void {
    const maxIntegerDigits = AppSettings.FID_MAX_DIGITS - this.gps.getMaxFractionDigits();
    if (currencySecurity) {
      const precision = this.gps.getCurrencyPrecision(currencySecurity);
      DynamicFieldHelper.adjustNumberFraction(
        this.configObject.taxCost,
        AppSettings.FID_STANDARD_INTEGER_DIGITS,
        precision
      );
      DynamicFieldHelper.adjustNumberFraction(
        this.configObject.transactionCost,
        AppSettings.FID_STANDARD_INTEGER_DIGITS,
        precision
      );
      if (!this.isOpenMarginInstrument()) {
        DynamicFieldHelper.adjustNumberFraction(
          this.configObject.assetInvestmentValue1,
          AppSettings.FID_STANDARD_INTEGER_DIGITS,
          precision
        );
      }
    }
    if (currencyCashaccount) {
      const precision = this.gps.getCurrencyPrecision(currencyCashaccount);
      DynamicFieldHelper.adjustNumberFraction(
        this.configObject.taxCostCA,
        AppSettings.FID_STANDARD_INTEGER_DIGITS,
        precision
      );
      DynamicFieldHelper.adjustNumberFraction(
        this.configObject.transactionCostCA,
        AppSettings.FID_STANDARD_INTEGER_DIGITS,
        precision
      );
      DynamicFieldHelper.adjustNumberFraction(this.configObject.securityRisk, maxIntegerDigits, precision);
      DynamicFieldHelper.adjustNumberFraction(this.configObject.cashaccountAmount, maxIntegerDigits, precision);
    }
  }

  private setVisibilityOnFields(): void {
    const transactionCallParam = this.transactionCallParam();
    AppHelper.invisibleAndHide(
      this.configObject.assetInvestmentValue1,
      !(
        (transactionCallParam.transactionType === TransactionType.REDUCE ||
          transactionCallParam.transactionType === TransactionType.ACCUMULATE) &&
        (this.isBondOrConvertibleBondAndDirectInvestment() || this.isOpenMarginInstrument())
      )
    );
    if (!this.configObject.assetInvestmentValue1.invisible) {
      const numberSettings = this.configObject.assetInvestmentValue1.inputNumberSettings;
      if (!numberSettings) throw new Error('Accrued interest requires a number input configuration');
      this.configObject.assetInvestmentValue1.labelKey = this.isOpenMarginInstrument()
        ? 'DAILY_CFD_HOLDING_COST'
        : 'ACCRUED_INTEREST';
      numberSettings.allowNegative = this.isOpenMarginInstrument();
      numberSettings.maxFractionDigits = this.isOpenMarginInstrument()
        ? this.gps.getMaxFractionDigits()
        : this.gps.getStandardFractionDigits();
    }
    AppHelper.invisibleAndHide(
      this.configObject.exDate,
      transactionCallParam.transactionType !== TransactionType.DIVIDEND ||
        this.isBondOrConvertibleBondAndDirectInvestment()
    );
    AppHelper.invisibleAndHide(this.configObject.securityRisk, !this.isMarginInstrument);
    FormHelper.disableEnableFieldConfigsWhenAlreadySet(!this.isOpenMarginInstrument(), [
      this.configObject.assetInvestmentValue2
    ]);
    AppHelper.invisibleAndHide(this.configObject.assetInvestmentValue2, !this.isMarginInstrument);
    AppHelper.invisibleAndHide(
      this.configObject.taxCost,
      transactionCallParam.transactionType === TransactionType.FINANCE_COST
    );
    AppHelper.invisibleAndHide(
      this.configObject.transactionCost,
      transactionCallParam.transactionType === TransactionType.FINANCE_COST
    );

    if (this.isMarginInstrument) {
      if (!requireFormControl(this.configObject.assetInvestmentValue2).value) {
        this.setValueToControl(this.configObject.assetInvestmentValue2, 1);
      }
    }
  }

  private isBondOrConvertibleBondAndDirectInvestment(): boolean {
    return Security.isBondDirectInvestment(this.selectedSecurity?.assetClass);
  }

  private timeChangedSubscribe(propertySub: string, control: AbstractControl, hasPriority: boolean): void {
    this.subObj[propertySub] = control.valueChanges.pipe(this.untilDialogClosed()).subscribe((value) => {
      if (!requireFormControl(this.configObject.exDate).value || hasPriority) {
        if (value != null && !moment(value).isBefore(DynamicFieldHelper.minDateCalendar)) {
          if (this.transactionCallParam().security == null) {
            this.prepareAsyncActiveSecurityOptions();
          } else {
            this.portfolios && this.readSecurityaccountAndHoldings();
          }
          this.getAndSetQuotationSecurity(this.selectedSecurity);
          this.updateCurrencyExchangeRate();
        }
      }
    });
  }

  private setMarginFlags(): void {
    this.isMarginInstrument =
      BusinessHelper.isMarginProduct(this.selectedSecurity) &&
      this.transactionCallParam().transactionType !== TransactionType.FINANCE_COST;
    const transactionCallParam = this.transactionCallParam();
    this.isCloseMarginInstrument =
      this.isMarginInstrument &&
      ((!!transactionCallParam.transaction && !!transactionCallParam.transaction.connectedIdTransaction) ||
        !!transactionCallParam.closeMarginPosition);
    const transactionCallParamValue = this.transactionCallParam();
    this.isOpenMarginInstrument.set(
      this.isMarginInstrument &&
        !this.isCloseMarginInstrument &&
        (transactionCallParamValue.transactionType === TransactionType.ACCUMULATE ||
          transactionCallParamValue.transactionType === TransactionType.REDUCE)
    );
  }

  /**
   * Should called only once
   */
  private readAllPortfoliosForTenant(): void {
    this.portfolioService
      .getPortfoliosForTenantOrderByName()
      .pipe(this.untilDialogClosed())
      .subscribe((portfolios: Portfolio[]) => {
        this.portfolios = portfolios;
        this.readSecurityaccountAndHoldings();
      });
  }

  private setValueToControl(fieldConfig: FieldConfig, value: any): void {
    if (requireFormControl(fieldConfig).value !== value) {
      requireFormControl(fieldConfig).setValue(value);
    }
  }

  private setHoldingsForTime(requiredTransactionTime: number): void {
    const transaction = this.transactionCallParam().transaction;
    const transactionCallParam = this.transactionCallParam();
    const closePosition = transactionCallParam.closeMarginPosition;
    const idSecuritycurrency = transactionCallParam.idSecuritycurrency;
    if (idSecuritycurrency == null) return;
    if (this.isCloseMarginInstrument && !closePosition) return;
    if (this.holdingCheck.hasChanged(idSecuritycurrency, requiredTransactionTime)) {
      const holdingsSecurityaccountObserable: Observable<SecurityOpenPositionPerSecurityaccount> =
        this.securityService.getOpenPositionByIdSecuritycurrencyAndIdTenant(
          idSecuritycurrency,
          moment(requiredTransactionTime).format('YYYYMMDDHHmm'),
          transaction !== null,
          transaction?.idTransaction ?? null,
          this.isCloseMarginInstrument ? (closePosition?.idOpenMarginTransaction ?? null) : null
        );
      this.replaceDialogRequest('holdings', () =>
        holdingsSecurityaccountObserable
          .pipe(this.untilDialogClosed())
          .subscribe((sopps: SecurityOpenPositionPerSecurityaccount) => {
            this.securityOpenPositionPerSecurityaccount = sopps;
            this.createSecurityaccountHtmlSelect();
          })
      );
    }
  }

  private createSecurityaccountHtmlSelect(): void {
    const portfolios = this.portfolios;
    if (!portfolios) return;
    let securityaccountsHtmlSelect: ValueKeyHtmlSelectOptions[] = [];
    portfolios.forEach((portfolio) => {
      securityaccountsHtmlSelect = securityaccountsHtmlSelect.concat(
        this.createHtmlSelectKeyValue(
          portfolio.name,
          portfolio.securityaccountList,
          this.getUnitsForSecurityAccountAsString.bind(this)
        )
      );
    });
    this.configObject.idSecurityaccount.valueKeyHtmlOptions = securityaccountsHtmlSelect;
    this.toggleAccountOptionsByActiveDate(
      this.configObject.idSecurityaccount,
      portfolios.flatMap((p) => p.securityaccountList || [])
    );
    const transactionCallParam = this.transactionCallParam();
    const financeCostAccount =
      transactionCallParam.transactionType === TransactionType.FINANCE_COST
        ? transactionCallParam.transaction?.idSecurityaccount
        : null;
    if (financeCostAccount != null) {
      this.setValueToControl(this.configObject.idSecurityaccount, financeCostAccount);
      requireFormControl(this.configObject.idSecurityaccount).disable();
    } else if (securityaccountsHtmlSelect.length === 1) {
      requireFormControl(this.configObject.idSecurityaccount).disable();
      this.setValueToControl(this.configObject.idSecurityaccount, securityaccountsHtmlSelect[0].key);
    } else {
      // For Edge an empty Option is needed, otherwise the first Account would be selected
      this.configObject.idSecurityaccount.valueKeyHtmlOptions = [
        new ValueKeyHtmlSelectOptions('', ''),
        ...securityaccountsHtmlSelect
      ];
      !(this.closedMarginPosition?.hasPosition === true) &&
        requireFormControl(this.configObject.idSecurityaccount).enable();
      this.selectAccumulateSecurityaccountWhenAvailable(securityaccountsHtmlSelect);
    }
    const transactionCallParamValue = this.transactionCallParam();
    const security = transactionCallParamValue.transaction?.security;
    if (security) this.setCurrencyOnSecurityAndCashaccount(security);
  }

  /**
   * Load securities of the watchlist which were active at transaction time
   */
  private prepareAsyncActiveSecurityOptions(): void {
    if (requireFormControl(this.configObject.transactionTime).value) {
      const transactionTime: number = +requireFormControl(this.configObject.transactionTime).value;
      const timeValue: number = this.getExDate() || transactionTime;
      if (this.securities.length === 0) {
        this.loadSecuritiesOnce(timeValue);
      } else {
        // Only disable or enable securities
        BusinessSelectOptionsHelper.securitiesEnableDisableOptionsByActiveDate(
          this.securities,
          this.configObject.idSecuritycurrency,
          timeValue
        );
      }
    }
  }

  private getExDate(): number | null {
    return this.transactionCallParam().transactionType === TransactionType.DIVIDEND &&
      requireFormControl(this.configObject.exDate).value
      ? +requireFormControl(this.configObject.exDate).value
      : null;
  }

  /**
   * Disables account options that are no longer active at the relevant date (the ex-date for dividends, otherwise the
   * transaction time), so a terminated security or cash account cannot be chosen for a later-dated transaction.
   */
  private toggleAccountOptionsByActiveDate(
    fieldConfig: FieldConfig,
    accounts: { idSecuritycashAccount: number | null; activeToDate?: string | Date | null }[]
  ): void {
    const time = this.getExDate() || +requireFormControl(this.configObject.transactionTime).value;
    if (time) {
      BusinessSelectOptionsHelper.accountsEnableDisableOptionsByActiveDate(accounts, fieldConfig, time);
    }
  }

  private loadSecuritiesOnce(timeValue: number): void {
    const idWatchlist = this.transactionCallParam().idWatchList;
    if (idWatchlist == null) return;
    this.securityService
      .getTradableSecuritiesByTenantAndIdWatchlist(idWatchlist)
      .pipe(this.untilDialogClosed())
      .subscribe((securities: Security[]) => {
        this.securities = securities;

        const securitiesF = this.securities.filter(
          (security) =>
            security.idSecuritycurrency === this.transactionCallParam().idSecuritycurrency ||
            !this.transactionEditType.securityOnlyParentSelected()
        );
        BusinessSelectOptionsHelper.securityCreateValueKeyHtmlSelectOptions(
          securitiesF,
          this.configObject.idSecuritycurrency
        );
        BusinessSelectOptionsHelper.securitiesEnableDisableOptionsByActiveDate(
          this.securities,
          this.configObject.idSecuritycurrency,
          timeValue
        );
        this.selectSecurity();
      });
  }

  private getTransactionByForm(): Transaction {
    const transaction = new Transaction();
    const transactionCallParam = this.transactionCallParam();
    if (transactionCallParam.transaction) {
      Object.assign(transaction, transactionCallParam.transaction);
    }

    this.form().cleanMaskAndTransferValuesToBusinessObject(transaction);
    transaction.cashaccount =
      transaction.idCashaccount != null && this.portfolios
        ? this.getCashaccountByIdCashaccountFormPortfolios(this.portfolios, +transaction.idCashaccount)?.cashaccount
        : undefined;
    transaction.security =
      transaction.idSecuritycurrency == null ? undefined : this.getSecurityById(+transaction.idSecuritycurrency);
    transaction.transactionType = BusinessHelper.getTransactionTypeAsName(
      requireInitialized(transactionCallParam.transactionType, 'TransactionSecurityEditComponent.transactionType')
    );
    return transaction;
  }

  private initialize(): void {
    this.currencypair = null;
    this.portfolios = null;
    this.closedMarginPosition = null;
    this.holdingCheck = new ChangedIdSecurityAndTime();

    this.selectedSecurity = null;
    this.currencyCashaccount = null;
    this.securities = [];

    // Reset transaction locked state
    this.resetTransactionLocked();

    this.form().setDefaultValuesAndEnableSubmit();

    const transactionCallParam = this.transactionCallParam();
    if (transactionCallParam.transactionType === TransactionType.FINANCE_COST) {
      this.transactionEditType = new TransactionSecurityEditFinanceCost(transactionCallParam);
    } else if (transactionCallParam.transactionType === TransactionType.ACCUMULATE) {
      this.transactionEditType = new TransactionSecurityEditAccumulate(transactionCallParam);
      this.isAccumaulteOrReduce = true;
    } else {
      this.isAccumaulteOrReduce = transactionCallParam.transactionType === TransactionType.REDUCE;
      this.transactionEditType = new TransactionSecurityEditDividendReduce(transactionCallParam);
    }

    this.maxTransactionDate =
      transactionCallParam.security?.activeToDate != null &&
      new Date(transactionCallParam.security.activeToDate).getTime() < new Date().getTime()
        ? new Date(transactionCallParam.security.activeToDate)
        : (transactionCallParam.defaultTransactionTime ?? new Date());

    this.setValueToControl(this.configObject.transactionTime, this.maxTransactionDate);
    this.configObject.transactionType.valueKeyHtmlOptions = SelectOptionsHelper.createHtmlOptionsFromEnum(
      this.translateService,
      TransactionType,
      [TransactionType.ACCUMULATE, TransactionType.REDUCE, TransactionType.DIVIDEND, TransactionType.FINANCE_COST]
    );

    this.prepareSecurityOptions();
    this.readAllPortfoliosForTenant();

    this.valueChangedOnSecurityaccount();
    this.valueChangedOnCashaccountOrSecurity();
    this.valueChangedOnTransactionTimeAndExDate();

    this.valueChangedOnCalcFields();
    this.setTransactionValue();
    this.changeExistingOpenMarginPos();
  }

  private changeExistingOpenMarginPos(): void {
    const transactionCallParam = this.transactionCallParam();
    const transactionId = transactionCallParam.transaction?.idTransaction;
    if (transactionId != null && this.isOpenMarginInstrument()) {
      this.transactionService
        .getConnectedMarginPositionByIdTransaction(transactionId)
        .pipe(this.untilDialogClosed())
        .subscribe((cmp) => {
          this.closedMarginPosition = cmp;
          cmp.hasPosition && requireFormControl(this.configObject.idSecurityaccount).disable();
        });
    }
  }

  private setTransactionValue(): void {
    const transactionCallParam = this.transactionCallParam();
    const transactionType = requireInitialized(
      transactionCallParam.transactionType,
      'TransactionSecurityEditComponent.transactionType'
    );
    this.setValueToControl(this.configObject.transactionType, TransactionType[transactionType]);
    requireFormControl(this.configObject.transactionType).disable();
    AppHelper.invisibleAndHide(
      this.configObject.taxableInterest,
      transactionCallParam.transactionType !== TransactionType.DIVIDEND
    );
    transactionCallParam.transaction && this.form().transferBusinessObjectToForm(transactionCallParam.transaction);
  }

  private loadStrategyAssignments(): void {
    this.subObj['meanReversionOpening']?.unsubscribe();
    this.subObj['meanReversionAssignment']?.unsubscribe();
    const security = this.selectedSecurity?.idSecuritycurrency;
    AppHelper.invisibleAndHide(this.configObject.idAlgoStrategy, !this.isAccumaulteOrReduce);
    if (!security || !this.isAccumaulteOrReduce) return;
    const transactionCallParam = this.transactionCallParam();
    if (transactionCallParam.closeMarginPosition) {
      requireFormControl(this.configObject.idAlgoStrategy).disable();
      this.subObj['meanReversionOpening'] = this.transactionService
        .getTransactionByIdTransaction(transactionCallParam.closeMarginPosition.idOpenMarginTransaction)
        .pipe(this.untilDialogClosed())
        .subscribe((opening) => requireFormControl(this.configObject.idAlgoStrategy).setValue(opening.idAlgoStrategy));
    }
    this.subObj['meanReversionAssignment'] = this.algoAlertService
      .assignmentOptions(security)
      .pipe(this.untilDialogClosed())
      .subscribe((strategies) => {
        this.configObject.idAlgoStrategy.valueKeyHtmlOptions = [
          new ValueKeyHtmlSelectOptions(null, this.translateService.instant('UNASSIGNED')),
          ...strategies.map(
            (strategy: { idAlgoRuleStrategy: string | number; name: string }) =>
              new ValueKeyHtmlSelectOptions(strategy.idAlgoRuleStrategy, strategy.name)
          )
        ];
        const selected = requireFormControl(this.configObject.idAlgoStrategy).value;
        if (selected && !strategies.some((s: { idAlgoRuleStrategy: number }) => s.idAlgoRuleStrategy === selected))
          requireFormControl(this.configObject.idAlgoStrategy).setValue(null);
      });
  }

  /**
   * Gets the history price for a security at transaction time and set it to the form.
   *
   * @param security Security for which historical price is determined
   */
  private getAndSetQuotationSecurity(security: Security | null | undefined): void {
    const id = security?.idSecuritycurrency;
    if (!security || id == null) {
      this.cancelDialogRequest('quotation');
      return;
    }
    const transactionCallParam = this.transactionCallParam();
    if (
      transactionCallParam.transactionType === TransactionType.ACCUMULATE ||
      transactionCallParam.transactionType === TransactionType.REDUCE
    ) {
      if (!transactionCallParam.transaction) {
        const transactionTime: Date = requireFormControl(this.configObject.transactionTime).value;
        if (
          moment(transactionTime).isAfter(moment(security.sTimestamp)) ||
          moment(transactionTime).isSame(new Date(), 'day')
        ) {
          if (security.sLast) {
            this.cancelDialogRequest('quotation');
            this.setValueToControl(this.configObject.quotation, security.sLast);
          }
        } else {
          if (this.historyquoteCheck.hasChanged(id, transactionTime)) {
            this.replaceDialogRequest('quotation', () =>
              BusinessHelper.setHistoryquoteCloseToFormControl(
                this.messageToastService,
                this.historyquoteService,
                this.gps,
                transactionTime,
                id,
                true,
                requireFormControl(this.configObject.quotation)
              )
            );
          }
        }
      }
    }
  }

  private getPortfolioByIdSecurityaccount(idSecurityaccount: number): Portfolio | null {
    if (this.portfolios) {
      for (const portfolio of this.portfolios) {
        for (const securityaccount of portfolio.securityaccountList ?? []) {
          if (securityaccount.idSecuritycashAccount === idSecurityaccount) {
            return portfolio;
          }
        }
      }
    }
    return null;
  }

  private getSecurityById(idSecuritycurrency: number): Security | undefined {
    return this.securities.find((security) => security.idSecuritycurrency === idSecuritycurrency);
  }

  /**
   * There are more than one Securityaccount for a portfolio, that one with open position will be selected.
   */
  private selectAccumulateSecurityaccountWhenAvailable(securityaccountsHtmlSelect: ValueKeyHtmlSelectOptions[]) {
    const transactionCallParam = this.transactionCallParam();
    if (transactionCallParam.portfolio) {
      const securityaccount = (transactionCallParam.portfolio.securityaccountList ?? []).filter((sa) =>
        this.getUnitsForSecurityAccount(sa)
      )[0];
      securityaccount &&
        this.setValueToControl(this.configObject.idSecurityaccount, securityaccount.idSecuritycashAccount);
    }
  }

  private setCurrencyOnSecurityAndCashaccount(security: Security) {
    if (requireFormControl(this.configObject.idCashaccount).value && this.portfolios) {
      const cashaccountPortfolio = this.getCashaccountByIdCashaccountFormPortfolios(
        this.portfolios,
        +requireFormControl(this.configObject.idCashaccount).value
      );
      if (!cashaccountPortfolio) return;
      this.currencyCashaccount = cashaccountPortfolio.cashaccount.currency;
      this.setCurrencyPrefixOnFields(null, this.currencyCashaccount);
      this.currencypair = BusinessHelper.getCurrencypairWithSetOfFromAndTo(
        cashaccountPortfolio.cashaccount.currency,
        security.currency
      );
      if (this.currencypair) {
        this.enableDisableCurrencyExRate(true);
        this.configObject.currencyExRate.labelSuffix =
          this.currencypair.fromCurrency + '/' + this.currencypair.toCurrency;
        this.updateCurrencyExchangeRate();
      } else {
        this.clearCurrencyExRate();
      }
    }
  }

  private enableDisableCurrencyExRate(enable: boolean): void {
    if (enable) {
      requireFormControl(this.configObject.currencyExRate).enable();
      this.valueChangedOnCA(this.valueChangedOnquotationCA, this.configObject.quotationCA, this.configObject.quotation);
      this.valueChangedOnCA(
        this.valueChangedOnTransactionCostCA,
        this.configObject.transactionCostCA,
        this.configObject.transactionCost
      );
      this.valueChangedOnCA(this.valueChangedOnTaxCostCA, this.configObject.taxCostCA, this.configObject.taxCost);
    } else {
      this.oldCurrencyExChange = null;
      requireFormControl(this.configObject.currencyExRate).disable();
      this.unSubscribeCA(this.valueChangedOnquotationCA, this.configObject.quotationCA);
      this.unSubscribeCA(this.valueChangedOnTransactionCostCA, this.configObject.transactionCostCA);
      this.unSubscribeCA(this.valueChangedOnTaxCostCA, this.configObject.taxCostCA);
    }
    this.disableEnableExchangeRateButtons();
  }

  private valueChangedOnCA(propertySub: string, sourceField: FieldConfig, targetField: FieldConfig): void {
    AppHelper.enableAndVisibleInput(sourceField);
    if (!this.subObj[propertySub]) {
      this.subObj[propertySub] = requireFormControl(sourceField)
        .valueChanges.pipe(this.untilDialogClosed())
        .subscribe((value) => this.calculateBaseValueFromCurrencyValue(sourceField, targetField));
    }
  }

  private currencyExRateHasChanged(): void {
    if (
      !requireFormControl(this.configObject.currencyExRate).disabled &&
      this.oldCurrencyExChange !== requireFormControl(this.configObject.currencyExRate).value
    ) {
      this.oldCurrencyExChange = requireFormControl(this.configObject.currencyExRate).value;
      this.calculateBaseValueFromCurrencyValue(this.configObject.quotationCA, this.configObject.quotation);
      this.calculateBaseValueFromCurrencyValue(this.configObject.transactionCostCA, this.configObject.transactionCost);
      this.calculateBaseValueFromCurrencyValue(this.configObject.taxCostCA, this.configObject.taxCost);
    }
  }

  private calculateBaseValueFromCurrencyValue(sourceField: FieldConfig, targetField: FieldConfig): void {
    if (requireFormControl(sourceField).value && requireFormControl(this.configObject.currencyExRate).value) {
      requireFormControl(targetField).setValue(
        requireFormControl(sourceField).value / requireFormControl(this.configObject.currencyExRate).value
      );
    }
  }

  private unSubscribeCA(propertySub: string, fieldConfig: FieldConfig): void {
    this.subObj[propertySub] && this.subObj[propertySub].unsubscribe();
    delete this.subObj[propertySub];
    requireFormControl(fieldConfig).setValue(null);
    AppHelper.disableAndHideInput(fieldConfig);
  }

  private updateCurrencyExchangeRate(): void {
    const pair = this.currencypair;
    if (pair && this.hasChangedOnExistingTransaction()) {
      this.replaceDialogRequest('exchangeRate', () =>
        BusinessHelper.getAndSetQuotationCurrencypair(
          this.currencypairService,
          pair,
          requireFormControl(this.configObject.transactionTime).value,
          requireFormControl(this.configObject.currencyExRate)
        )
      );
    } else {
      this.cancelDialogRequest('exchangeRate');
    }
  }

  private clearCurrencyExRate() {
    this.cancelDialogRequest('exchangeRate');
    this.configObject.currencyExRate.labelSuffix = '';
    this.setValueToControl(this.configObject.currencyExRate, 1);
    this.enableDisableCurrencyExRate(false);
  }

  private hasChangedOnExistingTransaction(): boolean {
    const t = this.transactionCallParam().transaction;
    return (
      t === null ||
      (t !== null &&
        (t.cashaccount?.idSecuritycashAccount !== requireFormControl(this.configObject.idCashaccount).value ||
          t.security?.idSecuritycurrency !== requireFormControl(this.configObject.idSecuritycurrency).value))
    );
  }

  private createHtmlSelectKeyValue(
    portfolioName: string | null,
    securitycashaccounts: (Securityaccount | Cashaccount)[] | undefined,
    callBackFn?: (securityaccount: Securityaccount) => string
  ): ValueKeyHtmlSelectOptions[] {
    const valueKeyHtmlSelect: ValueKeyHtmlSelectOptions[] = [];
    const securityaccountOpenPositionUnits: SecurityaccountOpenPositionUnits[] =
      this.initializedSecurityOpenPositionPerSecurityaccount?.securityaccountOpenPositionUnitsList || [];

    securitycashaccounts?.forEach((securityaccount) => {
      if (securityaccount.idSecuritycashAccount == null) return;
      if (
        this.transactionEditType.acceptSecurityaccount(
          securityaccount,
          securityaccountOpenPositionUnits,
          this.isOpenMarginInstrument(),
          this.transactionCallParam()?.closeMarginPosition?.idSecurityaccount
        )
      ) {
        valueKeyHtmlSelect.push(
          new ValueKeyHtmlSelectOptions(
            securityaccount.idSecuritycashAccount,
            securityaccount.name +
              ' / ' +
              portfolioName +
              (callBackFn ? callBackFn(<Securityaccount>securityaccount) : '')
          )
        );
      }
    });
    return valueKeyHtmlSelect;
  }

  private getUnitsForSecurityAccountAsString(securityaccount: Securityaccount): string {
    const units = this.getUnitsForSecurityAccount(securityaccount);

    const transactionCallParam = this.transactionCallParam();
    if (
      this.isCloseMarginInstrument &&
      units != null &&
      securityaccount.idSecuritycashAccount === transactionCallParam.transaction?.idSecurityaccount
    ) {
      let existingUnits = Math.abs(units);
      const transaction = transactionCallParam.transaction;
      if (transaction?.idTransaction != null && transaction.units != null) {
        existingUnits = Math.min(existingUnits, transaction.units);
      }
      requireFormControl(this.configObject.units).setValue(existingUnits);
    }
    return units ? ' / ' + this.volumei18n + ': ' + units : '';
  }

  private getUnitsForSecurityAccount(securityaccount: Securityaccount): number | null {
    const securityaccountOpenPositionUnits: SecurityaccountOpenPositionUnits[] =
      this.securityOpenPositionPerSecurityaccount.securityaccountOpenPositionUnitsList;
    const securityaccountOpenPositionUnit = securityaccountOpenPositionUnits.find(
      (pos) => securityaccount.idSecuritycashAccount === pos.idSecurityaccount
    );
    return securityaccountOpenPositionUnit ? securityaccountOpenPositionUnit.units : null;
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

class ChangedIdSecurityAndTime {
  private idSecuritycurrency?: number;
  private checkTime: any;

  hasChanged(idSecuritycurrency: number, checkTime: any): boolean {
    if (this.idSecuritycurrency !== idSecuritycurrency || this.checkTime !== checkTime) {
      this.idSecuritycurrency = idSecuritycurrency;
      this.checkTime = checkTime;
      return true;
    }
    return false;
  }
}
