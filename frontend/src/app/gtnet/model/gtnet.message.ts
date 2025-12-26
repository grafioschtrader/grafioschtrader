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
              public replyTo: number, public gtNetMessage: GTNetMessage, public isAllMessage: boolean = false) {
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
  GT_NET_UPDATE_SERVERLIST_SEL_RR_C = 10,
  GT_NET_UPDATE_SERVERLIST_ACCEPT_S = 11,
  GT_NET_UPDATE_SERVERLIST_REJECTED_S = 12,
  GT_NET_UPDATE_SERVERLIST_REVOKE_SEL_C = 13,
  GT_NET_OFFLINE_ALL_C = 20,
  GT_NET_ONLINE_ALL_C = 21,
  GT_NET_BUSY_ALL_C = 22,
  GT_NET_RELEASED_BUSY_ALL_C = 23,
  GT_NET_MAINTENANCE_ALL_C = 24,
  GT_NET_OPERATION_DISCONTINUED_ALL_C = 25,
  GT_NET_DATA_REQUEST_SEL_RR_C = 50,
  GT_NET_DATA_REQUEST_IN_PROCESS_S = 51,
  GT_NET_DATA_REQUEST_ACCEPT_S = 52,
  GT_NET_DATA_REQUEST_REJECTED_S = 53,
  GT_NET_DATA_REVOKE_SEL_C = 54
}
