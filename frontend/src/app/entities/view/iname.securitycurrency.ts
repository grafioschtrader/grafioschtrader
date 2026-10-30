import { Securitycurrency } from '../securitycurrency';

export interface INameSecuritycurrency {
  getName(): string | null;

  getSecuritycurrency(): Securitycurrency;
}
