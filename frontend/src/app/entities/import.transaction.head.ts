import { Securityaccount } from './securityaccount';
import { BaseID } from '../lib/entities/base.id';

export class ImportTransactionHead implements BaseID {
  idTransactionHead?: number;
  name: string = null;
  note: string = null;
  securityaccount: Securityaccount;
  /** When true, the import templates are taken from the tenant's Grafioschtrader import platform. */
  useGtPlatform = false;

  public getId(): number {
    return this.idTransactionHead;
  }
}
