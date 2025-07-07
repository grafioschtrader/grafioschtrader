import {Transaction} from '../../entities/transaction';
import {TransactionType} from '../types/transaction.type';
import {Currencypair} from '../../entities/currencypair';
import {CurrencypairService} from '../../securitycurrency/service/currencypair.service';
import {AbstractControl} from '@angular/forms';
import moment from 'moment';
import {CurrencypairWithHistoryquote} from '../../entities/view/currencypair.with.historyquote';
import {SecurityService} from '../../securitycurrency/service/security.service';
import {Observable} from 'rxjs';
import {SecurityTransactionSummary} from '../../entities/view/security.transaction.summary';
import {MessageToastService} from '../message/message.toast.service';
import {HistoryquoteService} from '../../historyquote/service/historyquote.service';
import {GlobalparameterService} from '../service/globalparameter.service';
import {InfoLevelType} from '../message/info.leve.type';
import {Securitycurrency} from '../../entities/securitycurrency';
import {MenuItem} from 'primeng/api';
import {Security} from '../../entities/security';
import {HelpIds} from '../help/help.ids';
import {AppSettings} from '../app.settings';
import {TenantLimit} from '../../entities/backend/tenant.limit';
import {AssetclassType} from '../types/assetclass.type';
import {SpecialInvestmentInstruments} from '../types/special.investment.instruments';
import {Assetclass} from '../../entities/assetclass';
import {ISecuritycurrencyIdDateClose} from '../../entities/projection/i.securitycurrency.id.date.close';
import {ColumnConfig} from '../datashowbase/column.config';
import {GlobalSessionNames} from '../global.session.names';

/**
 * Utility class providing static helper methods for financial business operations.
 * Handles transaction processing, currency conversions, security management,
 * and external integrations for a financial trading application.
 */
export class BusinessHelper {

  /**
   * Converts a numeric transaction type enum value to its string representation.
   * Iterates through the TransactionType enum to find the matching string name.
   *
   * @param transactionType The numeric transaction type enum value to convert
   * @returns The string name of the transaction type, or null if not found
   *
   * @example
   * ```typescript
   * const typeName = BusinessHelper.getTransactionTypeAsName(TransactionType.ACCUMULATE);
   * // Returns: "ACCUMULATE"
   * ```
   */
  public static getTransactionTypeAsName(transactionType: TransactionType): string {
    for (const n in TransactionType) {
      if (typeof TransactionType[n] === 'number' && transactionType === +TransactionType[n]) {
        return n;
      }
    }
    return null;
  }

  /**
   * Calculates the total amount from a transaction, applying sign corrections
   * for specific transaction types. FEE and WITHDRAWAL transactions are converted
   * to negative values to reflect their debit nature.
   *
   * @param transaction The transaction object containing cashaccountAmount and transactionType
   * @returns The calculated total amount (negative for fees and withdrawals)
   */
  public static getTotalAmountFromTransaction(transaction: Transaction) {
    let cashaccountAmount: number;
    switch (TransactionType[transaction.transactionType]) {
      case TransactionType.FEE:
      case TransactionType.WITHDRAWAL:
        cashaccountAmount = transaction.cashaccountAmount * -1;
        break;
      default:
        cashaccountAmount = transaction.cashaccountAmount;
    }
    return cashaccountAmount;
  }

  /**
   * Creates a currency pair object from source and target currencies.
   * Returns null if the source currency is null or if both currencies are identical.
   * The currency pair is created with target currency as 'from' and source as 'to'.
   *
   * @param sourceCurrency The source currency code (e.g., 'USD')
   * @param targetCurrency The target currency code (e.g., 'EUR')
   * @returns A new Currencypair object, or null if currencies are the same or source is null
   */
  public static getCurrencypairWithSetOfFromAndTo(sourceCurrency: string, targetCurrency: string): Currencypair {
    let currencypair: Currencypair = null;
    if (sourceCurrency != null && targetCurrency !== sourceCurrency) {
      currencypair = new Currencypair(targetCurrency, sourceCurrency);
    }
    return currencypair;
  }

  /**
   * Performs currency conversion calculations using exchange rates.
   * Determines whether to multiply or divide based on the relationship between
   * the source currency and the currency pair's from currency.
   *
   * @param value The monetary value to convert
   * @param currencyExRate The exchange rate to apply
   * @param sourceCurrency The source currency code
   * @param currencypair The currency pair object containing from/to currency information
   * @returns The converted value, or the original value rounded to 8 decimal places if no currency pair
   */
  public static divideMultiplyExchangeRate(value: number, currencyExRate: number, sourceCurrency: string,
    currencypair: Currencypair): number {
    if (sourceCurrency && currencypair) {
      if (sourceCurrency === currencypair.fromCurrency) {
        return value / currencyExRate;
      } else {
        return value * currencyExRate;
      }
    }
    return BusinessHelper.roundNumber(value, 8);
  }

