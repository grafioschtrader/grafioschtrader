import { InjectionToken } from '@angular/core';
import type { Observable } from 'rxjs';

/**
 * Opens the dialog in which the user applies for a higher daily limit of changes on an entity. A host binds it, because
 * the dialog belongs to the desktop component library and the services that report request errors must not import it:
 * the mobile application reaches them as well and has no such dialog. Where no host binds it, a limit error is only
 * shown as a toast.
 *
 * The function opens on subscription and emits the reference of the opened dialog, or nothing when opening is refused.
 */
export type LimitRequestOpener = (entityName: string) => Observable<{ onClose: Observable<unknown> }>;

export const LIMIT_REQUEST_OPENER = new InjectionToken<LimitRequestOpener>('LIMIT_REQUEST_OPENER');
