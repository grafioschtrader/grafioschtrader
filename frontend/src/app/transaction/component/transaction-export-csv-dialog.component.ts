import { afterNextRender, Component, DestroyRef, inject, OnInit, viewChild } from '@angular/core';
import { takeUntilDestroyed } from '@angular/core/rxjs-interop';
import { HttpResponse } from '@angular/common/http';
import { TranslateModule, TranslateService } from '@ngx-translate/core';
import { DynamicDialogConfig, DynamicDialogRef } from '@openng/optimus-ui/dynamicdialog';

import saveAs from '../../lib/filesaver/filesaver';
import { FormBase } from '../../lib/edit/form.base';
import { DynamicFormComponent } from '../../lib/dynamic-form/containers/dynamic-form/dynamic-form.component';
import { DYNAMIC_FORM_IMPORTS } from '../../lib/dynamic-form/dynamic-form.imports';
import { AppHelper } from '../../lib/helper/app.helper';
import { DynamicFieldHelper } from '../../lib/helper/dynamic.field.helper';
import { TranslateHelper } from '../../lib/helper/translate.helper';
import { DataType } from '../../lib/dynamic-form/models/data.type';
import { GlobalparameterService } from '../../lib/services/globalparameter.service';
import { HelpIds } from '../../lib/help/help.ids';
import { TransactionService } from '../service/transaction.service';

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
    <dynamic-form
      [config]="config"
      [formConfig]="formConfig"
      [translateService]="translateService"
      #form="dynamicForm"
      (submitBt)="submit($event)" />
  `,
  imports: [...DYNAMIC_FORM_IMPORTS, TranslateModule]
})
export class TransactionExportCsvDialogComponent extends FormBase implements OnInit {
  private dynamicDialogConfig: DynamicDialogConfig = inject(DynamicDialogConfig);
  private dynamicDialogRef: DynamicDialogRef = inject(DynamicDialogRef);
  public translateService: TranslateService = inject(TranslateService);
  private gps: GlobalparameterService = inject(GlobalparameterService);
  private transactionService: TransactionService = inject(TransactionService);

  private readonly destroyRef = inject(DestroyRef);
  readonly form = viewChild.required(DynamicFormComponent);

  constructor() {
    super();
    afterNextRender(() => this.form().setDefaultValuesAndEnableSubmit());
  }

  ngOnInit(): void {
    this.formConfig = AppHelper.getDefaultFormConfig(this.gps, 5, this.helpLink.bind(this));
    this.config = [
      DynamicFieldHelper.createFieldPcalendarHeqF(DataType.DateString, 'dateFrom', false),
      DynamicFieldHelper.createFieldPcalendarHeqF(DataType.DateString, 'dateTo', false),
      DynamicFieldHelper.createSubmitButton('DOWNLOAD')
    ];
    this.configObject = TranslateHelper.prepareFieldsAndErrors(this.translateService, this.config);
  }

  submit(value: { [name: string]: any }): void {
    const request: { dateFrom?: string; dateTo?: string } = {};
    this.form().cleanMaskAndTransferValuesToBusinessObject(request, true);
    const data: TransactionExportCsvDialogData = this.dynamicDialogConfig.data || {};
    this.transactionService
      .exportTransactionsCsv(data.idPortfolio ?? null, request.dateFrom ?? null, request.dateTo ?? null)
      .pipe(takeUntilDestroyed(this.destroyRef))
      .subscribe({
        next: (response: HttpResponse<Blob>) => {
          saveAs(response.body, this.getFileName(response));
          this.dynamicDialogRef.close();
        },
        error: () => (this.configObject.submit.disabled = false)
      });
  }

  /** Opens the user manual page of the transaction report, which describes the CSV export and its re-import. */
  helpLink(): void {
    this.gps.toExternalHelpWebpage(this.gps.getUserLang(), HelpIds.HELP_PORTFOLIOS_TRANSACTIONLIST);
  }

  /** Takes the file name from the Content-Disposition header set by the backend. */
  private getFileName(response: HttpResponse<Blob>): string {
    const contentDisposition = response.headers.get('Content-Disposition');
    const match = contentDisposition ? /filename="([^"]+)"/.exec(contentDisposition) : null;
    return match ? match[1] : 'gt_transaction_export.zip';
  }
}
