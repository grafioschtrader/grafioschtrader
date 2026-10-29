import { ProposeTransientTransfer } from './propose.transient.transfer';

export abstract class Auditable extends ProposeTransientTransfer {
  createdBy: number | null = null;
  creationTime: string | null = null;
  lastModifiedBy: number | null = null;
  lastModifiedTime: string | null = null;
  version: number | null = null;
}
