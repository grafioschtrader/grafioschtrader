import { inject, Service } from '@angular/core';
import { HttpClient, HttpContext, HttpParams } from '@angular/common/http';
import { SKIP_AUTH_HEADERS } from './auth.http.interceptor';
import { BaseSettings } from '../../base.settings';
import { Observable } from 'rxjs';
import { TranslateService } from '@ngx-translate/core';

@Service()
export class ReleaseNoteService {
  private httpClient = inject(HttpClient);
  private translateService = inject(TranslateService);

  getTopReleaseNotes(limit: number = 3): Observable<ReleaseNote[]> {
    const params = new HttpParams()
      .set('lang', this.translateService.getBrowserLang() ?? 'en')
      .set('limit', limit.toString());
    return this.httpClient.get<ReleaseNote[]>(`${BaseSettings.API_ENDPOINT}releasenote`, {
      params,
      context: new HttpContext().set(SKIP_AUTH_HEADERS, true)
    });
  }
}

export interface ReleaseNote {
  idReleaseNote?: number;
  version: string;
  language: string;
  note: string;
}
