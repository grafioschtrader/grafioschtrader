import {BaseID} from '../../lib/entities/base.id';
import {GTNetMessage} from './gtnet.message';
import {MessageComType} from '../../lib/mail/model/mail.send.recv';
import {BaseParam} from '../../lib/entities/base.param';

export class GTNet implements BaseID {
  idGtNet: number;
  domainRemoteName: string = null;
  timeZone: string = null;
  spreadCapability = true;
  entityServerState: number | GTNetServerStateTypes = null;
  acceptEntityRequest = false;
  dailyRequestLimit: number = null;
  dailyRequestLimitCount: number;
  dailyRequestLimitRemote: number;
  dailyRequestLimitRemoteCount: number;
  lastpriceServerState: number | GTNetServerStateTypes = null;
  acceptLastpriceRequest = false;
  lastpriceConsumerUsage: number;
  lastpriceUseDetailLog = 0;
  serverBusy = false;
  serverOnline: number | GTNetServerOnlineStatusTypes = GTNetServerOnlineStatusTypes.SOS_UNKNOWN;

  // Computed properties from GTNetConfig (read-only)
  authorized = false;
  lastpriceExchange: number | GTNetExchangeStatusTypes = GTNetExchangeStatusTypes.ES_NO_EXCHANGE;
  entityExchange: number | GTNetExchangeStatusTypes = GTNetExchangeStatusTypes.ES_NO_EXCHANGE;

  getId(): number {
    return this.idGtNet;
  }
}

export class MsgRequest {
  public gtNetMessageParamMap: Map<string, BaseParam> | { [key: string]: BaseParam };

  constructor(public idGTNetTargetDomain: number, public replyTo: number, public messageCode: MessageComType | string,
              public message: string) {
  }
}

export interface GTNetWithMessages {
  gtNetList: GTNet[];
  gtNetMessageMap: { [key: number]: GTNetMessage[] };
  gtNetMyEntryId: number;
}

export enum GTNetServerStateTypes {
  SS_NONE = 0,
  SS_CLOSED = 1,
  SS_MAINTENANCE = 2,
  SS_OPEN = 3
}

export enum GTNetServerOnlineStatusTypes {
  SOS_UNKNOWN = 0,
  SOS_ONLINE = 1,
  SOS_OFFLINE = 2
}

export enum GTNetExchangeStatusTypes {
  ES_NO_EXCHANGE = 0,
  ES_SEND = 1,
  ES_RECEIVE = 2,
  ES_BOTH = 3
}

/**
 * Represents a GTNetExchange configuration for a security or currency pair.
 * Contains 4 boolean flags controlling price data exchange via GTNet.
 */
export class GTNetExchange implements BaseID {
  idGtNetExchange: number;
  securitycurrency: any;
  lastpriceRecv: boolean;
  historicalRecv: boolean;
  lastpriceSend: boolean;
  historicalSend: boolean;
  detailCount?: number;

  getId(): number {
    return this.idGtNetExchange;
  }
}

/**
 * Enum for price types used in GTNetSupplierDetail
 */
export enum PriceType {
  LASTPRICE = 'LASTPRICE',
  HISTORICAL = 'HISTORICAL'
}

/**
 * Represents a GTNet supplier header entry.
 */
export interface GTNetSupplier {
  idGtNetSupplier: number;
  gtNet: GTNet;
  lastUpdate: string;
}

/**
 * Represents a detail entry for what price types a supplier offers.
 */
export interface GTNetSupplierDetail {
  idGtNetSupplierDetail: number;
  idGtNetSupplier: number;
  securitycurrency: any;
  priceType: PriceType;
}

/**
 * Combined DTO for supplier with details, used in expandable rows.
 */
export interface GTNetSupplierWithDetails {
  supplier: GTNetSupplier;
  details: GTNetSupplierDetail[];
}

export interface GTSecuritiyCurrencyExchange {
  securitiescurrenciesList: any[];
  exchangeMap: { [idSecuritycurrency: number]: GTNetExchange };
  idSecuritycurrenies: number[];
}
