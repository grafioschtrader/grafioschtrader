import { Exclude } from 'class-transformer';
import { BaseID } from '../lib/entities/base.id';
import { Auditable } from '../lib/entities/auditable';

/**
 * Marks an instrument whose issuer no longer supplies price data. Mirrors the backend
 * grafioschtrader.entities.BankruptSecurity.
 *
 * <p>
 * The end of day job keeps the price history of such an instrument complete by carrying its last known price forward,
 * because a single missing closing price removes that whole day from the performance report of every client holding the
 * instrument. The PK is the Integer surrogate idBankruptSecurity; idSecuritycurrency is functionally unique.
 * </p>
 */
export class BankruptSecurity extends Auditable implements BaseID {
  idBankruptSecurity: number = null;
  idSecuritycurrency: number = null;
  /** Day from which the issuer stopped delivering prices. Documentation only; the filling starts at the last price. */
  noDataSince: string = null;
  /**
   * First day on which the instrument can no longer be traded. From that day on a historical simulation neither buys
   * nor sells it; independent of noDataSince, because an instrument may still be quoted while its trading is suspended.
   */
  noTradingSince: string = null;
  note: string = null;

  @Exclude()
  override getId(): number {
    return this.idBankruptSecurity;
  }
}

/**
 * One row of the maintenance table: the marker itself plus enough of the instrument to recognize it, and the two newest
 * closing dates. It extends the entity rather than standing beside it so that the same object can be edited.
 */
export class BankruptSecurityRow extends BankruptSecurity {
  name: string = null;
  isin: string = null;
  currency: string = null;
  /** Newest closing price a provider, an import or the user supplied, so not a filled one. */
  lastRealQuoteDate: string = null;
  /** Newest closing price of any kind. A later date than lastRealQuoteDate is the filling at work. */
  lastQuoteDate: string = null;
}
