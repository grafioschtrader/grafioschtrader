import {Injectable} from '@angular/core';
import {HttpClient} from '@angular/common/http';
import {Observable} from 'rxjs';
import {catchError} from 'rxjs/operators';

import {AuthServiceWithLogout} from '../../lib/login/service/base.auth.service.with.logout';
import {DeleteService} from '../../lib/datashowbase/delete.service';
import {LoginService} from '../../lib/login/service/log-in.service';
import {MessageToastService} from '../../lib/message/message.toast.service';
import {BaseSettings} from '../../lib/base.settings';
import {AppSettings} from '../../shared/app.settings';
import {GTNetSecurityImpPos} from '../model/gtnet-security-imp-pos';
import {UploadHistoryquotesSuccess, UploadServiceFunction} from '../../lib/generaldialog/model/file.upload.param';

/**
 * Service for managing GTNet security import positions.
 * Provides CRUD operations for individual security entries within an import batch.
 */
@Injectable()
export class GTNetSecurityImpPosService extends AuthServiceWithLogout<GTNetSecurityImpPos>
  implements DeleteService, UploadServiceFunction {

  constructor(loginService: LoginService, httpClient: HttpClient, messageToastService: MessageToastService) {
    super(loginService, httpClient, messageToastService);
  }

  /**
   * Gets all positions for a specific import header.
   *
   * @param idGtNetSecurityImpHead the header ID
   * @returns Observable of positions belonging to the header
   */
  getByHead(idGtNetSecurityImpHead: number): Observable<GTNetSecurityImpPos[]> {
    return this.httpClient.get<GTNetSecurityImpPos[]>(
      `${BaseSettings.API_ENDPOINT}${AppSettings.GT_NET_SECURITY_IMP_POS_KEY}/head/${idGtNetSecurityImpHead}`,
      this.getHeaders()
    ).pipe(catchError(this.handleError.bind(this)));
  }

  /**
   * Creates a new position.
   *
   * @param entity the position to create
   * @returns Observable of the created position
   */
  create(entity: GTNetSecurityImpPos): Observable<GTNetSecurityImpPos> {
    return this.httpClient.post<GTNetSecurityImpPos>(
      `${BaseSettings.API_ENDPOINT}${AppSettings.GT_NET_SECURITY_IMP_POS_KEY}`,
      entity,
      this.getHeaders()
    ).pipe(catchError(this.handleError.bind(this)));
  }

  /**
   * Updates an existing position.
   *
   * @param entity the position to update
   * @returns Observable of the updated position
   */
  update(entity: GTNetSecurityImpPos): Observable<GTNetSecurityImpPos> {
    return this.httpClient.put<GTNetSecurityImpPos>(
      `${BaseSettings.API_ENDPOINT}${AppSettings.GT_NET_SECURITY_IMP_POS_KEY}`,
      entity,
      this.getHeaders()
    ).pipe(catchError(this.handleError.bind(this)));
  }

  /**
   * Saves a position (creates or updates based on presence of ID).
   *
   * @param entity the position to save
   * @returns Observable of the saved position
   */
  save(entity: GTNetSecurityImpPos): Observable<GTNetSecurityImpPos> {
    if (entity.idGtNetSecurityImpPos) {
      return this.update(entity);
    } else {
      return this.create(entity);
    }
  }

  /**
   * Deletes a position.
   *
   * @param id the position ID to delete
   * @returns Observable completing when delete is done
   */
  deleteEntity(id: number): Observable<any> {
    return this.httpClient.delete(
      `${BaseSettings.API_ENDPOINT}${AppSettings.GT_NET_SECURITY_IMP_POS_KEY}/${id}`,
      this.getHeaders()
    ).pipe(catchError(this.handleError.bind(this)));
  }

  /**
   * Uploads a CSV file containing GTNet security import positions.
   * Implements UploadServiceFunction interface.
   *
   * @param idGtNetSecurityImpHead the header ID to associate positions with
   * @param formData FormData containing the CSV file
   * @returns Observable of upload result statistics
   */
  uploadFiles(idGtNetSecurityImpHead: number, formData: FormData): Observable<UploadHistoryquotesSuccess> {
    return this.httpClient
      .post<UploadHistoryquotesSuccess>(
        `${BaseSettings.API_ENDPOINT}${AppSettings.GT_NET_SECURITY_IMP_POS_KEY}/head/${idGtNetSecurityImpHead}/upload`,
        formData,
        this.getMultipartHeaders()
      )
      .pipe(catchError(this.handleError.bind(this)));
  }

  /**
   * Deletes the linked security from a position.
   * The position remains and can be queried again via GTNet.
   *
   * @param idGtNetSecurityImpPos the position ID
   * @returns Observable of the updated position with security set to null
   */
  deleteLinkedSecurity(idGtNetSecurityImpPos: number): Observable<GTNetSecurityImpPos> {
    return this.httpClient.delete<GTNetSecurityImpPos>(
      `${BaseSettings.API_ENDPOINT}${AppSettings.GT_NET_SECURITY_IMP_POS_KEY}/${idGtNetSecurityImpPos}/security`,
      this.getHeaders()
    ).pipe(catchError(this.handleError.bind(this)));
  }
}
