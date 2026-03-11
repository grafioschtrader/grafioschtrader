import {Historyquote} from './historyquote';
import {Auditable} from '../lib/entities/auditable';


export class Securitycurrency extends Auditable {
  dtype?: string;
  idSecuritycurrency?: number = null;
  note?: string = null;
  fullLoadTimestamp: string;
  retryHistoryLoad?: number = null;
  idConnectorHistory?: string = null;
  urlHistoryExtend?: string = null;
  idConnectorIntra?: string = null;
  urlIntraExtend?: string = null;
  retryIntraLoad?: number = null;
  stockexchangeLink?: string = null;
  sPrevClose?: number;
  sChangePercentage?: number;
  sOpen?: number;
  sLast?: number;
  sLow?: number;
  sHigh?: number;
  historyquoteList?: Historyquote[];
  name: string = null;

  sTimestamp?: string = null;

  /** Receive intraday prices for this instrument via GTNet. */
  gtNetLastpriceRecv?: boolean = null;
  /** Receive historical price data for this instrument via GTNet. */
  gtNetHistoricalRecv?: boolean = null;
  /** Share intraday prices of this instrument via GTNet. */
  gtNetLastpriceSend?: boolean = null;
  /** Share historical price data of this instrument via GTNet. */
  gtNetHistoricalSend?: boolean = null;
  /** Timestamp when GTNet exchange settings were last modified. */
  gtNetLastModifiedTime?: string;

  public override getId(): number {
    return this.idSecuritycurrency;
  }

}
