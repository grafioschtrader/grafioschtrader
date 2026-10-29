import { Service } from '@angular/core';
import { AuthServiceWithLogout } from '../../lib/login/service/base.auth.service.with.logout';
import { ImportTransactionPlatform } from '../../entities/import.transaction.platform';
import { ServiceEntityUpdate } from '../../lib/edit/service.entity.update';
import { AppSettings } from '../../shared/app.settings';
import { Observable } from 'rxjs';
import { IPlatformTransactionImport } from '../../portfolio/component/iplatform.transaction.import';
import { DeleteService } from '../../lib/datashowbase/delete.service';
import { catchError } from 'rxjs/operators';
import { BaseSettings } from '../../lib/base.settings';

@Service()
export class ImportTransactionPlatformService
  extends AuthServiceWithLogout<ImportTransactionPlatform>
  implements DeleteService, ServiceEntityUpdate<ImportTransactionPlatform>
{
  getAllImportTransactionPlatforms(): Observable<ImportTransactionPlatform[]> {
    return this.httpClient
      .get<ImportTransactionPlatform[]>(
        `${BaseSettings.API_ENDPOINT}` + `${AppSettings.IMP_TRANS_PLATFORM_KEY}`,
        this.getHeaders()
      )
      .pipe(catchError(this.handleError.bind(this)));
  }

  getPlatformTransactionImport(): Observable<IPlatformTransactionImport[]> {
    return this.httpClient
      .get<IPlatformTransactionImport[]>(
        `${BaseSettings.API_ENDPOINT}` + `${AppSettings.IMP_TRANS_PLATFORM_KEY}/platformImports`,
        this.getHeaders()
      )
      .pipe(catchError(this.handleError.bind(this)));
  }

  /**
   * Chooses the import platform of this instance holding the Grafioschtrader authored import templates. Reserved for
   * an administrator, because which platform carries those templates is a property of the instance and not of a
   * client - a client only opts in through its tenant setting.
   *
   * @param idTransactionImportPlatform The platform to use, or null to clear the setting
   * @returns The platform id now configured, or null when the setting was cleared
   */
  setGtImportPlatform(idTransactionImportPlatform: number | null): Observable<number | null> {
    const params =
      idTransactionImportPlatform == null ? '' : `?idTransactionImportPlatform=${idTransactionImportPlatform}`;
    return this.httpClient
      .put<number | null>(
        `${BaseSettings.API_ENDPOINT}${AppSettings.IMP_TRANS_PLATFORM_KEY}/gtplatform${params}`,
        null,
        this.getHeaders()
      )
      .pipe(catchError(this.handleError.bind(this)));
  }

  update(importTransactionPlatform: ImportTransactionPlatform): Observable<ImportTransactionPlatform> {
    return this.updateEntity(
      importTransactionPlatform,
      importTransactionPlatform.idTransactionImportPlatform,
      AppSettings.IMP_TRANS_PLATFORM_KEY
    );
  }

  public deleteEntity(idTransactionImportPlatform: number): Observable<any> {
    return this.httpClient
      .delete(
        `${BaseSettings.API_ENDPOINT}${AppSettings.IMP_TRANS_PLATFORM_KEY}/` + `${idTransactionImportPlatform}`,
        this.getHeaders()
      )
      .pipe(catchError(this.handleError.bind(this)));
  }

  uploadAndTransformPDFToTxt(pdfFileToUpload: File): Observable<string> {
    const formData: FormData = new FormData();
    formData.append('file', pdfFileToUpload, pdfFileToUpload.name);
    const options = { ...this.getMultipartHeaders(), responseType: 'text' as const };

    return this.httpClient
      .post(`${BaseSettings.API_ENDPOINT}${AppSettings.IMP_TRANS_PLATFORM_KEY}/transformpdftotxt`, formData, options)
      .pipe(catchError(this.handleError.bind(this)));
  }
}
