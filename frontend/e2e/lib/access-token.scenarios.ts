import { expect, Locator, Page, Route, test } from '@playwright/test';

const BASE = '/api/useraccesstoken';
const FORM = '/api/globalparameters/formdefinition/UserAccessTokenCreateRequest';
const CREATE = /^(Create token|Token erstellen)$/;
const REVOKE = /^(Revoke|Widerrufen)$/;
const YES = /^\s*(yes|ja)\s*$/i;
const NO = /^\s*(no|nein)\s*$/i;
const SYNTHETIC_TOKEN = `gpat_${'x'.repeat(43)}`;

function token(id: number, name: string, createdAt = '2026-01-01T12:00:00') {
  return {
    idUserAccessToken: id,
    tokenName: name,
    tokenPrefix: 'gpat_test',
    scope: 'READ',
    createdAt,
    expiresAt: '2026-12-01T12:00:00',
    lastUsedAt: null,
    expired: false
  };
}

function authorization(id: number, clientName: string) {
  return {
    idOAuthAuthorization: id,
    clientName,
    clientId: `https://example.invalid/client-${id}.json`,
    scope: 'READ',
    createdAt: '2026-01-01T12:00:00',
    expiresAt: '2026-12-01T12:00:00',
    lastRefreshAt: null,
    expired: false
  };
}

/** Real login/form definition; all token and OAuth traffic is page-local, including writes. */
async function fixture(page: Page) {
  const gates: {
    method: string;
    path: string;
    arrived: boolean;
    gate: Promise<void>;
    release: () => void;
    used: boolean;
  }[] = [];
  const state = {
    tokens: [token(1, 'Older test token'), token(2, 'Newer test token', '2026-02-01T12:00:00')],
    oauth: [authorization(1, 'Alpha test app'), authorization(2, 'Zulu test app')],
    clientInfo: { endpointUrl: 'https://example.invalid/api/mcp', configSnippet: 'token=<TOKEN>; zone=${TIME_ZONE}' },
    rejectNextCreate: false,
    writes: [] as string[]
  };
  async function handler(route: Route): Promise<void> {
    const request = route.request();
    const path = new URL(request.url()).pathname;
    const method = request.method();
    let json: unknown;
    let response: Awaited<ReturnType<Route['fetch']>> | undefined;
    let status = 200;
    if (path === FORM) {
      response = await route.fetch();
      expect(response.ok()).toBe(true);
    } else if (method === 'GET' && path === `${BASE}/clientinfo`) json = { ...state.clientInfo };
    else if (method === 'GET' && path === `${BASE}/oauthauthorization`) json = state.oauth.map((row) => ({ ...row }));
    else if (method === 'GET' && path === BASE) json = state.tokens.map((row) => ({ ...row }));
    else if (method === 'POST' && path === BASE) {
      state.writes.push('create');
      if (state.rejectNextCreate) {
        state.rejectNextCreate = false;
        status = 400;
        json = 'E2E rejected creation';
      } else {
        const row = token(3, request.postDataJSON().tokenName, '2026-03-01T12:00:00');
        state.tokens = [...state.tokens, row];
        json = { token: row, plainToken: SYNTHETIC_TOKEN };
      }
    } else if (method === 'DELETE' && path.startsWith(`${BASE}/oauthauthorization/`)) {
      const id = Number(path.split('/').pop());
      state.writes.push(`oauth-${id}`);
      state.oauth = state.oauth.filter((row) => row.idOAuthAuthorization !== id);
      status = 204;
    } else if (method === 'DELETE' && path.startsWith(`${BASE}/`)) {
      const id = Number(path.split('/').pop());
      state.writes.push(`token-${id}`);
      state.tokens = state.tokens.filter((row) => row.idUserAccessToken !== id);
      status = 204;
    } else throw new Error(`Unexpected token request: ${method} ${path}`);
    const pending = gates.find((gate) => !gate.used && gate.method === method && gate.path === path);
    if (pending) {
      pending.used = true;
      pending.arrived = true;
      await pending.gate;
    }
    try {
      await route.fulfill(response ? { response } : status === 204 ? { status } : { status, json });
    } catch (error) {
      if (request.failure()?.errorText !== 'net::ERR_ABORTED') throw error;
    }
  }
  await page.route(/\/api\/useraccesstoken(?:\/.*)?$/, handler);
  await page.route('**/api/globalparameters/formdefinition/UserAccessTokenCreateRequest?*', handler);
  return {
    state,
    hold(path: string, method = 'GET') {
      let release!: () => void;
      const gate = new Promise<void>((resolve) => (release = resolve));
      const pending = { path, method, arrived: false, used: false, gate, release };
      gates.push(pending);
      return pending;
    },
    releaseAll: () => gates.forEach((gate) => gate.release())
  };
}

