import {Injectable} from '@angular/core';
import {AuthServiceWithLogout} from '../../../lib/login/service/base.auth.service.with.logout';
import {FieldDescriptorInputAndShowExtendedSecurity, UDFMetadataSecurity} from '../model/udf.metadata';
import {ServiceEntityUpdate} from '../../../lib/edit/service.entity.update';
import {Observable} from 'rxjs';
import {AppSettings} from '../../app.settings';
import {catchError} from 'rxjs/operators';
import {LoginService} from '../../../lib/login/service/log-in.service';
import {HttpClient} from '@angular/common/http';
import {MessageToastService} from '../../../lib/message/message.toast.service';
import {DeleteReadAllService} from '../components/udf.metadata.table';
import {BaseSettings} from '../../../lib/base.settings';

@Injectable()
export class UDFMetadataSecurityService extends AuthServiceWithLogout<UDFMetadataSecurity>
  implements DeleteReadAllService<UDFMetadataSecurity>, ServiceEntityUpdate<UDFMetadataSecurity> {

  constructor(loginService: LoginService, httpClient: HttpClient, messageToastService: MessageToastService) {
    super(loginService, httpClient, messageToastService);
  }

  public getAllByIdUser(): Observable<UDFMetadataSecurity[]> {
    return <Observable<UDFMetadataSecurity[]>>this.httpClient.get(`${BaseSettings.API_ENDPOINT}${AppSettings.UDF_METADATA_SECURITY_KEY}`,
      this.getHeaders()).pipe(catchError(this.handleError.bind(this)));
  }

  public getAllByIdUserInOrderByUiOrderExcludeDisabled(): Observable<FieldDescriptorInputAndShowExtendedSecurity[]> {
    return <Observable<FieldDescriptorInputAndShowExtendedSecurity[]>>this.httpClient.get(
      `${BaseSettings.API_ENDPOINT}${AppSettings.UDF_METADATA_SECURITY_KEY}/fielddescriptor`,
      this.getHeaders()).pipe(catchError(this.handleError.bind(this)));
  }

  public update(udfMetadataSecurity: UDFMetadataSecurity): Observable<UDFMetadataSecurity> {
    return this.updateEntity(udfMetadataSecurity, udfMetadataSecurity.idUDFMetadata, AppSettings.UDF_METADATA_SECURITY_KEY);
  }

  public deleteEntity(idUDFMetadata: number): Observable<any> {
    return this.httpClient.delete(`${BaseSettings.API_ENDPOINT}${AppSettings.UDF_METADATA_SECURITY_KEY}/${idUDFMetadata}`, this.getHeaders())
      .pipe(catchError(this.handleError.bind(this)));
  }
}
