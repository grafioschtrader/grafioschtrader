import {Injectable} from '@angular/core';
import {AuthServiceWithLogout} from '../../lib/login/service/base.auth.service.with.logout';
import {LoginService} from '../../lib/login/service/log-in.service';
import {HttpClient} from '@angular/common/http';
import {MessageToastService} from '../../lib/message/message.toast.service';
import {Observable} from 'rxjs';
import {catchError} from 'rxjs/operators';
import {BaseSettings} from '../../lib/base.settings';

/**
 * Service for managing chart drawing shapes via REST API.
 * Handles retrieval, persistence, and deletion of Plotly.js shapes per security/currency pair.
 */
@Injectable()
export class UserChartShapeService extends AuthServiceWithLogout<any> {

  constructor(loginService: LoginService, httpClient: HttpClient, messageToastService: MessageToastService) {
    super(loginService, httpClient, messageToastService);
  }

  /**
   * Retrieves saved chart shapes for a specific security/currency pair.
   *
   * @param idSecuritycurrency - ID of the security or currency pair
   * @returns Observable emitting the shape data, or null if no shapes exist (204 No Content)
   */
  getShapes(idSecuritycurrency: number): Observable<any> {
    return this.httpClient.get(`${BaseSettings.API_ENDPOINT}${BaseSettings.USER_CHART_SHAPE_KEY}/${idSecuritycurrency}`,
      this.getHeaders()).pipe(catchError(this.handleError.bind(this)));
  }

  /**
   * Creates or updates chart shapes for a specific security/currency pair.
   *
   * @param idSecuritycurrency - ID of the security or currency pair
   * @param shapes - Array of Plotly.js shape objects to persist
   * @returns Observable emitting the saved entity
   */
  saveShapes(idSecuritycurrency: number, shapes: any[]): Observable<any> {
    const body = {
      userChartShapeKey: {idSecuritycurrency},
      shapeData: shapes
    };
    return this.httpClient.put(`${BaseSettings.API_ENDPOINT}${BaseSettings.USER_CHART_SHAPE_KEY}`, body,
      this.getHeaders()).pipe(catchError(this.handleError.bind(this)));
  }

  /**
   * Deletes all chart shapes for a specific security/currency pair.
   *
   * @param idSecuritycurrency - ID of the security or currency pair
   * @returns Observable completing when deletion is successful
   */
  deleteShapes(idSecuritycurrency: number): Observable<any> {
    return this.httpClient.delete(`${BaseSettings.API_ENDPOINT}${BaseSettings.USER_CHART_SHAPE_KEY}/${idSecuritycurrency}`,
      this.getHeaders()).pipe(catchError(this.handleError.bind(this)));
  }
}
