import {ClassDescriptorInputAndShow} from '../../lib/dynamicfield/field.descriptor.input.and.show';
import {BaseParam} from '../../lib/entities/base.param';

export class GTNetMessage {
  idGtNetMessage: number;
  idGtNet: number = null;
  timestamp: number;
  sendRecv: SendReceivedType | string = null;
  replyTo: number = null;
  messageCode: GTNetMessageCodeType | string = null;
  gtNetMessageParamMap: Map<string, BaseParam> | { [key: string]: BaseParam };
  message: string = null;
  hasBeenRead: boolean = false;
  deliveryStatus: DeliveryStatus | string = DeliveryStatus.PENDING;
  waitDaysApply: number = 0;
  canDelete: boolean = false;
}

export enum DeliveryStatus {
  PENDING = 0,
  DELIVERED = 1,
  FAILED = 2
}

export class MsgCallParam {
  constructor(public formDefinitions: { [type: string]: ClassDescriptorInputAndShow }, public idGTNet: number,
              public replyTo: number, public gtNetMessage: GTNetMessage, public isAllMessage: boolean = false,
              public validResponseCodes: GTNetMessageCodeType[] = null,
              public idOpenDiscontinuedMessage: number = null) {
  }
}

export enum SendReceivedType {
  SEND = 0,
  RECEIVE = 1,
  ANSWER = 2
}

export enum GTNetMessageCodeType {
  GT_NET_PING = 0,

  GT_NET_FIRST_HANDSHAKE_SEL_RR_S = 1,
  GT_NET_FIRST_HANDSHAKE_ACCEPT_S = 2,
  GT_NET_FIRST_HANDSHAKE_REJECT_S = 3,
  GT_NET_FIRST_HANDSHAKE_REJECT_NOT_IN_LIST_S = 4,

  GT_NET_UPDATE_SERVERLIST_SEL_RR_C = 10,
  GT_NET_UPDATE_SERVERLIST_ACCEPT_S = 11,
  GT_NET_UPDATE_SERVERLIST_REJECTED_S = 12,
  GT_NET_UPDATE_SERVERLIST_REVOKE_SEL_C = 13,

  GT_NET_OFFLINE_ALL_C = 20,
  GT_NET_MAINTENANCE_ALL_C = 24,
  GT_NET_OPERATION_DISCONTINUED_ALL_C = 25,
  GT_NET_MAINTENANCE_CANCEL_ALL_C = 26,
  GT_NET_OPERATION_DISCONTINUED_CANCEL_ALL_C = 27,
  GT_NET_SETTINGS_UPDATED_ALL_C = 28,

  // Data exchange (50–59)
  GT_NET_DATA_REQUEST_SEL_RR_C = 50,
  GT_NET_DATA_REQUEST_ACCEPT_S = 52,
  GT_NET_DATA_REQUEST_REJECTED_S = 53,
  GT_NET_DATA_REVOKE_SEL_C = 54,

  // Lastprice exchange (60–69)
  GT_NET_LASTPRICE_EXCHANGE_SEL_C = 60,
  GT_NET_LASTPRICE_EXCHANGE_RESPONSE_S = 61,
  GT_NET_LASTPRICE_PUSH_SEL_C = 62,
  GT_NET_LASTPRICE_PUSH_ACK_S = 63,
  GT_NET_LASTPRICE_MAX_LIMIT_EXCEEDED_S = 64,

  // Exchange sync (30–39)
  GT_NET_EXCHANGE_SYNC_SEL_RR_C = 70,
  GT_NET_EXCHANGE_SYNC_RESPONSE_S = 71,

  // Historyquote exchange (80–84)
  GT_NET_HISTORYQUOTE_EXCHANGE_SEL_C = 80,
  GT_NET_HISTORYQUOTE_EXCHANGE_RESPONSE_S = 81,
  GT_NET_HISTORYQUOTE_PUSH_SEL_C = 82,
  GT_NET_HISTORYQUOTE_PUSH_ACK_S = 83,
  GT_NET_HISTORYQUOTE_MAX_LIMIT_EXCEEDED_S = 84,

  // Security lookup (90–93)
  GT_NET_SECURITY_LOOKUP_SEL_C = 90,
  GT_NET_SECURITY_LOOKUP_RESPONSE_S = 91,
  GT_NET_SECURITY_LOOKUP_NOT_FOUND_S = 92,
  GT_NET_SECURITY_LOOKUP_REJECTED_S = 93
}

/** Maps request codes (_RR_) to their valid response codes */
export const RESPONSE_CODE_MAP: { [key: number]: GTNetMessageCodeType[] } = {
  [GTNetMessageCodeType.GT_NET_FIRST_HANDSHAKE_SEL_RR_S]: [
    GTNetMessageCodeType.GT_NET_FIRST_HANDSHAKE_ACCEPT_S,
    GTNetMessageCodeType.GT_NET_FIRST_HANDSHAKE_REJECT_S
  ],
  [GTNetMessageCodeType.GT_NET_UPDATE_SERVERLIST_SEL_RR_C]: [
    GTNetMessageCodeType.GT_NET_UPDATE_SERVERLIST_ACCEPT_S,
    GTNetMessageCodeType.GT_NET_UPDATE_SERVERLIST_REJECTED_S
  ],
  [GTNetMessageCodeType.GT_NET_DATA_REQUEST_SEL_RR_C]: [
    GTNetMessageCodeType.GT_NET_DATA_REQUEST_ACCEPT_S,
    GTNetMessageCodeType.GT_NET_DATA_REQUEST_REJECTED_S
  ]
};

/** Checks if a message code is a request that requires a response */
export function isRequestRequiringResponse(code: GTNetMessageCodeType | string): boolean {
  const codeName = typeof code === 'string' ? code : GTNetMessageCodeType[code];
  return codeName?.includes('_RR_') ?? false;
}

/** Gets valid response codes for a request code */
export function getValidResponseCodes(requestCode: GTNetMessageCodeType | string): GTNetMessageCodeType[] {
  const codeValue = typeof requestCode === 'string'
    ? GTNetMessageCodeType[requestCode as keyof typeof GTNetMessageCodeType]
    : requestCode;
  return RESPONSE_CODE_MAP[codeValue] ?? [];
}

/** Maps announcement codes to their cancellation codes */
export const REVERSE_CODE_MAP: { [key: number]: GTNetMessageCodeType } = {
  [GTNetMessageCodeType.GT_NET_MAINTENANCE_ALL_C]: GTNetMessageCodeType.GT_NET_MAINTENANCE_CANCEL_ALL_C,
  [GTNetMessageCodeType.GT_NET_OPERATION_DISCONTINUED_ALL_C]: GTNetMessageCodeType.GT_NET_OPERATION_DISCONTINUED_CANCEL_ALL_C
};

/** Gets the cancel code for a reversible announcement message */
export function getReverseCode(messageCode: GTNetMessageCodeType | string): GTNetMessageCodeType | null {
  const codeValue = typeof messageCode === 'string'
    ? GTNetMessageCodeType[messageCode as keyof typeof GTNetMessageCodeType]
    : messageCode;
  return REVERSE_CODE_MAP[codeValue] ?? null;
}
