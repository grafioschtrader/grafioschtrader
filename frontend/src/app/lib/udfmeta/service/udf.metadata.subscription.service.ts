import { Service } from '@angular/core';
import { Observable, catchError } from 'rxjs';
import { AuthServiceWithLogout } from '../../login/service/base.auth.service.with.logout';
import { BaseSettings } from '../../base.settings';
import { UDFMetadataSubscription } from '../model/udf.metadata.subscription';

@Service()
export class UDFMetadataSubscriptionService extends AuthServiceWithLogout<UDFMetadataSubscription> {
  update(subscription: UDFMetadataSubscription): Observable<UDFMetadataSubscription> {
    return this.updateEntity(subscription, subscription.idUDFMetadataSubscription, 'udfmetadatasubscription');
  }

  deleteEntity(id: number): Observable<void> {
    return this.httpClient
      .delete<void>(`${BaseSettings.API_ENDPOINT}udfmetadatasubscription/${id}`, this.getHeaders())
      .pipe(catchError(this.handleError.bind(this)));
  }

  claimOwnership(id: number): Observable<void> {
    return this.httpClient
      .post<void>(`${BaseSettings.API_ENDPOINT}udfmetadatasubscription/${id}/claim`, {}, this.getHeaders())
      .pipe(catchError(this.handleError.bind(this)));
  }
}
