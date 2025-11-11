import {Injectable} from '@angular/core';
import {AuthServiceWithLogout} from '../../login/service/base.auth.service.with.logout';
import {UDFData} from '../model/udf.metadata';
import {LoginService} from '../../login/service/log-in.service';
import {HttpClient} from '@angular/common/http';
import {MessageToastService} from '../../message/message.toast.service';
import {Observable} from 'rxjs';
import {catchError} from 'rxjs/operators';
import {BaseSettings} from '../../base.settings';

/**
 * Service for managing user-defined field data values via REST API.
 * Handles retrieval, creation, and update of actual UDF data entered by users for specific entity instances.
 * Unlike UDFMetadataGeneralService which manages field definitions, this service manages the field values.
 */
@Injectable()
export class UDFDataService extends AuthServiceWithLogout<UDFData> {
  /**
   * Creates the UDF data service.
   *
   * @param loginService - Service for authentication and session management
   * @param httpClient - HTTP client for REST API calls
   * @param messageToastService - Service for displaying user notifications
   */
  constructor(loginService: LoginService, httpClient: HttpClient, messageToastService: MessageToastService) {
    super(loginService, httpClient, messageToastService);
  }

  /**
   * Retrieves UDF data values for a specific entity instance.
   * Returns the user-entered values for all UDF fields defined for this entity type.
   *
   * @param entity - Entity type name (e.g., "Portfolio", "Watchlist")
   * @param idEntity - Unique identifier of the specific entity instance
   * @returns Observable emitting UDF data containing field values, or null if no data exists
   */
  public getUDFDataByEntityAndIdEntity(entity: string, idEntity: number): Observable<UDFData> {
    return <Observable<UDFData>>this.httpClient.get(`${BaseSettings.API_ENDPOINT}${BaseSettings.UDF_DATA_KEY}`
      + `/${entity}/${idEntity}`,
      this.getHeaders()).pipe(catchError(this.handleError.bind(this)));
  }

  /**
   * Creates or updates UDF data for an entity instance.
   * If idUser is null, creates new UDF data; otherwise updates existing data.
   *
   * @param udfData - UDF data object containing the field values to save
   * @param idUser - User ID for update (1 for existing data, null for new data)
   * @returns Observable emitting the saved UDF data
   */
  public update(udfData: UDFData, idUser: number): Observable<UDFData> {
    return this.updateEntity(udfData, idUser, BaseSettings.UDF_DATA_KEY);
  }

  /**
   * Deletes UDF data by ID.
   * Removes all UDF field values for a specific entity instance.
   *
   * @param idUDFData - Unique identifier of the UDF data to delete
   * @returns Observable completing when deletion is successful
   */
  public deleteEntity(idUDFData: number): Observable<any> {
    return this.httpClient.delete(`${BaseSettings.API_ENDPOINT}${BaseSettings.UDF_DATA_KEY}/${idUDFData}`, this.getHeaders())
      .pipe(catchError(this.handleError.bind(this)));
  }
}