  /**
   * Rounds a number to the specified number of decimal places using standard
   * mathematical rounding rules.
   *
   * @param num The number to round
   * @param dec The number of decimal places to round to
   * @returns The rounded number
   */
  public static roundNumber(num, dec) {
    return Math.round(num * Math.pow(10, dec)) / Math.pow(10, dec);
  }

  /**
   * Retrieves historical quotation data for a currency pair and sets the appropriate
   * exchange rate in the provided form control. Uses current price for future/today
   * transactions, or historical close price for past transactions.
   *
   * @param currencypairService Service for currency pair operations and data retrieval
   * @param currencypair The currency pair object to get quotation for
   * @param transactionTime Unix timestamp of the transaction
   * @param currencyExRateFormControl Angular form control to update with the exchange rate
   */
  public static getAndSetQuotationCurrencypair(currencypairService: CurrencypairService, currencypair: Currencypair,
    transactionTime: number, currencyExRateFormControl: AbstractControl): void {
    currencypairService.getCurrencypairWithHistoryquoteByIdSecuritycurrencyAndDate(currencypair,
      moment(transactionTime).format('YYYYMMDD')).subscribe((cWh: CurrencypairWithHistoryquote) => {
      if (cWh.currencypair) {
        currencypair.idSecuritycurrency = cWh.currencypair.idSecuritycurrency;
        if (transactionTime > cWh.currencypair.sTimestamp || moment(transactionTime).isSame(new Date(), 'day')) {
          currencyExRateFormControl.setValue(cWh.currencypair.sLast);
        } else {
          currencyExRateFormControl.setValue(cWh.historyquote.close);
        }
      }
    });
  }

  /**
   * Determines the appropriate data source for security transaction summary based on
   * the provided scope parameters. Follows a hierarchy: security accounts > portfolio > tenant.
   *
   * @param securityService Service for security-related operations
   * @param idSecuritycurrency The unique identifier of the security or currency
   * @param idSecuritycashAccounts Array of security cash account IDs (highest priority)
   * @param idPortfolio Portfolio ID for portfolio-level data (medium priority)
   * @param forChart Boolean flag indicating if data is for chart visualization
   * @returns Observable containing the security transaction summary
   */
  public static getSecurityTransactionSummary(securityService: SecurityService,
    idSecuritycurrency: number,
    idSecuritycashAccounts: number[],
    idPortfolio: number,
    forChart: boolean): Observable<SecurityTransactionSummary> {
    if (idSecuritycashAccounts && idSecuritycashAccounts.length > 0 && idSecuritycashAccounts[0] !== null) {
      return securityService.getTransactionsByIdSecurityaccountsAndIdSecurity(idSecuritycashAccounts, idSecuritycurrency, forChart);
    } else if (idPortfolio) {
      return securityService.getTransactionsByIdPortfolioAndIdSecurity(idPortfolio, idSecuritycurrency, forChart);
    } else {
      return securityService.getTransactionsByIdTenantAndIdSecurity(idSecuritycurrency, forChart);
    }
  }

  /**
   * Validates whether a tenant limit has been exceeded and displays a warning message
   * if the limit is reached. Returns false if the actual usage equals or exceeds the limit.
   *
   * @param tenantLimit Object containing limit configuration with actual usage and maximum limit
   * @param messageToastService Service for displaying user notifications and messages
   * @returns True if limit check passes (usage below limit), false if limit exceeded
   *
   * @example
   * ```typescript
   * const limit: TenantLimit = {
   *   actual: 8,
   *   limit: 10,
   *   className: 'Portfolio',
   *   msgKey: 'MAX_PORTFOLIOS'
   * };
   * const isOk = BusinessHelper.isLimitCheckOk(limit, messageToastService);
   * // Returns: true (8 < 10)
   *
   * const exceededLimit: TenantLimit = { actual: 10, limit: 10, className: 'Portfolio' };
   * const isOk2 = BusinessHelper.isLimitCheckOk(exceededLimit, messageToastService);
   * // Returns: false (10 >= 10) and shows warning message
   * ```
   */
  public static isLimitCheckOk(tenantLimit: TenantLimit, messageToastService: MessageToastService): boolean {
    if (tenantLimit.actual >= tenantLimit.limit) {
      messageToastService.showMessageI18n(InfoLevelType.WARNING, 'MAX_LIMIT', {
        limit: tenantLimit.limit, i18nEntity: tenantLimit.className.toUpperCase() + 'S'
      });
      return false;
    }
    return true;
  }

