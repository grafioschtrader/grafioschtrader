import {BaseID} from '../../entities/base.id';
import {GTNetMessage} from './gtnet.message';
import {MessageComType} from '../../lib/mail/model/mail.send.recv';
import {BaseParam} from '../../entities/view/base.param';

export class GTNet implements BaseID {
  idGtNet: number;
  domainRemoteName: string = null;
  timeZone: string = null;
  spreadCapability = true;
  entityServerState: number | GTNetServerStateTypes = null;
  acceptEntityRequest = false;
  dailyRequestLimit: number = null;
  dailyRequestLimitCount: number;
  dailyRequestLimitRemote: number;
  dailyRequestLimitRemoteCount: number;
  lastpriceServerState: number | GTNetServerStateTypes = null;
  acceptLastpriceRequest = false;
  lastpriceConsumerUsage: number;
  lastpriceUseDetailLog = 0;

  getId(): number {
    return this.idGtNet;
  }
}

export class MsgRequest {
  public gtNetMessageParamMap: Map<string, BaseParam> | { [key: string]: BaseParam };

  constructor(public idGTNetTargetDomain: number, public replyTo: number, public messageCode: MessageComType | string,
              public message: string) {
  }
}

export interface GTNetWithMessages {
  gtNetList: GTNet[];
  gtNetMessageMap: { [key: number]: GTNetMessage[] };
  gtNetMyEntryId: number;
}

export enum GTNetServerStateTypes {
  SS_NONE = 0,
  SS_CLOSED = 1,
  SS_MAINTENANCE = 2,
  SS_OPEN = 3
}




