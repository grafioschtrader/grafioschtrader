import {BaseID} from '../../../lib/entities/base.id';
import {Security} from '../../../entities/security';
import {GTNetSecurityImpGap} from './gtnet-security-imp-gap';

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

  /**
   * List of gaps (mismatches) identified during the last GTNet import attempt.
   * Contains details about what didn't match (asset class, connectors) when the
   * import couldn't create a security.
   */
  gaps: GTNetSecurityImpGap[] = null;

  getId(): number {
    return this.idGtNetSecurityImpPos;
  }
}
