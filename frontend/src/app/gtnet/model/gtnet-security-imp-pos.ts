import {BaseID} from '../../lib/entities/base.id';
import {Security} from '../../entities/security';

/**
 * Position entity representing a single security to be imported via GTNet.
 * Each position contains identification data (ISIN, ticker symbol) and currency.
 */
export class GTNetSecurityImpPos implements BaseID {
  idGtNetSecurityImpPos: number = null;
  idGtNetSecurityImpHead: number = null;
  isin: string = null;
  tickerSymbol: string = null;
  currency: string = null;
  security: Security = null;

  getId(): number {
    return this.idGtNetSecurityImpPos;
  }
}
