import {BaseID} from './base.id';
import {Auditable} from './auditable';

export class ImportTransactionPlatform extends Auditable implements BaseID {
  idTransactionImportPlatform: number;
  name: string = null;
  idCsvImportImplementation: string = null;

  public override getId() {
    return this.idTransactionImportPlatform;
  }

}