async function open(page: Page, mcp: boolean): Promise<Locator> {
  await page.getByRole('menuitem', { name: /^(Settings|Einstellungen)$/ }).click();
  await page
    .getByRole('menuitem', {
      name: mcp ? /^(AI assistants|KI-Assistenten) \(MCP\)/ : /^(Personal access tokens|Persönliche Zugriffstoken)/
    })
    .click();
  const dialog = page.locator('.p-dialog:visible').filter({ has: page.locator('user-access-token-table') });
  await expect(dialog).toBeVisible();
  return dialog;
}

async function close(dialog: Locator): Promise<void> {
  await dialog.locator('.p-dialog-close-button').click();
  await expect(dialog).toHaveCount(0);
}

async function create(page: Page, dialog: Locator, name = 'Created test token'): Promise<Locator> {
  await dialog.getByRole('button', { name: CREATE }).click();
  const editor = page.getByRole('dialog', { name: CREATE });
  await expect(editor.locator('#expiresInDays input, input#expiresInDays').first()).toHaveValue('90');
  await expect(editor.locator('#tokenName')).toHaveValue('');
  await editor.locator('#tokenName').fill(name);
  await editor.locator('#tokenName').blur();
  await expect(editor.locator('button[type="submit"]')).toBeEnabled();
  return editor;
}

function tokens(dialog: Locator): Locator {
  return dialog.locator('user-access-token-table');
}
function oauth(dialog: Locator): Locator {
  return dialog.locator('oauth-authorization-table');
}
function row(table: Locator, name: string): Locator {
  return table.locator('tbody > tr').filter({ hasText: name });
}

