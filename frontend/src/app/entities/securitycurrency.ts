import {Historyquote} from './historyquote';
import {Auditable} from '../lib/entities/auditable';


export class Securitycurrency extends Auditable {
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

  public override getId(): number {
    return this.idSecuritycurrency;
  }

}
