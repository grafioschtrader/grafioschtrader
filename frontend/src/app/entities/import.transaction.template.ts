import {BaseID} from './base.id';
import {TemplateFormatType} from '../shared/types/template.format.type';
import {Auditable} from './auditable';

export class ImportTransactionTemplate extends Auditable implements BaseID {
  public static readonly KEY_NAME = 'idAssetClass';

  idTransactionImportTemplate: number;
  idTransactionImportPlatform: number;
  templateFormatType: TemplateFormatType | string = null;
  templatePurpose: string = null;
  templateAsTxt: string = null;
  validSince ? = null;
  templateLanguage: string = null;

  public getId() {
    return this.idTransactionImportTemplate;
  }

}
