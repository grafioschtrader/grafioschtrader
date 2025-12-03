import {TranslateLoader} from '@ngx-translate/core';
import {HttpClient} from '@angular/common/http';
import {forkJoin, Observable, of} from 'rxjs';
import {catchError, map} from 'rxjs/operators';
import deepmerge from 'deepmerge';


export interface ITranslationResource {
  prefix: string;
  suffix: string;
}

export class MultiTranslateHttpLoader implements TranslateLoader {
  constructor(
    private http: HttpClient,
    private resources: ITranslationResource[],
  ) {
  }

  public getTranslation(language: string): Observable<any> {
    const requests = this.resources.map(resource => {
      const path = resource.prefix + language + resource.suffix;
      return this.http.get<Record<string, any>>(path).pipe(
        catchError(res => {
          console.error('Could not find translation file:', path);
          return of({} as Record<string, any>);
        })
      );
    });

    return forkJoin(requests).pipe(
      map((response: Record<string, any>[]) => deepmerge.all(response))
    );
  }
}
