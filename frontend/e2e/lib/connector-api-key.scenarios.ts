import { expect, Locator, Page, test } from '@playwright/test';

const CREATE = /^(Create|Erstellen)\b/;
const EDIT = /^(Edit|Bearbeiten)\b/;
const DELETE = /^(Delete|Löschen)\b/;
const KEY_URL = /\/api\/connectorapikey(?:\/[^?]*)?$/;
const PROVIDERS = {
  configured: { readableName: 'Configured test provider', subscriptionTypes: ['OPEN'] },
  plain: { readableName: 'Provider without subscription', subscriptionTypes: [] },
  named: { readableName: 'Provider with named subscriptions', subscriptionTypes: ['OPEN', 'CLOSED'] },
  numeric: { readableName: 'Provider with numeric subscriptions', subscriptionTypes: [1, 2] },
  object: { readableName: 'Provider with object subscriptions', subscriptionTypes: [{ id: 17 }] }
};

interface Key {
  idProvider: string;
  apiKey: string;
  subscriptionType: string | number | null;
}

/** All key traffic stays inside this page. No real credentials are read, written or sent to a provider. */
async function fixture(page: Page) {
  const state = {
    keys: [{ idProvider: 'configured', apiKey: 'existing-test-key-only', subscriptionType: 'OPEN' }] as Key[],
    writes: [] as { method: string; value: Key | string }[],
    metadataReads: 0,
    listReads: 0,
    pending: null as { path: string; arrived: boolean; gate: Promise<void> } | null
  };
  await page.route(KEY_URL, async (route) => {
    const request = route.request();
    const path = new URL(request.url()).pathname.replace('/api/connectorapikey', '');
    const method = request.method();
    let json: unknown;
    let status = 200;
    if (method === 'GET' && path === '/subscriptiontypeconnector') {
      state.metadataReads++;
      json = PROVIDERS;
    } else if (method === 'GET' && path === '') {
      state.listReads++;
      // Snapshot before a held response, so a late response really contains the old state.
      json = state.keys.map((key) => ({ ...key }));
    } else if ((method === 'PUT' || method === 'POST') && path === '') {
      const key = request.postDataJSON() as Key;
      expect(Object.keys(PROVIDERS)).toContain(key.idProvider);
      state.writes.push({ method, value: key });
      state.keys = [...state.keys.filter((existing) => existing.idProvider !== key.idProvider), key];
      json = key;
    } else if (method === 'DELETE') {
      const provider = decodeURIComponent(path.slice(1));
      expect(Object.keys(PROVIDERS)).toContain(provider);
      state.writes.push({ method, value: provider });
      state.keys = state.keys.filter((key) => key.idProvider !== provider);
      status = 204;
    } else {
      throw new Error(`Unexpected key request: ${method} ${path}`);
    }
    const pending = state.pending;
    if (method === 'GET' && pending?.path === path) {
      state.pending = null;
      pending.arrived = true;
      await pending.gate;
    }
    try {
      await route.fulfill(status === 204 ? { status } : { status, json });
    } catch (error) {
      if (request.failure()?.errorText !== 'net::ERR_ABORTED') throw error;
    }
  });
  return {
    state,
    hold(path: string) {
      let release!: () => void;
      const gate = new Promise<void>((resolve) => (release = resolve));
      const pending = { path, gate, arrived: false };
      state.pending = pending;
      return { pending, release };
    }
  };
}

async function openView(page: Page): Promise<Locator> {
  await page.goto(`${page.url().split('/mainview')[0]}/mainview/connectorapikey`);
  const table = page.locator('configurable-table');
  await expect(table).toBeVisible();
  return table;
}

async function menu(page: Page, target: Locator): Promise<Locator> {
  if (!(await target.getAttribute('class'))?.split(' ').includes('p-datatable-row-selected')) await target.click();
  await target.click({ button: 'right' });
  const actions = page.locator('[role="menu"]:visible');
  await expect(actions).toBeVisible();
  return actions;
}

async function openDialog(page: Page, target: Locator, action = CREATE): Promise<Locator> {
  await (await menu(page, target)).getByRole('menuitem', { name: action }).first().click();
  const dialog = page.locator('connector-api-key-edit .p-dialog');
  await expect(dialog).toBeVisible();
  await expect(dialog.locator('select#idProvider option')).not.toHaveCount(0);
  return dialog;
}

