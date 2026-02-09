import {BaseID} from '../../lib/entities/base.id';
import {MessageComType} from '../../lib/mail/model/mail.send.recv';
import {BaseParam} from '../../lib/entities/base.param';

/**
 * Enum for entity kinds - types of data that can be exchanged.
 */
export enum GTNetExchangeKindType {
  LAST_PRICE = 0,
  HISTORICAL_PRICES = 1,
  SECURITY_METADATA = 2
}

/**
 * Defines the acceptance modes for incoming GTNet data exchange requests.
 * AC_CLOSED: No requests accepted, data exchange disabled
 * AC_OPEN: Accepts incoming requests, provides data to remote instances
 * AC_PUSH_OPEN: Accepts requests AND actively receives pushed updates (for both LAST_PRICE and HISTORICAL_PRICES)
 */
export enum AcceptRequestTypes {
  AC_CLOSED = 0,
  AC_OPEN = 1,
  AC_PUSH_OPEN = 2
}

/**
 * Defines logging levels for GTNet exchange operations, separate for supplier and consumer roles.
 * SCL_OFF: Logging is disabled for this role.
 * SCL_OVERVIEW: Exchange statistics are recorded to GTNetExchangeLog.
 * SCL_DETAIL: Includes overview logging plus detailed price change audit trail.
 */
export enum SupplierConsumerLogTypes {
  SCL_OFF = 0,
  SCL_OVERVIEW = 1,
  SCL_DETAIL = 2
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
  acceptRequest: AcceptRequestTypes|string;
  /**
   * Maximum number of instruments (securities or currency pairs) that can be transferred in a single request.
   * For example, 300 for LAST_PRICE means a maximum of 300 instruments per request.
   */
  maxLimit?: number;
  gtNetConfigEntity?: GTNetConfigEntity;
}

/**
 * Entity-specific configuration for exchange settings, logging, and consumer usage.
 * Primary key is shared with GTNetEntity (idGtNetEntity).
 */
export interface GTNetConfigEntity {
  idGtNetEntity: number;
  exchange: boolean;
  supplierLog: SupplierConsumerLogTypes|string;
  consumerLog: SupplierConsumerLogTypes|string;
  consumerUsage: number;
}

/**
 * GTNet configuration containing connection settings and token presence indicators.
 * Created after a successful handshake with a remote GT instance.
 * Primary key is shared with GTNet (idGtNet).
 */
export interface GTNetConfig {
  idGtNet: number;
  dailyRequestLimitCount?: number;
  dailyRequestLimitRemoteCount?: number;
  authorizedRemoteEntry?: boolean;
  /**
   * Counter that tracks the number of request violations from this remote domain.
   * Incremented when the remote sends a request that exceeds the configured max_limit.
   */
  requestViolationCount?: number;
}

export class GTNet implements BaseID {
  idGtNet: number;
  domainRemoteName: string = null;
  timeZone: string = null;
  spreadCapability = true;
  dailyRequestLimit: number = null;
  serverBusy = false;
  serverOnline: number | GTNetServerOnlineStatusTypes = GTNetServerOnlineStatusTypes.SOS_UNKNOWN;
  allowServerCreation = false;
  // Collection of entity-specific configurations
  gtNetEntities: GTNetEntity[] = [];
  // Connection configuration with token presence indicators
  gtNetConfig: GTNetConfig = null;

  getId(): number {
    return this.idGtNet;
  }

}

export class MsgRequest {
  public gtNetMessageParamMap: Map<string, BaseParam> | { [key: string]: BaseParam };
  public waitDaysApply: number = null;
  /**
   * ID of the original message being cancelled. Only used for cancellation messages
   * (GT_NET_MAINTENANCE_CANCEL_ALL_C, GT_NET_OPERATION_DISCONTINUED_CANCEL_ALL_C).
   */
  public idOriginalMessage: number = null;
  /**
   * Visibility level for admin messages. Controls who can see the message:
   * ALL_USERS = visible to everyone, ADMIN_ONLY = visible only to administrators.
   */
  public visibility: string = null;

  constructor(public idGTNetTargetDomain: number, public replyTo: number, public messageCode: MessageComType | string,
              public message: string) {
  }
}

export interface GTNetWithMessages {
  gtNetList: GTNet[];
  /** Message count per idGtNet - used to determine if expander should show */
  gtNetMessageCountMap: { [key: number]: number };
  /** Outgoing pending replies grouped by idGtNet - used for "Answer expected" column */
  outgoingPendingReplies: { [key: number]: number[] };
  /** Incoming pending replies grouped by idGtNet - used for "To be answered" column */
  incomingPendingReplies: { [key: number]: number[] };
  gtNetMyEntryId: number;
  /**
   * ID of an open GT_NET_OPERATION_DISCONTINUED_ALL_C message if one exists.
   * Only one such message can be open at a time per instance.
   */
  idOpenDiscontinuedMessage: number;
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

/**
 * Interface for GTNet exchange fields on Securitycurrency entities.
 * These fields are now directly on Security and Currencypair entities.
 */
export interface GTNetExchangeFields {
  gtNetLastpriceRecv?: boolean;
  gtNetHistoricalRecv?: boolean;
  gtNetLastpriceSend?: boolean;
  gtNetHistoricalSend?: boolean;
  gtNetLastModifiedTime?: Date;
}

/**
 * Represents a detail entry for what entity kinds a supplier offers.
 */
export interface GTNetSupplierDetail {
  idGtNetSupplierDetail: number;
  idGtNet: number;
  idEntity: number;
  entityKind: GTNetExchangeKindType;
}

/**
 * Combined DTO for supplier with details, used in expandable rows.
 * Contains the GTNet domain information (with domainRemoteName and config)
 * along with a list of detail entries for price types offered.
 */
export interface GTNetSupplierWithDetails {
  gtNet: GTNet;
  details: GTNetSupplierDetail[];
}

/**
 * DTO containing securities or currency pairs with their GTNet exchange configurations.
 * The GTNet fields are now directly on the Securitycurrency entities.
 */
export interface GTSecuritiyCurrencyExchange {
  securitiescurrenciesList: any[];
  idSecuritycurrenies: number[];
}

export interface GTNetCallParam {
  gtNet: GTNet;
  isMyEntry: boolean;
}
