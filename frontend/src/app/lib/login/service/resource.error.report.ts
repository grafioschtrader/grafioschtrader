import { effect, EffectRef, Injector, Resource, untracked } from '@angular/core';
import type { BaseAuthService } from './base.auth.service';

/**
 * Decides which errors of one resource are reported. The `error()` signal of a resource can show the same error object
 * again, for example while a reload of an unchanged request is pending, and every report shows a toast. A request that
 * a newer parameter value superseded is aborted and never produces an error, so it never reaches this gate.
 */
export class ResourceErrorGate {
  private lastReported: unknown;

  /**
   * Admits an error for reporting when it is the failure of a request not reported yet.
   *
   * @param error the current value of the resource's `error()` signal; undefined while loading or after a success
   * @returns true when the error must be reported, false for no error or an error object already reported
   */
  admit(error: unknown): boolean {
    if (error == null || error === this.lastReported) {
      return false;
    }
    this.lastReported = error;
    return true;
  }
}

/**
 * Reports each failed request of an `httpResource` exactly once through the owning service, with the toast and logout
 * semantics of the observable requests. The template therefore renders the error state without a toast of its own.
 *
 * @param resource the resource whose failures are reported
 * @param service the service that built the request; its `handleResourceError` maps the error
 * @param injector required when called outside an injection context
 * @returns the effect, destroyed with the injection context it was created in
 */
export function reportResourceErrors(
  resource: Resource<unknown>,
  service: Pick<BaseAuthService<unknown>, 'handleResourceError'>,
  injector?: Injector
): EffectRef {
  const gate = new ResourceErrorGate();
  return effect(
    () => {
      const error = resource.error();
      untracked(() => {
        if (gate.admit(error)) {
          service.handleResourceError(error);
        }
      });
    },
    injector ? { injector } : undefined
  );
}
