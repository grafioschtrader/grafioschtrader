import type { ImportTransactionPos } from '../../entities/import.transaction.pos';
import { FailedParsedTemplateState, ParsedTemplateState } from './failed.parsed.template.state';

export class FormTemplateCheck {
  pdfAsTxt: string;
  idTransactionImportPlatform: number;

  constructor(idTransactionImportPlatform: number, pdfAsTxt: string) {
    this.idTransactionImportPlatform = idTransactionImportPlatform;
    this.pdfAsTxt = pdfAsTxt;
  }
}

/** Template parsing adds result data to the submitted text and platform. */
export interface FormTemplateCheckResult extends FormTemplateCheck {
  importTransactionPos: ImportTransactionPos | null;
  successParsedTemplateState: ParsedTemplateState | null;
  failedParsedTemplateStateList: FailedParsedTemplateState[] | null;
}