export function accessTokenScenarios(login: (page: Page, nickname: string) => Promise<unknown>, mcp: boolean): void {
  test.describe('access token dialog notification with isolated transport', () => {
    test.use({ viewport: { width: 1500, height: 1200 } });
    let api: Awaited<ReturnType<typeof fixture>>;
    test.beforeEach(async ({ page }) => {
      page.setDefaultTimeout(15_000);
      api = await fixture(page);
      await login(page, 'admin');
      if (mcp)
        test.skip(
          !(await page.evaluate(() => JSON.parse(sessionStorage.getItem('useFeatures') ?? '[]').includes('MCP'))),
          'MCP is disabled'
        );
    });
    test.afterEach(() => api?.releaseAll());

    test('delayed client info, form definition and lists render without another click', async ({ page }) => {
      const pending = [FORM, `${BASE}/clientinfo`, BASE, `${BASE}/oauthauthorization`].map((path) => api.hold(path));
      const dialog = await open(page, mcp);
      for (const gate of pending) await expect.poll(() => gate.arrived || gate.path === BASE).toBe(true);
      await expect(dialog.getByRole('button', { name: CREATE })).toBeDisabled();
      pending[0].release();
      await expect.poll(() => pending[2].arrived).toBe(true);
      for (const gate of pending) gate.release();
      await expect(dialog.getByRole('button', { name: CREATE })).toBeEnabled();
      await expect(dialog.locator('code')).toHaveText(api.state.clientInfo.endpointUrl);
      const zone = await page.evaluate(() => Intl.DateTimeFormat().resolvedOptions().timeZone);
      await expect(dialog.locator('pre')).toHaveText(`token=<TOKEN>; zone=${zone}`);
      await expect(tokens(dialog).locator('tbody > tr').first()).toContainText('Newer test token');
      await expect(oauth(dialog).locator('tbody > tr')).toHaveCount(2);
      const editor = await create(page, dialog);
      await close(editor);
      await close(dialog);
    });

    test('a rejected creation can be retried and the latest reload keeps selection and new rows', async ({ page }) => {
      const dialog = await open(page, mcp);
      await row(tokens(dialog), 'Older test token').click();
      const editor = await create(page, dialog);
      api.state.rejectNextCreate = true;
      await editor.locator('button[type="submit"]').click();
      await expect(editor.locator('button[type="submit"]')).toBeEnabled();
      const old = api.hold(BASE);
      const cancelled: string[] = [];
      page.on('requestfailed', (request) => {
        if (new URL(request.url()).pathname === BASE) cancelled.push(request.failure()?.errorText ?? '');
      });
      await editor.locator('button[type="submit"]').click();
      await expect(editor.locator('input[aria-label="token"]')).toHaveValue(SYNTHETIC_TOKEN);
      await expect.poll(() => old.arrived).toBe(true);
      api.state.tokens = api.state.tokens.map((token) =>
        token.idUserAccessToken === 3 ? { ...token, tokenName: 'Latest token name' } : token
      );
      await close(editor); // triggers a newer reload while the first one is still pending
      await expect(row(tokens(dialog), 'Latest token name')).toHaveCount(1);
      await expect(row(tokens(dialog), 'Older test token')).toHaveClass(/p-datatable-row-selected/);
      await expect.poll(() => cancelled).toContain('net::ERR_ABORTED');
      old.release();
      await expect(row(tokens(dialog), 'Created test token')).toHaveCount(0);
      expect(api.state.writes).toEqual(['create', 'create']);
      await close(dialog);
    });

    test('closing during creation ends the answer subscription and reopening lists the saved row without its secret', async ({
      page
    }) => {
      let dialog = await open(page, mcp);
      const editor = await create(page, dialog);
      const pending = api.hold(BASE, 'POST');
      let cancelled = false;
      page.on('requestfailed', (request) => {
        if (request.method() === 'POST' && new URL(request.url()).pathname === BASE) cancelled = true;
      });
      await editor.locator('button[type="submit"]').click();
      await expect.poll(() => pending.arrived).toBe(true);
      await close(editor);
      await expect.poll(() => cancelled).toBe(true);
      await close(dialog);
      dialog = await open(page, mcp);
      await expect(row(tokens(dialog), 'Created test token')).toHaveCount(1);
      pending.release();
      const next = await create(page, dialog, 'A new draft');
      await expect(next.locator('input[aria-label="token"]')).toHaveCount(0);
      await expect(next.locator('#tokenName')).toHaveValue('A new draft');
      await close(next);
      await close(dialog);
    });

    test('OAuth revocation can be cancelled, reloads asynchronously and clears selection and the empty table', async ({
      page
    }) => {
      const dialog = await open(page, mcp);
      const table = oauth(dialog);
      await row(table, 'Alpha test app').click();
      await table.getByRole('button', { name: REVOKE }).click();
      await page.locator('[role="alertdialog"]:visible').getByRole('button', { name: NO }).click();
      expect(api.state.writes).toEqual([]);
      const pending = api.hold(`${BASE}/oauthauthorization`);
      await table.getByRole('button', { name: REVOKE }).click();
      await page.locator('[role="alertdialog"]:visible').getByRole('button', { name: YES }).click();
      await expect.poll(() => pending.arrived).toBe(true);
      await expect(row(table, 'Alpha test app')).toHaveCount(1);
      pending.release();
      await expect(row(table, 'Alpha test app')).toHaveCount(0);
      await expect(table.getByRole('button', { name: REVOKE })).toHaveCount(0);
      await row(table, 'Zulu test app').click();
      await table.getByRole('button', { name: REVOKE }).click();
      await page.locator('[role="alertdialog"]:visible').getByRole('button', { name: YES }).click();
      await expect(table.locator('configurable-table')).toHaveCount(0);
      await expect(table.locator('p')).not.toHaveText('');
      expect(api.state.writes).toEqual(['oauth-1', 'oauth-2']);
      await close(dialog);
    });

    for (const path of [`${BASE}/clientinfo`, BASE, `${BASE}/oauthauthorization`]) {
      test(`closing the management dialog cancels its pending ${path.split('/').pop()} request`, async ({ page }) => {
        const pending = api.hold(path);
        let cancelled = false;
        page.on('requestfailed', (request) => {
          if (new URL(request.url()).pathname === path) cancelled = true;
        });
        let dialog = await open(page, mcp);
        await expect.poll(() => pending.arrived).toBe(true);
        await close(dialog);
        await expect.poll(() => cancelled).toBe(true);
        api.state.clientInfo = { endpointUrl: 'https://example.invalid/new', configSnippet: 'New client example' };
        api.state.tokens = [token(4, 'Fresh token list')];
        api.state.oauth = [authorization(4, 'Fresh OAuth list')];
        dialog = await open(page, mcp);
        await expect(row(tokens(dialog), 'Fresh token list')).toHaveCount(1);
        await expect(row(oauth(dialog), 'Fresh OAuth list')).toHaveCount(1);
        await expect(dialog.locator('code')).toHaveText(api.state.clientInfo.endpointUrl);
        pending.release();
        await expect(row(tokens(dialog), 'Older test token')).toHaveCount(0);
        await expect(row(oauth(dialog), 'Alpha test app')).toHaveCount(0);
        await close(dialog);
      });
    }

    test('a clipboard promise completing after close cannot report success or close a new editor', async ({ page }) => {
      const dialog = await open(page, mcp);
      const editor = await create(page, dialog);
      await editor.locator('button[type="submit"]').click();
      await expect(editor.locator('input[aria-label="token"]')).toBeVisible();
      await page.evaluate(() => {
        const gate = { complete: null as null | (() => void) };
        (window as any).__tokenCopyGate = gate;
        Object.defineProperty(navigator.clipboard, 'writeText', {
          configurable: true,
          value: () => new Promise<void>((resolve) => (gate.complete = resolve))
        });
      });
      await editor.getByRole('button', { name: /^(Copy|Kopieren)$/ }).click();
      await expect.poll(() => page.evaluate(() => typeof (window as any).__tokenCopyGate.complete)).toBe('function');
      await close(editor);
      const next = await create(page, dialog, 'Keep this draft');
      await page.evaluate(async () => {
        (window as any).__tokenCopyGate.complete();
        await Promise.resolve();
      });
      await expect(next.locator('#tokenName')).toHaveValue('Keep this draft');
      await expect(
        page
          .locator('.p-toast-message-text')
          .filter({ hasText: /Copied to the clipboard|In die Zwischenablage kopiert/ })
      ).toHaveCount(0);
      await close(next);
      await close(dialog);
    });
  });
}
