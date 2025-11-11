import {Injectable} from '@angular/core';
import {AuthServiceWithLogout} from '../../login/service/base.auth.service.with.logout';
import {UDFMetadataGeneral} from '../model/udf.metadata';
import {ServiceEntityUpdate} from '../../edit/service.entity.update';
import {Observable} from 'rxjs';
import {catchError} from 'rxjs/operators';
import {LoginService} from '../../login/service/log-in.service';
import {HttpClient} from '@angular/common/http';
import {MessageToastService} from '../../message/message.toast.service';
import {FieldDescriptorInputAndShowExtended} from '../../dynamicfield/field.descriptor.input.and.show';
import {DeleteReadAllService} from '../components/udf.metadata.table';
import {BaseSettings} from '../../base.settings';

/**
 * Service for managing general UDF metadata definitions via REST API.
 * Handles CRUD operations for user-defined field metadata that can be applied to various entity types.
 * Supports both user-specific and system-wide UDF metadata entries.
 */
@Injectable()
export class UDFMetadataGeneralService extends AuthServiceWithLogout<UDFMetadataGeneral> implements DeleteReadAllService<UDFMetadataGeneral>, ServiceEntityUpdate<UDFMetadataGeneral> {

  /**
   * Creates the UDF metadata general service.
   *
   * @param loginService - Service for authentication and session management
   * @param httpClient - HTTP client for REST API calls
   * @param messageToastService - Service for displaying user notifications
   */
  constructor(loginService: LoginService, httpClient: HttpClient, messageToastService: MessageToastService) {
    super(loginService, httpClient, messageToastService);
  }

  /**
   * Retrieves all UDF metadata entries accessible to the current user.
   * Returns both user-specific metadata (owned by the current user) and system-wide metadata (idUser = 0).
   *
   * @returns Observable emitting array of UDF metadata general entries
   */
  public getAllByIdUser(): Observable<UDFMetadataGeneral[]> {
    return <Observable<UDFMetadataGeneral[]>>this.httpClient.get(`${BaseSettings.API_ENDPOINT}${BaseSettings.UDF_METADATA_GENERAL_KEY}`,
      this.getHeaders()).pipe(catchError(this.handleError.bind(this)));
  }

  /**
   * Retrieves field descriptors for UDF fields applicable to a specific entity type.
   * Returns descriptors for both current user's custom fields and system-wide fields, used for dynamic form generation.
   *
   * @param entity - Entity type name (e.g., "Portfolio", "Watchlist", "Transaction")
   * @returns Observable emitting array of field descriptors for the entity's UDF fields
   */
  public getFieldDescriptorByIdUserAndEveryUserForEntity(entity: string): Observable<FieldDescriptorInputAndShowExtended[]> {
    return <Observable<FieldDescriptorInputAndShowExtended[]>>this.httpClient.get(
      `${BaseSettings.API_ENDPOINT}${BaseSettings.UDF_METADATA_GENERAL_KEY}/fielddescriptor/${entity}`,
      this.getHeaders()).pipe(catchError(this.handleError.bind(this)));
  }

  /**
   * Updates an existing UDF metadata entry.
   * Can only update metadata owned by the current user (not system-wide entries).
   *
   * @param udfMetadataGeneral - UDF metadata entity with updated values
   * @returns Observable emitting the updated UDF metadata entity
   */
  public update(udfMetadataGeneral: UDFMetadataGeneral): Observable<UDFMetadataGeneral> {
    return this.updateEntity(udfMetadataGeneral, udfMetadataGeneral.idUDFMetadata, BaseSettings.UDF_METADATA_GENERAL_KEY);
  }

  /**
   * Deletes a UDF metadata entry by ID.
   * Can only delete metadata owned by the current user (not system-wide entries).
   *
   * @param idUDFMetadata - Unique identifier of the UDF metadata to delete
   * @returns Observable completing when deletion is successful
   */
  public deleteEntity(idUDFMetadata: number): Observable<any> {
    return this.httpClient.delete(`${BaseSettings.API_ENDPOINT}${BaseSettings.UDF_METADATA_GENERAL_KEY}/${idUDFMetadata}`, this.getHeaders())
      .pipe(catchError(this.handleError.bind(this)));
  }
}
