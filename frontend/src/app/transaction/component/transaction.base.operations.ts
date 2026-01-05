import {Portfolio} from '../../entities/portfolio';
import {Cashaccount} from '../../entities/cashaccount';
import {TranslateService} from '@ngx-translate/core';
import {AppSettings} from '../../shared/app.settings';
import {GlobalparameterService} from '../../lib/services/globalparameter.service';
import {FieldConfig} from '../../lib/dynamic-form/models/field.config';
import {Currencypair} from '../../entities/currencypair';
import {BusinessHelper} from '../../shared/helper/business.helper';
import {MessageToastService} from '../../lib/message/message.toast.service';
import {HistoryquoteService} from '../../historyquote/service/historyquote.service';
import {DynamicFieldHelper} from '../../lib/helper/dynamic.field.helper';
import {CurrencypairService} from '../../securitycurrency/service/currencypair.service';
import {BaseSettings} from '../../lib/base.settings';
import moment from 'moment';

/**
 * Abstract base class providing common operations for transaction handling, including cash account lookups,
 * currency exchange rate calculations, and form field management. Serves as a foundation for various
 * transaction-related components that need standardized portfolio and currency conversion functionality.
 */
export abstract class TransactionBaseOperations {
  /** Field configurations mapped by field name for form management */
  configObject: { [name: string]: FieldConfig };

  /** Current currency pair used for exchange rate calculations */
  protected currencypair?: Currencypair;

  /** Indicates if the transaction is locked due to being within a closed period */
  transactionLocked = false;

  /** Message displayed when the transaction is locked */
  transactionLockedMessage = '';

  /**
   * Creates a new TransactionBaseOperations instance with required services.
   * @param messageToastService Service for displaying user notifications
   * @param currencypairService Service for currency pair operations and data retrieval
   * @param historyquoteService Service for historical quote data operations
   * @param translateService Angular translation service for internationalization
   * @param gps Global parameter service for application-wide settings
   */
  protected constructor(public messageToastService: MessageToastService,
              public currencypairService: CurrencypairService,
              public historyquoteService: HistoryquoteService,
              public translateService: TranslateService,
              protected gps: GlobalparameterService) {
  }

  /** Returns whether the transaction dialog is currently visible */
  abstract isVisibleDialog(): boolean;

  /**
   * Searches for a cash account across multiple portfolios by ID.
   * @param portfolios Array of portfolios to search through
   * @param idSecuritycashaccount ID of the cash account to find
   * @returns Object containing the found cash account and its portfolio, or null if not found
   */
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


  /**
   * Finds a cash account by ID within a collection of portfolios.
   * @param portfolios Array of portfolios to search
   * @param idSecuritycashaccount ID of the cash account to locate
   * @returns Object with cash account and portfolio reference, or null if not found
   */
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

  /**
   * Locates a specific cash account within a single portfolio.
   * @param portfolio Portfolio to search within
   * @param idSecuritycashaccount ID of the cash account to find
   * @returns Object containing the cash account and portfolio, or null if not found
   */
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


  /**
   * Calculates and sets the inverse of the current exchange rate.
   * @param event Event object from user interaction
   */
  oneOverX(event): void {
    this.configObject.currencyExRate.formControl.setValue(1 / this.configObject.currencyExRate.formControl.value);
  }


  /**
   * Retrieves and sets the appropriate exchange rate based on transaction timing.
   * @param event Event object from user interaction
   */
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

  /**
   * Creates form field configurations for exchange rate operation buttons.
   * @returns Array of field configurations for exchange rate buttons
   */
  protected createExRateButtons(): FieldConfig[] {
    return [
      DynamicFieldHelper.createFunctionButtonFieldName('oneOverX', 'ONE_OVER_X',
        (e) => this.oneOverX(e), {
          buttonInForm: true, usedLayoutColumns: 1,
        }),
      DynamicFieldHelper.createFunctionButtonFieldName('exRateButton', 'TIME_DEPENDING_EXCHANGE_RATE_',
        (e) => this.getTimeDependingExchangeRate(e), {
          icon: BaseSettings.PATH_ASSET_ICONS + 'refresh.svg',
          buttonInForm: true, usedLayoutColumns: 1
        })
    ];
  }

  /** Updates the enabled/disabled state of exchange rate buttons based on form validity */
  protected disableEnableExchangeRateButtons(): void {
    this.configObject.exRateButton.disabled = this.configObject.currencyExRate.formControl.disabled ||
      !this.configObject.transactionTime.formControl.valid;
    this.configObject.oneOverX.disabled = this.configObject.currencyExRate.formControl.disabled;
  }

  /**
   * Resets the transaction locked state. Should be called when initializing for a new transaction.
   */
  protected resetTransactionLocked(): void {
    this.transactionLocked = false;
    this.transactionLockedMessage = '';
  }

  /**
   * Checks if an existing transaction is within the closed period and locks the form if so.
   * When locked, sets the transactionLocked flag, displays a message, and hides the submit button.
   *
   * @param effectiveClosedUntil The effective closedUntil date from portfolio or tenant
   * @param transactionTime The transaction time to check (from existing transaction)
   */
  protected checkTransactionLocked(effectiveClosedUntil: Date | null, transactionTime: Date | string | number): void {
    if (effectiveClosedUntil && transactionTime) {
      const transactionDate = new Date(transactionTime);
      // Compare dates only (ignore time component)
      const transactionDateOnly = new Date(transactionDate.getFullYear(), transactionDate.getMonth(), transactionDate.getDate());
      const closedUntilDateOnly = new Date(effectiveClosedUntil.getFullYear(), effectiveClosedUntil.getMonth(), effectiveClosedUntil.getDate());

      if (transactionDateOnly <= closedUntilDateOnly) {
        this.transactionLocked = true;
        const formattedDate = moment(effectiveClosedUntil).format(this.gps.getDateFormat());
        this.translateService.get('TRANSACTION_LOCKED_MESSAGE', {date: formattedDate}).subscribe(
          msg => this.transactionLockedMessage = msg
        );
        // Hide the submit button
        if (this.configObject.submit) {
          this.configObject.submit.invisible = true;
        }
      }
    }
  }

}

