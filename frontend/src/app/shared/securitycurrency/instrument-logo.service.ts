import { HttpParams } from '@angular/common/http';
import { DestroyRef, inject, Service, signal } from '@angular/core';
import { Observable, of } from 'rxjs';
import { catchError, map } from 'rxjs/operators';
import { BaseSettings } from '../../lib/base.settings';
import { AuthServiceWithLogout } from '../../lib/login/service/base.auth.service.with.logout';
import { AppSettings } from '../app.settings';
import type {
  InstrumentLogoRefDto,
  InstrumentLogoResolveParams,
  InstrumentLogoSearchParams,
  InstrumentLogoUpdatePayload,
  InstrumentLogoUploadPayload
} from './instrument-logo.model';

interface InstrumentLogoEntityResponse extends InstrumentLogoRefDto {
  hasSvgContent?: boolean;
}

/**
 * Root service for the shared instrument logo catalog. Resolves deployed SVGs to static asset URLs and caches
 * authenticated DB-stored SVGs as ObjectURLs with a reactive revision counter for OnPush views and Plotly treemaps.
 */
@Service()
export class InstrumentLogoService extends AuthServiceWithLogout<InstrumentLogoRefDto> {
  private readonly destroyRef = inject(DestroyRef);

  /** Incremented whenever a DB-stored SVG finishes loading into the ObjectURL cache. */
  readonly revision = signal(0);

  private readonly blobUrlCache = new Map<string, string>();
  private readonly inFlightFetches = new Set<string>();
  private readonly failedKeys = new Set<string>();

  constructor() {
    super();
    this.destroyRef.onDestroy(() => this.clearCache());
  }

  /**
   * Returns a displayable image URL (static asset path or cached blob ObjectURL) for a resolved logo reference.
   * For DB-stored SVGs not yet cached, triggers a deduplicated authenticated fetch and bumps {@link revision} once
   * available so reactive callers re-render automatically.
   */
  getLogoUrl(ref: InstrumentLogoRefDto | null | undefined): string | null {
    if (!ref) {
      return null;
    }
    if (ref.assetPath) {
      return `assets/company-logos/${ref.assetPath}`;
    }
    const cacheKey = this.cacheKey(ref.idInstrumentLogo, ref.contentSha256);
    const cached = this.blobUrlCache.get(cacheKey);
    if (cached) {
      return cached;
    }
    if (!this.inFlightFetches.has(cacheKey) && !this.failedKeys.has(cacheKey)) {
      this.inFlightFetches.add(cacheKey);
      let params = new HttpParams();
      if (ref.contentSha256) {
        params = params.set('v', ref.contentSha256);
      }
      this.httpClient
        .get(`${BaseSettings.API_ENDPOINT}${AppSettings.INSTRUMENT_LOGO_KEY}/${ref.idInstrumentLogo}/svg`, {
          headers: this.prepareHeaders(),
          params,
          responseType: 'blob'
        })
        .subscribe({
          next: (blob) => {
            this.inFlightFetches.delete(cacheKey);
            const svgBlob = blob.type ? blob : new Blob([blob], { type: 'image/svg+xml' });
            const url = URL.createObjectURL(svgBlob);
            this.blobUrlCache.set(cacheKey, url);
            this.revision.update((v) => v + 1);
          },
          error: () => {
            this.inFlightFetches.delete(cacheKey);
            this.failedKeys.add(cacheKey);
          }
        });
    }
    return null;
  }

  /** Searches the shared logo catalog by name, exact ISIN and optional initial security context. */
  search(params: InstrumentLogoSearchParams): Observable<InstrumentLogoRefDto[]> {
    let httpParams = new HttpParams();
    if (params.name?.trim()) {
      httpParams = httpParams.set('name', params.name.trim());
    }
    if (params.isin?.trim()) {
      httpParams = httpParams.set('isin', params.isin.trim().toUpperCase());
    }
    if (params.scope) {
      httpParams = httpParams.set('scope', params.scope);
    }
    if (params.idInstrumentLogo != null) {
      httpParams = httpParams.set('idInstrumentLogo', String(params.idInstrumentLogo));
    }
    if (params.productLink?.trim()) {
      httpParams = httpParams.set('productLink', params.productLink.trim());
    }
    if (params.specialInvestmentInstrument) {
      httpParams = httpParams.set('specialInvestmentInstrument', params.specialInvestmentInstrument);
    }
    return this.httpClient
      .get<InstrumentLogoRefDto[]>(`${BaseSettings.API_ENDPOINT}${AppSettings.INSTRUMENT_LOGO_KEY}/search`, {
        headers: this.prepareHeaders(),
        params: httpParams
      })
      .pipe(catchError(this.handleError.bind(this)));
  }

