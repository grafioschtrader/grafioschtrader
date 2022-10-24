import {BaseID} from "../../entities/base.id";
import {GTNetMessage} from "./gtnet.message";

export class GTNet implements BaseID {
  idGtNet: number;
  domainRemoteName: string;
  timeZone: string = null;
  spreadCapability: boolean;
  entityServerState: number;
  acceptEntityRequest: boolean;
  dailyRequestLimit: number;
  dailyRequestLimitCount: number;
  dailyRequestLimitRemote: number;
  dailyRequestLimitRemoteCount: number;
  lastpriceServerState: number;
  lastpriceConsumerUsage: number;
  lastpriceUseDetailLog: number;

  getId(): number {
    return this.idGtNet;
  }
}

export interface GTNetWithMessages {
   gtNetList: GTNet[];
   gtNetMessageMap: { [key: number]: GTNetMessage[]};
}
