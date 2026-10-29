import { HttpResourceRequest } from '@angular/common/http';
import { Service } from '@angular/core';
import { Observable } from 'rxjs';
import { catchError, map } from 'rxjs/operators';
import { ImportTransactionHead } from '../../entities/import.transaction.head';
import { BaseSettings } from '../../lib/base.settings';
import { DeleteService } from '../../lib/datashowbase/delete.service';
import { ServiceEntityUpdate } from '../../lib/edit/service.entity.update';
import { UploadServiceFunction } from '../../lib/generaldialog/model/file.upload.param';
import { AuthServiceWithLogout } from '../../lib/login/service/base.auth.service.with.logout';
import { IPlatformTransactionImport } from '../../portfolio/component/iplatform.transaction.import';
import { AppSettings } from '../../shared/app.settings';

@Service()
export class ImportTransactionHeadService
  extends AuthServiceWithLogout<ImportTransactionHead>
  implements DeleteService, ServiceEntityUpdate<ImportTransactionHead>, UploadServiceFunction
{
  getImportTransactionHeadBySecurityaccountRequest(idSecuritycashaccount: number) {
    return {
      url: `${BaseSettings.API_ENDPOINT}${AppSettings.IMPORT_TRANSACTION_HEAD_KEY}/${AppSettings.SECURITYACCOUNT_KEY}/${idSecuritycashaccount}`,
      headers: this.prepareHeaders()
    } satisfies HttpResourceRequest;
  }

  getPlatformTransactionImport(): Observable<IPlatformTransactionImport[]> {
    return <Observable<IPlatformTransactionImport[]>>(
      this.httpClient
        .get(
          `${BaseSettings.API_ENDPOINT}` + `${AppSettings.IMPORT_TRANSACTION_HEAD_KEY}/platformImports`,
          this.getHeaders()
        )
        .pipe(catchError(this.handleError.bind(this)))
    );
  }

  getImportTransactionHeadBySecurityaccount(idSecuritycashaccount: number): Observable<ImportTransactionHead[]> {
    const { url, ...options } = this.getImportTransactionHeadBySecurityaccountRequest(idSecuritycashaccount);
    return this.httpClient.get<ImportTransactionHead[]>(url, options).pipe(catchError(this.handleError.bind(this)));
  }

  uploadPdfFileSecurityAccountTransactions(
    idSecuritycashaccount: number,
    formData: FormData
  ): Observable<SuccessFailedDirectImportTransaction> {
    return this.httpClient
      .post(
        `${BaseSettings.API_ENDPOINT}${AppSettings.IMPORT_TRANSACTION_HEAD_KEY}/${idSecuritycashaccount}/uploadpdftransactions`,
        formData,
        this.getMultipartHeaders()
      )
      .pipe(catchError(this.handleError.bind(this)));
  }

  uploadFiles(idTransactionHead: number, formData: FormData): Observable<boolean> {
    return this.httpClient
      .post<void>(
        `${BaseSettings.API_ENDPOINT}${AppSettings.IMPORT_TRANSACTION_HEAD_KEY}/${idTransactionHead}/uploadtransaction`,
        formData,
        this.getMultipartHeaders()
      )
      .pipe(
        map(() => true), // HTTP 204 = Success
        catchError(this.handleError.bind(this))
      );
  }

  /**
   * Deletes the transactions created from the positions of an import head, newest first, so the import can be run
   * again. The backend refuses when other transactions of the tenant are dated in or after the import period.
   *
   * @param idTransactionHead - The import head whose imported transactions are rolled back
   * @returns The number of deleted transactions
   */
  rollbackImportedTransactions(idTransactionHead: number): Observable<number> {
    return this.httpClient
      .post<number>(
        `${BaseSettings.API_ENDPOINT}${AppSettings.IMPORT_TRANSACTION_HEAD_KEY}/${idTransactionHead}/rollback`,
        null,
        this.getHeaders()
      )
      .pipe(catchError(this.handleError.bind(this)));
  }

  update(importTransactionHead: ImportTransactionHead): Observable<ImportTransactionHead> {
    return this.updateEntity(
      importTransactionHead,
      importTransactionHead.idTransactionHead,
      AppSettings.IMPORT_TRANSACTION_HEAD_KEY
    );
  }

  public deleteEntity(idTransactionHead: number): Observable<any> {
    return this.httpClient
      .delete(
        `${BaseSettings.API_ENDPOINT}${AppSettings.IMPORT_TRANSACTION_HEAD_KEY}/` + `${idTransactionHead}`,
        this.getHeaders()
      )
      .pipe(catchError(this.handleError.bind(this)));
  }
}

export interface SuccessFailedDirectImportTransaction {
  idTransactionHead: number;
  noOfImportedTransactions: number;
  noOfDifferentSecurities: number;
  failed: boolean;
  /** True when at least one position was not imported because it may already exist as a transaction. */
  possibleDuplicate: boolean;
}
