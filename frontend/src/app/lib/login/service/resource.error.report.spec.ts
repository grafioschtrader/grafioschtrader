import { describe, expect, it } from 'vitest';
import { ResourceErrorGate } from './resource.error.report';

/**
 * Every admitted error shows a toast and may log the user out, so the gate must neither drop a new failure nor repeat
 * one that was already reported.
 */
describe('ResourceErrorGate', () => {
  it('reports nothing while loading or after a success', () => {
    const gate = new ResourceErrorGate();
    expect(gate.admit(undefined)).toBe(false);
    expect(gate.admit(null)).toBe(false);
  });

  it('reports a failure once, even when the resource shows it again after loading', () => {
    const gate = new ResourceErrorGate();
    const failure = new Error('500');
    expect(gate.admit(failure)).toBe(true);
    expect(gate.admit(failure)).toBe(false);
    expect(gate.admit(undefined)).toBe(false);
    expect(gate.admit(failure)).toBe(false);
  });

  it('reports the failure of a retry again, although it carries the same message', () => {
    const gate = new ResourceErrorGate();
    expect(gate.admit(new Error('500'))).toBe(true);
    expect(gate.admit(undefined)).toBe(false);
    expect(gate.admit(new Error('500'))).toBe(true);
  });
});
