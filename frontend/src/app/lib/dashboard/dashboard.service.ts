import { Injectable } from '@angular/core';
import { HttpClient } from '@angular/common/http';
import { AuthServiceWithLogout } from '../login/service/base.auth.service.with.logout';
import { LoginService } from '../login/service/log-in.service';
import { MessageToastService } from '../message/message.toast.service';
import { BaseSettings } from '../base.settings';
import {
  DashboardCatalogue,
  DashboardConfig,
  DashboardDocument,
  DashboardResult,
  DashboardWidget
} from './dashboard.types';

@Injectable({ providedIn: 'root' })
export class DashboardService extends AuthServiceWithLogout<DashboardDocument> {
  constructor(login: LoginService, http: HttpClient, toast: MessageToastService) {
    super(login, http, toast);
  }
  load() {
    return this.httpClient.get<DashboardDocument>(`${BaseSettings.API_ENDPOINT}dashboard`, this.getHeaders());
  }
  catalogue() {
    return this.httpClient.get<DashboardCatalogue>(
      `${BaseSettings.API_ENDPOINT}dashboard/catalogue`,
      this.getHeaders()
    );
  }
  /**
   * Reloads one widget. Settings passed here apply to this read alone and are never saved, which is how a card offers a
   * control - a date, for instance - without turning every adjustment into a layout change.
   */
  refresh(instanceId: string, configOverride?: DashboardConfig) {
    const url = `${BaseSettings.API_ENDPOINT}dashboard/widget/${encodeURIComponent(instanceId)}`;
    return this.httpClient.get<DashboardResult>(
      configOverride ? `${url}?config=${encodeURIComponent(JSON.stringify(configOverride))}` : url,
      this.getHeaders()
    );
  }
  save(expectedRevision: number | null, widgets: DashboardWidget[], resetToDefault: boolean) {
    return this.httpClient.put<DashboardDocument>(
      `${BaseSettings.API_ENDPOINT}userdashboard`,
      {
        expectedRevision,
        schemaVersion: 1,
        widgets: resetToDefault ? [] : widgets,
        resetToDefault
      },
      this.getHeaders()
    );
  }
}
