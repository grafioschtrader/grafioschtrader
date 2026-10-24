import { describe, expect, it } from 'vitest';
import { Subject, Subscription } from 'rxjs';
import { untilSessionEnds } from './dialog.session';

describe('untilSessionEnds', () => {
  it('passes values until the session ends and then completes', () => {
    const session = new Subscription();
    const source = new Subject<number>();
    const values: number[] = [];
    let completed = false;
    source
      .pipe(untilSessionEnds(session))
      .subscribe({ next: (v) => values.push(v), complete: () => (completed = true) });
    source.next(1);
    session.unsubscribe();
    source.next(2);
    expect(values).toEqual([1]);
    expect(completed).toBe(true);
    expect(source.observed).toBe(false);
  });

  it('never subscribes to the source once the session has ended', () => {
    const session = new Subscription();
    session.unsubscribe();
    const source = new Subject<number>();
    let completed = false;
    source.pipe(untilSessionEnds(session)).subscribe({ complete: () => (completed = true) });
    expect(completed).toBe(true);
    expect(source.observed).toBe(false);
  });

  it('leaves nothing registered in a session that outlives an unsubscribed source', () => {
    const session = new Subscription();
    const source = new Subject<number>();
    source.pipe(untilSessionEnds(session)).subscribe().unsubscribe();
    expect(source.observed).toBe(false);
    let completedLater = false;
    source.pipe(untilSessionEnds(session)).subscribe({ complete: () => (completedLater = true) });
    session.unsubscribe();
    expect(completedLater).toBe(true);
  });
});
