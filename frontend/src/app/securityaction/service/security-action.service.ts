import { Service } from '@angular/core';
import { Observable } from 'rxjs';
import { catchError } from 'rxjs/operators';
import { AuthServiceWithLogout } from '../../lib/login/service/base.auth.service.with.logout';
import { BaseSettings } from '../../lib/base.settings';
import { Security } from '../../entities/security';
import { ValueKeyHtmlSelectOptions } from '../../lib/dynamic-form/models/value.key.html.select.options';
import {
  SecurityAction,
  SecurityActionApplication,
  SecurityActionTreeData,
  SecurityTransfer,
  SpinOffAllocationProposal,
  SpinOffAllocationQuery,
  SpinOffAccountDecision,
  SpinOffPreview,
  SpinOffProviderSplits
} from '../model/security-action.model';
import { ServiceEntityUpdate } from '../../lib/edit/service.entity.update';

@Service()
export class SecurityActionService
  extends AuthServiceWithLogout<SecurityAction>
  implements ServiceEntityUpdate<SecurityAction>
{
  private static readonly BASE_URL = BaseSettings.API_ENDPOINT + 'securityaction';

  update(entity: SecurityAction): Observable<SecurityAction> {
    return entity.idSecurityAction == null
      ? this.createSecurityAction(entity)
      : this.httpClient
          .put<SecurityAction>(
            `${SecurityActionService.BASE_URL}/${entity.idSecurityAction}`,
            entity,
            this.getHeaders()
          )
          .pipe(catchError(this.handleError.bind(this)));
  }

  getTree(): Observable<SecurityActionTreeData> {
    return this.httpClient
      .get<SecurityActionTreeData>(`${SecurityActionService.BASE_URL}/tree`, this.getHeaders())
      .pipe(catchError(this.handleError.bind(this)));
  }

  createSecurityAction(action: SecurityAction): Observable<SecurityAction> {
    return this.httpClient
      .post<SecurityAction>(`${SecurityActionService.BASE_URL}`, action, this.getHeaders())
      .pipe(catchError(this.handleError.bind(this)));
  }

  getActionTypes(): Observable<ValueKeyHtmlSelectOptions[]> {
    return this.getOptions('actiontypes');
  }

  getSpinOffTaxTreatments(): Observable<ValueKeyHtmlSelectOptions[]> {
    return this.getOptions('spinoff/taxtreatments');
  }

  getSpinOffDecisions(): Observable<ValueKeyHtmlSelectOptions[]> {
    return this.getOptions('spinoff/decisions');
  }

  getSpinOffHistoryBases(): Observable<ValueKeyHtmlSelectOptions[]> {
    return this.getOptions('spinoff/historybases');
  }

  /** Reads the provider splits and the history basis of a spin-off parent around an action date (administrator). */
  getSpinOffProviderSplits(idSecurityOld: number, actionDate: string): Observable<SpinOffProviderSplits> {
    return this.httpClient
      .get<SpinOffProviderSplits>(`${SecurityActionService.BASE_URL}/spinoff/providersplits`, {
        ...this.getHeaders(),
        params: { idSecurityOld, actionDate }
      })
      .pipe(catchError(this.handleError.bind(this)));
  }

  private getOptions(path: string): Observable<ValueKeyHtmlSelectOptions[]> {
    return this.httpClient
      .get<ValueKeyHtmlSelectOptions[]>(`${SecurityActionService.BASE_URL}/${path}`, this.getHeaders())
      .pipe(catchError(this.handleError.bind(this)));
  }

  previewSpinOffChild(idSecurityOld: number, isin: string, currency: string, actionDate: string): Observable<Security> {
    return this.httpClient
      .get<Security>(`${SecurityActionService.BASE_URL}/spinoff/child`, {
        ...this.getHeaders(),
        params: { idSecurityOld, isin, currency, actionDate }
      })
      .pipe(catchError(this.handleError.bind(this)));
  }

  proposeSpinOffAllocation(query: SpinOffAllocationQuery): Observable<SpinOffAllocationProposal> {
    return this.httpClient
      .get<SpinOffAllocationProposal>(`${SecurityActionService.BASE_URL}/spinoff/allocation`, {
        ...this.getHeaders(),
        params: { ...query }
      })
      .pipe(catchError(this.handleError.bind(this)));
  }

  deleteSecurityAction(id: number): Observable<any> {
    return this.httpClient
      .delete(`${SecurityActionService.BASE_URL}/${id}`, this.getHeaders())
      .pipe(catchError(this.handleError.bind(this)));
  }

  previewSpinOff(id: number): Observable<SpinOffPreview> {
    return this.httpClient
      .get<SpinOffPreview>(`${SecurityActionService.BASE_URL}/${id}/spinoff/preview`, this.getHeaders())
      .pipe(catchError(this.handleError.bind(this)));
  }

  applySecurityAction(
    id: number,
    decisions: SpinOffAccountDecision[] | null = null
  ): Observable<SecurityActionApplication> {
    return this.httpClient
      .post<SecurityActionApplication>(`${SecurityActionService.BASE_URL}/${id}/apply`, decisions, this.getHeaders())
      .pipe(catchError(this.handleError.bind(this)));
  }

  reverseSecurityAction(id: number): Observable<any> {
    return this.httpClient
      .post(`${SecurityActionService.BASE_URL}/${id}/reverse`, null, this.getHeaders())
      .pipe(catchError(this.handleError.bind(this)));
  }

  createTransfer(transfer: SecurityTransfer): Observable<SecurityTransfer> {
    return this.httpClient
      .post<SecurityTransfer>(`${SecurityActionService.BASE_URL}/transfer`, transfer, this.getHeaders())
      .pipe(catchError(this.handleError.bind(this)));
  }

  reverseTransfer(id: number): Observable<any> {
    return this.httpClient
      .delete(`${SecurityActionService.BASE_URL}/transfer/${id}`, this.getHeaders())
      .pipe(catchError(this.handleError.bind(this)));
  }
}
