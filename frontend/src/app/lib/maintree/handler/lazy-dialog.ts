import { Type } from '@angular/core';
import { defer, EMPTY, from, Observable } from 'rxjs';
import { catchError, finalize, switchMap } from 'rxjs/operators';

/**
 * Loads the component class of a dialog on first use. A contributor passes a dynamic import, so the dialog and
 * everything it pulls in stays out of the bundle of the application shell:
 *
 * ```ts
 * () => import('../component/portfolio.edit.dynamic.component').then((m) => m.PortfolioEditDynamicComponent)
 * ```
 */
export type DialogComponentLoader = () => Promise<Type<unknown>>;

/** Loaders whose import is still pending; a second request for the same dialog in that time is ignored. */
const pendingLoaders = new Set<DialogComponentLoader>();

/**
 * Opens a dialog whose component class is loaded on demand, keeping the contract of `DynamicDialogRef.onClose`:
 *
 * - the import starts when the result is subscribed, and each subscription opens at most one dialog;
 * - unsubscribing before the import resolves opens no dialog, so a logout or navigation during the import leaves no
 *   orphaned dialog behind;
 * - while the import of the same loader is pending, a further request (a double click) completes without effect;
 * - a failed import is passed once to `reportLoadFailure`, opens nothing and completes without a value;
 * - otherwise the observable emits the close value of the dialog and completes with it.
 *
 * @param loader - Dynamic import of the dialog component
 * @param open - Opens the loaded component and returns the close stream of the dialog
 * @param reportLoadFailure - Reports a failed import to the user
 * @returns Observable of the value the dialog closes with
 */
export function openLazyDialog<R>(
  loader: DialogComponentLoader,
  open: (componentType: Type<unknown>) => Observable<R>,
  reportLoadFailure: (error: unknown) => void
): Observable<R> {
  return defer(() => {
    if (pendingLoaders.has(loader)) {
      return EMPTY;
    }
    pendingLoaders.add(loader);
    return from(loader()).pipe(
      finalize(() => pendingLoaders.delete(loader)),
      catchError((error) => {
        reportLoadFailure(error);
        return EMPTY;
      }),
      switchMap((componentType) => open(componentType))
    );
  });
}
