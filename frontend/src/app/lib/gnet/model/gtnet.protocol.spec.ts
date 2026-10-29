import { describe, expect, it } from 'vitest';
import { getReverseCode, GTNetMessageCodeType } from './gtnet.message';
import { messageCodeName } from './gtnet.protocol';

describe('GTNet message code resolution', () => {
  it('distinguishes a missing code from the zero-valued ping code', () => {
    expect(messageCodeName(null)).toBeNull();
    expect(messageCodeName(undefined)).toBeNull();
    expect(messageCodeName(0)).toBe('GT_NET_PING');
    expect(getReverseCode(null)).toBeNull();
    expect(getReverseCode(undefined)).toBeNull();
    expect(getReverseCode(GTNetMessageCodeType.GT_NET_PING)).toBeNull();
  });

  it('resolves the same cancellation for a stored numeric announcement and a form selection', () => {
    for (const code of [GTNetMessageCodeType.GT_NET_MAINTENANCE_ALL_C, 'GT_NET_MAINTENANCE_ALL_C']) {
      expect(getReverseCode(code)).toBe(GTNetMessageCodeType.GT_NET_MAINTENANCE_CANCEL_ALL_C);
    }
  });
});
