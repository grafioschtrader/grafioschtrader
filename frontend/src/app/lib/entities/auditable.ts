import {ProposeTransientTransfer} from './propose.transient.transfer';

export abstract class Auditable extends ProposeTransientTransfer {
  createdBy: number = null;
  creationTime: number = null;
  lastModifiedBy: number = null;
  lastModifiedTime: number = null;
  version: number = null;
}
