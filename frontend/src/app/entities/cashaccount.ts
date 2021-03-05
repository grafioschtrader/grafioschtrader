import {Securitycashaccount} from './securitycashaccount';

export class Cashaccount extends Securitycashaccount {
  public balance: number;
  public currency: string = null;
  public connectIdSecurityaccount: number = null;
}