  /**
   * Retrieves historical quotation close price for a specific date and security,
   * then sets the value in the provided form control. Shows warning message if
   * no quotation data is available for the specified date.
   *
   * @param messageToastService Service for displaying user notifications
   * @param historyquoteService Service for historical quote data operations
   * @param gps Global parameter service for application-wide settings
   * @param transactionTime Unix timestamp of the transaction date
   * @param idSecuritycurrency Unique identifier of the security or currency
   * @param asTraded Boolean indicating whether to use traded prices vs. theoretical prices
   * @param formControl Angular form control to update with the retrieved close price
   */
  public static setHistoryquoteCloseToFormControl(messageToastService: MessageToastService,
    historyquoteService: HistoryquoteService,
    gps: GlobalparameterService,
    transactionTime: number, idSecuritycurrency: number,
    asTraded: boolean, formControl: AbstractControl): void {
    historyquoteService.getCertainOrOlderDayInHistoryquoteByIdSecuritycurrency(idSecuritycurrency,
      moment(transactionTime).format('YYYYMMDD'), asTraded).subscribe((historyquote: ISecuritycurrencyIdDateClose) => {
      if (historyquote != null) {
        formControl.setValue(historyquote.close);
      } else {
        messageToastService.showMessageI18n(InfoLevelType.WARNING, 'MSG_NON_TIME_QUOTATION');
      }
    });
  }

  /**
   * Creates menu items for external links related to a security or currency.
   * Always includes a stock exchange link, and adds a product link for Security objects.
   * Menu items are disabled if their corresponding URLs are not available.
   *
   * @param securitycurrency The security or currency object containing link information
   * @returns Array of MenuItem objects for external links
   */
  static getUrlLinkMenus(securitycurrency: Securitycurrency): MenuItem[] {
    const menuItems: MenuItem[] = [];
    menuItems.push({separator: true});
    menuItems.push(
      {
        label: 'STOCKEXCHANGE_LINK',
        command: (e) => this.toExternalWebpage(securitycurrency.stockexchangeLink, 'exchange'),
        disabled: !securitycurrency.stockexchangeLink
      }
    );
    if (securitycurrency.hasOwnProperty('productLink')) {
      menuItems.push(
        {
          label: 'PRODUCT_LINK',
          command: (e) => this.toExternalWebpage((<Security>securitycurrency).productLink, 'product'),
          disabled: !(<Security>securitycurrency).productLink
        }
      );
    }
    return menuItems;
  }

  /**
   * Opens an external help webpage in a new window/tab using the application's
   * help domain and specified language and help section.
   *
   * @param language ISO language code for the help documentation (e.g., 'en', 'de')
   * @param helpIds Enum value specifying which help section to display
   *
   * @example
   * ```typescript
   * BusinessHelper.toExternalHelpWebpage('en', HelpIds.HELP_PORTFOLIO);
   * // Opens: //grafioschtrader.github.io/gt-user-manual/en/HELP_PORTFOLIO
   * ```
   */
  public static toExternalHelpWebpage(language: string, helpIds: HelpIds) {
    this.toExternalWebpage(AppSettings.HELP_DOMAIN + '/' + language + '/' + helpIds, 'help');
  }

  /**
   * Determines if a security should display denomination values based on its asset class
   * and market price availability. Returns true for specific fixed income instruments
   * with direct investment when market price exists, or always true when no market price.
   *
   * @param assetclass The asset class configuration of the security
   * @param hasMarkedPrice Boolean indicating if the security has current market pricing
   * @returns True if security should show denomination, false otherwise
   */
  public static hasSecurityDenomination(assetclass: Assetclass, hasMarkedPrice: boolean): boolean {
    if (hasMarkedPrice) {
      return !!assetclass && ((assetclass.categoryType === AssetclassType[AssetclassType.FIXED_INCOME]
          || assetclass.categoryType === AssetclassType[AssetclassType.CONVERTIBLE_BOND]
          || assetclass.categoryType === AssetclassType[AssetclassType.MONEY_MARKET])
        && assetclass.specialInvestmentInstrument === SpecialInvestmentInstruments[SpecialInvestmentInstruments.DIRECT_INVESTMENT]);
    } else {
      return true;
    }
  }

