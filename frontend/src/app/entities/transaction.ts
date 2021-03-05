import {Cashaccount} from './cashaccount';
import {Currencypair} from './currencypair';
import {Security} from './security';
import {TransactionType} from '../shared/types/transaction.type';

export class Transaction {
  //Some values are initialied with null, otherwise values will not be transfered

  idTransaction?: number;
  units?: number = null;
  quotation?: number = null;
  transactionType: string = null;
  connectedIdTransaction?: number;
  taxableInterest?: boolean = null;
  taxCost?: number = null;
  transactionCost?: number = null;
  currencyExRate?: number = null;
  note?: string = null;
  cashaccountAmount?: number = null;
  transactionTime = null;
  exDate = null;

  idSecurityaccount: number = null;

  idSecuritycurrency?: number = null;
  // Like server model
  security?: Security;

  idCashaccount?: number = null;
  // Like server model
  cashaccount?: Cashaccount;

  assetInvestmentValue1?: number = null;
  assetInvestmentValue2?: number = null;
  // Like server model
  idCurrencypair?: number = null;
  currencypair?: Currencypair;

  // Transient
  securityRisk: number = null;

  public static isSecurityTransaction(transactionType: string): boolean {
    switch (TransactionType[transactionType]) {
      case TransactionType.ACCUMULATE:
      case TransactionType.REDUCE:
      case TransactionType.DIVIDEND:
      case TransactionType.FINANCE_COST:
        return true;
      default:
        return false;
    }
  }

  public static isOnlyCashAccountTransaction(transactionType: string): boolean {
    switch (TransactionType[transactionType]) {
      case TransactionType.FEE:
      case TransactionType.INTEREST_CASHACCOUNT:
        return true;
      default:
        return this.isWithdrawalOrDeposit(transactionType);
    }
  }

  public static isWithdrawalOrDeposit(transactionType: string): boolean {
    switch (TransactionType[transactionType]) {
      case TransactionType.WITHDRAWAL:
      case TransactionType.DEPOSIT:
        return true;
      default:
        return false;
    }
  }

}
