import { describe, expect, it } from 'vitest';
import { splitSimulationFailureMessage } from './simulation.run';

describe('splitSimulationFailureMessage', () => {
  it('translates a legacy replay failure without displaying its null diagnostic', () => {
    expect(splitSimulationFailureMessage('REPLAY_DIVIDEND_UNAVAILABLE: null')).toEqual({
      key: 'REPLAY_DIVIDEND_UNAVAILABLE',
      detail: undefined
    });
  });

  it('keeps a useful diagnostic separate from its translation key', () => {
    expect(splitSimulationFailureMessage('REPLAY_FX_UNAVAILABLE: USD/CHF')).toEqual({
      key: 'REPLAY_FX_UNAVAILABLE',
      detail: 'USD/CHF'
    });
  });
});
