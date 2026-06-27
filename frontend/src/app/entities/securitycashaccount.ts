import {Portfolio} from './portfolio';

export class Securitycashaccount {
  portfolio: Portfolio;

  idSecuritycashAccount: number = null;
  name?: string = null;
  note?: string = null;

  /** Optional active-until date. Null means the account is active indefinitely; a past date marks it terminated. */
  activeToDate?: string | Date = null;

  // TODO It does not match with server entity
  idPortfolio: number;

}
