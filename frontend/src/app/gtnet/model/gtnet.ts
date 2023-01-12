import {BaseID} from '../../entities/base.id';
import {GTNetMessage} from './gtnet.message';

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
  lastpriceConsumerUsage: number;
  lastpriceUseDetailLog = 0;

  getId(): number {
    return this.idGtNet;
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




