import { TranslateService } from '@ngx-translate/core';
import { DialogService, DynamicDialogRef } from '@openng/optimus-ui/dynamicdialog';
import { Subject, switchMap } from 'rxjs';
import { describe, expect, it, vi } from 'vitest';
import { DynamicDialogHelper } from './dynamicDialogHelper';

class DialogToken {}

describe('translated dynamic dialog opening', () => {
  it('waits for an asynchronous title, opens once and then observes the real close event', () => {
    const title = new Subject<string>();
    const ref = new DynamicDialogRef();
    const open = vi.fn(() => ref);
    const helper = new DynamicDialogHelper(
      { get: () => title } as unknown as TranslateService,
      { open } as unknown as DialogService,
      DialogToken,
      'EDIT'
    );
    const opening = helper.openDynamicDialog(400, { id: 7 });
    expect(open).not.toHaveBeenCalled();
    const results: string[] = [];
    const subscription = opening.pipe(switchMap((dialog) => dialog.onClose)).subscribe((value) => results.push(value));
    expect(open).not.toHaveBeenCalled();

    title.next('Bearbeiten');
    title.next('Edit');
    expect(open).toHaveBeenCalledOnce();
    expect(open).toHaveBeenCalledWith(DialogToken, expect.objectContaining({ header: 'Bearbeiten', data: { id: 7 } }));
    expect(results).toEqual([]);
    ref.close('saved');
    expect(results).toEqual(['saved']);
    subscription.unsubscribe();
  });

  it('opens nothing when the owner unsubscribes before the translation arrives', () => {
    const title = new Subject<string>();
    const open = vi.fn((): DynamicDialogRef | null => null);
    const helper = new DynamicDialogHelper(
      { get: () => title } as unknown as TranslateService,
      { open } as unknown as DialogService,
      DialogToken,
      'EDIT'
    );
    helper.openDynamicDialog(400).subscribe().unsubscribe();
    title.next('Bearbeiten');
    expect(open).not.toHaveBeenCalled();
  });

  it('completes without a reference when the dialog service refuses the opening', () => {
    const title = new Subject<string>();
    const helper = new DynamicDialogHelper(
      { get: () => title } as unknown as TranslateService,
      { open: (): DynamicDialogRef | null => null } as unknown as DialogService,
      DialogToken,
      'EDIT'
    );
    const received = vi.fn();
    let completed = false;
    helper.openDynamicDialog(400).subscribe({ next: received, complete: () => (completed = true) });
    title.next('Bearbeiten');
    expect(received).not.toHaveBeenCalled();
    expect(completed).toBe(true);
  });
});
