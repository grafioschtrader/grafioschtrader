import {expect, Locator, Page, test} from '@playwright/test';
import * as fs from 'fs';
import * as path from 'path';
import {loginAsFixtureUser, parseCsvRow} from './helpers';
import {fillText, openContextMenu, selectByValue} from './generic-connector.helpers';

/**
 * Creates the connector API keys in grafioschtrader_t through the admin UI, as the admin user.
 *
 * This spec replaces the former Flyway migration V3__seed_connector_apikey, which injected rows
 * straight into the table. Going through the dialog means the keys are stored the way the
 * application stores them (ConnectorApiKey.setApiKey() encrypts with Jasypt), and the create
 * workflow itself is covered.
 *
 * Its fixture is local-seed/connector_apikey.csv — pipe-delimited
 * `idProvider|apiKey|subscriptionType` with PLAINTEXT keys, because the dialog submits a plain value
 * that the backend then encrypts. The file is git-ignored for exactly that reason and is produced by
 * a maintainer from their own database with:
 *
 *   mvn -pl grafioschtrader-server test -Dtest=ConnectorApiKeyCsvExportTest -Dgt.export.apikeys=true
 *
 * Without the file every test here skips — the normal state for contributors and CI. The API-key
 * connector tests (FinnhubConnectorTest, EodHistoricalDataConnectorTest, StockDataFeedConnectorTest,
 * FredFeedConnectorTest) then skip in turn via assumeTrue(isActivated()).
 *
 * Numbered 005 so it runs before every other spec (workers: 1, alphabetical order): every later spec
 * benefits from configured connectors. The full price update is no longer queued at backend startup
 * either — the `e2e` profile switches `ExecuteStartupTask` off with
 * `gt.startup.price.update.task=false`, and `100-schedule-batch-jobs.spec.ts` schedules it once these
 * keys exist.
 *
 * The admin user runs a German UI (locale de-CH in users.json), so every text selector matches DE, EN
 * and the raw NLS key — same convention as generic-connector.helpers.ts.
 */

const ADMIN = 'admin';

const CSV_PATH = path.resolve(__dirname,
  '../../backend/grafioschtrader-server/src/test/resources/local-seed/connector_apikey.csv');

const API_KEY_NODE_RX = /(Connector-API-Key|Verbindungs-API-Key|CONNECTOR_API_KEY)/i;
const ADMIN_DATA_NODE_RX = /(Administrative data|Administrative Daten|ADMIN_DATA)/i;
const CREATE_RX = /(Create|Erstellen)/i;
const DELETE_RX = /(Delete|Löschen)/i;
const CONFIRM_YES_RX = /^\s*(yes|ja)\s*$/i;

interface ApiKeyRow {
  idProvider: string;
  apiKey: string;
  subscriptionType: string;
}

/** Loads the git-ignored fixture; an empty array when it was never exported. */
function loadApiKeys(): ApiKeyRow[] {
  if (!fs.existsSync(CSV_PATH)) {
    return [];
  }
  return fs.readFileSync(CSV_PATH, 'utf-8')
    .split(/\r?\n/)
    .filter(line => line.trim().length > 0)
    .map(line => {
      const [idProvider, apiKey, subscriptionType] = parseCsvRow(line);
      return {idProvider, apiKey, subscriptionType: subscriptionType ?? ''};
    });
}

/** Navigates to the Connector-API-Key view under the 'Administrative data' root and waits for the table. */
async function openConnectorApiKeyView(page: Page): Promise<void> {
  const node = page.locator('.p-tree-node-content', {hasText: API_KEY_NODE_RX}).first();
  if (!await node.isVisible()) {
    // The root is created expanded, but expand it explicitly when a previous state collapsed it.
    const root = page.locator('.p-tree-node-content', {hasText: ADMIN_DATA_NODE_RX}).first();
    await root.waitFor({state: 'visible', timeout: 15_000});
    await root.dblclick();
  }
  await node.waitFor({state: 'visible', timeout: 15_000});
  await node.click();

  await page.locator('.data-container').first().waitFor({state: 'visible', timeout: 15_000});
  // Let readData() populate the table before the caller counts rows.
  await page.waitForTimeout(800);
}

/** All data rows of the API-key table; the table renders no placeholder row when empty. */
function apiKeyRows(page: Page): Locator {
  return page.locator('.data-container tbody tr');
}

/** Opens the row context menu for the given row (pContextMenuRow selects the row on right-click). */
async function openRowMenu(page: Page, row: Locator): Promise<Locator> {
  await row.click();
  await page.waitForTimeout(200);
  await row.click({button: 'right'});
  const menu = page.locator('[role="menu"]:visible');
  await menu.waitFor({state: 'visible', timeout: 5_000});
  return menu;
}

