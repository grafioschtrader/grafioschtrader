import {Injectable} from '@angular/core';
import {AuthServiceWithLogout} from '../../lib/login/service/base.auth.service.with.logout';
import {ServiceEntityUpdate} from '../../lib/edit/service.entity.update';
import {Observable} from 'rxjs';
import {AppSettings} from '../../shared/app.settings';
import {catchError} from 'rxjs/operators';
import {LoginService} from '../../lib/login/service/log-in.service';
import {HttpClient} from '@angular/common/http';
import {MessageToastService} from '../../lib/message/message.toast.service';
import {DeleteReadAllService} from '../../lib/udfmeta/components/udf.metadata.table';
import {BaseSettings} from '../../lib/base.settings';
import {FieldDescriptorInputAndShowExtendedSecurity, UDFMetadataSecurity} from '../model/udf.metadata.security';

/**
 * Service for managing security-specific UDF metadata definitions via REST API.
 * Handles CRUD operations for user-defined field metadata applicable to securities and financial instruments.
 * Supports asset class and instrument type filtering to ensure UDF fields appear only for relevant security types.
 */
@Injectable()
export class UDFMetadataSecurityService extends AuthServiceWithLogout<UDFMetadataSecurity>
  implements DeleteReadAllService<UDFMetadataSecurity>, ServiceEntityUpdate<UDFMetadataSecurity> {

  /**
   * Creates the UDF metadata security service.
   *
   * @param loginService - Service for authentication and session management
   * @param httpClient - HTTP client for REST API calls
   * @param messageToastService - Service for displaying user notifications
   */
  constructor(loginService: LoginService, httpClient: HttpClient, messageToastService: MessageToastService) {
    super(loginService, httpClient, messageToastService);
  }

  /**
   * Retrieves all security UDF metadata entries accessible to the current user.
   * Returns both user-specific metadata (owned by the current user) and system-wide metadata (idUser = 0).
   *
   * @returns Observable emitting array of security UDF metadata entries
   */
  public getAllByIdUser(): Observable<UDFMetadataSecurity[]> {
    return <Observable<UDFMetadataSecurity[]>>this.httpClient.get(`${BaseSettings.API_ENDPOINT}${AppSettings.UDF_METADATA_SECURITY_KEY}`,
      this.getHeaders()).pipe(catchError(this.handleError.bind(this)));
  }

  /**
   * Retrieves field descriptors for active security UDF fields, ordered by UI display order.
   * Returns only enabled fields (excludes user-disabled special types) for dynamic form generation.
   * Used when displaying UDF fields in security editing forms.
   *
   * @returns Observable emitting array of field descriptors for enabled security UDF fields, sorted by UI order
   */
  public getAllByIdUserInOrderByUiOrderExcludeDisabled(): Observable<FieldDescriptorInputAndShowExtendedSecurity[]> {
    return <Observable<FieldDescriptorInputAndShowExtendedSecurity[]>>this.httpClient.get(
      `${BaseSettings.API_ENDPOINT}${AppSettings.UDF_METADATA_SECURITY_KEY}/fielddescriptor`,
      this.getHeaders()).pipe(catchError(this.handleError.bind(this)));
  }

  /**
   * Updates an existing security UDF metadata entry.
   * Can only update metadata owned by the current user (not system-wide entries).
   *
   * @param udfMetadataSecurity - Security UDF metadata entity with updated values
   * @returns Observable emitting the updated security UDF metadata entity
   */
  public update(udfMetadataSecurity: UDFMetadataSecurity): Observable<UDFMetadataSecurity> {
    return this.updateEntity(udfMetadataSecurity, udfMetadataSecurity.idUDFMetadata, AppSettings.UDF_METADATA_SECURITY_KEY);
  }

  /**
   * Deletes a security UDF metadata entry by ID.
   * Can only delete metadata owned by the current user (not system-wide entries).
   *
   * @param idUDFMetadata - Unique identifier of the security UDF metadata to delete
   * @returns Observable completing when deletion is successful
   */
  public deleteEntity(idUDFMetadata: number): Observable<any> {
    return this.httpClient.delete(`${BaseSettings.API_ENDPOINT}${AppSettings.UDF_METADATA_SECURITY_KEY}/${idUDFMetadata}`, this.getHeaders())
      .pipe(catchError(this.handleError.bind(this)));
  }
}
