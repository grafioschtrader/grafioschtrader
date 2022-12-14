import {Component, EventEmitter, Input, OnInit, Output, ViewChild} from '@angular/core';
import {MessageToastService} from '../../shared/message/message.toast.service';
import {PortfolioService} from '../../portfolio/service/portfolio.service';
import {GlobalparameterService} from '../../shared/service/globalparameter.service';
import {Portfolio} from '../../entities/portfolio';
import {Cashaccount} from '../../entities/cashaccount';
import {Securityaccount} from '../../entities/securityaccount';
import {Security} from '../../entities/security';
import {TransactionType} from '../../shared/types/transaction.type';
import {AppHelper} from '../../shared/helper/app.helper';
import {SecurityService} from '../../securitycurrency/service/security.service';
import {Transaction} from '../../entities/transaction';
import {TransactionService} from '../service/transaction.service';
import {SecurityOpenPositionPerSecurityaccount} from '../../entities/view/security.open.position.per.securityaccount';
import {CurrencypairService} from '../../securitycurrency/service/currencypair.service';
import {ITransactionEditType} from './i.transaction.edit.type';
import {TransactionSecurityEditAccumulate} from './transaction.security.edit.accumulate';
import {
  TransactionSecurityEditDividendReduce,
  TransactionSecurityEditFinanceCost
} from './transaction.security.edit.reduce';
import {SecurityaccountOpenPositionUnits} from '../../entities/view/securityaccount.open.position.units';
import {HistoryquoteService} from '../../historyquote/service/historyquote.service';
import * as moment from 'moment';
import {ActivatedRoute} from '@angular/router';
import {ProcessedActionData} from '../../shared/types/processed.action.data';
import {ProcessedAction} from '../../shared/types/processed.action';
import {TransactionCallParam} from './transaction.call.parm';
import {TranslateService} from '@ngx-translate/core';
import {ValueKeyHtmlSelectOptions} from '../../dynamic-form/models/value.key.html.select.options';
import {Helper} from '../../helper/helper';
import {InfoLevelType} from '../../shared/message/info.leve.type';
import {merge, Observable, Subscription} from 'rxjs';

import {AssetclassType} from '../../shared/types/assetclass.type';
import {SpecialInvestmentInstruments} from '../../shared/types/special.investment.instruments';
import {FieldConfig} from '../../dynamic-form/models/field.config';
import {FormConfig} from '../../dynamic-form/models/form.config';
import {DataType} from '../../dynamic-form/models/data.type';
import {FieldFormGroup} from '../../dynamic-form/models/form.group.definition';
import {TransactionBaseOperations} from './transaction.base.operations';
import {DynamicFormComponent} from '../../dynamic-form/containers/dynamic-form/dynamic-form.component';
import {HelpIds} from '../../shared/help/help.ids';
import {map} from 'rxjs/operators';
import {FormDefinitionHelper} from '../../shared/edit/form.definition.helper';
import {DynamicFieldHelper} from '../../shared/helper/dynamic.field.helper';
import {SelectOptionsHelper} from '../../shared/helper/select.options.helper';
import {BusinessHelper} from '../../shared/helper/business.helper';
import {TranslateHelper} from '../../shared/helper/translate.helper';
import {FormHelper} from '../../dynamic-form/components/FormHelper';
import {ClosedMarginPosition} from '../model/closed.margin.position';
import {AbstractControl} from '@angular/forms';
import {AppSettings} from '../../shared/app.settings';

/**
 * Edit transaction for an investment product, also margin product. The transaction type (buy, dividend, sell,
 * finance cost) can not be changed in this form, it has to be set in advance.
 */
@Component({
  selector: 'transaction-security-edit',
  template: `
    <p-dialog [(visible)]="visibleSecurityTransactionDialog"
              [responsive]="true" [style]="{width: '640px'}" [resizable]="false"
              (onShow)="onShow($event)" (onHide)="onHide($event)" [modal]="true">
      <p-header>
        <h4>{{'INVESTMENT_TRANSACTION' | translate}}</h4>
        <div *ngIf="isOpenMarginInstrument && !transactionCallParam.transaction">
          <p class="big-size">{{'MARGIN_BUY_SELL' | translate}}</p>
        </div>
      </p-header>

      <dynamic-form [config]="config" [formConfig]="formConfig" [translateService]="translateService"
                    #form="dynamicForm"
                    (submitBt)="submit($event)">
      </dynamic-form>
    </p-dialog>
  `
})
export class TransactionSecurityEditComponent extends TransactionBaseOperations implements OnInit {

  // InputMask from parent view
  @Input() visibleSecurityTransactionDialog: boolean;
  @Input() transactionCallParam: TransactionCallParam;

  // Access the form
  @ViewChild(DynamicFormComponent) form: DynamicFormComponent;

  @Output() closeDialog = new EventEmitter<ProcessedActionData>();

  // Form configuration
  config: FieldFormGroup[] = [];
  isOpenMarginInstrument: boolean;
  formConfig: FormConfig;
  // configObject: { [name: string]: FieldConfig };
  // Transaction depentable
  transactionEditType: ITransactionEditType;
  // Data from Server
  private securities: Security [] = [];
  private portfolios: Portfolio[];
  private securityOpenPositionPerSecurityaccount: SecurityOpenPositionPerSecurityaccount;
  // Form content variables

  private selectedSecurity: Security;
  private isAccumaulteOrReduce;
  private volumei18n: string = null;
  private currencyCashaccount: string;
  private maxTransactionDate = new Date();
  private oldCurrencyExChange: number;
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

  private isMarginInstrument: boolean;
  private closedMarginPosition: ClosedMarginPosition;

  private isCloseMarginInstrument: boolean;

