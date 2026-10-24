import { MonoTypeOperatorFunction, Observable, Subscription } from 'rxjs';

/**
 * Operator that completes the source when the given session subscription is closed. A source subscribed after the
 * session has already ended completes at once without subscribing to the source, so no request is sent for a dialog
 * that is no longer shown.
 *
 * @param session - The subscription that marks the lifetime, typically the dialog session of an edit dialog
 * @returns Operator mirroring the source until the session ends
 */
export function untilSessionEnds<T>(session: Subscription): MonoTypeOperatorFunction<T> {
  return (source: Observable<T>) =>
    new Observable<T>((subscriber) => {
      const complete = () => subscriber.complete();
      session.add(complete);
      if (!subscriber.closed) {
        subscriber.add(source.subscribe(subscriber));
      }
      return () => session.remove(complete);
    });
}
