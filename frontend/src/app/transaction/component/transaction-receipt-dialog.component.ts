import {Component, OnInit} from '@angular/core';
import {HttpResponse} from '@angular/common/http';
import {TranslateModule} from '@ngx-translate/core';
import {ButtonModule} from '@openng/optimus-ui/button';
import {DynamicDialogConfig, DynamicDialogRef} from '@openng/optimus-ui/dynamicdialog';

import saveAs from '../../lib/filesaver/filesaver';
import {TransactionReceiptTableComponent} from './transaction-receipt-table.component';
import {BusinessHelper} from '../../shared/helper/business.helper';
import {SecurityService} from '../../securitycurrency/service/security.service';
import {TransactionService} from '../service/transaction.service';
import {Transaction} from '../../entities/transaction';
import {SecurityTransactionSummary} from '../../entities/view/security.transaction.summary';

/** Data passed to the transaction receipt dialog via the DynamicDialog configuration. */
export interface TransactionReceiptDialogData {
  idSecuritycurrency: number;
  idPortfolio?: number;
  idSecurityaccount?: number;
}

/**
 * Dialog listing all transactions of a security with checkbox selection. For the selected transactions the backend
 * generates re-importable receipt PDFs which are downloaded as a single PDF or, for multiple selections, as a ZIP
 * archive. Opened programmatically through the DialogService (see TransactionReceiptService).
 */
@Component({
  template: `
    <transaction-receipt-table [transactions]="transactions" [marginBased]="marginBased"
                               (selectedChange)="selected = $event">
    </transaction-receipt-table>
    <div style="text-align: right; margin-top: 1rem;">
      <p-button [label]="'DOWNLOAD' | translate" [disabled]="selected.length === 0 || downloading"
                (click)="download()">
        <i class="pi pi-download" pButtonIcon></i>
      </p-button>
    </div>
  `,
  standalone: true,
  imports: [TranslateModule, ButtonModule, TransactionReceiptTableComponent]
})
export class TransactionReceiptDialogComponent implements OnInit {
  transactions: Transaction[] = [];
  selected: Transaction[] = [];
  marginBased = false;
  downloading = false;

  constructor(private dynamicDialogConfig: DynamicDialogConfig,
    private dynamicDialogRef: DynamicDialogRef,
    private securityService: SecurityService,
    private transactionService: TransactionService) {
  }

  ngOnInit(): void {
    const data: TransactionReceiptDialogData = this.dynamicDialogConfig.data;
    BusinessHelper.getSecurityTransactionSummary(this.securityService, data.idSecuritycurrency,
      data.idSecurityaccount ? [data.idSecurityaccount] : null, data.idPortfolio, false)
      .subscribe((sts: SecurityTransactionSummary) => {
        this.marginBased = BusinessHelper.isMarginProduct(sts.securityPositionSummary.security);
        this.transactions = sts.transactionPositionList
          .map(tp => tp.transaction)
          .filter(t => Transaction.isSecurityTransaction(t.transactionType));
      });
  }

  download(): void {
    this.downloading = true;
    this.transactionService.downloadReceipts(this.selected.map(t => t.idTransaction)).subscribe({
      next: (response: HttpResponse<Blob>) => {
        saveAs(response.body, this.getFileName(response));
        this.downloading = false;
        this.dynamicDialogRef.close();
      },
      error: () => this.downloading = false
    });
  }

  /** Takes the file name from the Content-Disposition header set by the backend. */
  private getFileName(response: HttpResponse<Blob>): string {
    const contentDisposition = response.headers.get('Content-Disposition');
    const match = contentDisposition ? /filename="([^"]+)"/.exec(contentDisposition) : null;
    return match ? match[1] : 'transaction_receipts.zip';
  }
}