  // When this changes, the holdings must be reloaded
  private holdingCheck = new ChangedIdSecurityAndTime();
  // When this changedes the price of historyquote will be reloaded
  private historyquoteCheck = new ChangedIdSecurityAndTime();

  constructor(private transactionService: TransactionService,
              private portfolioService: PortfolioService,
              private securityService: SecurityService,
              private activeRoute: ActivatedRoute,
              messageToastService: MessageToastService,
              currencypairService: CurrencypairService,
              historyquoteService: HistoryquoteService,
              translateService: TranslateService,
              gps: GlobalparameterService) {
    super(messageToastService, currencypairService, historyquoteService, translateService, gps);
  }

  ngOnInit(): void {
    if (this.transactionCallParam.transaction) {
      this.transactionCallParam.transactionType = TransactionType[this.transactionCallParam.transaction.transactionType];
      this.transactionCallParam.idSecuritycurrency = this.transactionCallParam.security.idSecuritycurrency;
      this.transactionCallParam.transaction.security = this.transactionCallParam.security;
      this.historyquoteCheck.hasChanged(this.transactionCallParam.idSecuritycurrency,
        this.transactionCallParam.transaction.transactionTime);
    }

    this.formConfig = AppHelper.getDefaultFormConfig(this.gps,
      4, this.helpLink.bind(this));

    const calcGroupConfig: FieldConfig[] = [
      DynamicFieldHelper.createFieldCurrencyNumber('currencyExRate', 'EXCHANGE_RATE', true,
        7, AppSettings.FID_MAX_FRACTION_DIGITS, false,
        this.gps.getNumberCurrencyMask(), false, {usedLayoutColumns: 8}),

      ...this.createExRateButtons(),

      this.createQuotationField(),
      DynamicFieldHelper.createFieldCurrencyNumber('quotation', 'QUOTATION_DIV', true,
        AppSettings.FID_MAX_DIGITS - AppSettings.FID_MAX_FRACTION_DIGITS,
        AppSettings.FID_MAX_FRACTION_DIGITS, this.transactionCallParam.transactionType === TransactionType.DIVIDEND
        || this.transactionCallParam.transactionType === TransactionType.FINANCE_COST,
        this.gps.getNumberCurrencyMask(), true, {userDefinedValue: 'S', usedLayoutColumns: 8}),
      DynamicFieldHelper.createFieldCurrencyNumber('quotationCA', '_CA', false,
        AppSettings.FID_MAX_DIGITS - AppSettings.FID_MAX_FRACTION_DIGITS,
        AppSettings.FID_MAX_FRACTION_DIGITS, true, this.gps.getNumberCurrencyMask(),
        true, {userDefinedValue: 'C', usedLayoutColumns: 4}),

      DynamicFieldHelper.createFieldCurrencyNumberHeqF('taxCost', false,
        AppSettings.FID_STANDARD_INTEGER_DIGITS, AppSettings.FID_STANDARD_FRACTION_DIGITS, false,
        this.gps.getNumberCurrencyMask(), true, {userDefinedValue: 'S', usedLayoutColumns: 8}),

      DynamicFieldHelper.createFieldCurrencyNumber('taxCostCA', '_CA', false,
        AppSettings.FID_STANDARD_INTEGER_DIGITS, AppSettings.FID_STANDARD_FRACTION_DIGITS, false,
        this.gps.getNumberCurrencyMask(), true, {userDefinedValue: 'C', usedLayoutColumns: 4}),

      DynamicFieldHelper.createFieldCurrencyNumberHeqF('transactionCost', false,
        AppSettings.FID_STANDARD_INTEGER_DIGITS, AppSettings.FID_STANDARD_FRACTION_DIGITS, false,
        this.gps.getNumberCurrencyMask(), true, {userDefinedValue: 'S', usedLayoutColumns: 8}),

      DynamicFieldHelper.createFieldCurrencyNumber('transactionCostCA', '_CA', false,
        AppSettings.FID_STANDARD_INTEGER_DIGITS, AppSettings.FID_STANDARD_FRACTION_DIGITS, false,
        this.gps.getNumberCurrencyMask(), true, {userDefinedValue: 'C', usedLayoutColumns: 4}),

      // Used for accrued interest and daily finance cost for margin instrument
      DynamicFieldHelper.createFieldCurrencyNumber('assetInvestmentValue1', 'ACCRUED_INTEREST',
        false, AppSettings.FID_STANDARD_INTEGER_DIGITS, AppSettings.FID_MAX_FRACTION_DIGITS - 3,
        true,
        this.gps.getNumberCurrencyMask(), true, {userDefinedValue: 'S'}),

      DynamicFieldHelper.createFieldCurrencyNumber('assetInvestmentValue2', 'VALUE_PER_POINT',
        false, AppSettings.FID_STANDARD_INTEGER_DIGITS - 3, 0, true,
        this.gps.getNumberCurrencyMask(), true)
    ];

    this.config = [
      DynamicFieldHelper.createFieldSelectNumberHeqF('transactionType', true),
      FormDefinitionHelper.getTransactionTime(true),
      DynamicFieldHelper.createFieldPcalendarHeqF(DataType.DateNumeric, 'exDate', false,
        {calendarConfig: {disabledDays: [0, 6]}}),
      DynamicFieldHelper.createFieldSelectNumber('idSecuritycurrency', AppSettings.SECURITY.toUpperCase(), true,
        {dataproperty: 'security.idSecuritycurrency'}),
      DynamicFieldHelper.createFieldSelectNumber('idSecurityaccount', AppSettings.SECURITYACCOUNT.toUpperCase(), true),
      DynamicFieldHelper.createFieldSelectNumber('idCashaccount', AppSettings.CASHACCOUNT.toUpperCase(), true,
        {dataproperty: 'cashaccount.idSecuritycashAccount'}),
      DynamicFieldHelper.createFieldCheckboxHeqF('taxableInterest', {defaultValue: true}),
      {formGroupName: 'calcGroup', fieldConfig: calcGroupConfig},

      DynamicFieldHelper.createFieldCurrencyNumberHeqF('securityRisk', false,
        9, 2, true, this.gps.getNumberCurrencyMask(),
        true, {readonly: true, userDefinedValue: 'C'}),

      DynamicFieldHelper.createFieldCurrencyNumberHeqF('cashaccountAmount', false,
        9, 2, true, this.gps.getNumberCurrencyMask(),
        true, {readonly: true, userDefinedValue: 'C', usedLayoutColumns: 8}),
      /*
       DynamicFieldHelper.createFieldInputString('cashaccountAmountExact', '_exact',
         15, false, { dataproperty: 'cashaccountAmount', readonly: true,  usedLayoutColumns: 4}),
 */
      DynamicFieldHelper.createFieldTextareaInputStringHeqF('note', AppSettings.FID_MAX_LETTERS, false),
      DynamicFieldHelper.createSubmitButton()
    ];

    this.configObject = TranslateHelper.prepareFieldsAndErrors(this.translateService, this.config);
    this.translateService.get('VOLUME').subscribe(text => this.volumei18n = text);
    this.setCurrencyPrefixOnFields(null, this.transactionCallParam.transaction?.cashaccount.currency);
  }

