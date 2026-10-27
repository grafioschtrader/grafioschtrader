import { DestroyRef, DOCUMENT, EnvironmentProviders, inject, provideEnvironmentInitializer } from '@angular/core';

/** Close button of a template-bound and of a dynamic Optimus dialog; both components use this class. */
const DIALOG_CLOSE_BUTTON = '.p-dialog-close-button';

/**
 * Keeps the focus where it is while the pointer presses the close button of a dialog, so the first click closes it.
 *
 * Without this the button takes the focus on mousedown. A focused, empty required field then counts as touched and
 * shows its message, the vertically centred dialog grows and moves up, and the mouseup no longer hits the button: the
 * browser fires no click and the dialog stays open until the second click. Suppressing the default of mousedown keeps
 * the focus and therefore the layout; the click still follows. The button stays reachable with Tab, and Escape closes
 * the dialog as before.
 *
 * Both hosts register it once among their root providers.
 *
 * @returns Environment initializer that installs one capturing mousedown listener on the document
 */
export function provideDialogCloseKeepsFocus(): EnvironmentProviders {
  return provideEnvironmentInitializer(() => {
    const document = inject(DOCUMENT);
    const keepFocus = (event: MouseEvent) => {
      if (event.button === 0 && (event.target as Element)?.closest?.(DIALOG_CLOSE_BUTTON)) {
        event.preventDefault();
      }
    };
    document.addEventListener('mousedown', keepFocus, true);
    inject(DestroyRef).onDestroy(() => document.removeEventListener('mousedown', keepFocus, true));
  });
}
