import { BaseID } from '../../../lib/entities/base.id';
import { Security } from '../../../entities/security';
import { GTNetSecurityImpGap } from './gtnet-security-imp-gap';

/**
 * Position entity representing a single security to be imported via GTNet.
 * Each position contains identification data (ISIN, ticker symbol) and currency.
 */
export class GTNetSecurityImpPos implements BaseID {
  idGtNetSecurityImpPos: number | null = null;
  idGtNetSecurityImpHead: number | null = null;
  isin: string | null = null;
  tickerSymbol: string | null = null;
  currency: string | null = null;
  security: Security | null = null;

  /**
   * List of gaps (mismatches) identified during the last GTNet import attempt.
   * Contains details about what didn't match (asset class, connectors) when the
   * import couldn't create a security.
   */
  gaps: GTNetSecurityImpGap[] | null = null;

  getId(): number | null {
    return this.idGtNetSecurityImpPos;
  }
}
