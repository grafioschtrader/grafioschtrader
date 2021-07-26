import {Cashaccount} from './cashaccount';
import {BaseID} from './base.id';
import {Security} from './security';
import {ImportTransactionPosFailed} from './import.transaction.pos.failed';

export class ImportTransactionPos implements BaseID {
  idTransactionPos: number;
  idTransactionHead: number;
  transactionType: string;
  transactionTime;
  transactionTypeImp: string;
  cashaccount: Cashaccount;
  currencyAccount: string;
  cashAccountImp: string;
  currencyExRate: number;
  currencySecurity: string;
  isin: string;
  symbolImp: string;
  securityNameImp: string;
  security: Security;
  units: number;
  quotation: number;
  readyForTransaction: boolean;
  taxCost: number;
  transactionCost: number;
  cashaccountAmount: number;
  accruedInterest: number;
  field1StringImp: string;
  importTransactionPosFailedList: ImportTransactionPosFailed[];
  calcCashaccountAmount: number;
  fileNameOriginal: string;
  idTransaction: number;
  idTransactionMaybe: number;
  transactionError: string;
  fileType: string;

  public getId(): number {
    return this.idTransactionPos;
  }

}


