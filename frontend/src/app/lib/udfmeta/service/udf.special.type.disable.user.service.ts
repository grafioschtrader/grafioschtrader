import { Injectable } from '@angular/core';
import { AuthServiceWithLogout } from '../../login/service/base.auth.service.with.logout';
import { UDFSpecialTypeDisableUser } from '../model/udf.metadata';
import { Observable } from 'rxjs';
import { LoginService } from '../../login/service/log-in.service';
import { HttpClient } from '@angular/common/http';
import { MessageToastService } from '../../message/message.toast.service';
import { catchError, map } from 'rxjs/operators';
import { BaseSettings } from '../../base.settings';
import { UDFSpecialTypeRegistry } from '../model/udf.special.type.registry';

/**
 * Service for managing user preferences to disable/enable predefined UDF special types.
 * Allows users to hide UDF fields with special purposes that they don't need in their workflow,
 * providing a cleaner UI by showing only relevant fields.
 */
@Injectable()
export class UDFSpecialTypeDisableUserService extends AuthServiceWithLogout<UDFSpecialTypeDisableUser> {
  /**
   * Creates the UDF special type disable user service.
   *
   * @param loginService - Service for authentication and session management
   * @param httpClient - HTTP client for REST API calls
   * @param messageToastService - Service for displaying user notifications
   */
  constructor(loginService: LoginService, httpClient: HttpClient, messageToastService: MessageToastService) {
    super(loginService, httpClient, messageToastService);
  }

  /**
   * Retrieves the list of UDF special types that the current user has disabled.
   * These special types will be hidden in UDF editing interfaces for this user.
   *
   * @returns Observable emitting array of disabled UDF special type values for the current user.
   *          Use UDFSpecialTypeRegistry.getByValue() to resolve full special type objects.
   */
  public getDisabledSpecialTypes(): Observable<number[]> {
    return this.httpClient
      .get<(number | string)[]>(
        `${BaseSettings.API_ENDPOINT}${BaseSettings.UDF_SPECIAL_TYPE_DISABLE_USER}`,
        this.getHeaders()
      )
      .pipe(
        map((types) =>
          types
            .map((type) => (typeof type === 'string' ? UDFSpecialTypeRegistry.getByName(type)?.value : type))
            .filter((type) => type != null)
        ),
        catchError(this.handleError.bind(this))
      );
  }

  /**
   * Disables a UDF special type for the current user.
   * The specified special type will be hidden in UDF editing interfaces.
   *
   * @param udfSpecialType - Numeric registry value of the special type to disable
   * @returns Observable completing when the special type has been disabled
   */
  public create(udfSpecialType: number): Observable<any> {
    return this.httpClient
      .post(
        `${BaseSettings.API_ENDPOINT}${BaseSettings.UDF_SPECIAL_TYPE_DISABLE_USER}`,
        udfSpecialType,
        this.getHeaders()
      )
      .pipe(catchError(this.handleError.bind(this)));
  }

  /**
   * Re-enables a previously disabled UDF special type for the current user.
   * The specified special type will become visible again in UDF editing interfaces.
   *
   * @param udfSpecialType - Numeric registry value of the special type to enable
   * @returns Observable completing when the special type has been re-enabled
   */
  public delete(udfSpecialType: number): Observable<any> {
    return this.httpClient
      .delete(
        `${BaseSettings.API_ENDPOINT}${BaseSettings.UDF_SPECIAL_TYPE_DISABLE_USER}/${udfSpecialType}`,
        this.getHeaders()
      )
      .pipe(catchError(this.handleError.bind(this)));
  }
}
