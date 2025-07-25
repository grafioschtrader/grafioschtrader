import {Injectable} from '@angular/core';
import {AuthServiceWithLogout} from '../../../lib/login/service/base.auth.service.with.logout';
import {UDFMetadataGeneral} from '../model/udf.metadata';
import {ServiceEntityUpdate} from '../../../lib/edit/service.entity.update';
import {Observable} from 'rxjs';
import {AppSettings} from '../../app.settings';
import {catchError} from 'rxjs/operators';
import {LoginService} from '../../../lib/login/service/log-in.service';
import {HttpClient} from '@angular/common/http';
import {MessageToastService} from '../../../lib/message/message.toast.service';
import {FieldDescriptorInputAndShowExtended} from '../../dynamicfield/field.descriptor.input.and.show';
import {DeleteReadAllService} from '../components/udf.metadata.table';
import {BaseSettings} from '../../../lib/base.settings';

@Injectable()
export class UDFMetadataGeneralService extends AuthServiceWithLogout<UDFMetadataGeneral> implements DeleteReadAllService<UDFMetadataGeneral>, ServiceEntityUpdate<UDFMetadataGeneral> {

  constructor(loginService: LoginService, httpClient: HttpClient, messageToastService: MessageToastService) {
    super(loginService, httpClient, messageToastService);
  }

  public getAllByIdUser(): Observable<UDFMetadataGeneral[]> {
    return <Observable<UDFMetadataGeneral[]>>this.httpClient.get(`${BaseSettings.API_ENDPOINT}${AppSettings.UDF_METADATA_GENERAL_KEY}`,
      this.getHeaders()).pipe(catchError(this.handleError.bind(this)));
  }


  public getFieldDescriptorByIdUserAndEveryUserForEntity(entity: string): Observable<FieldDescriptorInputAndShowExtended[]> {
    return <Observable<FieldDescriptorInputAndShowExtended[]>>this.httpClient.get(
      `${BaseSettings.API_ENDPOINT}${AppSettings.UDF_METADATA_GENERAL_KEY}/fielddescriptor/${entity}`,
      this.getHeaders()).pipe(catchError(this.handleError.bind(this)));
  }

  public update(udfMetadataGeneral: UDFMetadataGeneral): Observable<UDFMetadataGeneral> {
    return this.updateEntity(udfMetadataGeneral, udfMetadataGeneral.idUDFMetadata, AppSettings.UDF_METADATA_GENERAL_KEY);
  }

  public deleteEntity(idUDFMetadata: number): Observable<any> {
    return this.httpClient.delete(`${BaseSettings.API_ENDPOINT}${AppSettings.UDF_METADATA_GENERAL_KEY}/${idUDFMetadata}`, this.getHeaders())
      .pipe(catchError(this.handleError.bind(this)));
  }
}
