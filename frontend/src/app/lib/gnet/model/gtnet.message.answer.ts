import { BaseID } from '../../entities/base.id';
import { GTNetMessageCodeType } from './gtnet.message';

/**
 * Defines automatic response templates for incoming GTNet messages requiring replies.
 * Each row represents one conditional response option, with multiple rows per request type
 * forming a priority-ordered chain. The priority field determines evaluation order.
 */
export class GTNetMessageAnswer implements BaseID {
  idGtNetMessageAnswer: number | null = null;
  requestMsgCode: GTNetMessageCodeType | number | null = null;
  responseMsgCode: GTNetMessageCodeType | number | null = null;
  priority: number = 1;
  responseMsgConditional: string | null = null;
  responseMsgMessage: string | null = null;
  waitDaysApply: number = 0;

  getId(): number | null {
    return this.idGtNetMessageAnswer;
  }
}

/**
 * Call parameter for GTNetMessageAnswer edit dialog.
 */
export class GTNetMessageAnswerCallParam {
  constructor(public gtNetMessageAnswer: GTNetMessageAnswer) {}
}
