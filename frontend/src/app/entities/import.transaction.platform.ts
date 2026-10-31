import { BaseID } from '../lib/entities/base.id';
import { Auditable } from '../lib/entities/auditable';

export class ImportTransactionPlatform extends Auditable implements BaseID {
  idTransactionImportPlatform?: number;
  name: string | null = null;
  idCsvImportImplementation: string | null = null;

  public override getId() {
    return this.idTransactionImportPlatform;
  }
}
