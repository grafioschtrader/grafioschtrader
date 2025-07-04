import {Securityaccount} from './securityaccount';
import {BaseID} from '../lib/entities/base.id';

export class ImportTransactionHead implements BaseID {
  idTransactionHead?: number;
  name: string = null;
  note: string = null;
  securityaccount: Securityaccount;

  public getId(): number {
    return this.idTransactionHead;
  }

}
