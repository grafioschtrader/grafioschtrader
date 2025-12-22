import {BaseID} from '../../lib/entities/base.id';
import {GTNetMessage} from './gtnet.message';
import {MessageComType} from '../../lib/mail/model/mail.send.recv';
import {BaseParam} from '../../lib/entities/base.param';

/**
 * Enum for entity kinds - types of data that can be exchanged.
 */
export enum GTNetExchangeKindType {
  LAST_PRICE = 0,
  HISTORICAL_PRICES = 1
}

/**
 * Represents a data type configuration for a specific GTNet connection.
 * Each GTNet can have multiple GTNetEntity entries (one per data type).
 */
export interface GTNetEntity {
  idGtNetEntity?: number;
  idGtNet: number;
  entityKind: GTNetExchangeKindType|string;
  serverState: GTNetServerStateTypes;
  acceptRequest: boolean;
  gtNetConfigEntity?: GTNetConfigEntity;
}

/**
 * Entity-specific configuration for exchange settings, logging, and consumer usage.
 */
export interface GTNetConfigEntity {
  idGtNetConfigEntity?: number;
  idGtNetConfig: number;
  idGtNetEntity: number;
  exchange: GTNetExchangeStatusTypes;
  useDetailLog: boolean;
  consumerUsage: number;
}

export class GTNet implements BaseID {
  idGtNet: number;
  domainRemoteName: string = null;
  timeZone: string = null;
  spreadCapability = true;
  dailyRequestLimit: number = null;
  dailyRequestLimitRemote: number;
  serverBusy = false;
  serverOnline: number | GTNetServerOnlineStatusTypes = GTNetServerOnlineStatusTypes.SOS_UNKNOWN;
  // Collection of entity-specific configurations
  gtNetEntities: GTNetEntity[] = [];
  // Computed properties from GTNetConfig (read-only)
  authorized = false;

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
  SS_OPEN = 1,
  SS_CLOSED = 2,
  SS_MAINTENANCE = 3
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

export interface GTNetCallParam {
  gtNet: GTNet;
  isMyEntry: boolean;
}
