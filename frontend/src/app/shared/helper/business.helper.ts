import {Transaction} from '../../entities/transaction';
import {TransactionType} from '../types/transaction.type';
import {Currencypair} from '../../entities/currencypair';
import {CurrencypairService} from '../../securitycurrency/service/currencypair.service';
import {AbstractControl} from '@angular/forms';
import * as moment from 'moment';
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

export class BusinessHelper {

  public static getTransactionTypeAsName(transactionType: TransactionType): string {
    for (const n in TransactionType) {
      if (typeof TransactionType[n] === 'number' && transactionType === +TransactionType[n]) {
        return n;
      }
    }
  }

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
   * Determine the currency pair depending on the portfolio and source and target currency.
   *
   * @param portfolio Portfolio in which belongs this request
   * @param sourceCurrency The source currency
   * @param targetCurrency The target currency
   */
  public static getCurrencypairWithSetOfFromAndTo(sourceCurrency: string, targetCurrency: string): Currencypair {
    let currencypair: Currencypair = null;
    if (sourceCurrency != null && targetCurrency !== sourceCurrency) {
      currencypair = new Currencypair(targetCurrency, sourceCurrency);
    }
    return currencypair;
  }

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

  public static roundNumber(num, dec) {
    return Math.round(num * Math.pow(10, dec)) / Math.pow(10, dec);
  }

  /**
   * Sets the quotation depending of a certain date for a currency pair to a FormControl.
   */
  public static getAndSetQuotationCurrencypair(currencypairService: CurrencypairService, currencypair: Currencypair,
                                               transactionTime: number, currencyExRateFormControl: AbstractControl) {
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

  public static setSecurityTransactionSummary(securityService: SecurityService,
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

  public static isLimitCheckOk(tenantLimit: TenantLimit, messageToastService: MessageToastService): boolean {
    if (tenantLimit.actual >= tenantLimit.limit) {
      messageToastService.showMessageI18n(InfoLevelType.WARNING, 'MAX_LIMIT', {
        limit: tenantLimit.limit, i18nEntity: tenantLimit.className.toUpperCase() + 'S'
      });
      return false;
    }
    return true;
  }

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


  public static toExternalHelpWebpage(language: string, helpIds: HelpIds) {
    this.toExternalWebpage(AppSettings.HELP_DOMAIN + '/' + language + '/' + helpIds, 'help');
  }

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

  public static toExternalWebpage(url: string, target: string = 'blank'): void {
    if (!url.match(/^https?:\/\//i)) {
      url = 'http://' + url;
    }
    window.open(url, target);
  }

  public static getServerUrl(location: Location, port: number, addToDomain: string): string {
    let serverUrl = `//${location.hostname}`;
    if (serverUrl === '//localhost' || serverUrl === '//127.0.0.1') {
      serverUrl += ':' + port;
    }
    return serverUrl + '/' + addToDomain;
  }

  public static isMarginProduct(security: Security): boolean {
    return security.assetClass.specialInvestmentInstrument === SpecialInvestmentInstruments[SpecialInvestmentInstruments.CFD]
      || security.assetClass.specialInvestmentInstrument === SpecialInvestmentInstruments[SpecialInvestmentInstruments.FOREX];
  }

  public static getDisplayLeverageFactor(dataobject: any, field: ColumnConfig, valueField: any): string {
    return Number(+valueField) === 1? '': valueField;
  }

}
