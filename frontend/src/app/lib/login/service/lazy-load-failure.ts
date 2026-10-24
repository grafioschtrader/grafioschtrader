import { inject } from '@angular/core';
import { NavigationError } from '@angular/router';
import { MessageToastService } from '../../message/message.toast.service';
import { InfoLevelType } from '../../message/info.leve.type';

/** Messages the browsers use when the file behind a dynamic import cannot be fetched. */
const CHUNK_LOAD_FAILURE_PATTERNS = [
  /failed to fetch dynamically imported module/i,
  /error loading dynamically imported module/i,
  /importing a module script failed/i
];

/**
 * Tells whether an error comes from a dynamic import whose file could not be fetched. After a deployment replaced the
 * hashed files, a tab opened before it fails this way on the first visit of an area it has not loaded yet.
 *
 * @param error - Error thrown by the router or a loader
 * @returns True for a failed dynamic import
 */
export function isChunkLoadFailure(error: unknown): boolean {
  const message = error instanceof Error ? error.message : String(error ?? '');
  return CHUNK_LOAD_FAILURE_PATTERNS.some((pattern) => pattern.test(message));
}

/**
 * Router navigation error handler (`withNavigationErrorHandler`). A route whose code cannot be loaded keeps the
 * previous view and asks the user to reload the page instead of leaving an empty outlet behind.
 *
 * @param navigationError - The failed navigation
 */
export function handleLazyLoadNavigationError(navigationError: NavigationError): void {
  if (isChunkLoadFailure(navigationError.error)) {
    inject(MessageToastService).showMessageI18n(InfoLevelType.ERROR, 'MSG_LOAD_NEW_VERSION');
  }
}
