import {ImportTransactionPos} from '../../entities/import.transaction.pos';
import {FailedParsedTemplateState, ParsedTemplateState} from './failed.parsed.template.state';


export class FormTemplateCheck {
  pdfAsTxt: string;
  idTransactionImportPlatform: number;
  importTransactionPos: ImportTransactionPos;
  successParsedTemplateState: ParsedTemplateState;
  failedParsedTemplateStateList: FailedParsedTemplateState[];

  constructor(idTransactionImportPlatform: number, pdfAsTxt: string) {
    this.idTransactionImportPlatform = idTransactionImportPlatform;
    this.pdfAsTxt = pdfAsTxt;
  }
}
