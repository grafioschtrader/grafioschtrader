import { Securitycashaccount } from './securitycashaccount';

export class Cashaccount extends Securitycashaccount {
  public balance: number;
  public currency: string | null = null;
  public borrowingRate: number | null = null;
  public connectIdSecurityaccount: number | null = null;
}
