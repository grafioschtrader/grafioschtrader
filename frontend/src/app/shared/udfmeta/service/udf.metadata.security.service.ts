import {Injectable} from '@angular/core';
import {AuthServiceWithLogout} from '../../login/service/base.auth.service.with.logout';
import {UDFMetadataSecurity} from '../model/udf.metadata';
import {DeleteService} from '../../datashowbase/delete.service';
import {ServiceEntityUpdate} from '../../edit/service.entity.update';
import {Observable} from 'rxjs';
import {AppSettings} from '../../app.settings';
import {catchError} from 'rxjs/operators';
import {LoginService} from '../../login/service/log-in.service';
import {HttpClient} from '@angular/common/http';
import {MessageToastService} from '../../message/message.toast.service';

@Injectable()
export class UDFMetadataSecurityService extends AuthServiceWithLogout<UDFMetadataSecurity> implements DeleteService, ServiceEntityUpdate<UDFMetadataSecurity> {

  constructor(loginService: LoginService, httpClient: HttpClient, messageToastService: MessageToastService) {
    super(loginService, httpClient, messageToastService);
  }

  public getAllByIdUser(): Observable<UDFMetadataSecurity[]> {
    return <Observable<UDFMetadataSecurity[]>>this.httpClient.get(`${AppSettings.API_ENDPOINT}${AppSettings.UDF_METADATA_SECURITY_KEY}`,
      this.getHeaders()).pipe(catchError(this.handleError.bind(this)));
  }

  public update(udfMetadataSecurity: UDFMetadataSecurity): Observable<UDFMetadataSecurity> {
    return this.updateEntity(udfMetadataSecurity, udfMetadataSecurity.idUDFMetadata, AppSettings.UDF_METADATA_SECURITY_KEY);
  }

  public deleteEntity(idUDFMetadata: number): Observable<any> {
    return this.httpClient.delete(`${AppSettings.API_ENDPOINT}${AppSettings.UDF_METADATA_SECURITY_KEY}/${idUDFMetadata}`, this.getHeaders())
      .pipe(catchError(this.handleError.bind(this)));
  }
}
