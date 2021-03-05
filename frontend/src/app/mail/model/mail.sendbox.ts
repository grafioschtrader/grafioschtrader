import {MailInOut} from './mail.in.out';

export class MailSendbox extends MailInOut {
  domainTo: string;
  sendTime: Date;
}
