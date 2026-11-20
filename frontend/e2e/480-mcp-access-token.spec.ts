import { expect, Locator, Page, test } from '@playwright/test';
import * as fs from 'fs';
import * as path from 'path';
import { loginAsFixtureUser } from './helpers';

interface TokenFixture {
  nickname: string;
  tokenName: string;
  expiresInDays: number;
  finishBy: 'copy' | 'close';
  e2e: string;
}

interface TokenRow {
  idUserAccessToken: number;
  tokenName: string;
  tokenPrefix: string;
  scope: string;
  createdAt: string;
  expiresAt: string;
  lastUsedAt: string | null;
}

const FIXTURES = (
  JSON.parse(
    fs.readFileSync(
      path.resolve(
        __dirname,
        '../../backend/grafioschtrader-server/src/test/resources/testdata/mcp-access-tokens.json'
      ),
      'utf8'
    )
  ) as TokenFixture[]
).filter((row) => row.e2e === 'e');
const ENDPOINT = '/api/useraccesstoken';
const CREATE = /^(Create token|Token erstellen)$/;
const REVOKE = /^(Revoke|Widerrufen)$/;
const YES = /^\s*(yes|ja)\s*$/i;
const NO = /^\s*(no|nein)\s*$/i;

async function headers(page: Page): Promise<Record<string, string>> {
  const jwt = await page.evaluate(() => sessionStorage.getItem('jwt'));
  expect(Boolean(jwt), 'The fixture user must be logged in').toBe(true);
  return { 'x-auth-token': jwt! };
}

async function list(page: Page): Promise<TokenRow[]> {
  const response = await page.request.get(ENDPOINT, { headers: await headers(page) });
  expect(response.status(), 'Token listing must succeed').toBe(200);
  const rows = await response.json();
  expect(Array.isArray(rows)).toBe(true);
  // Never print the response body or a token, including on assertion failures.
  expect(
    rows.every((row: object) => !Object.hasOwn(row, 'plainToken') && !Object.hasOwn(row, 'tokenHash')),
    'Token listings must contain neither clear text nor hashes'
  ).toBe(true);
  return rows;
}

/** Deletes only the natural keys owned by this spec, including leftovers of an interrupted run. */
async function cleanup(page: Page, fixture: TokenFixture): Promise<void> {
  const names = FIXTURES.filter((row) => row.nickname === fixture.nickname).map((row) => row.tokenName);
  for (const row of (await list(page)).filter((row) => names.includes(row.tokenName))) {
    const response = await page.request.delete(`${ENDPOINT}/${row.idUserAccessToken}`, {
      headers: await headers(page)
    });
    expect([204, 404]).toContain(response.status());
  }
  expect(
    (await list(page)).some((row) => names.includes(row.tokenName)),
    'Owned tokens must be removed'
  ).toBe(false);
}

async function openManagement(page: Page): Promise<Locator> {
  await page.getByRole('menuitem', { name: /^(Settings|Einstellungen)$/ }).click();
  await page.getByRole('menuitem', { name: /^(AI assistants|KI-Assistenten) \(MCP\)/ }).click();
  const dialog = page.locator('.p-dialog:visible').filter({ has: page.locator('user-access-token-table') });
  await expect(dialog).toBeVisible();
  await expect(dialog.getByRole('button', { name: CREATE })).toBeEnabled();
  return dialog;
}

function creation(page: Page): Locator {
  return page.getByRole('dialog', { name: CREATE });
}

async function openCreation(page: Page, management: Locator): Promise<Locator> {
  await management.getByRole('button', { name: CREATE }).click();
  const dialog = creation(page);
  await expect(dialog).toBeVisible();
  await expect(dialog.locator('#tokenName')).toHaveValue('');
  await expect(dialog.locator('input[aria-label="token"]')).toHaveCount(0);
  await expect(dialog.locator('button[type="submit"]')).toBeDisabled();
  return dialog;
}

async function close(dialog: Locator): Promise<void> {
  await dialog.locator('.p-dialog-close-button').click();
  await expect(dialog).toHaveCount(0);
}

function tokenRow(management: Locator, name: string): Locator {
  return management.locator('user-access-token-table tbody > tr').filter({ hasText: name });
}

async function mcpStatus(page: Page, token: string): Promise<number> {
  const response = await page.request.post('/api/mcp', {
    headers: { Authorization: `Bearer ${token}`, Accept: 'application/json, text/event-stream' },
    data: { jsonrpc: '2.0', id: 1, method: 'tools/list', params: {} }
  });
  if (response.status() === 200) {
    const answer = await response.json();
    expect(
      answer.result?.tools?.some((tool: { name: string }) => tool.name === 'list_portfolios'),
      'A valid token must receive the MCP tool list'
    ).toBe(true);
  }
  return response.status();
}

// These are real, short-lived test credentials. Do not retain them in traces or screenshots.
test.use({
  trace: 'off',
  screenshot: 'off',
  permissions: ['clipboard-read', 'clipboard-write'],
  viewport: { width: 1500, height: 1150 }
});

