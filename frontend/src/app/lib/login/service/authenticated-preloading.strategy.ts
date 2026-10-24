import { Injectable } from '@angular/core';
import { PreloadingStrategy, Route } from '@angular/router';
import { EMPTY, Observable, timer } from 'rxjs';
import { switchMap } from 'rxjs/operators';
import { GlobalSessionNames } from '../../global.session.names';

/**
 * Preloads the lazily loaded routes only for an authenticated user, and only once the browser is idle. The login and
 * registration pages therefore fetch no feature code, while after a successful login the feature chunks arrive in
 * the background and the first click into an area does not wait for the network.
 *
 * The router asks the strategy again after every navigation, so the navigation that follows the login starts the
 * preloading. Each host lists this class among its providers.
 */
@Injectable()
export class AuthenticatedPreloadingStrategy implements PreloadingStrategy {
  /** Upper bound for the wait on an idle period, after which the chunks are requested anyway. */
  private static readonly IDLE_TIMEOUT_MS = 2000;

  preload(route: Route, load: () => Observable<any>): Observable<any> {
    if (!sessionStorage.getItem(GlobalSessionNames.JWT)) {
      return EMPTY;
    }
    return AuthenticatedPreloadingStrategy.whenIdle().pipe(
      switchMap(() => (sessionStorage.getItem(GlobalSessionNames.JWT) ? load() : EMPTY))
    );
  }

  /**
   * Emits once when the browser reports an idle period, or after the timeout. Browsers without
   * requestIdleCallback fall back to a short timer.
   */
  private static whenIdle(): Observable<unknown> {
    if (typeof requestIdleCallback !== 'function') {
      return timer(200);
    }
    return new Observable<void>((subscriber) => {
      const handle = requestIdleCallback(
        () => {
          subscriber.next();
          subscriber.complete();
        },
        { timeout: AuthenticatedPreloadingStrategy.IDLE_TIMEOUT_MS }
      );
      return () => cancelIdleCallback(handle);
    });
  }
}
