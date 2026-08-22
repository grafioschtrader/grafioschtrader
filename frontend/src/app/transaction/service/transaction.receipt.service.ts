import { Injectable } from '@angular/core';
import { TranslateService } from '@ngx-translate/core';
import { DialogService } from '@openng/optimus-ui/dynamicdialog';

import {
  TransactionReceiptDialogComponent,
  TransactionReceiptDialogData
} from '../component/transaction-receipt-dialog.component';

/**
 * Opens the transaction receipt dialog for a security. Kept as a small service so menu providers like
 * TimeSeriesQuotesService, which cannot host a dialog in a template, can trigger the dialog programmatically
 * through the Optimus DialogService.
 */
@Injectable()
export class TransactionReceiptService {
  constructor(
    private dialogService: DialogService,
    private translateService: TranslateService
  ) {}

  /**
   * Opens the receipt dialog listing the security's transactions with checkbox selection.
   *
   * @param idSecuritycurrency the security whose transactions are offered for receipt creation
   * @param idPortfolio optional portfolio scope of the transaction list
   * @param idSecurityaccount optional security account scope of the transaction list
   */
  openDialog(idSecuritycurrency: number, idPortfolio: number, idSecurityaccount: number): void {
    const data: TransactionReceiptDialogData = { idSecuritycurrency, idPortfolio, idSecurityaccount };
    this.dialogService.open(TransactionReceiptDialogComponent, {
      header: this.translateService.instant('TRANSACTION_RECEIPTS'),
      data,
      width: '850px',
      modal: true,
      closable: true,
      closeOnEscape: true
    });
  }
}