/**
 * Confirms the PrimeNG confirm dialog. Several p-dialog hosts with role="alertdialog" are present in
 * the DOM at all times (the tree's and the table's), so the visible one must be selected explicitly —
 * an unfiltered .first() resolves to a permanently hidden host.
 */
async function confirmDialogAccept(page: Page): Promise<void> {
  const confirmDialog = page.locator('[role="alertdialog"]:visible').first();
  await confirmDialog.waitFor({state: 'visible', timeout: 10_000});
  await confirmDialog.getByRole('button', {name: CONFIRM_YES_RX}).first().click();
}

test.describe.serial('connector API keys — created through the admin UI from the local CSV fixture', () => {
  const apiKeys = loadApiKeys();

  test('removes pre-existing connector API keys', async ({page}) => {
    test.skip(apiKeys.length === 0, `no API-key fixture at ${CSV_PATH}`);
    await loginAsFixtureUser(page, ADMIN);
    await openConnectorApiKeyView(page);

    // Delete every row rather than only the CSV providers: the create dialog offers just the
    // providers that are NOT configured yet, so a leftover row would make its provider
    // unselectable. Starting from an empty table is what makes this spec re-runnable.
    const rows = apiKeyRows(page);
    for (let guard = 0; guard < 40; guard++) {
      const count = await rows.count();
      if (count === 0) {
        break;
      }
      const menu = await openRowMenu(page, rows.first());
      const deleted = page.waitForResponse(r =>
        r.url().includes('/connectorapikey') && r.request().method() === 'DELETE' && r.ok(),
        {timeout: 20_000});
      await menu.getByText(DELETE_RX).first().click();
      await confirmDialogAccept(page);
      await deleted;
      await expect(rows).toHaveCount(count - 1, {timeout: 10_000});
    }
    await expect(rows).toHaveCount(0);
  });

  test('creates every connector API key from the CSV fixture', async ({page}) => {
    test.skip(apiKeys.length === 0, `no API-key fixture at ${CSV_PATH}`);
    test.setTimeout(180_000);
    await loginAsFixtureUser(page, ADMIN);
    await openConnectorApiKeyView(page);

    // Provider id -> readable name as offered by the dialog; used for the table assertions below,
    // because the table renders the readable name and not the raw provider id.
    const readableNames = new Map<string, string>();

    for (const row of apiKeys) {
      const menu = await openContextMenu(page);
      await menu.getByText(CREATE_RX).first().click();

      const dialog = page.locator('.p-dialog');
      await dialog.waitFor({state: 'visible', timeout: 10_000});
      await expect(dialog.locator('select#idProvider')).toBeEnabled({timeout: 10_000});

      const option = dialog.locator(`select#idProvider option[value="${row.idProvider}"]`).first();
      await expect(option, `provider '${row.idProvider}' is not offered — is it already configured, `
        + 'or unknown to this build?').toHaveCount(1);
      readableNames.set(row.idProvider, (await option.textContent() ?? '').trim());

      await selectByValue(dialog, 'idProvider', row.idProvider);
      await page.waitForTimeout(300);

      // Providers without subscription levels hide the select and clear its validator.
      const subscriptionSelect = dialog.locator('select#subscriptionType');
      if (row.subscriptionType && await subscriptionSelect.isVisible()) {
        await selectByValue(dialog, 'subscriptionType', row.subscriptionType);
      }

      await fillText(dialog, 'input#apiKey', row.apiKey);
      await expect(dialog.locator('button[type="submit"]')).toBeEnabled();

      const saved = page.waitForResponse(r =>
        r.url().includes('/connectorapikey') && ['POST', 'PUT'].includes(r.request().method()) && r.ok(),
        {timeout: 20_000});
      await dialog.locator('button[type="submit"]').click();
      await saved;
      await dialog.waitFor({state: 'hidden', timeout: 15_000});
      await page.waitForTimeout(500);
    }

    const rows = apiKeyRows(page);
    await expect(rows).toHaveCount(apiKeys.length, {timeout: 15_000});

    // The table shows what the backend returned, i.e. the key after an encrypt/decrypt round trip.
    // Asserting it equals the CSV plaintext proves the key was stored usably, not just accepted.
    for (const row of apiKeys) {
      const tableRow = rows.filter({hasText: readableNames.get(row.idProvider) as string}).first();
      await expect(tableRow, `no table row for provider '${row.idProvider}'`).toHaveCount(1);
      await expect(tableRow).toContainText(row.apiKey);
    }
  });
});
