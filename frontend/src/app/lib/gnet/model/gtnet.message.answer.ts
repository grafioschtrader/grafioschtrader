import { BaseID } from '../../entities/base.id';
import { GTNetMessageCodeType } from './gtnet.message';

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
  constructor(public gtNetMessageAnswer: GTNetMessageAnswer) {}
}
