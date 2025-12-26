import {BaseID} from '../../lib/entities/base.id';
import {GTNetMessageCodeType} from './gtnet.message';

/**
 * Defines automatic response templates for incoming GTNet messages requiring replies.
 * Each row represents one conditional response option, with multiple rows per request type
 * forming a priority-ordered chain. The priority field determines evaluation order.
 */
export class GTNetMessageAnswer implements BaseID {
  idGtNetMessageAnswer: number = null;
  requestMsgCode: GTNetMessageCodeType | number = null;
  responseMsgCode: GTNetMessageCodeType | number = null;
  priority: number = 1;
  responseMsgConditional: string = null;
  responseMsgMessage: string = null;
  waitDaysApply: number = 0;

  getId(): number {
    return this.idGtNetMessageAnswer;
  }
}

/**
 * Call parameter for GTNetMessageAnswer edit dialog.
 */
export class GTNetMessageAnswerCallParam {
  constructor(public gtNetMessageAnswer: GTNetMessageAnswer) {
  }
}

/** Request codes that support auto-response configuration */
export const REQUEST_CODES_FOR_AUTO_RESPONSE: GTNetMessageCodeType[] = [
  GTNetMessageCodeType.GT_NET_FIRST_HANDSHAKE_SEL_RR_S,
  GTNetMessageCodeType.GT_NET_UPDATE_SERVERLIST_SEL_RR_C,
  GTNetMessageCodeType.GT_NET_DATA_REQUEST_SEL_RR_C
];

/** Maps request codes to their possible response codes */
export const REQUEST_TO_RESPONSE_CODES: { [key: number]: GTNetMessageCodeType[] } = {
  [GTNetMessageCodeType.GT_NET_FIRST_HANDSHAKE_SEL_RR_S]: [
    GTNetMessageCodeType.GT_NET_FIRST_HANDSHAKE_ACCEPT_S,
    GTNetMessageCodeType.GT_NET_FIRST_HANDSHAKE_REJECT_S,
    GTNetMessageCodeType.GT_NET_FIRST_HANDSHAKE_REJECT_NOT_IN_LIST_S
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

/**
 * Gets valid response codes for a given request code.
 */
export function getResponseCodesForRequest(requestCode: GTNetMessageCodeType | number): GTNetMessageCodeType[] {
  const codeValue = typeof requestCode === 'number' ? requestCode : requestCode;
  return REQUEST_TO_RESPONSE_CODES[codeValue] ?? [];
}
