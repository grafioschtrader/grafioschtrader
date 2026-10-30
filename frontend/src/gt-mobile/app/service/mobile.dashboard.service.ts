import { inject, Service } from '@angular/core';
import { Observable } from 'rxjs';
import { catchError } from 'rxjs/operators';
import { BaseSettings } from '../../../app/lib/base.settings';
import { DashboardConfig, DashboardDocument, DashboardResult } from '../../../app/lib/dashboard/dashboard.types';
import { BaseAuthService } from '../../../app/lib/login/service/base.auth.service';
import { MobileLoginService } from './mobile.login.service';

/**
 * Reads the dashboard of the signed-in user. The mobile application only shows the layout maintained in the desktop
 * application; it never saves one.
 */
@Service()
export class MobileDashboardService extends BaseAuthService<DashboardDocument> {
  private readonly loginService = inject(MobileLoginService);

  /** The layout and the results of the fastest load group. */
  load(): Observable<DashboardDocument> {
    return this.httpClient
      .get<DashboardDocument>(`${BaseSettings.API_ENDPOINT}dashboard`, { headers: this.prepareHeaders() })
      .pipe(catchError(this.handleError.bind(this)));
  }

  /** The results of one of the slower load groups the document listed as pending. */
  group(group: number): Observable<DashboardResult[]> {
    return this.httpClient
      .get<DashboardResult[]>(`${BaseSettings.API_ENDPOINT}dashboard/group/${group}`, {
        headers: this.prepareHeaders()
      })
      .pipe(catchError(this.handleError.bind(this)));
  }

  /**
   * Reloads one widget. Settings passed here apply to this read alone and are never saved.
   *
   * @param instanceId Widget instance of the layout
   * @param configOverride Settings for this read, such as the period of a chart
   */
  refresh(instanceId: string, configOverride?: DashboardConfig): Observable<DashboardResult> {
    const url = `${BaseSettings.API_ENDPOINT}dashboard/widget/${encodeURIComponent(instanceId)}`;
    return this.httpClient
      .get<DashboardResult>(
        configOverride ? `${url}?config=${encodeURIComponent(JSON.stringify(configOverride))}` : url,
        { headers: this.prepareHeaders() }
      )
      .pipe(catchError(this.handleError.bind(this)));
  }

  protected override toLogout(): void {
    this.loginService.logout();
  }
}
