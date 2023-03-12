import {BaseID} from '../../entities/base.id';
import {Exclude} from 'class-transformer';

export class MailSendRecv implements BaseID {
  idMailSendRecv: number;
  sendRecv: SendRecvType | string;
  idUserFrom: number;
  idUserTo: number;
  idRoleTo: number;
  idEntity: number;
  messageComType: MessageComType | string;
  idGtNet: number;
  subject: string;
  message: string;
  idReplyToLocal: number;
  idReplyToRemote: number;
  replyToRolePrivate: ReplyToRolePrivateType;
  sendRecvTime: Date;
  roleNameTo: string;
  hasBeenRead: boolean;

  @Exclude()
  getId(): number {
    return this.idMailSendRecv;
  }
}

export enum SendRecvType {
  SEND = 'S',
  RECEIVE = 'R'
}

export enum ReplyToRolePrivateType {
  REPLY_NORMAL = 0,
  REPLY_AS_ROLE = 1,
  REPLY_IS_PRIVATE = 2
}

export enum MessageComType {
  GENERAL_PURPOSE_USER_TO_USER = 0,
  SYSTEM_MISSING_USER_ACTION = 1,
  RECEIVED_PROPOSED_CHANGE = 2
}

export interface MailInboxWithSend {
  mailSendRecvList: MailSendRecv[];
  countMsgMap: { [idMailSendRecv: number]: number };
}

export interface AnswerWithFirstSend {
  numberOfAnswer: number;
  subject: string;
  idUserFrom: number;

}