test.describe('personal MCP access tokens through the settings dialog', () => {
  for (const fixture of FIXTURES) {
    test(`creates a token, ${fixture.finishBy === 'copy' ? 'copies it' : 'closes its one-time display'} and revokes its MCP access`, async ({
      page
    }) => {
      page.setDefaultTimeout(15_000);
      await loginAsFixtureUser(page, fixture.nickname);
      const enabled = await page.evaluate(() =>
        (JSON.parse(sessionStorage.getItem('useFeatures') ?? '[]') as string[]).includes('MCP')
      );
      test.skip(!enabled, 'MCP is disabled by this instance; the settings entry is not offered');
      await cleanup(page, fixture);
      try {
        let management = await openManagement(page);
        let dialog = await openCreation(page, management);
        const submit = dialog.locator('button[type="submit"]');
        const days = dialog.locator('#expiresInDays input, input#expiresInDays').first();
        await expect(days).toHaveValue('90');
        await dialog.locator('#tokenName').fill(fixture.tokenName);
        await days.fill('0');
        await days.blur();
        await expect(submit).toBeDisabled();
        await days.fill(String(fixture.expiresInDays));
        await days.blur();
        await expect(submit).toBeEnabled();

        const createdResponse = page.waitForResponse(
          (response) => new URL(response.url()).pathname === ENDPOINT && response.request().method() === 'POST'
        );
        await submit.click();
        const response = await createdResponse;
        expect(response.status(), 'Creating the token through the form must succeed').toBe(200);
        expect(response.headers()['cache-control']).toContain('no-store');
        const created = (await response.json()) as { token: TokenRow; plainToken: string };
        const token = created.plainToken;
        expect(
          typeof token === 'string' && token.startsWith('gpat_') && token.length === 48,
          'Creation must return a personal access token'
        ).toBe(true);
        const plaintext = dialog.locator('input[aria-label="token"]');
        await expect(plaintext).toBeVisible();
        await expect(plaintext).toHaveAttribute('readonly');
        await expect
          .poll(async () => (await plaintext.inputValue()) === token, {
            message: 'The one-time display must contain the token returned by the real server'
          })
          .toBe(true);
        await expect(tokenRow(management, fixture.tokenName)).toHaveCount(1);
        const persisted = (await list(page)).find((row) => row.tokenName === fixture.tokenName)!;
        expect(persisted.scope).toBe('READ');
        expect(persisted.tokenPrefix === token.slice(0, 12)).toBe(true);
        expect(Math.round((Date.parse(persisted.expiresAt) - Date.parse(persisted.createdAt)) / 86_400_000)).toBe(
          fixture.expiresInDays
        );
        expect(await mcpStatus(page, token), 'The newly created token must authenticate on MCP').toBe(200);

        if (fixture.finishBy === 'copy') {
          await page.evaluate(() => navigator.clipboard.writeText('e2e-token-clipboard-sentinel'));
          await dialog.getByRole('button', { name: /^(Copy|Kopieren)$/ }).click();
          await expect(dialog).toHaveCount(0);
          expect(
            await page.evaluate((expected) => navigator.clipboard.readText().then((text) => text === expected), token),
            'Copy must put the actual token on the clipboard'
          ).toBe(true);
        } else {
          await close(dialog);
        }

        dialog = await openCreation(page, management);
        await close(dialog);
        await close(management);
        management = await openManagement(page);
        await expect(tokenRow(management, fixture.tokenName)).toHaveCount(1);
        await expect(management.locator('input[aria-label="token"]')).toHaveCount(0);
        expect(
          (await list(page)).find((row) => row.tokenName === fixture.tokenName)?.lastUsedAt != null,
          'Successful MCP authentication must record usage'
        ).toBe(true);

        // Cancelling revocation keeps the persisted token and its access.
        await tokenRow(management, fixture.tokenName).click();
        await management.getByRole('button', { name: REVOKE }).click();
        let confirm = page.locator('[role="alertdialog"]:visible');
        await confirm.getByRole('button', { name: NO }).click();
        await expect(confirm).toBeHidden();
        expect((await list(page)).some((row) => row.tokenName === fixture.tokenName)).toBe(true);
        expect(await mcpStatus(page, token)).toBe(200);

        const deleted = page.waitForResponse(
          (result) =>
            new URL(result.url()).pathname === `${ENDPOINT}/${persisted.idUserAccessToken}` &&
            result.request().method() === 'DELETE'
        );
        await management.getByRole('button', { name: REVOKE }).click();
        confirm = page.locator('[role="alertdialog"]:visible');
        await confirm.getByRole('button', { name: YES }).click();
        expect((await deleted).status()).toBe(204);
        await expect(tokenRow(management, fixture.tokenName)).toHaveCount(0);
        expect((await list(page)).some((row) => row.tokenName === fixture.tokenName)).toBe(false);
        expect(await mcpStatus(page, token), 'Revocation must immediately end MCP access').toBe(401);
        await expect(management.getByRole('button', { name: REVOKE })).toHaveCount(0);
        await close(management);
      } finally {
        await cleanup(page, fixture);
        // The headless browser's clipboard must not retain the test token either.
        await page.evaluate(() => navigator.clipboard.writeText(''));
      }
    });
  }
});
