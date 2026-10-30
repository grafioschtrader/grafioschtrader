import { Historyquote } from './historyquote';
import { Auditable } from '../lib/entities/auditable';

export class Securitycurrency extends Auditable {
  dtype?: string;
  idSecuritycurrency?: number | null = null;
  note?: string | null = null;
  fullLoadTimestamp: string;
  retryHistoryLoad?: number | null = null;
  idConnectorHistory?: string | null = null;
  urlHistoryExtend?: string | null = null;
  idConnectorIntra?: string | null = null;
  urlIntraExtend?: string | null = null;
  retryIntraLoad?: number | null = null;
  stockexchangeLink?: string | null = null;
  sPrevClose?: number;
  sChangePercentage?: number;
  sOpen?: number;
  sLast?: number;
  sLow?: number;
  sHigh?: number;
  /**
   * Number of fraction digits the backend rounded the prices of this instrument to, set for index levels only. Null
   * when the prices keep their full precision.
   */
  priceFractionDigits?: number;
  historyquoteList?: Historyquote[];
  name: string | null = null;

  sTimestamp?: string | null = null;

  /** Receive intraday prices for this instrument via GTNet. */
  gtNetLastpriceRecv?: boolean = false;
  /** Receive historical price data for this instrument via GTNet. */
  gtNetHistoricalRecv?: boolean = false;
  /** Share intraday prices of this instrument via GTNet. */
  gtNetLastpriceSend?: boolean = false;
  /** Share historical price data of this instrument via GTNet. */
  gtNetHistoricalSend?: boolean = false;
  /** Timestamp when GTNet exchange settings were last modified. */
  gtNetLastModifiedTime?: string;

  public override getId(): number | null | undefined {
    return this.idSecuritycurrency;
  }
}
