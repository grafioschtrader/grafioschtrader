import { Injectable } from '@angular/core';
import { HttpClient, HttpContext, HttpParams } from '@angular/common/http';
import { SKIP_AUTH_HEADERS } from './auth.http.interceptor';
import { BaseSettings } from '../../base.settings';
import { Observable } from 'rxjs';
import { TranslateService } from '@ngx-translate/core';

@Injectable()
export class ReleaseNoteService {
  constructor(
    private httpClient: HttpClient,
    private translateService: TranslateService
  ) {}

  getTopReleaseNotes(limit: number = 3): Observable<ReleaseNote[]> {
    const params = new HttpParams().set('lang', this.translateService.getBrowserLang()).set('limit', limit.toString());
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
