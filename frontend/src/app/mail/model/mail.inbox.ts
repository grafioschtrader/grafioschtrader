import {BaseID} from '../../entities/base.id';
import {Exclude} from 'class-transformer';
import {MailInOut} from './mail.in.out';

export class MailInbox extends MailInOut {
  domainFrom: string;
  receivedTime: Date;
  hasBeenRead: boolean;
}
