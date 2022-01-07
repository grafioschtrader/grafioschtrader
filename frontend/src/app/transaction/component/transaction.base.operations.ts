import {Portfolio} from '../../entities/portfolio';
import {Cashaccount} from '../../entities/cashaccount';
import {TranslateService} from '@ngx-translate/core';
import {AppSettings} from '../../shared/app.settings';
import {GlobalparameterService} from '../../shared/service/globalparameter.service';
import {FieldConfig} from '../../dynamic-form/models/field.config';
import {Currencypair} from '../../entities/currencypair';
import {BusinessHelper} from '../../shared/helper/business.helper';
import {MessageToastService} from '../../shared/message/message.toast.service';
import {HistoryquoteService} from '../../historyquote/service/historyquote.service';
import {DynamicFieldHelper} from '../../shared/helper/dynamic.field.helper';
import {CurrencypairService} from '../../securitycurrency/service/currencypair.service';

export abstract class TransactionBaseOperations {

  configObject: { [name: string]: FieldConfig };
  protected currencypair?: Currencypair;

  constructor(public messageToastService: MessageToastService,
              public currencypairService: CurrencypairService,
              public historyquoteService: HistoryquoteService,
              public translateService: TranslateService,
              protected gps: GlobalparameterService) {
  }

  abstract isVisibleDialog(): boolean;

  getCashaccountByIdCashaccountFormPortfolios(portfolios: Portfolio[], idSecuritycashaccount: number): {
    cashaccount: Cashaccount;
    portfolio: Portfolio;
  } {
    if (idSecuritycashaccount != null) {
      for (const portfolio of portfolios) {
        const found: { cashaccount: Cashaccount; portfolio: Portfolio } = this.getCashaccountByIdCashaccountFromPortfolio(portfolio,
          idSecuritycashaccount);
        if (found) {
          return found;
        }
      }
    }
    return null;
  }

  getCashaccountByIdCashaccountFromPortfolios(portfolios: Portfolio[], idSecuritycashaccount: number): {
    cashaccount: Cashaccount; portfolio: Portfolio;
  } {
    for (const portfolio of portfolios) {
      const found = this.getCashaccountByIdCashaccountFromPortfolio(portfolio, idSecuritycashaccount);
      if (found) {
        return found;
      }
    }
    return null;
  }

  getCashaccountByIdCashaccountFromPortfolio(portfolio: Portfolio, idSecuritycashaccount: number): {
    cashaccount: Cashaccount; portfolio: Portfolio;
  } {
    for (const cashaccount of portfolio.cashaccountList) {
      if (cashaccount.idSecuritycashAccount === idSecuritycashaccount) {
        return {cashaccount, portfolio};
      }
    }
    return null;
  }

  oneOverX(event): void {
    this.configObject.currencyExRate.formControl.setValue(1 / this.configObject.currencyExRate.formControl.value);
  }

  getTimeDependingExchangeRate(event): void {
    const transactionTime: number = +this.configObject.transactionTime.formControl.value;
    if (this.currencypair.idSecuritycurrency) {
      BusinessHelper.setHistoryquoteCloseToFormControl(this.messageToastService, this.historyquoteService,
        this.gps,
        transactionTime, this.currencypair.idSecuritycurrency, false, this.configObject.currencyExRate.formControl);
    } else {
      BusinessHelper.getAndSetQuotationCurrencypair(this.currencypairService, this.currencypair,
        transactionTime, this.configObject.currencyExRate.formControl);
    }
  }

  protected createExRateButtons(): FieldConfig[] {
    return [
      DynamicFieldHelper.createFunctionButtonFieldName('oneOverX', 'ONE_OVER_X',
        (e) => this.oneOverX(e), {
          buttonInForm: true, usedLayoutColumns: 1,
        }),

      DynamicFieldHelper.createFunctionButtonFieldName('exRateButton', 'TIME_DEPENDING_EXCHANGE_RATE_',
        (e) => this.getTimeDependingExchangeRate(e), {
          icon: AppSettings.PATH_ASSET_ICONS + 'refresh.svg',
          buttonInForm: true, usedLayoutColumns: 1
        })
    ];
  }

  protected disableEnableExchangeRateButtons() {
    this.configObject.exRateButton.disabled = this.configObject.currencyExRate.formControl.disabled ||
      !this.configObject.transactionTime.formControl.valid;
    this.configObject.oneOverX.disabled = this.configObject.currencyExRate.formControl.disabled;
  }

}

