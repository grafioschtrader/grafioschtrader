import {Injectable} from '@angular/core';
import {AuthServiceWithLogout} from '../../shared/login/service/base.auth.service.with.logout';
import {ServiceEntityUpdate} from '../../shared/edit/service.entity.update';
import {ImportTransactionHead} from '../../entities/import.transaction.head';
import {Observable} from 'rxjs';
import {AppSettings} from '../../shared/app.settings';
import {HttpClient} from '@angular/common/http';
import {MessageToastService} from '../../shared/message/message.toast.service';
import {IPlatformTransactionImport} from '../../portfolio/component/iplatform.transaction.import';
import {DeleteService} from '../../shared/datashowbase/delete.service';
import {catchError, map} from 'rxjs/operators';
import {LoginService} from '../../shared/login/service/log-in.service';
import {UploadServiceFunction} from '../../shared/generaldialog/model/file.upload.param';


@Injectable()
export class ImportTransactionHeadService extends AuthServiceWithLogout<ImportTransactionHead>
  implements DeleteService, ServiceEntityUpdate<ImportTransactionHead>, UploadServiceFunction {

  constructor(loginService: LoginService, httpClient: HttpClient,
              messageToastService: MessageToastService) {
    super(loginService, httpClient, messageToastService);
  }

  getPlatformTransactionImport(): Observable<IPlatformTransactionImport[]> {
    return <Observable<IPlatformTransactionImport[]>>this.httpClient.get(`${AppSettings.API_ENDPOINT}`
      + `${AppSettings.IMPORT_TRANSACTION_HEAD_KEY}/platformImports`,
      this.getHeaders()).pipe(catchError(this.handleError.bind(this)));
  }

  getImportTransactionHeadBySecurityaccount(idSecuritycashaccount: number): Observable<ImportTransactionHead[]> {
    return <Observable<ImportTransactionHead[]>>this.httpClient.get(`${AppSettings.API_ENDPOINT}`
      + `${AppSettings.IMPORT_TRANSACTION_HEAD_KEY}/${AppSettings.SECURITYACCOUNT_KEY}/${idSecuritycashaccount}`,
      this.getHeaders()).pipe(catchError(this.handleError.bind(this)));
  }

  uploadPdfFileSecurityAccountTransactions(idSecuritycashaccount: number, formData: FormData):
    Observable<SuccessFailedDirectImportTransaction> {
    return this.httpClient
      .post(`${AppSettings.API_ENDPOINT}${AppSettings.IMPORT_TRANSACTION_HEAD_KEY}/${idSecuritycashaccount}/uploadpdftransactions`,
        formData, this.getMultipartHeaders()).pipe(catchError(this.handleError.bind(this)));
  }


  uploadFiles(idTransactionHead: number, formData: FormData):
    Observable<SuccessFailedDirectImportTransaction> {
    return this.httpClient
      .post(`${AppSettings.API_ENDPOINT}${AppSettings.IMPORT_TRANSACTION_HEAD_KEY}/${idTransactionHead}/uploadtransaction`,
        formData, this.getMultipartHeaders())
      .pipe(map(() => true), catchError(this.handleError.bind(this)));
  }

  update(importTransactionHead: ImportTransactionHead): Observable<ImportTransactionHead> {
    return this.updateEntity(importTransactionHead, importTransactionHead.idTransactionHead, AppSettings.IMPORT_TRANSACTION_HEAD_KEY);
  }

  public deleteEntity(idTransactionHead: number): Observable<any> {
    return this.httpClient.delete(`${AppSettings.API_ENDPOINT}${AppSettings.IMPORT_TRANSACTION_HEAD_KEY}/`
      + `${idTransactionHead}`, this.getHeaders()).pipe(catchError(this.handleError.bind(this)));
  }

}

export interface SuccessFailedDirectImportTransaction {
  idTransactionHead: number;
  noOfImportedTransactions: number;
  noOfDifferentSecurities: number;
  failed: boolean;
}
