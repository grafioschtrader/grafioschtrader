import { describe, expect, it, vi } from 'vitest';
import { of, Subject } from 'rxjs';
import { DialogComponentLoader, openLazyDialog } from './lazy-dialog';

class DialogA {}

/** Loader whose import resolves only when the test says so. */
function controlledLoader(): { loader: DialogComponentLoader; resolve: () => void } {
  let resolveFn: (type: any) => void;
  const promise = new Promise<any>((res) => (resolveFn = res));
  return { loader: () => promise, resolve: () => resolveFn(DialogA) };
}

const flush = () => new Promise((r) => setTimeout(r, 0));

describe('openLazyDialog', () => {
  it('opens the loaded dialog and passes on its close value', async () => {
    const open = vi.fn(() => of('CLOSED'));
    const values: string[] = [];
    openLazyDialog(() => Promise.resolve(DialogA), open, vi.fn()).subscribe((v) => values.push(v));
    await flush();
    expect(open).toHaveBeenCalledWith(DialogA);
    expect(values).toEqual(['CLOSED']);
  });

  it('ignores a second request while the same import is pending', async () => {
    const { loader, resolve } = controlledLoader();
    const open = vi.fn(() => new Subject<string>());
    openLazyDialog(loader, open, vi.fn()).subscribe();
    let secondCompleted = false;
    openLazyDialog(loader, open, vi.fn()).subscribe({ complete: () => (secondCompleted = true) });
    resolve();
    await flush();
    expect(secondCompleted).toBe(true);
    expect(open).toHaveBeenCalledTimes(1);
  });

  it('opens nothing when unsubscribed before the import resolves', async () => {
    const { loader, resolve } = controlledLoader();
    const open = vi.fn(() => of('CLOSED'));
    openLazyDialog(loader, open, vi.fn()).subscribe().unsubscribe();
    resolve();
    await flush();
    expect(open).not.toHaveBeenCalled();
  });

  it('reports a failed import once, opens nothing and lets the same dialog be retried', async () => {
    let attempts = 0;
    const loader: DialogComponentLoader = () =>
      attempts++ === 0 ? Promise.reject(new Error('chunk')) : Promise.resolve(DialogA);
    const report = vi.fn();
    const open = vi.fn(() => of('CLOSED'));
    let completed = false;
    openLazyDialog(loader, open, report).subscribe({ complete: () => (completed = true) });
    await flush();
    expect(report).toHaveBeenCalledTimes(1);
    expect(open).not.toHaveBeenCalled();
    expect(completed).toBe(true);

    openLazyDialog(loader, open, report).subscribe();
    await flush();
    expect(open).toHaveBeenCalledTimes(1);
  });
});
