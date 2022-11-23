import {ClassDescriptorInputAndShow} from '../../shared/dynamicfield/field.descriptor.input.and.show';
import {BaseParam} from '../../entities/view/base.param';

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
  constructor(public formDefinitions: { [type: string]: ClassDescriptorInputAndShow }, public idsGTNet: number[],
              public replyTo: number, public gtNetMessage: GTNetMessage) {
  }
}

export enum SendReceivedType {
  SEND= 0,
  RECEIVE = 1,
  ANSWER=2
}

export enum GTNetMessageCodeType {
  GTNET_PING= 0,
  GTNET_FIRST_HANDSHAKE_S = 1,
  GTNET_FIRST_HANDSHAKE_ACCEPT_S= 2,
  GTNET_FIRST_HANDSHAKE_REJECT_S = 3,
  GTNET_UPDATE_SERVERLIST_C = 4,
  GTNET_UPDATE_SERVERLIST_ACCEPT = 5,
  GTNET_UPDATE_SERVERLIST_REJECTED_S = 6,
  GTNET_LASTPRICE_REQUEST_C = 7,
  GTNET_LASTPRICE_REQUEST_IN_PROCESS_S = 8,
  GTNET_LASTPRICE_REQUEST_ACCEPT_S = 9,
  GTNET_LASTPRICE_REQUEST_REJECTED_S = 10,
  GTNET_ENTITY_REQUEST_C = 11,
  GTNET_ENTITY_REQUEST_IN_PROCESS_S = 12,
  GTNET_ENTITY_REQUEST_ACCEPT_S = 13,
  GTNET_ENTITY_REQUEST_REJECTED_S = 14,
  GTNET_BOTH_REQUEST_C = 15,
  GTNET_BOTH_REQUEST_IN_PROCESS_S = 16,
  GTNET_BOTH_REQUEST_ACCEPT_S = 17,
  GTNET_BOTH_REQUEST_REJECTED_S = 18,
  GTNET_MAINTENANCE_C = 19,
  GTNET_OPERATION_DISCONTINUED_C = 20
}