  /**
   * Opens an external webpage in a new window or tab. Handles URL preprocessing
   * including domain prefixing for internal links and protocol addition for external links.
   *
   * @param url The URL to open (can be relative, absolute, or prefixed with '--') A “--” is used in the user interface
   * with the link output, and an icon should also appear after the link content. Clicking on the icon navigates to
   * the corresponding page.
   * @param targetPage The target window name (default: 'blank' for new tab)
   */
  public static toExternalWebpage(url: string, targetPage: string = 'blank'): void {
    if (url.startsWith('--')) {
      url = location.host + url.substring(2);
    }
    if (!url.match(/^https?:\/\//i)) {
      url = 'http://' + url;
    }
    window.open(url, targetPage);
  }

  /**
   * Constructs a server URL using the current location hostname and specified port.
   * Automatically includes port number for localhost/127.0.0.1, otherwise uses standard ports.
   *
   * @param location The browser Location object containing hostname information
   * @param port The port number to use for localhost connections
   * @param addToDomain Additional path to append to the domain
   * @returns Formatted server URL string
   */
  public static getServerUrl(location: Location, port: number, addToDomain: string): string {
    let serverUrl = `//${location.hostname}`;
    if (serverUrl === '//localhost' || serverUrl === '//127.0.0.1') {
      serverUrl += ':' + port;
    }
    return serverUrl + '/' + addToDomain;
  }

  /**
   * Determines if a security is a margin product based on its asset class
   * special investment instrument type. Returns true for CFD and FOREX instruments.
   *
   * @param security The security object containing asset class information
   * @returns True if the security is a margin product (CFD or FOREX), false otherwise
   */
  public static isMarginProduct(security: Security): boolean {
    return security.assetClass.specialInvestmentInstrument === SpecialInvestmentInstruments[SpecialInvestmentInstruments.CFD]
      || security.assetClass.specialInvestmentInstrument === SpecialInvestmentInstruments[SpecialInvestmentInstruments.FOREX];
  }

  /**
   * Formats leverage factor display value for table presentation. Returns empty string
   * for leverage factor of 1 (no leverage), otherwise returns the original value.
   * ETFs can have a leverage, the number corresponds to the factor, where 1 is standard.
   * For a double short ETF this is -2. Used as a column value formatter function in data tables.
   *
   * @param dataobject The row data object (unused in current implementation)
   * @param field The column configuration object (unused in current implementation)
   * @param valueField The leverage factor value to format
   * @returns Empty string for leverage factor of 1, otherwise the original value as string
   */
  public static getDisplayLeverageFactor(dataobject: any, field: ColumnConfig, valueField: any): string {
    return Number(+valueField) === 1 ? '' : valueField;
  }

  /**
   * Retrieves the "until date" from session storage for reporting purposes.
   * This can be set by the user and should work across the board for certain reports.
   * Falls back to current date if no stored date is found.
   *
   * @returns Date object representing the until date for reports
   */
  static getUntilDateBySessionStorage(): Date {
    return BusinessHelper.getDateFromSessionStorage(GlobalSessionNames.REPORT_UNTIL_DATE, new Date());
  }

  /**
   * Retrieves a date from session storage with fallback to default date.
   * Parses stored date string using moment.js for reliable date handling.
   *
   * @param property Session storage key for the date
   * @param defaultDate Default date to return if no stored date exists
   * @returns Date object from storage or default date
   */
  static getDateFromSessionStorage(property: string, defaultDate = new Date()): Date {
    const date = sessionStorage.getItem(property);
    return date ? moment(date).toDate() : defaultDate;
  }

  /**
   * Saves the "until date" to session storage for reporting persistence.
   * Maintains user's date selection across browser sessions.
   *
   * @param untilDate Date to store in session storage
   */
  static saveUntilDateInSessionStorage(untilDate: Date): void {
    BusinessHelper.saveDateToSessionStore(GlobalSessionNames.REPORT_UNTIL_DATE, untilDate);
  }

  /**
   * Saves a date to session storage in standardized format.
   * Uses application's standard date format for consistent storage.
   *
   * @param property Session storage key for the date
   * @param date Date object to store
   */
  private static saveDateToSessionStore(property: string, date: Date) {
    sessionStorage.setItem(property, moment(date).format(AppSettings.FORMAT_DATE_SHORT_NATIVE));
  }

}
