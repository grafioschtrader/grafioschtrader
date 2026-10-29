import { AuthServiceWithLogout } from '../../login/service/base.auth.service.with.logout';
import { ServiceEntityUpdate } from '../../edit/service.entity.update';
import { GTNetMessage } from '../model/gtnet.message';
import { Observable } from 'rxjs/internal/Observable';
import { catchError } from 'rxjs/operators';
import { Service } from '@angular/core';
import { ClassDescriptorInputAndShow } from '../../dynamicfield/field.descriptor.input.and.show';
import { BaseSettings } from '../../base.settings';

@Service()
export class GTNetMessageService
  extends AuthServiceWithLogout<GTNetMessage>
  implements ServiceEntityUpdate<GTNetMessage>
{
  getAllFormDefinitionsWithClass(): Observable<{ [type: string]: ClassDescriptorInputAndShow }> {
    return this.httpClient
      .get<{ [type: string]: ClassDescriptorInputAndShow }>(
        `${BaseSettings.API_ENDPOINT}${BaseSettings.GT_NET_MESSAGE_KEY}` + `/msgformdefinition`,
        this.getHeaders()
      )
      .pipe(catchError(this.handleError.bind(this)));
  }

  update(gtNetMessage: GTNetMessage): Observable<GTNetMessage> {
    return this.updateEntity(gtNetMessage, gtNetMessage.idGtNetMessage, BaseSettings.GT_NET_MESSAGE_KEY);
  }

  markAsRead(idGtNetMessage: number): Observable<void> {
    return this.httpClient
      .patch<void>(
        `${BaseSettings.API_ENDPOINT}${BaseSettings.GT_NET_MESSAGE_KEY}/${idGtNetMessage}/markasread`,
        null,
        this.getHeaders()
      )
      .pipe(catchError(this.handleError.bind(this)));
  }

  deleteEntity(gtNetMessage: number): Observable<any> {
    return this.httpClient
      .delete(`${BaseSettings.API_ENDPOINT}${BaseSettings.GT_NET_MESSAGE_KEY}/${gtNetMessage}`, this.getHeaders())
      .pipe(catchError(this.handleError.bind(this)));
  }

  /**
   * Retrieves all admin-only messages. Requires ROLE_ADMIN.
   */
  getAdminMessages(): Observable<GTNetMessage[]> {
    return this.httpClient
      .get<GTNetMessage[]>(`${BaseSettings.API_ENDPOINT}${BaseSettings.GT_NET_MESSAGE_KEY}/admin`, this.getHeaders())
      .pipe(catchError(this.handleError.bind(this)));
  }

  /**
   * Retrieves counts of admin-only messages grouped by idGtNet. Requires ROLE_ADMIN.
   */
  getAdminMessageCounts(): Observable<{ [idGtNet: number]: number }> {
    return this.httpClient
      .get<{ [idGtNet: number]: number }>(
        `${BaseSettings.API_ENDPOINT}${BaseSettings.GT_NET_MESSAGE_KEY}/admin/count`,
        this.getHeaders()
      )
      .pipe(catchError(this.handleError.bind(this)));
  }
}
