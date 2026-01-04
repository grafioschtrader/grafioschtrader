import {Historyquote} from './historyquote';
import {Auditable} from '../lib/entities/auditable';


export class Securitycurrency extends Auditable {
  dtype?: string;
  idSecuritycurrency?: number = null;
  note?: string = null;
  fullLoadTimestamp: number;
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

  sTimestamp?: number = null;

  /** Receive intraday prices for this instrument via GTNet. */
  gtNetLastpriceRecv?: boolean = false;
  /** Receive historical price data for this instrument via GTNet. */
  gtNetHistoricalRecv?: boolean = false;
  /** Share intraday prices of this instrument via GTNet. */
  gtNetLastpriceSend?: boolean = false;
  /** Share historical price data of this instrument via GTNet. */
  gtNetHistoricalSend?: boolean = false;
  /** Timestamp when GTNet exchange settings were last modified. */
  gtNetLastModifiedTime?: Date;

  public override getId(): number {
    return this.idSecuritycurrency;
  }

}