  /** Resolves the effective logo for an unsaved security form state or proposal review comparison. */
  resolve(params: InstrumentLogoResolveParams): Observable<InstrumentLogoRefDto | null> {
    let httpParams = new HttpParams();
    if (params.idInstrumentLogo != null) {
      httpParams = httpParams.set('idInstrumentLogo', String(params.idInstrumentLogo));
    }
    if (params.isin?.trim()) {
      httpParams = httpParams.set('isin', params.isin.trim().toUpperCase());
    }
    if (params.name?.trim()) {
      httpParams = httpParams.set('name', params.name.trim());
    }
    if (params.productLink?.trim()) {
      httpParams = httpParams.set('productLink', params.productLink.trim());
    }
    if (params.specialInvestmentInstrument) {
      httpParams = httpParams.set('specialInvestmentInstrument', params.specialInvestmentInstrument);
    }
    return this.httpClient
      .get<InstrumentLogoRefDto | null>(`${BaseSettings.API_ENDPOINT}${AppSettings.INSTRUMENT_LOGO_KEY}/resolve`, {
        headers: this.prepareHeaders(),
        params: httpParams
      })
      .pipe(
        map((result) => result ?? null),
        catchError(() => of(null))
      );
  }

  /** Pre-validates a drag-and-drop SVG draft or edit payload on the server without writing to the database. */
  validate(payload: InstrumentLogoUploadPayload | InstrumentLogoUpdatePayload): Observable<void> {
    return this.httpClient
      .post<void>(`${BaseSettings.API_ENDPOINT}${AppSettings.INSTRUMENT_LOGO_KEY}/validate`, payload, {
        headers: this.prepareHeaders()
      })
      .pipe(catchError(this.handleError.bind(this)));
  }

  /** Persists a new DB-stored SVG logo entry and returns its metadata reference. */
  create(payload: InstrumentLogoUploadPayload): Observable<InstrumentLogoRefDto> {
    return this.httpClient
      .post<InstrumentLogoEntityResponse>(`${BaseSettings.API_ENDPOINT}${AppSettings.INSTRUMENT_LOGO_KEY}`, payload, {
        headers: this.prepareHeaders()
      })
      .pipe(
        map((created) => this.toRefAndSeedCache(created, payload.svgContent)),
        catchError(this.handleError.bind(this))
      );
  }

  /** Updates an existing catalog logo entry (metadata and/or replacement SVG) following standard Auditable rights. */
  update(payload: InstrumentLogoUpdatePayload): Observable<InstrumentLogoRefDto> {
    return this.httpClient
      .put<InstrumentLogoEntityResponse>(`${BaseSettings.API_ENDPOINT}${AppSettings.INSTRUMENT_LOGO_KEY}`, payload, {
        headers: this.prepareHeaders()
      })
      .pipe(
        map((updated) => this.toRefAndSeedCache(updated, payload.svgContent ?? null)),
        catchError(this.handleError.bind(this))
      );
  }

  /** Deletes an unreferenced catalog logo entry following standard Auditable rights. */
  delete(idInstrumentLogo: number): Observable<void> {
    return this.httpClient
      .delete<void>(`${BaseSettings.API_ENDPOINT}${AppSettings.INSTRUMENT_LOGO_KEY}/${idInstrumentLogo}`, {
        headers: this.prepareHeaders()
      })
      .pipe(catchError(this.handleError.bind(this)));
  }

  /** Revokes all cached ObjectURLs and clears internal caches. */
  clearCache(): void {
    this.blobUrlCache.forEach((url) => URL.revokeObjectURL(url));
    this.blobUrlCache.clear();
    this.inFlightFetches.clear();
    this.failedKeys.clear();
  }

  private toRefAndSeedCache(entity: InstrumentLogoEntityResponse, uploadedSvg: string | null): InstrumentLogoRefDto {
    const ref: InstrumentLogoRefDto = {
      idInstrumentLogo: entity.idInstrumentLogo,
      name: entity.name,
      isin: entity.isin ?? null,
      logoScope: entity.logoScope,
      assetPath: entity.hasSvgContent || uploadedSvg ? null : (entity.assetPath ?? null),
      contentSha256: entity.contentSha256 ?? null,
      explicit: true,
      createdBy: entity.createdBy ?? null
    };
    // Seed cache immediately from the uploaded SVG so badges and tables render the updated image right away.
    const key = this.cacheKey(ref.idInstrumentLogo, ref.contentSha256);
    if (uploadedSvg && !this.blobUrlCache.has(key)) {
      const url = URL.createObjectURL(new Blob([uploadedSvg], { type: 'image/svg+xml' }));
      this.blobUrlCache.set(key, url);
      this.revision.update((v) => v + 1);
    }
    return ref;
  }

  private cacheKey(idInstrumentLogo: number, contentSha256: string | null | undefined): string {
    return `${idInstrumentLogo}:${contentSha256 ?? ''}`;
  }
}
