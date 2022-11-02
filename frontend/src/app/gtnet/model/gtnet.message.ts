import {GTNet} from "./gtnet";
import {FieldDescriptorInputAndShow} from "../../shared/dynamicfield/field.descriptor.input.and.show";

export class GTNetMessage {
  idGtNetMessage: number;
  idGtNet: number;
  timestamp: number;
  sendRecv: number = null;
  replyTo: number;
  messageCode: number | GTNetMessageCodeType = null;
  messageCodeValue: string;
  message: string;
}
export class MsgCallParam {
  constructor(public  formDefinitions: { [type: string]: FieldDescriptorInputAndShow[] }, public idsGTNet: number[],
              public replyTo: number, public gtNetMessage: GTNetMessage){}
}

export enum GTNetMessageCodeType {
  GTNET_UPDATE_SERVERLIST_C = 0,
  GTNET_UPDATE_SERVERLIST_ACCEPT = 1,
  GTNET_LASTPRICE_REQUEST_C = 2,
  GTNET_LASTPRICE_REQUEST_IN_PROCESS_S = 3,
  GTNET_LASTPRICE_REQUEST_ACCEPT_S = 4,
  GTNET_LASTPRICE_REQUEST_REJECTED_S = 5,
  GTNET_ENTITY_REQUEST_C = 6,
  GTNET_ENTITY_REQUEST_IN_PROCESS_S = 7,
  GTNET_ENTITY_REQUEST_ACCEPT_S = 8,
  GTNET_ENTITY_REQUEST_REJECTED_S = 9,
  GTNET_BOTH_REQUEST_C = 10,
  GTNET_BOTH_RREQUEST_IN_PROCESS_S = 11,
  GTNET_BOTH_RREQUEST_ACCEPT_S = 12,
  GTNET_BOTH_RREQUEST_REJECTED_S = 13,
  GTNET_MAINTENANCE_S = 14,
  GTNET_OPERATION_DISCONTINUED_S = 15
}