  isVisibleDialog(): boolean {
    return this.visibleSecurityTransactionDialog;
  }

  valueChangedOnCalcFields(): void {
    this.subObj[this.valueChangedOnValueCalcFields] = this.configObject.calcGroup.formControl.valueChanges.subscribe(data => {
      this.calcPosTotalOnChanges(data);
    });
  }

  /**
   * Probably a currency pair is introduced when cash account or Security changes. Because currency of cash account
   * and Security may be different.
   */
  valueChangedOnCashaccountOrSecurity(): void {
    this.clearCurrencyExRate();
    const obs1 = this.configObject.idCashaccount.formControl.valueChanges.pipe(map(v => ({
      control: this.configObject.idCashaccount.formControl,
      value: v
    })));

    const obs2 = (this.configObject.idSecuritycurrency.formControl.valueChanges.pipe(map(v => ({
      control: this.configObject.idSecuritycurrency.formControl,
      value: v
    }))));

    this.subObj[this.cashaccountSecurity] = merge(obs1, obs2).subscribe(
      (controlValue) => {
        if (this.configObject.idSecuritycurrency.formControl.value != null) {
          this.selectedSecurity = this.getSecurityById(+this.configObject.idSecuritycurrency.formControl.value);
          if (this.selectedSecurity) {
            this.transactionCallParam.idSecuritycurrency = this.selectedSecurity.idSecuritycurrency;
            if (!this.transactionCallParam.transaction && this.transactionCallParam.transactionType !== TransactionType.DIVIDEND) {
              this.getAndSetQuotationSecurity(this.selectedSecurity);
            }
            if (controlValue.control === this.configObject.idSecuritycurrency.formControl) {
              this.readSecurityaccountAndHoldings();
            }
            this.setCurrencyOnSecurityAndCashaccount(this.selectedSecurity);
            this.setMarginFlags();
            this.setVisibilityOnFields();
            this.calcPosTotalOnChanges(this.getTransactionByForm());
            this.setCurrencyPrefixOnFields(this.selectedSecurity.currency, this.currencyCashaccount);
          }
        }
      }
    );
  }

  /**
   * When transaction time changes, possible the available securities and possible holdings need a update.
   */
  valueChangedOnTransactionTimeAndExDate(): void {
    this.timeChangedSubscribe(this.valueChangedOnTransactionTime, this.configObject.transactionTime.formControl, false);
    this.timeChangedSubscribe(this.valueChangedOnExDate, this.configObject.exDate.formControl, true);
  }

