import type { Cashaccount } from './cashaccount';
import type { Security } from './security';
import type { ImportTransactionPosFailed } from './import.transaction.pos.failed';

/** Parsed or persisted import data returned by the backend. */
export interface ImportTransactionPos {
  idTransactionPos: number | null;
  idTransactionHead: number | null;
  transactionType: string | null;
  transactionTime: string | Date | null;
  transactionTypeImp: string | null;
  cashaccount: Cashaccount | null;
  currencyAccount: string | null;
  cashAccountImp: string | null;
  currencyExRate: number | null;
  currencySecurity: string | null;
  isin: string | null;
  symbolImp: string | null;
  securityNameImp: string | null;
  security: Security | null;
  units: number | null;
  quotation: number | null;
  readyForTransaction: boolean;
  taxCost: number | null;
  transactionCost: number | null;
  cashaccountAmount: number | null;
  accruedInterest: number | null;
  field1StringImp: string | null;
  importTransactionPosFailedList: ImportTransactionPosFailed[] | null;
  calcCashaccountAmount: number | null;
  fileNameOriginal: string | null;
  idTransaction: number | null;
  idTransactionMaybe: number | null;
  transactionError: string | null;
  fileType: string | null;
}

/** Import-table rows originate from saved positions of an import head. */
export interface PersistedImportTransactionPos extends ImportTransactionPos {
  idTransactionPos: number;
  idTransactionHead: number;
  fileNameOriginal: string;
}
