import type { PersistedImportTransactionPos } from '../../entities/import.transaction.pos';
import type { ImportTransactionTemplate } from '../../entities/import.transaction.template';

/** Saved import position with its matching parser template and file-path information. */
export interface CombineTemplateAndImpTransPos {
  importTransactionPos: PersistedImportTransactionPos;
  importTransactionTemplate: ImportTransactionTemplate | null;
  fullPath: boolean;
}
