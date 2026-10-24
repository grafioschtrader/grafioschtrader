import { describe, expect, it } from 'vitest';
import { HttpContext, HttpHeaders } from '@angular/common/http';
import { authHeadersFor, SKIP_AUTH_HEADERS } from './auth.http.interceptor';
import { BaseService } from './base.service';

const baseUri = 'https://example.test/grafioschtrader/';
const sessionHeaders = { 'x-auth-token': 'session-token', 'x-time-zone': 'Europe/Zurich' };

describe('authHeadersFor', () => {
  it.each([
    ['/api/user/own', baseUri],
    ['../api/user/own', baseUri],
    ['api/user/own', 'https://example.test/'],
    ['https://example.test/api/user/own?test=1', baseUri],
    ['//example.test/api/user/own', baseUri]
  ])('authenticates own API URL %s against %s', (url, base) => {
    expect(authHeadersFor(url, new HttpContext(), 'session-token', 'Europe/Zurich', base)).toEqual(sessionHeaders);
  });

  it.each([
    'api/user/own',
    './api/user/own',
    '/api',
    '/api-docs',
    '/api-docs/api/user',
    '/assets/image.svg',
    '/api/../assets/image.svg',
    'https://other.test/api/user/own',
    '//other.test/api/user/own',
    'https://example.test.other.test/api/user/own',
    'https://example.test:8443/api/user/own',
    'http://example.test/api/user/own',
    'https://example.test@other.test/api/user/own',
    'data:text/plain,/api/user/own',
    'https://['
  ])('leaves non-API or foreign URL %s untouched under the production base href', (url) => {
    expect(authHeadersFor(url, new HttpContext(), 'session-token', 'Europe/Zurich', baseUri)).toEqual({});
  });

  it('omits both session headers for public endpoints even with an old token', () => {
    const context = new HttpContext().set(SKIP_AUTH_HEADERS, true);
    expect(authHeadersFor('/api/login', context, 'old-token', 'Asia/Tokyo', baseUri)).toEqual({});
  });

  it.each([null, ''])('omits both session headers without a token (%s)', (jwt) => {
    expect(authHeadersFor('/api/user/own', new HttpContext(), jwt, 'Asia/Tokyo', baseUri)).toEqual({});
  });

  it('uses the current time zone and leaves caller headers intact regardless of their casing', () => {
    const headers = new HttpHeaders({ 'X-Auth-Token': 'explicit-token', Accept: 'application/zip' });
    expect(authHeadersFor('/api/user', new HttpContext(), 'session-token', 'Asia/Tokyo', baseUri, headers)).toEqual({
      'x-time-zone': 'Asia/Tokyo'
    });
    expect(
      authHeadersFor(
        '/api/user',
        new HttpContext(),
        'session-token',
        'UTC',
        baseUri,
        new HttpHeaders({ 'X-Time-Zone': 'America/Los_Angeles' })
      )
    ).toEqual({ 'x-auth-token': 'session-token' });
    expect(
      authHeadersFor(
        '/api/user',
        new HttpContext(),
        'session-token',
        'UTC',
        baseUri,
        headers.set('X-Time-Zone', 'America/Los_Angeles')
      )
    ).toEqual({});
    expect(headers.get('X-Auth-Token')).toBe('explicit-token');
    expect(headers.get('Accept')).toBe('application/zip');
  });

  it('keeps JSON, download and multipart media headers separate from authentication', () => {
    class TestService extends BaseService {}
    const service = new TestService();
    const json = service.prepareHeaders();
    const download = service.prepareHeaders('application/zip');
    const multipart = service.prepareMultipartHeaders();
    expect(json.get('Content-Type')).toBe('application/json');
    expect(download.get('Accept')).toBe('application/zip');
    expect(multipart.get('Accept')).toBe('application/json');
    for (const headers of [json, download, multipart]) {
      expect(headers.has('x-auth-token')).toBe(false);
      expect(headers.has('x-time-zone')).toBe(false);
      expect(
        authHeadersFor('/api/upload', new HttpContext(), 'session-token', 'Europe/Zurich', baseUri, headers)
      ).toEqual(sessionHeaders);
    }
    expect(multipart.has('Content-Type')).toBe(false);
  });
});