async function close(dialog: Locator): Promise<void> {
  await dialog.locator('.p-dialog-close-button').click();
  await expect(dialog).toHaveCount(0);
}

function row(table: Locator, provider: keyof typeof PROVIDERS): Locator {
  return table.locator('tbody > tr').filter({ hasText: PROVIDERS[provider].readableName });
}

export function connectorApiKeyScenarios(login: (page: Page, nickname: string) => Promise<unknown>): void {
  test.describe('connector API key notification with isolated transport', () => {
    let keys: Awaited<ReturnType<typeof fixture>>;
    test.beforeEach(async ({ page }) => {
      keys = await fixture(page);
      await login(page, 'admin');
    });

    test('delayed provider metadata and rows publish readable names and translated subscriptions', async ({ page }) => {
      const metadata = keys.hold('/subscriptiontypeconnector');
      let rows: ReturnType<typeof keys.hold> | undefined;
      try {
        const table = await openView(page);
        await expect.poll(() => metadata.pending.arrived).toBe(true);
        expect(keys.state.listReads).toBe(0);
        rows = keys.hold('');
        metadata.release();
        await expect.poll(() => rows!.pending.arrived).toBe(true);
        await expect(row(table, 'configured')).toHaveCount(0);
        rows.release();
        const target = row(table, 'configured');
        await expect(target).toHaveCount(1);
        const token = (await page.evaluate(() => sessionStorage.getItem('jwt')))!;
        const nls = await page.request.get('/api/globalparameters/properties/de', {
          headers: { 'x-auth-token': token }
        });
        expect(nls.ok()).toBeTruthy();
        await expect(target.locator('td').last()).toHaveText((await nls.json()).OPEN);
        const dialog = await openDialog(page, target, EDIT);
        await expect(dialog.locator('select#idProvider')).toBeDisabled();
        await expect(dialog.locator('select#subscriptionType')).toHaveValue('OPEN');
        await close(dialog);
      } finally {
        metadata.release();
        rows?.release();
      }
    });

    test('provider switches replace options, visibility and required validation across reopened dialogs', async ({
      page
    }) => {
      const table = await openView(page);
      await expect(row(table, 'configured')).toHaveCount(1);
      for (let opening = 0; opening < 2; opening++) {
        const dialog = await openDialog(page, table.locator('h4'));
        const provider = dialog.locator('select#idProvider');
        const subscription = dialog.locator('select#subscriptionType');
        const save = dialog.locator('button[type="submit"]');
        await expect(provider.locator('option[value="configured"]')).toHaveCount(0);
        await expect(dialog.locator('#apiKey')).toHaveValue('');
        await provider.selectOption('plain');
        await expect(subscription).toBeHidden();
        await dialog.locator('#apiKey').fill('short');
        await expect(save).toBeDisabled();
        await dialog.locator('#apiKey').fill('synthetic-test-key-only');
        await expect(save).toBeEnabled();
        await provider.selectOption('named');
        await expect(subscription).toBeVisible();
        await expect(save).toBeDisabled();
        await subscription.selectOption('OPEN');
        await expect(save).toBeEnabled();
        await provider.selectOption('numeric');
        await expect(subscription.locator('option')).toHaveCount(2);
        await expect(subscription.locator('option[value="1"]')).toHaveCount(1);
        await expect(subscription).not.toHaveValue('OPEN');
        await expect(save).toBeDisabled();
        await subscription.selectOption('2');
        await expect(save).toBeEnabled();
        await provider.selectOption('object');
        await expect(subscription.locator('option[value="17"]')).toHaveCount(1);
        await expect(save).toBeDisabled();
        await subscription.selectOption('17');
        await expect(save).toBeEnabled();
        await provider.selectOption('plain');
        await expect(subscription).toBeHidden();
        await expect(save).toBeEnabled();
        await close(dialog);
      }
      expect(keys.state.writes).toEqual([]);
    });

    test('create, edit, cancel and delete update the table and refresh the selected record after a delayed reload', async ({
      page
    }) => {
      const table = await openView(page);
      await expect(row(table, 'configured')).toHaveCount(1);
      let dialog = await openDialog(page, table.locator('h4'));
      await dialog.locator('select#idProvider').selectOption('named');
      await dialog.locator('select#subscriptionType').selectOption('CLOSED');
      const longKey = `synthetic-${'k'.repeat(180)}`;
      await dialog.locator('#apiKey').fill(longKey);
      await dialog.locator('button[type="submit"]').click();
      await expect(dialog).toHaveCount(0);
      await expect(row(table, 'named')).toContainText(longKey);
      expect(keys.state.writes[0].value).toEqual({ idProvider: 'named', apiKey: longKey, subscriptionType: 'CLOSED' });

      dialog = await openDialog(page, row(table, 'named'), EDIT);
      await expect(dialog.locator('select#idProvider')).toBeDisabled();
      await expect(dialog.locator('select#subscriptionType')).toHaveValue('CLOSED');
      await dialog.locator('#apiKey').fill('changed-synthetic-key');
      await dialog.locator('select#subscriptionType').selectOption('OPEN');
      const pending = keys.hold('');
      try {
        await dialog.locator('button[type="submit"]').click();
        await expect(dialog).toHaveCount(0);
        await expect.poll(() => pending.pending.arrived).toBe(true);
        await expect(row(table, 'named')).toContainText(longKey);
        pending.release();
        await expect(row(table, 'named')).toContainText('changed-synthetic-key');
      } finally {
        pending.release();
      }
      dialog = await openDialog(page, row(table, 'named'), EDIT);
      await expect(dialog.locator('#apiKey')).toHaveValue('changed-synthetic-key');
      await expect(dialog.locator('select#subscriptionType')).toHaveValue('OPEN');
      await dialog.locator('#apiKey').fill('cancelled-draft-key');
      await close(dialog);
      expect(keys.state.writes).toHaveLength(2);
      await expect(row(table, 'named')).toContainText('changed-synthetic-key');

      await (await menu(page, row(table, 'named'))).getByRole('menuitem', { name: DELETE }).click();
      await page
        .locator('[role="alertdialog"]:visible')
        .getByRole('button', { name: /^\s*(yes|ja)\s*$/i })
        .click();
      await expect(row(table, 'named')).toHaveCount(0);
      await expect(row(table, 'configured')).toHaveCount(1);
      expect(keys.state.writes[2]).toEqual({ method: 'DELETE', value: 'named' });
      dialog = await openDialog(page, table.locator('h4'));
      await expect(dialog.locator('select#idProvider option[value="named"]')).toHaveCount(1);
      await close(dialog);
    });

    for (const path of ['/subscriptiontypeconnector', '']) {
      test(`navigation cancels a pending ${path ? 'provider' : 'key list'} request before reopening`, async ({
        page
      }) => {
        const cancelled: string[] = [];
        page.on('requestfailed', (request) => {
          if (request.url().endsWith(`/api/connectorapikey${path}`)) cancelled.push(request.failure()?.errorText ?? '');
        });
        const pending = keys.hold(path);
        try {
          await openView(page);
          await expect.poll(() => pending.pending.arrived).toBe(true);
          const token = (await page.evaluate(() => sessionStorage.getItem('jwt')))!;
          const response = await page.request.get('/api/globalparameters/properties/de', {
            headers: { 'x-auth-token': token }
          });
          expect(response.ok()).toBeTruthy();
          const texts = await response.json();
          // Client-side navigation destroys the component while keeping the browser document alive.
          await page.locator('.p-tree-node-content').filter({ hasText: texts.ENTITY_LIMIT_INFO_CLASS }).click();
          await expect(page).toHaveURL(/\/mainview\/entitylimit$/);
          await expect.poll(() => cancelled).toContain('net::ERR_ABORTED');
          keys.state.keys = [{ idProvider: 'plain', apiKey: 'fresh-session-test-key', subscriptionType: null }];
          await page.locator('.p-tree-node-content').filter({ hasText: texts.CONNECTOR_API_KEY }).click();
          const table = page.locator('configurable-table');
          await expect(row(table, 'plain')).toHaveCount(1);
          pending.release();
          await expect(row(table, 'configured')).toHaveCount(0);
          await expect(row(table, 'plain')).toContainText('fresh-session-test-key');
        } finally {
          pending.release();
        }
      });
    }
  });
}
