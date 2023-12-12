import {ImportTransactionPos} from '../../entities/import.transaction.pos';
import {ImportTransactionTemplate} from '../../entities/import.transaction.template';
import {BaseID} from '../../entities/base.id';

export class CombineTemplateAndImpTransPos implements BaseID {
  public static readonly EntityKeyName = 'importTransactionPos.idTransactionPos';

  importTransactionPos: ImportTransactionPos;
  importTransactionTemplate: ImportTransactionTemplate;
  fileType: string;
  fullPath: boolean;

  public getId(): number {
    return this.importTransactionPos.getId();
  }

}
