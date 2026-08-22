import { TranslateLoader } from '@ngx-translate/core';
import { HttpClient } from '@angular/common/http';
import { forkJoin, Observable, of } from 'rxjs';
import { catchError, map, retry } from 'rxjs/operators';
import deepmerge from 'deepmerge';
import { OPTIMUS_TRANSLATIONS } from './optimus.translations';

export interface ITranslationResource {
  prefix: string;
  suffix: string;
  /**
   * When true, a failed request aborts the whole load instead of contributing an empty object.
   *
   * The backend language endpoint is the only source of user interface texts, so swallowing its failure would render
   * the whole application -- including the login screen -- as raw translation keys, with nothing to indicate that
   * anything went wrong. Optional sources keep the previous lenient behaviour.
   */
  mandatory?: boolean;
}

export class MultiTranslateHttpLoader implements TranslateLoader {
  constructor(
    private http: HttpClient,
    private resources: ITranslationResource[]
  ) {}

  public getTranslation(language: string): Observable<any> {
    const requests = this.resources.map((resource) => {
      const path = resource.prefix + language + resource.suffix;
      const request = this.http.get<Record<string, any>>(path);
      return resource.mandatory
        ? request.pipe(retry({ count: 2, delay: 1500 }))
        : request.pipe(
            catchError(() => {
              console.error('Could not find translation file:', path);
              return of({} as Record<string, any>);
            })
          );
    });

    // Optimus UI's own widget texts are merged last so they cannot be shadowed; optimus.translations.ts explains why
    // they are kept on the client instead of being served by the backend.
    return forkJoin(requests).pipe(
      map((response: Record<string, any>[]) => deepmerge.all([...response, OPTIMUS_TRANSLATIONS[language] ?? {}]))
    );
  }
}
