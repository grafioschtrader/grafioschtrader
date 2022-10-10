import {BaseID} from "../../entities/base.id";
import {GTNetMessage} from "./gtnet.message";

export class GTNet implements BaseID {
  idGtNet: number;
  domainRemoteName: string;
  spreadCapability: boolean;
  allowGiveAway: boolean;
  acceptRequest: boolean;
  dailyRequestLimit: number;
  dailyRequestLimitCount: number;
  dailyRequestLimitRemote: number;
  dailyRequestLimitRemoteCount: number;
  lastpriceSupplierCapability: number;
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
