import { DOCUMENT, inject } from '@angular/core';
import { HttpContext, HttpContextToken, HttpHeaders, HttpInterceptorFn } from '@angular/common/http';
import { BaseSettings } from '../../base.settings';
import { GlobalSessionNames } from '../../global.session.names';

/** Public requests opt out even when a previous session still has a JWT. */
export const SKIP_AUTH_HEADERS = new HttpContextToken<boolean>(() => false);

/**
 * Determines the missing authentication headers without reading browser state.
 *
 * @param url Request URL, resolved as the browser resolves it against baseUri
 * @param context Request metadata, including the public-endpoint opt-out
 * @param jwt Current session token, or null for an anonymous session
 * @param timeZone Current browser IANA time zone
 * @param baseUri Application document's base URI, including its deployment path
 * @param headers Caller-supplied headers, which always take precedence (case-insensitive)
 * @returns Headers to add to an own-API request; empty for public, foreign or anonymous requests
 */
export function authHeadersFor(
  url: string,
  context: HttpContext,
  jwt: string | null,
  timeZone: string,
  baseUri: string,
  headers = new HttpHeaders()
): Record<string, string> {
  if (!jwt || context.get(SKIP_AUTH_HEADERS)) {
    return {};
  }
  let target: URL;
  let base: URL;
  try {
    base = new URL(baseUri);
    target = new URL(url, base);
  } catch {
    return {};
  }
  if (target.origin !== base.origin || !target.pathname.startsWith(BaseSettings.API_ENDPOINT)) {
    return {};
  }
  const additions: Record<string, string> = {};
  if (!headers.has('x-auth-token')) {
    additions['x-auth-token'] = jwt;
  }
  if (!headers.has(BaseSettings.TIME_ZONE_HEADER)) {
    additions[BaseSettings.TIME_ZONE_HEADER] = timeZone;
  }
  return additions;
}

/** Adds session headers to the own API; response events and error handling remain with the caller. */
export const authInterceptor: HttpInterceptorFn = (req, next) => {
  const setHeaders = authHeadersFor(
    req.url,
    req.context,
    sessionStorage.getItem(GlobalSessionNames.JWT),
    Intl.DateTimeFormat().resolvedOptions().timeZone,
    inject(DOCUMENT).baseURI,
    req.headers
  );
  return next(Object.keys(setHeaders).length ? req.clone({ setHeaders }) : req);
};
