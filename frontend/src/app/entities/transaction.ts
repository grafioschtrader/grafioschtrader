import { Cashaccount } from './cashaccount';
import { Currencypair } from './currencypair';
import { Security } from './security';
import { TransactionType } from '../shared/types/transaction.type';

export class Transaction {
  //Some values are initialied with null, otherwise values will not be transfered

  idTransaction?: number;
  /** Server-owned marker protecting the simulation's opening ledger. */
  readonly simulationOpening?: boolean;
  idAlgoStrategy: number | null = null;
  units?: number | null = null;
  quotation?: number | null = null;
  transactionType: string | null = null;
  connectedIdTransaction?: number;
  taxableInterest?: boolean | null = null;
  taxCost?: number | null = null;
  transactionCost?: number | null = null;
  currencyExRate?: number | null = null;
  note?: string | null = null;
  cashaccountAmount?: number | null = null;
  cashaccountRoundingDiff?: number | null = null;
  transactionTime: string | number | Date | null = null;
  exDate: string | Date | null = null;

  idSecurityaccount: number | null = null;

  idSecuritycurrency?: number | null = null;
  // Like server model
  security?: Security;

  idCashaccount?: number | null = null;
  // Like server model
  cashaccount?: Cashaccount;

  assetInvestmentValue1?: number | null = null;
  assetInvestmentValue2?: number | null = null;
  // Like server model
  idCurrencypair?: number | null = null;
  currencypair?: Currencypair;

  // Transient
  securityRisk: number | null = null;

  public static isSecurityTransaction(transactionType: string | null | undefined): boolean {
    if (transactionType == null) return false;
    switch (TransactionType[transactionType as keyof typeof TransactionType]) {
      case TransactionType.ACCUMULATE:
      case TransactionType.REDUCE:
      case TransactionType.DIVIDEND:
      case TransactionType.FINANCE_COST:
        return true;
      default:
        return false;
    }
  }

  public static isOnlyCashAccountTransaction(transactionType: string | null | undefined): boolean {
    if (transactionType == null) return false;
    switch (TransactionType[transactionType as keyof typeof TransactionType]) {
      case TransactionType.FEE:
      case TransactionType.INTEREST_CASHACCOUNT:
        return true;
      default:
        return this.isWithdrawalOrDeposit(transactionType);
    }
  }

  public static isDividendOrInterest(transactionType: string | null | undefined): boolean {
    if (transactionType == null) return false;
    switch (TransactionType[transactionType as keyof typeof TransactionType]) {
      case TransactionType.DIVIDEND:
      case TransactionType.INTEREST_CASHACCOUNT:
        return true;
      default:
        return false;
    }
  }

  public static isWithdrawalOrDeposit(transactionType: string | null | undefined): boolean {
    if (transactionType == null) return false;
    switch (TransactionType[transactionType as keyof typeof TransactionType]) {
      case TransactionType.WITHDRAWAL:
      case TransactionType.DEPOSIT:
        return true;
      default:
        return false;
    }
  }
}
