import { inject, Injector, Provider } from '@angular/core';
import { from, switchMap } from 'rxjs';
import { LIMIT_REQUEST_OPENER, LimitRequestOpener } from '../login/service/limit.request.opener';

/**
 * Binds {@link LIMIT_REQUEST_OPENER} to the dialog for applying for a higher daily limit. Both desktop hosts register
 * it; the mobile application does not, because it is read only and must not reach the Optimus UI dialog. The dialog is
 * imported on first use, so it stays out of the start bundle.
 *
 * @returns The provider to add to the providers of a host
 */
export function provideLimitRequestOpener(): Provider {
  return {
    provide: LIMIT_REQUEST_OPENER,
    useFactory: (): LimitRequestOpener => {
      const injector = inject(Injector);
      return (entityName) =>
        from(import('./component/dynamic.dialogs')).pipe(
          switchMap((m) => m.DynamicDialogs.openLimitTransactionRequest(injector, entityName))
        );
    }
  };
}
