import {BaseID} from './base.id';
import {TemplateFormatType} from '../shared/types/template.format.type';
import {Auditable} from './auditable';

export class ImportTransactionTemplate extends Auditable implements BaseID {
  public static readonly KEY_NAME = 'idAssetClass';

  idTransactionImportTemplate: number;
  idTransactionImportPlatform: number;
  templateFormatType: TemplateFormatType | string = null;
  templatePurpose: string = null;
  templateCategory?: TemplateCategory;
  templateAsTxt: string = null;
  validSince ? = null;
  templateLanguage: string = null;

  public getId() {
    return this.idTransactionImportTemplate;
  }
}

export enum TemplateCategory {
  BUY_SELL_INSTRUMENT = 0,
  BUY_SELL_EQUITY = 1,
  BUY_SELL_BOND = 2,
  BUY_INSTRUMENT = 3,
  BUY_EQUITY = 4,
  BUY_BOND = 5,
  SELL_INSTRUMENT = 6,
  SELL_EQUITY = 7,
  SELL_BOND = 8,
  REPURCHASE_OFFER_ACCEPTED = 9,
  REPAYMENT_BOND = 10,
  PAID_DIVIDEND_INTEREST = 11,
  PAID_DIVIDEND_INTEREST_INTEREST_WITHOLDING_TAX = 12,
  PAID_DIVIDEND = 13,
  PAID_DIVIDEND_VARIANT_1 = 14,
  PAID_DIVIDEND_TAX_FREE = 15,
  PAID_INTEREST = 16,
  CSV_BASE = 20,
  CSV_ADDITION = 21
}
