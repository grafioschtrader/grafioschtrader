import {ProposeTransientTransfer} from './propose.transient.transfer';

export abstract class Auditable extends ProposeTransientTransfer {
  createdBy: number = null;
  creationTime: string = null;
  lastModifiedBy: number = null;
  lastModifiedTime: string = null;
  version: number = null;
}
