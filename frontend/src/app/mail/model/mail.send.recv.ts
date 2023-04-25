import {BaseID} from '../../entities/base.id';
import {Exclude} from 'class-transformer';
import {ValueKeyHtmlSelectOptions} from '../../dynamic-form/models/value.key.html.select.options';

export class MailSettingForwardVar {
  public static readonly MESSAGE_COM_TYPE = 'messageComType';
  public static readonly MESSAGE_TARGET_TYPE = 'messageTargetType';
  public static readonly ID_USER_DIRECT = 'idUserRedirect';

}

export class MailSendRecv implements BaseID {
  idMailSendRecv: number;
  sendRecv: SendRecvType | string;
  idUserFrom: number;
  idUserTo: number;
  idRoleTo: number;
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

export class MailSettingForward implements BaseID{
  idMailSettingForward: number;
  idUser: number;
  messageComType: MessageComType = null;
  messageTargetType: MessageTargetType = null;
  idUserRedirect: number = null;

  @Exclude()
  getId(): number {
    return this.idMailSettingForward;
  }
}

export class MailSettingForwardParam {
  constructor(public possibleMsgComType: string[], public mailSendForwardDefault: MailSendForwardDefault,
              public mailSettingForward: MailSettingForward ) {
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
  USER_GENERAL_PURPOSE_USER_TO_USER = 0,
  USER_SECURITY_HELD_INACTIVE = 1,
  USER_SECURITY_MISSING_DIV_INTEREST = 2,
  USER_ADMIN_ANNOUNCEMENT = 3,
  USER_RECEIVED_PROPOSED_CHANGE = 8,
  MAIN_ADMIN_HISTORY_PROVIDER_NOT_WORKING= 50,
  MAIN_ADMIN_RELEASE_LOGOUT = 51

}

export enum MessageTargetType {
  NO_MAIL = 0,
  INTERNAL_MAIL = 1,
  EXTERNAL_MAIL = 2,
  INTERNAL_AND_EXTERNAL_MAIL = 3
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

export interface MailSendForwardDefault {
  mainAdminBaseValue: number;
  canRedirectToUsers: ValueKeyHtmlSelectOptions[];
  mailSendForwardDefaultMapForUser: { [messageComType: MessageComType|number]: MailSendForwardDefaultConfig};
}

export interface MailSendForwardDefaultConfig {
  messageTargetDefaultType: MessageTargetType | string;
  mttPossibleTypeSet: MessageTargetType[];
  canRedirect: boolean;
}
