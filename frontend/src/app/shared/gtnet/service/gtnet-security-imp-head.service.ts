import {Injectable} from '@angular/core';
import {HttpClient} from '@angular/common/http';
import {Observable} from 'rxjs';
import {catchError} from 'rxjs/operators';

import {AuthServiceWithLogout} from '../../../lib/login/service/base.auth.service.with.logout';
import {ServiceEntityUpdate} from '../../../lib/edit/service.entity.update';
import {DeleteService} from '../../../lib/datashowbase/delete.service';
import {LoginService} from '../../../lib/login/service/log-in.service';
import {MessageToastService} from '../../../lib/message/message.toast.service';
import {BaseSettings} from '../../../lib/base.settings';
import {AppSettings} from '../../app.settings';
import {GTNetSecurityImpHead} from '../model/gtnet-security-imp-head';

/**
 * Service for managing GTNet security import headers.
 * Provides CRUD operations for organizing security import batches.
 */
@Injectable()
export class GTNetSecurityImpHeadService extends AuthServiceWithLogout<GTNetSecurityImpHead>
  implements DeleteService, ServiceEntityUpdate<GTNetSecurityImpHead> {

  constructor(loginService: LoginService, httpClient: HttpClient, messageToastService: MessageToastService) {
    super(loginService, httpClient, messageToastService);
  }

  /**
   * Gets all import headers for the current tenant.
   *
   * @returns Observable of all headers belonging to the authenticated user's tenant
   */
  getAll(): Observable<GTNetSecurityImpHead[]> {
    return this.httpClient.get<GTNetSecurityImpHead[]>(
      `${BaseSettings.API_ENDPOINT}${AppSettings.GT_NET_SECURITY_IMP_HEAD_KEY}`,
      this.getHeaders()
    ).pipe(catchError(this.handleError.bind(this)));
  }

  /**
   * Creates or updates an import header.
   *
   * @param entity the header to save
   * @returns Observable of the saved header
   */
  update(entity: GTNetSecurityImpHead): Observable<GTNetSecurityImpHead> {
    return this.updateEntity(entity, entity.idGtNetSecurityImpHead, AppSettings.GT_NET_SECURITY_IMP_HEAD_KEY);
  }

  /**
   * Deletes an import header and all associated positions.
   *
   * @param id the header ID to delete
   * @returns Observable completing when delete is done
   */
  deleteEntity(id: number): Observable<any> {
    return this.httpClient.delete(
      `${BaseSettings.API_ENDPOINT}${AppSettings.GT_NET_SECURITY_IMP_HEAD_KEY}/${id}`,
      this.getHeaders()
    ).pipe(catchError(this.handleError.bind(this)));
  }

  /**
   * Queues a background job to import securities from GTNet for the given header.
   * If a job is already pending for this header, no new job is created.
   *
   * @param idGtNetSecurityImpHead the header ID
   * @param idTransactionHead optional import transaction head ID; if provided, the task will auto-assign
   *                          linked securities to matching ImportTransactionPos entries
   * @returns Observable with job queue result (queued: true if new job created, false if already pending)
   */
  queueImportJob(idGtNetSecurityImpHead: number, idTransactionHead?: number): Observable<{queued: boolean, idGtNetSecurityImpHead: number}> {
    let url = `${BaseSettings.API_ENDPOINT}${AppSettings.GT_NET_SECURITY_IMP_HEAD_KEY}/${idGtNetSecurityImpHead}/importjob`;
    if (idTransactionHead != null) {
      url += `?idTransactionHead=${idTransactionHead}`;
    }
    return this.httpClient.post<{queued: boolean, idGtNetSecurityImpHead: number}>(
      url,
      {},
      this.getHeaders()
    ).pipe(catchError(this.handleError.bind(this)));
  }
}
