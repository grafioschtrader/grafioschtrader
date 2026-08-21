import {AfterViewInit, Component, OnInit, ViewChild} from '@angular/core';
import {HttpResponse} from '@angular/common/http';
import {TranslateModule, TranslateService} from '@ngx-translate/core';
import {DynamicDialogConfig, DynamicDialogRef} from '@openng/optimus-ui/dynamicdialog';

import saveAs from '../../lib/filesaver/filesaver';
import {FormBase} from '../../lib/edit/form.base';
import {DynamicFormComponent} from '../../lib/dynamic-form/containers/dynamic-form/dynamic-form.component';
import {DynamicFormModule} from '../../lib/dynamic-form/dynamic-form.module';
import {AppHelper} from '../../lib/helper/app.helper';
import {DynamicFieldHelper} from '../../lib/helper/dynamic.field.helper';
import {TranslateHelper} from '../../lib/helper/translate.helper';
import {DataType} from '../../lib/dynamic-form/models/data.type';
import {GlobalparameterService} from '../../lib/services/globalparameter.service';
import {TransactionService} from '../service/transaction.service';

/** Data passed to the transaction CSV export dialog via the DynamicDialog configuration. */
export interface TransactionExportCsvDialogData {
  /** Restricts the export to one portfolio; omitted for the tenant-wide export. */
  idPortfolio?: number;
}

/**
 * Dialog for downloading the re-importable transaction CSV export with an optional date range. The backend produces
 * one CSV file per securities account: a single file is downloaded as plain CSV, several files as ZIP archive.
 * Opened programmatically through the DialogService from the transaction tables; the portfolio table passes its
 * portfolio ID, the tenant table exports all securities accounts.
 */
@Component({
  template: `
    <p>{{ 'EXPORT_TRANSACTIONS_CSV_MARGIN_HINT' | translate }}</p>
    <dynamic-form [config]="config" [formConfig]="formConfig" [translateService]="translateService" #form="dynamicForm"
                  (submitBt)="submit($event)">
    </dynamic-form>
  `,
  standalone: true,
  imports: [DynamicFormModule, TranslateModule]
})
export class TransactionExportCsvDialogComponent extends FormBase implements OnInit, AfterViewInit {
  @ViewChild(DynamicFormComponent) form: DynamicFormComponent;

  constructor(private dynamicDialogConfig: DynamicDialogConfig,
    private dynamicDialogRef: DynamicDialogRef,
    public translateService: TranslateService,
    private gps: GlobalparameterService,
    private transactionService: TransactionService) {
    super();
  }

  ngOnInit(): void {
    this.formConfig = AppHelper.getDefaultFormConfig(this.gps, 5);
    this.config = [
      DynamicFieldHelper.createFieldPcalendarHeqF(DataType.DateString, 'dateFrom', false),
      DynamicFieldHelper.createFieldPcalendarHeqF(DataType.DateString, 'dateTo', false),
      DynamicFieldHelper.createSubmitButton('DOWNLOAD')
    ];
    this.configObject = TranslateHelper.prepareFieldsAndErrors(this.translateService, this.config);
  }

  ngAfterViewInit(): void {
    setTimeout(() => this.form.setDefaultValuesAndEnableSubmit());
  }

  submit(value: { [name: string]: any }): void {
    const request: { dateFrom?: string; dateTo?: string } = {};
    this.form.cleanMaskAndTransferValuesToBusinessObject(request, true);
    const data: TransactionExportCsvDialogData = this.dynamicDialogConfig.data || {};
    this.transactionService.exportTransactionsCsv(data.idPortfolio ?? null, request.dateFrom ?? null,
      request.dateTo ?? null).subscribe({
      next: (response: HttpResponse<Blob>) => {
        saveAs(response.body, this.getFileName(response));
        this.dynamicDialogRef.close();
      },
      error: () => this.configObject.submit.disabled = false
    });
  }

  /** Takes the file name from the Content-Disposition header set by the backend. */
  private getFileName(response: HttpResponse<Blob>): string {
    const contentDisposition = response.headers.get('Content-Disposition');
    const match = contentDisposition ? /filename="([^"]+)"/.exec(contentDisposition) : null;
    return match ? match[1] : 'gt_transaction_export.zip';
  }
}