  valueChangedOnSecurityaccount(): void {
    this.subObj[this.valueChangedOnIdSecurityaccount] = this.configObject.idSecurityaccount.formControl.valueChanges.subscribe(value => {
      // Change cashaccount when securityaccount was changed
      const portfolio = this.getPortfolioByIdSecurityaccount(+value);
      if (portfolio != null) {
        this.configObject.idCashaccount.valueKeyHtmlOptions = this.createHtmlSelectKeyValue(portfolio.name,
          portfolio.cashaccountList);

        this.selectedSecurity = this.getSecurityById(this.configObject.idSecuritycurrency.formControl.value);

        if (this.selectedSecurity && !this.transactionCallParam.transaction) {
          // select default cashaccount depending on selected currency of the security
          const cashaccounts: Cashaccount[] = portfolio.cashaccountList.filter(cashaccount =>
            cashaccount.currency === this.selectedSecurity.currency);
          if (cashaccounts.length > 0) {
            this.setValueToControl(this.configObject.idCashaccount, cashaccounts[0].idSecuritycashAccount);
            this.currencyCashaccount = this.selectedSecurity.currency;
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
      let checkTime: number = +this.configObject.transactionTime.formControl.value;
      if (this.isCloseMarginInstrument) {
        this.setHoldingsForTime(checkTime);
        // this.createSecurityaccountHtmlSelect();
      } else {
        const exDate = this.getExDate();
        checkTime = (exDate) ? moment(+exDate).set({
          hour: 23,
          minute: 59,
          second: 59
        }).toDate().getTime() : checkTime;
        this.setHoldingsForTime(checkTime);
      }
    }
  }

  prepareSecurityOptions(): void {
    if (this.transactionCallParam.security) {
      const securitiesHtmlSelect: ValueKeyHtmlSelectOptions[] = [];
      securitiesHtmlSelect.push(
        new ValueKeyHtmlSelectOptions(this.transactionCallParam.security.idSecuritycurrency,
          this.transactionCallParam.security.name
          + ' / ' + this.transactionCallParam.security.currency));
      this.transactionCallParam.idSecuritycurrency = this.transactionCallParam.security.idSecuritycurrency;
      this.securities.push(this.transactionCallParam.security);
      this.configObject.idSecuritycurrency.valueKeyHtmlOptions = securitiesHtmlSelect;
      this.selectSecurity();
    } else {
      this.prepareAsyncActiveSecurityOptions();
    }
  }

  selectSecurity(): void {
    this.setValueToControl(this.configObject.idSecuritycurrency, this.transactionCallParam.idSecuritycurrency);
    this.selectedSecurity = this.getSecurityById(this.configObject.idSecuritycurrency.formControl.value);
    this.setMarginFlags();
    if (this.configObject.idSecuritycurrency.valueKeyHtmlOptions.length === 1) {
      this.configObject.idSecuritycurrency.formControl.disable();
    } else {
      this.configObject.idSecuritycurrency.formControl.enable();
    }
  }

  public onShow(event) {
    setTimeout(() => this.initialize());
  }

  onHide(event) {
    Object.keys(this.subObj).forEach(key => this.subObj[key] && this.subObj[key].unsubscribe());
    this.closeDialog.emit(new ProcessedActionData(ProcessedAction.NO_CHANGE));
  }

  submit(value: { [name: string]: any }) {
    const transaction = this.getTransactionByForm();
    if (this.isMarginInstrument) {
      transaction.securityRisk = this.calcPosTotal(transaction, true) * -1;
      transaction.cashaccountAmount = this.calcPosTotal(transaction, false);
    } else {
      transaction.cashaccountAmount = this.calcPosTotal(transaction, false);
    }

    if (this.currencypair != null) {
      this.currencypairService.findOrCreateCurrencypairByFromAndToCurrency(this.currencypair.fromCurrency,
        this.currencypair.toCurrency).subscribe(currencypair => {
        transaction.idCurrencypair = currencypair.idSecuritycurrency;
        this.saveTransaction(transaction);
      });
    } else {
      this.saveTransaction(transaction);
    }
  }

  saveTransaction(transaction: Transaction) {
    this.transactionService.updateCreateSecurityTrans(transaction).subscribe({
      next: newTransaction => {
        this.messageToastService.showMessageI18n(InfoLevelType.SUCCESS, 'MSG_RECORD_SAVED',
          {i18nRecord: AppSettings.TRANSACTION.toUpperCase()});
        this.closeDialog.emit(new ProcessedActionData(transaction.idTransaction ? ProcessedAction.UPDATED
          : ProcessedAction.CREATED, newTransaction));
      }, error: () => this.configObject.submit.disabled = false
    });
  }

  helpLink(): void {
    BusinessHelper.toExternalHelpWebpage(this.gps.getUserLang(),
      this.isMarginInstrument ? HelpIds.HELP_TRANSACTION_MARGIN_BASED : HelpIds.HELP_TRANSACTION_CASH_BASED);
  }

  private createQuotationField(): FieldConfig {
    if (this.transactionCallParam.transactionType === TransactionType.FINANCE_COST) {
      return DynamicFieldHelper.createFieldCurrencyNumber('units', 'NUMBER_OF_DAYS', true,
        4, 0, false, this.gps.getNumberCurrencyMask(), true);
    } else {
      return DynamicFieldHelper.createFieldCurrencyNumber('units', 'QUANTITY', true,
        9, 3, false, this.gps.getNumberCurrencyMask(), false);
    }
  }

  private calcPosTotalOnChanges(data: any): void {
    this.currencyExRateHasChanged();
    if (data.units && data.quotation) {
      if (!this.configObject.securityRisk.invisible) {
        this.setValueToControl(this.configObject.securityRisk, (this.calcPosTotal(data, true) * -1));
      }
      const amount = this.calcPosTotal(data, false);
      this.setValueToControl(this.configObject.cashaccountAmount, amount);
      // this.setValueToControl(this.configObject.cashaccountAmountExact, this.gps.getNumberFormatRaw().format(amount));
    }
  }

  private calcPosTotal(data: any, calcSecurityRisk): number {
    const currencyExRate = (!this.configObject.currencyExRate.formControl.disabled
      && Helper.hasValue(data.currencyExRate)) ? data.currencyExRate : 1.0;
    const units = data.units;

    let quotation = data.quotation;
    if (!calcSecurityRisk) {
      if (this.isOpenMarginInstrument) {
        quotation = 0;
      } else if (this.isCloseMarginInstrument) {
        // Calc the difference
        if (this.securityOpenPositionPerSecurityaccount) {
          quotation -= this.transactionCallParam.closeMarginPosition.quotationOpenPosition /
            this.securityOpenPositionPerSecurityaccount.securityPositionSummary.splitFactorFromBaseTransaction;
        }
      }
    }

    const taxCost = data.taxCost ? data.taxCost : 0;
    const transactionCost = data.transactionCost ? data.transactionCost : 0;
    const accruedInterest = (!this.configObject.assetInvestmentValue1.invisible
      && !this.isMarginInstrument && data.assetInvestmentValue1) ? data.assetInvestmentValue1 : 0;

    let valuePerPoint = 1.0;
    if (this.isMarginInstrument) {
      valuePerPoint = data.assetInvestmentValue2 ? data.assetInvestmentValue2 : this.transactionCallParam.transaction.assetInvestmentValue2;
    }

    return BusinessHelper.divideMultiplyExchangeRate(
      this.transactionEditType.calcPosTotal(quotation, units, taxCost, transactionCost, accruedInterest, valuePerPoint),
      currencyExRate, this.currencyCashaccount, this.currencypair);
  }

  private setCurrencyPrefixOnFields(currencySecurity: string, currencyCashaccount): void {
    currencySecurity && Object.values(this.configObject).filter(fieldConfig => fieldConfig.userDefinedValue === 'S')
      .map(fc => {
        fc.currencyMaskConfig.prefix = AppHelper.addSpaceToCurrency(currencySecurity);
        /*
         DynamicFieldHelper.adjustNumberFraction(fc, AppSettings.FID_MAX_INTEGER_DIGITS,
           this.gps.getCurrencyPrecision(currencySecurity));
         */
      });
    currencyCashaccount &&
    Object.values(this.configObject).filter(fieldConfig => fieldConfig.userDefinedValue === 'C')
      .map(fc => {
        fc.currencyMaskConfig.prefix = AppHelper.addSpaceToCurrency(this.currencyCashaccount);
        /*
                DynamicFieldHelper.adjustNumberFraction(fc, AppSettings.FID_MAX_INTEGER_DIGITS,
                  this.gps.getCurrencyPrecision(this.currencyCashaccount));
        */
      });
  }

  private setVisibilityOnFields(): void {
    AppHelper.invisibleAndHide(this.configObject.assetInvestmentValue1, !((this.transactionCallParam.transactionType
        === TransactionType.REDUCE || this.transactionCallParam.transactionType === TransactionType.ACCUMULATE) &&
      (this.isBondOrConvertibleBondAndDirectInvestment() || this.isOpenMarginInstrument)));
    if (!this.configObject.assetInvestmentValue1.invisible) {
      this.configObject.assetInvestmentValue1.labelKey = this.isOpenMarginInstrument ? 'DAILY_CFD_HOLDING_COST'
        : 'ACCRUED_INTEREST';
      this.configObject.assetInvestmentValue1.currencyMaskConfig.allowNegative = this.isOpenMarginInstrument;
      this.configObject.assetInvestmentValue1.currencyMaskConfig.precision = this.isOpenMarginInstrument
        ? AppSettings.FID_MAX_FRACTION_DIGITS : AppSettings.FID_STANDARD_FRACTION_DIGITS;
    }
    AppHelper.invisibleAndHide(this.configObject.exDate, this.transactionCallParam.transactionType !== TransactionType.DIVIDEND
      || this.isBondOrConvertibleBondAndDirectInvestment());
    AppHelper.invisibleAndHide(this.configObject.securityRisk, !this.isMarginInstrument);
    FormHelper.disableEnableFieldConfigsWhenAlreadySet(!this.isOpenMarginInstrument,
      [this.configObject.assetInvestmentValue2]);
    AppHelper.invisibleAndHide(this.configObject.assetInvestmentValue2, !this.isMarginInstrument);
    AppHelper.invisibleAndHide(this.configObject.taxCost, this.transactionCallParam.transactionType
      === TransactionType.FINANCE_COST);
    AppHelper.invisibleAndHide(this.configObject.transactionCost,
      this.transactionCallParam.transactionType === TransactionType.FINANCE_COST);

    if (this.isMarginInstrument) {
      if (!this.configObject.assetInvestmentValue2.formControl.value) {
        this.setValueToControl(this.configObject.assetInvestmentValue2, 1);
      }
    }
  }

  private isBondOrConvertibleBondAndDirectInvestment(): boolean {
    return (this.selectedSecurity.assetClass.categoryType === AssetclassType[AssetclassType.FIXED_INCOME]
        || this.selectedSecurity.assetClass.categoryType === AssetclassType[AssetclassType.CONVERTIBLE_BOND])
      && this.selectedSecurity.assetClass.specialInvestmentInstrument
      === SpecialInvestmentInstruments[SpecialInvestmentInstruments.DIRECT_INVESTMENT];
  }

  private timeChangedSubscribe(propertySub: string, control: AbstractControl, hasPriority: boolean): void {
    this.subObj[propertySub] = control.valueChanges.subscribe(value => {
      if (!this.configObject.exDate.formControl.value || hasPriority) {
        if (value != null && !moment(value).isBefore(DynamicFieldHelper.minDateCalendar)) {
          if (this.transactionCallParam.security == null) {
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
    this.isMarginInstrument = BusinessHelper.isMarginProduct(this.selectedSecurity)
      && this.transactionCallParam.transactionType !== TransactionType.FINANCE_COST;
    this.isCloseMarginInstrument = this.isMarginInstrument && ((!!this.transactionCallParam.transaction
        && !!this.transactionCallParam.transaction.connectedIdTransaction)
      || !!this.transactionCallParam.closeMarginPosition);
    this.isOpenMarginInstrument = this.isMarginInstrument && !this.isCloseMarginInstrument
      && (this.transactionCallParam.transactionType === TransactionType.ACCUMULATE
        || this.transactionCallParam.transactionType === TransactionType.REDUCE);
  }

  /**
   * Should called only once
   */
  private readAllPortfoliosForTenant(): void {
    this.portfolioService.getPortfoliosForTenantOrderByName().subscribe((portfolios: Portfolio[]) => {
      this.portfolios = portfolios;
      this.readSecurityaccountAndHoldings();
    });
  }

  private setValueToControl(fieldConfig: FieldConfig, value: any): void {
    if (fieldConfig.formControl.value !== value) {
      fieldConfig.formControl.setValue(value);
    }
  }

  private setHoldingsForTime(requiredTransactionTime: number): void {
    const transaction = this.transactionCallParam.transaction;
    if (this.holdingCheck.hasChanged(this.transactionCallParam.idSecuritycurrency, requiredTransactionTime)) {
      const holdingsSecurityaccountObserable: Observable<SecurityOpenPositionPerSecurityaccount> =
        this.securityService.getOpenPositionByIdSecuritycurrencyAndIdTenant(this.transactionCallParam.idSecuritycurrency,
          moment(requiredTransactionTime).format('YYYYMMDDHHmm'),
          transaction !== null, (transaction) ? transaction.idTransaction : null,
          this.isCloseMarginInstrument ?
            this.transactionCallParam.closeMarginPosition.idOpenMarginTransaction : null);
      holdingsSecurityaccountObserable.subscribe((sopps: SecurityOpenPositionPerSecurityaccount) => {
          this.securityOpenPositionPerSecurityaccount = sopps;
          this.createSecurityaccountHtmlSelect();
        }
      );
    }
  }

  private createSecurityaccountHtmlSelect(): void {
    let securityaccountsHtmlSelect: ValueKeyHtmlSelectOptions[] = [];
    this.portfolios.forEach(portfolio => {
      securityaccountsHtmlSelect = securityaccountsHtmlSelect.concat(this.createHtmlSelectKeyValue(portfolio.name,
        portfolio.securityaccountList, this.getUnitsForSecurityAccountAsString.bind(this)));
    });
    this.configObject.idSecurityaccount.valueKeyHtmlOptions = securityaccountsHtmlSelect;
    // if there is only one security account, select it
    if (securityaccountsHtmlSelect.length === 1) {
      this.configObject.idSecurityaccount.formControl.disable();
      this.setValueToControl(this.configObject.idSecurityaccount, securityaccountsHtmlSelect[0].key);
    } else {
      // For Edge a empty Option is needed, otherwise the first Account would be selected
      securityaccountsHtmlSelect.splice(0, 0, new ValueKeyHtmlSelectOptions('', ''));
      this.configObject.idSecurityaccount.formControl.enable();
      this.selectAccumulteSecurityaccountWhenAvailable(securityaccountsHtmlSelect);
    }
    this.transactionCallParam.transaction && this.setCurrencyOnSecurityAndCashaccount(this.transactionCallParam.transaction.security);
  }

  /**
   * Load securities of the watchlist which were active at transaction time
   */
  private prepareAsyncActiveSecurityOptions(): void {
    if (this.configObject.transactionTime.formControl.value) {
      const transactionTime: number = +this.configObject.transactionTime.formControl.value;
      const timeValue: number = this.getExDate() || transactionTime;
      if (this.securities.length === 0) {
        this.loadSecuritiesOnce(timeValue);
      } else {
        // Only disable or enable securities
        SelectOptionsHelper.securitiesEnableDisableOptionsByActiveDate(this.securities,
          this.configObject.idSecuritycurrency, timeValue);
      }
    }
  }

  private getExDate(): number {
    return this.transactionCallParam.transactionType === TransactionType.DIVIDEND
    && this.configObject.exDate.formControl.value ?
      +this.configObject.exDate.formControl.value : null;
  }

  private loadSecuritiesOnce(timeValue: number): void {
    this.securityService.getTradableSecuritiesByTenantAndIdWatschlist(this.transactionCallParam.idWatchList).subscribe(
      (securities: Security[]) => {
        this.securities = securities;

        const securitiesF = this.securities.filter(security =>
          security.idSecuritycurrency === this.transactionCallParam.idSecuritycurrency ||
          !this.transactionEditType.securityOnlyParentSelected());
        SelectOptionsHelper.securityCreateValueKeyHtmlSelectOptions(securitiesF, this.configObject.idSecuritycurrency);
        SelectOptionsHelper.securitiesEnableDisableOptionsByActiveDate(this.securities,
          this.configObject.idSecuritycurrency, timeValue);
        this.selectSecurity();
      });
  }

  private getTransactionByForm(): Transaction {
    const transaction = new Transaction();
    if (this.transactionCallParam.transaction) {
      Object.assign(transaction, this.transactionCallParam.transaction);
    }

    this.form.cleanMaskAndTransferValuesToBusinessObject(transaction);
    if (transaction.idCashaccount && this.portfolios) {
      transaction.cashaccount = this.getCashaccountByIdCashaccountFormPortfolios(this.portfolios, +transaction.idCashaccount).cashaccount;
    }
    transaction.security = this.getSecurityById(+transaction.idSecuritycurrency);
    transaction.transactionType = BusinessHelper.getTransactionTypeAsName(this.transactionCallParam.transactionType);
    return transaction;
  }

  private initialize(): void {
    this.currencypair = null;

    this.selectedSecurity = null;
    this.currencyCashaccount = null;
    this.securities = [];

    this.form.setDefaultValuesAndEnableSubmit();

    if (this.transactionCallParam.transactionType === TransactionType.FINANCE_COST) {
      this.transactionEditType = new TransactionSecurityEditFinanceCost(this.transactionCallParam);
    } else if (this.transactionCallParam.transactionType === TransactionType.ACCUMULATE) {
      this.transactionEditType = new TransactionSecurityEditAccumulate(this.transactionCallParam);
      this.isAccumaulteOrReduce = true;
    } else {
      this.isAccumaulteOrReduce = this.transactionCallParam.transactionType === TransactionType.REDUCE;
      this.transactionEditType = new TransactionSecurityEditDividendReduce(this.transactionCallParam);
    }

    this.maxTransactionDate = (this.transactionCallParam.security
      && new Date(this.transactionCallParam.security.activeToDate).getTime() < new Date().getTime())
      ? new Date(this.transactionCallParam.security.activeToDate) : this.transactionCallParam.defaultTransactionTime;
    this.maxTransactionDate = this.maxTransactionDate || new Date();

    this.setValueToControl(this.configObject.transactionTime, this.maxTransactionDate);
    this.configObject.transactionType.valueKeyHtmlOptions = SelectOptionsHelper.createHtmlOptionsFromEnum(
      this.translateService, TransactionType,
      [TransactionType.ACCUMULATE, TransactionType.REDUCE,
        TransactionType.DIVIDEND, TransactionType.FINANCE_COST]);

    this.prepareSecurityOptions();
    this.readAllPortfoliosForTenant();

    this.valueChangedOnSecurityaccount();
    this.valueChangedOnCashaccountOrSecurity();
    this.valueChangedOnTransactionTimeAndExDate();

    this.valueChangedOnCalcFields();
    this.setTransactionValue();
    this.changeExistingOpenMarginPos();
  }

  private changeExistingOpenMarginPos() {
    if (this.transactionCallParam.transaction && this.isOpenMarginInstrument) {
      this.transactionService.getConnectedMarginPositionByIdTransaction(this.transactionCallParam.transaction.idTransaction)
        .subscribe(cmp => {
          this.closedMarginPosition = cmp;
          cmp.hasPosition && this.configObject.idSecurityaccount.formControl.disable();
        });
    }
  }

  private setTransactionValue() {
    this.setValueToControl(this.configObject.transactionType, TransactionType[this.transactionCallParam.transactionType]);
    this.configObject.transactionType.formControl.disable();
    AppHelper.invisibleAndHide(this.configObject.taxableInterest, this.transactionCallParam.transactionType !== TransactionType.DIVIDEND);
    this.transactionCallParam.transaction && this.form.transferBusinessObjectToForm(this.transactionCallParam.transaction);
  }

  /**
   * Gets the history price for a security at transaction time and set it to the form.
   *
   * @param security Security for which historical price is determined
   */
  private getAndSetQuotationSecurity(security: Security): void {
    if (this.transactionCallParam.transactionType === TransactionType.ACCUMULATE ||
      this.transactionCallParam.transactionType === TransactionType.REDUCE) {

      const transactionTime: number = +this.configObject.transactionTime.formControl.value;
      if (transactionTime > security.sTimestamp || moment(transactionTime).isSame(new Date(), 'day')) {
        if (security.sLast) {
          this.setValueToControl(this.configObject.quotation, security.sLast);
        }
      } else {
        if (this.historyquoteCheck.hasChanged(security.idSecuritycurrency, transactionTime)) {
          BusinessHelper.setHistoryquoteCloseToFormControl(this.messageToastService, this.historyquoteService, this.gps,
            transactionTime, security.idSecuritycurrency, true, this.configObject.quotation.formControl);
        }
      }
    }
  }

  private getPortfolioByIdSecurityaccount(idSecurityaccount: number): Portfolio {
    if (this.portfolios) {
      for (const portfolio of this.portfolios) {
        for (const securityaccount of portfolio.securityaccountList) {
          if (securityaccount.idSecuritycashAccount === idSecurityaccount) {
            return portfolio;
          }
        }
      }
    }
    return null;
  }

  private getSecurityById(idSecuritycurrency: number): Security {
    return this.securities.filter(security => security.idSecuritycurrency === idSecuritycurrency)[0];
  }

  /**
   * There are more than one Securityaccount for a portfolio, that one with open position will be selected.
   */
  private selectAccumulteSecurityaccountWhenAvailable(securityaccountsHtmlSelect: ValueKeyHtmlSelectOptions[]) {
    if (this.transactionCallParam.portfolio) {
      const securityaccount = this.transactionCallParam.portfolio.securityaccountList.filter(
        sa => this.getUnitsForSecurityAccount(sa))[0];
      securityaccount && this.setValueToControl(this.configObject.idSecurityaccount, securityaccount.idSecuritycashAccount);
    }
  }

  private setCurrencyOnSecurityAndCashaccount(security: Security) {
    if (this.configObject.idCashaccount.formControl.value && this.portfolios) {
      const cashaccountPortfolio = this.getCashaccountByIdCashaccountFormPortfolios(this.portfolios,
        +this.configObject.idCashaccount.formControl.value);
      this.currencyCashaccount = cashaccountPortfolio.cashaccount.currency;
      this.setCurrencyPrefixOnFields(null, this.currencyCashaccount);
      this.currencypair = BusinessHelper.getCurrencypairWithSetOfFromAndTo(cashaccountPortfolio.cashaccount.currency,
        security.currency);
      if (this.currencypair) {
        this.enableDisableCurrencyExRate(true);
        this.configObject.currencyExRate.labelSuffix = this.currencypair.fromCurrency + '/' + this.currencypair.toCurrency;
        this.updateCurrencyExchangeRate();
      } else {
        this.clearCurrencyExRate();
      }
    }
  }

  private enableDisableCurrencyExRate(enable: boolean): void {
    if (enable) {
      this.configObject.currencyExRate.formControl.enable();
      this.valueChangedOnCA(this.valueChangedOnquotationCA, this.configObject.quotationCA,
        this.configObject.quotation);
      this.valueChangedOnCA(this.valueChangedOnTransactionCostCA, this.configObject.transactionCostCA,
        this.configObject.transactionCost);
      this.valueChangedOnCA(this.valueChangedOnTaxCostCA, this.configObject.taxCostCA, this.configObject.taxCost);
    } else {
      this.oldCurrencyExChange = null;
      this.configObject.currencyExRate.formControl.disable();
      this.unSubscribeCA(this.valueChangedOnquotationCA, this.configObject.quotationCA);
      this.unSubscribeCA(this.valueChangedOnTransactionCostCA, this.configObject.transactionCostCA);
      this.unSubscribeCA(this.valueChangedOnTaxCostCA, this.configObject.taxCostCA);
    }
    this.disableEnableExchangeRateButtons();
  }

  private valueChangedOnCA(propertySub: string, sourceField: FieldConfig, targetField: FieldConfig): void {
    AppHelper.enableAndVisibleInput(sourceField);
    if (!this.subObj[propertySub]) {
      this.subObj[propertySub] = sourceField.formControl.valueChanges.subscribe(value =>
        this.calculateBaseValueFromCurrencyValue(sourceField, targetField));
    }
  }

  private currencyExRateHasChanged(): void {
    if (!this.configObject.currencyExRate.formControl.disabled
      && this.oldCurrencyExChange !== this.configObject.currencyExRate.formControl.value) {
      this.oldCurrencyExChange = this.configObject.currencyExRate.formControl.value;
      this.calculateBaseValueFromCurrencyValue(this.configObject.quotationCA, this.configObject.quotation);
      this.calculateBaseValueFromCurrencyValue(this.configObject.transactionCostCA, this.configObject.transactionCost);
      this.calculateBaseValueFromCurrencyValue(this.configObject.taxCostCA, this.configObject.taxCost);
    }
  }

  private calculateBaseValueFromCurrencyValue(sourceField: FieldConfig, targetField: FieldConfig): void {
    if (sourceField.formControl.value && this.configObject.currencyExRate.formControl.value) {
      targetField.formControl.setValue(sourceField.formControl.value / this.configObject.currencyExRate.formControl.value);
    }
  }

  private unSubscribeCA(propertySub: string, fieldConfig: FieldConfig): void {
    this.subObj[propertySub] && this.subObj[propertySub].unsubscribe();
    this.subObj[propertySub] = null;
    fieldConfig.formControl.setValue(null);
    AppHelper.disableAndHideInput(fieldConfig);
  }


  private updateCurrencyExchangeRate(): void {
    if (this.currencypair && this.hasChangedOnExistingTransaction()) {
      BusinessHelper.getAndSetQuotationCurrencypair(this.currencypairService, this.currencypair,
        +this.configObject.transactionTime.formControl.value, this.configObject.currencyExRate.formControl);
    }
  }

  private clearCurrencyExRate() {
    this.configObject.currencyExRate.labelSuffix = '';
    this.setValueToControl(this.configObject.currencyExRate, 1);
    this.enableDisableCurrencyExRate(false);
  }

  private hasChangedOnExistingTransaction(): boolean {
    const t = this.transactionCallParam.transaction;
    return t === null || t !== null && (t.cashaccount.idSecuritycashAccount
      !== this.configObject.idCashaccount.formControl.value
      || t.security.idSecuritycurrency !== this.configObject.idSecuritycurrency.formControl.value);
  }

  private createHtmlSelectKeyValue(portfolioName: string, securitycashaccounts: any[],
                                   callBackFn?: (securityaccount: Securityaccount) => string): ValueKeyHtmlSelectOptions[] {
    const valueKeyHtmlSelect: ValueKeyHtmlSelectOptions[] = [];
    const securityaccountOpenPositionUnits: SecurityaccountOpenPositionUnits[] =
      this.securityOpenPositionPerSecurityaccount?.securityaccountOpenPositionUnitsList || [];

    securitycashaccounts.forEach((securityaccount: Securityaccount | Cashaccount) => {
        if (this.transactionEditType.acceptSecurityaccount(securityaccount, securityaccountOpenPositionUnits,
          this.isOpenMarginInstrument)) {
          valueKeyHtmlSelect.push(new ValueKeyHtmlSelectOptions(securityaccount.idSecuritycashAccount, securityaccount.name + ' / '
            + portfolioName + (callBackFn ? callBackFn(<Securityaccount>securityaccount) : '')));
        }
      }
    );
    return valueKeyHtmlSelect;
  }

  private getUnitsForSecurityAccountAsString(securityaccount: Securityaccount): string {
    const units = this.getUnitsForSecurityAccount(securityaccount);

    if (this.isCloseMarginInstrument && securityaccount.idSecuritycashAccount
      === this.transactionCallParam?.transaction.idSecurityaccount) {
      let existingUnits = Math.abs(units);
      if (this.transactionCallParam?.transaction.idTransaction) {
        existingUnits = Math.min(existingUnits, this.transactionCallParam?.transaction.units);
      }
      this.configObject.units.formControl.setValue(existingUnits);
    }
    return units ? ' / ' + this.volumei18n + ': ' + units : '';
  }

  private getUnitsForSecurityAccount(securityaccount: Securityaccount): number {
    const securityaccountOpenPositionUnits: SecurityaccountOpenPositionUnits[]
      = this.securityOpenPositionPerSecurityaccount.securityaccountOpenPositionUnitsList;
    const securityaccountOpenPositionUnit = securityaccountOpenPositionUnits.find(pos =>
      securityaccount.idSecuritycashAccount === pos.idSecurityaccount);
    return (securityaccountOpenPositionUnit) ? securityaccountOpenPositionUnit.units : null;
  }
}

class ChangedIdSecurityAndTime {
  private idSecuritycurrency: number;
  private checkTime: number;

  hasChanged(idSecuritycurrency: number, checkTime: number): boolean {
    if (this.idSecuritycurrency !== idSecuritycurrency || this.checkTime !== checkTime) {
      this.idSecuritycurrency = idSecuritycurrency;
      this.checkTime = checkTime;
      return true;
    }
    return false;
  }
}
