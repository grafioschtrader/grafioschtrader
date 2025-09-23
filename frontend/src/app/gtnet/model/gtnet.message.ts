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
}

export class MsgCallParam {
  constructor(public formDefinitions: { [type: string]: ClassDescriptorInputAndShow }, public idGTNet: number,
              public replyTo: number, public gtNetMessage: GTNetMessage) {
  }
}

export enum SendReceivedType {
  SEND = 0,
  RECEIVE = 1,
  ANSWER = 2
}

export enum GTNetMessageCodeType {
  GT_NET_PING = 0,
  GT_NET_FIRST_HANDSHAKE_S = 1,
  GT_NET_FIRST_HANDSHAKE_ACCEPT_S = 2,
  GT_NET_FIRST_HANDSHAKE_REJECT_S = 3,
  GT_NET_UPDATE_SERVERLIST_SEL_C = 10,
  GT_NET_UPDATE_SERVERLIST_ACCEPT_S = 11,
  GT_NET_UPDATE_SERVERLIST_REJECTED_S = 12,
  GT_NET_UPDATE_SERVERLIST_REVOKE_SEL_C = 13,
  GT_NET_LASTPRICE_REQUEST_SEL_C = 20,
  GT_NET_LASTPRICE_REQUEST_IN_PROCESS_S = 21,
  GT_NET_LASTPRICE_REQUEST_ACCEPT_S = 22,
  GT_NET_LASTPRICE_REQUEST_REJECTED_S = 23,
  GT_NET_LASTPRICE_REVOKE_SEL_C = 24,
  GT_NET_ENTITY_REQUEST_SEL_C = 30,
  GT_NET_ENTITY_REQUEST_IN_PROCESS_S = 31,
  GT_NET_ENTITY_REQUEST_ACCEPT_S = 32,
  GT_NET_ENTITY_REQUEST_REJECTED_S = 33,
  GT_NET_ENTITY_REVOKE_SEL_C = 34,
  GT_NET_BOTH_REQUEST_SEL_C = 40,
  GT_NET_BOTH_REQUEST_IN_PROCESS_S = 41,
  GT_NET_BOTH_REQUEST_ACCEPT_S = 42,
  GT_NET_BOTH_REQUEST_REJECTED_S = 43,
  GT_NET_BOTH_REVOKE_SEL_C = 44,
  GT_NET_MAINTENANCE_ALL_C = 50,
  GT_NET_OPERATION_DISCONTINUED_ALL_C = 51
}
