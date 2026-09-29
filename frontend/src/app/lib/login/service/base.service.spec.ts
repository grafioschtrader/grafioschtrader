import { afterEach, describe, expect, it, vi } from 'vitest';
import { BaseService } from './base.service';
import { BaseSettings } from '../../base.settings';

class TestService extends BaseService {}

describe('BaseService request time zone', () => {
  afterEach(() => {
    vi.unstubAllGlobals();
    vi.restoreAllMocks();
  });

  it('adds the current browser zone to JSON and multipart authenticated requests', () => {
    vi.stubGlobal('sessionStorage', { getItem: () => 'test-token' });
    const zone = vi.spyOn(Intl.DateTimeFormat.prototype, 'resolvedOptions');
    const service = new TestService();
    zone.mockReturnValue({ timeZone: 'America/Los_Angeles' } as Intl.ResolvedDateTimeFormatOptions);
    const json = service.prepareHeaders();
    expect(json.get('x-auth-token')).toBe('test-token');
    expect(json.get(BaseSettings.TIME_ZONE_HEADER)).toBe('America/Los_Angeles');
    zone.mockReturnValue({ timeZone: 'Asia/Tokyo' } as Intl.ResolvedDateTimeFormatOptions);
    const multipart = service.prepareMultipartHeaders();
    expect(multipart.get(BaseSettings.TIME_ZONE_HEADER)).toBe('Asia/Tokyo');
    expect(multipart.has('Content-Type')).toBe(false);
  });

  it('leaves anonymous request headers unchanged', () => {
    vi.stubGlobal('sessionStorage', { getItem: () => null });
    expect(new TestService().prepareHeaders().has(BaseSettings.TIME_ZONE_HEADER)).toBe(false);
  });
});
