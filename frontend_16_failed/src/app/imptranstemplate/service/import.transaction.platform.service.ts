import {Injectable} from '@angular/core';
import {AuthServiceWithLogout} from '../../shared/login/service/base.auth.service.with.logout';
import {ImportTransactionPlatform} from '../../entities/import.transaction.platform';
import {ServiceEntityUpdate} from '../../shared/edit/service.entity.update';
import {AppSettings} from '../../shared/app.settings';
import {Observable} from 'rxjs';
import {HttpClient} from '@angular/common/http';
import {MessageToastService} from '../../shared/message/message.toast.service';
import {IPlatformTransactionImport} from '../../portfolio/component/iplatform.transaction.import';
import {DeleteService} from '../../shared/datashowbase/delete.service';
import {catchError} from 'rxjs/operators';
import {LoginService} from '../../shared/login/service/log-in.service';

@Injectable()
export class ImportTransactionPlatformService extends AuthServiceWithLogout<ImportTransactionPlatform>
  implements DeleteService, ServiceEntityUpdate<ImportTransactionPlatform> {

  constructor(loginService: LoginService, httpClient: HttpClient, messageToastService: MessageToastService) {
    super(loginService, httpClient, messageToastService);
  }

  getAllImportTransactionPlatforms(): Observable<ImportTransactionPlatform[]> {
    return <Observable<ImportTransactionPlatform[]>>this.httpClient.get(`${AppSettings.API_ENDPOINT}`
      + `${AppSettings.IMP_TRANS_PLATFORM_KEY}`, this.getHeaders()).pipe(catchError(this.handleError.bind(this)));
  }

  getPlatformTransactionImport(): Observable<IPlatformTransactionImport[]> {
    return <Observable<IPlatformTransactionImport[]>>this.httpClient.get(`${AppSettings.API_ENDPOINT}`
      + `${AppSettings.IMP_TRANS_PLATFORM_KEY}/platformImports`,
      this.getHeaders()).pipe(catchError(this.handleError.bind(this)));
  }

  update(importTransactionPlatform: ImportTransactionPlatform): Observable<ImportTransactionPlatform> {
    return this.updateEntity(importTransactionPlatform, importTransactionPlatform.idTransactionImportPlatform,
      AppSettings.IMP_TRANS_PLATFORM_KEY);
  }

  public deleteEntity(idTransactionImportPlatform: number): Observable<any> {
    return this.httpClient.delete(`${AppSettings.API_ENDPOINT}${AppSettings.IMP_TRANS_PLATFORM_KEY}/`
      + `${idTransactionImportPlatform}`, this.getHeaders()).pipe(catchError(this.handleError.bind(this)));
  }

  uploadAndTransformPDFToTxt(pdfFileToUpload: File): Observable<string> {
    const formData: FormData = new FormData();
    formData.append('file', pdfFileToUpload, pdfFileToUpload.name);
    const options: any = this.getMultipartHeaders();
    options.responseType = 'text';

    return <Observable<string>>this.httpClient.post(`${AppSettings.API_ENDPOINT}${AppSettings.IMP_TRANS_PLATFORM_KEY}/transformpdftotxt`,
      formData, options).pipe(catchError(this.handleError.bind(this)));
  }
}
