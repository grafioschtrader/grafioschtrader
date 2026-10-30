import { BaseID } from '../../../lib/entities/base.id';

/**
 * Header entity for organizing GTNet security import operations. Groups multiple security
 * import positions together for batch processing.
 */
export class GTNetSecurityImpHead implements BaseID {
  idGtNetSecurityImpHead: number | null = null;
  name: string | null = null;
  note: string | null = null;

  getId(): number | null {
    return this.idGtNetSecurityImpHead;
  }
}
