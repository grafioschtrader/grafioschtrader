import {Portfolio} from './portfolio';

export class Securitycashaccount {
  portfolio: Portfolio;

  idSecuritycashAccount: number = null;
  name?: string = null;
  note?: string = null;

  // TODO It does not match with server entity
  idPortfolio: number;

}
