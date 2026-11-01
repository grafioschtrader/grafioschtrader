/**
 * What code outside the dynamic form may call on a rendered input: moving the focus to it, and on the reactive form
 * re-evaluating the required marker after its validators were replaced (`DynamicFieldHelper.resetValidator`). The
 * signal form derives the marker from the `required` rule and has no such method.
 */
export interface FocusableInput {
  focus(): void;
  reEvaluateRequired?(): void;
}

/**
 * Moves the keyboard focus to the '#input' view child of an input component. It is a native element for the plain
 * HTML controls, but an Optimus UI component instance for the wrapped ones (p-select, ...), where 'nativeElement'
 * does not exist and the component offers its own focus(). Both cases are handled, so callers do not have to know
 * which kind of control a field happens to use.
 *
 * @param target - The view child, absent before the input has rendered
 */
export function focusInputTarget(target: unknown): void {
  const candidate = target as { nativeElement?: { focus?: () => void }; focus?: () => void } | undefined;
  if (typeof candidate?.nativeElement?.focus === 'function') {
    candidate.nativeElement.focus();
  } else if (typeof candidate?.focus === 'function') {
    candidate.focus();
  }
}
