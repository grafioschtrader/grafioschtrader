import {Injectable} from '@angular/core';
import {HttpClient} from '@angular/common/http';
import {lastValueFrom, Observable} from 'rxjs';
import {catchError} from 'rxjs/operators';
import {BaseAuthService} from '../../login/service/base.auth.service';
import {MessageToastService} from '../../message/message.toast.service';
import {BaseSettings} from '../../base.settings';

/**
 * Service for managing user personal data operations including data export and account deletion.
 * Provides functionality for users to download their personal data as a ZIP archive and to
 * permanently delete their account along with all associated data.
 */
@Injectable()
export class UserDataService extends BaseAuthService<any> {

  constructor(httpClient: HttpClient, messageToastService: MessageToastService) {
    super(httpClient, messageToastService);
  }

  /**
   * Exports all personal data for the current user as a ZIP archive.
   * This includes tenant data, portfolios, transactions, securities, and all related information.
   * The ZIP file is returned as a Blob for download.
   *
   * @returns Promise resolving to a Blob containing the zipped personal data
   */
  public async getExportPersonalDataAsZip(): Promise<Blob> {
    const blob$ = this.httpClient.get<Blob>(
      `${BaseSettings.API_ENDPOINT}${BaseSettings.TENANT_KEY}/exportpersonaldataaszip`,
      {headers: this.prepareHeaders('application/zip'), responseType: 'blob' as 'json'});
    return await lastValueFrom(blob$);
  }

  /**
   * Permanently deletes the current user's account and all associated data.
   * This operation is irreversible and will remove all tenant data, portfolios, transactions,
   * securities, watchlists, and user settings from the database.
   *
   * @returns Observable that completes when the deletion is successful
   */
  public deleteMyDataAndUserAccount(): Observable<any> {
    return this.httpClient.delete(`${BaseSettings.API_ENDPOINT}${BaseSettings.TENANT_KEY}`, this.getHeaders())
      .pipe(catchError(this.handleError.bind(this)));
  }

}
