import { expect, Locator, Page, test } from '@playwright/test';

import { loginAsFixtureUser } from './helpers';

/**
 * The GTNet security import staging area: `gtnet-security-import-edit-head` and the editable position table
 * beneath it, including its CSV upload.
 *
 * An import set is an ordinary tenant-private staging structure — the list of instruments an instance wants to
 * look up over GTNet — so nothing here needs an own GTNet entry or a peer. The application GTNet menu is gated
 * only by `useGtnet()`.
 *
 * Shape of the screen, which decides every selector below:
 *
 *  - The head is a **non-modal `dynamic-form`** at the top (a select over the existing sets plus a disabled
 *    note), and the CRUD entries live in the context menu of the surrounding container, not in a table.
 *  - The head **dialog** is a modal that uses the non-HeqF field helpers with explicit keys, so it renders
 *    `input#name` (required, maxlength 40) and `textarea#note`.
 *  - The positions are an `editable-table` with **per-row** persistence: the pencil opens a row, the check
 *    saves it through `POST/PUT /api/gtnetsecurityimppos`. A row needs an ISIN or a ticker symbol, and a
 *    currency, or `onRowEditSave` refuses it before the request goes out.
 *  - The CSV upload is the shared `upload-file-dialog`, so the file input is `input[type=file]#fileToUpload`
 *    and the submit button is labelled `UPLOAD`.
 *
 * Bounded by the limits seeded in `V4__seed_entity_limits.sql`: 200 `GTNetSecurityImpHead` per tenant and 200
 * `GTNetSecurityImpPos` per head, so the handful of rows here is far inside the budget.
 *
 * Re-runnable: the import set this spec owns is deleted by name before it is created, which takes its
 * positions with it.
 */

const IMPORT_SET = {
  name: 'E2E GTNet import set',
  note: 'Created by 180-gtnet-security-import.spec.ts',
  positions: [
    { isin: 'GB0002374006', tickerSymbol: 'DGE', currency: 'GBP' },
    { isin: 'FR0000120271', tickerSymbol: 'TTE', currency: 'EUR' }
  ]
} as const;

/** Rows added through the CSV upload rather than through the inline editor. */
const CSV_POSITIONS = [
  { isin: 'DE0007164600', tickerSymbol: 'SAP', currency: 'EUR' },
  { isin: 'NL0011821202', tickerSymbol: 'INGA', currency: 'EUR' }
];

/**
 * An import position names an instrument the instance does **not** have yet — that is what an import set is for —
 * so `saveWithTenantCheck` refuses an ISIN that already exists as a security. None of the ISINs above may therefore
 * belong to a security in `grafioschtrader_t`, and none of them is created by any spec.
 */
const FIXTURE_ISINS = [...IMPORT_SET.positions, ...CSV_POSITIONS].map((position) => position.isin);

const RX = {
  create: /^(Create|Erstellen|CREATE)\s/,
  // UPLOAD_CSV carries no {{i18nRecord}} placeholder, so the entity name the label pairs it with is dropped and
  // the rendered text is just the translation plus the dialog dots - no trailing separator to anchor on.
  uploadCsv: /^(Upload CSV file|Hochladen CSV-Datei|UPLOAD_CSV)/,
  save: /^(Save|Speichern|SAVE)$/,
  upload: /^(Upload|Hochladen|UPLOAD)$/
};

interface ApiImpHead {
  idGtNetSecurityImpHead: number;
  name: string;
  note: string | null;
}

interface ApiImpPos {
  idGtNetSecurityImpPos: number;
  isin: string | null;
  tickerSymbol: string | null;
  currency: string | null;
}

async function authHeaders(page: Page): Promise<{ [key: string]: string }> {
  const token = await page.evaluate(() => sessionStorage.getItem('jwt'));
  expect(token, 'JWT in sessionStorage after login').toBeTruthy();
  return { 'x-auth-token': token! };
}

async function readHeads(page: Page): Promise<ApiImpHead[]> {
  const response = await page.request.get('/api/gtnetsecurityimphead', { headers: await authHeaders(page) });
  expect(response.ok(), `loading import sets: ${response.status()} ${await response.text()}`).toBeTruthy();
  return response.json() as Promise<ApiImpHead[]>;
}

async function readPositions(page: Page, idHead: number): Promise<ApiImpPos[]> {
  const response = await page.request.get(`/api/gtnetsecurityimppos/head/${idHead}`, {
    headers: await authHeaders(page)
  });
  expect(response.ok(), `loading positions: ${response.status()} ${await response.text()}`).toBeTruthy();
  return response.json() as Promise<ApiImpPos[]>;
}

/** Removes the import set this spec owns, so a partial previous run cannot break the retry. */
async function deleteOwnImportSet(page: Page): Promise<void> {
  const headers = await authHeaders(page);
  for (const head of await readHeads(page)) {
    if (head.name === IMPORT_SET.name) {
      // The positions hang off the head and go with it; the head delete refuses nothing at this point.
      for (const position of await readPositions(page, head.idGtNetSecurityImpHead)) {
        const removed = await page.request.delete(`/api/gtnetsecurityimppos/${position.idGtNetSecurityImpPos}`, {
          headers
        });
        expect(removed.ok(), `deleting position ${position.idGtNetSecurityImpPos}`).toBeTruthy();
      }
      const response = await page.request.delete(`/api/gtnetsecurityimphead/${head.idGtNetSecurityImpHead}`, {
        headers
      });
      expect(response.ok(), `deleting import set ${head.idGtNetSecurityImpHead}`).toBeTruthy();
    }
  }
}

/**
 * Fails with a usable message when a fixture ISIN has meanwhile become a real security. Without it the clash
 * surfaces as a localized validation error deep inside the row editor, which says nothing about the fixture.
 */
async function assertFixtureIsinsAreFree(page: Page): Promise<void> {
  const response = await page.request.get('/api/security/gtnetexchange?activeOnly=false', {
    headers: await authHeaders(page)
  });
  expect(response.ok(), `loading securities: ${await response.text()}`).toBeTruthy();
  const securities = ((await response.json()) as { securitiescurrenciesList: { isin: string | null }[] })
    .securitiescurrenciesList;
  const clashing = FIXTURE_ISINS.filter((isin) => securities.some((security) => security.isin === isin));
  expect(
    clashing,
    `these fixture ISINs already exist as securities, so an import position may not name them - pick unused ones: ${clashing.join(', ')}`
  ).toHaveLength(0);
}

async function openImportView(page: Page): Promise<Locator> {
  await page.goto('/mainview/gtnetsecurityimport');
  const container = page.locator('.data-container').first();
  await container.waitFor({ state: 'visible', timeout: 15_000 });
  return container;
}

/**
 * Opens the context menu of the import view.
 *
 * `prepareEditMenu()` is rebuilt in `resetMenu()`, which only runs from `onComponentClick`, so the container
 * has to be clicked before the menu carries the entries of the current selection.
 */
async function openImportMenu(page: Page, container: Locator): Promise<Locator> {
  await container.click();
  await container.click({ button: 'right' });
  const menu = page.locator('[role="menu"]:visible');
  await menu.waitFor({ state: 'visible', timeout: 10_000 });
  return menu;
}

/** Selects an import set in the non-modal head form and waits for its positions to load. */
async function selectImportSet(page: Page, idHead: number): Promise<void> {
  const positionsPromise = page.waitForResponse((response) =>
    response.url().includes(`/api/gtnetsecurityimppos/head/${idHead}`)
  );
  await page.locator('select#idGtNetSecurityImpHead').selectOption(String(idHead));
  await positionsPromise;
}

test.describe.serial('GTNet security import', () => {
  test('creates an import set through the head dialog', async ({ page }) => {
    await loginAsFixtureUser(page, 'admin');
    await deleteOwnImportSet(page);
    await assertFixtureIsinsAreFree(page);

    const container = await openImportView(page);
    const menu = await openImportMenu(page, container);
    await menu.getByRole('menuitem', { name: RX.create }).first().click();

    const dialog = page.locator('.p-dialog:visible').first();
    await dialog.waitFor({ state: 'visible', timeout: 10_000 });
    // Nothing may be typed before initialize() has run: it fires on the dialog's onShow, and its
    // setDefaultValuesAndEnableSubmit() resets every control — a value filled in between is silently wiped and the
    // required Name stays empty, leaving Save disabled. The focus its trailing setTimeout puts on #name is the
    // observable signal that it has finished.
    await expect(dialog.locator('input#name')).toBeFocused({ timeout: 10_000 });
    await dialog.locator('input#name').fill(IMPORT_SET.name);
    await dialog.locator('textarea#note').fill(IMPORT_SET.note);
    await expect(dialog.locator('input#name')).toHaveValue(IMPORT_SET.name);

    const savePromise = page.waitForResponse(
      (response) =>
        response.url().endsWith('/api/gtnetsecurityimphead') && ['POST', 'PUT'].includes(response.request().method())
    );
    await dialog.getByRole('button', { name: RX.save }).click();
    const saveResponse = await savePromise;
    expect(saveResponse.ok(), `${saveResponse.status()} ${await saveResponse.text()}`).toBeTruthy();
    await dialog.waitFor({ state: 'hidden', timeout: 15_000 });

    const created = (await readHeads(page)).find((head) => head.name === IMPORT_SET.name);
    expect(created, `import set '${IMPORT_SET.name}'`).toBeTruthy();
    expect(created!.note).toBe(IMPORT_SET.note);
  });

  test('adds positions through the inline row editor', async ({ page }) => {
    await loginAsFixtureUser(page, 'admin');
    const head = (await readHeads(page)).find((entry) => entry.name === IMPORT_SET.name);
    expect(head, 'the import set created by the previous test').toBeTruthy();

    const container = await openImportView(page);
    await selectImportSet(page, head!.idGtNetSecurityImpHead);

    for (const position of IMPORT_SET.positions) {
      const menu = await openImportMenu(page, container);
      await menu.getByRole('menuitem', { name: RX.create }).last().click();

      // addNewRow() appends the row and opens it for editing, so the new one is the last. EditableTable exposes the
      // configured field name on each control, which stays meaningful if columns are inserted or reordered.
      const editingRow = page.locator('gtnet-security-import-table editable-table tbody tr').last();
      const isinInput = editingRow.locator('input[data-field="isin"]');
      const tickerInput = editingRow.locator('input[data-field="tickerSymbol"]');
      const currencySelect = editingRow.locator('select[data-field="currency"]');
      await expect(isinInput).toBeVisible({ timeout: 10_000 });
      await expect(tickerInput).toBeVisible({ timeout: 10_000 });
      await isinInput.fill(position.isin);
      await expect(isinInput).toHaveValue(position.isin);
      await tickerInput.fill(position.tickerSymbol);
      await currencySelect.selectOption(position.currency);

      await expect(isinInput).toHaveValue(position.isin);
      await expect(tickerInput).toHaveValue(position.tickerSymbol);
      await expect(currencySelect).toHaveValue(position.currency);

      const savePromise = page.waitForResponse(
        (response) =>
          response.url().endsWith('/api/gtnetsecurityimppos') && ['POST', 'PUT'].includes(response.request().method())
      );
      await editingRow.locator('button:has(.pi-check)').first().click();
      const saveResponse = await savePromise;
      expect(saveResponse.ok(), `saving ${position.isin}: ${await saveResponse.text()}`).toBeTruthy();
    }

    const positions = await readPositions(page, head!.idGtNetSecurityImpHead);
    expect(positions.map((entry) => entry.isin).sort()).toEqual(IMPORT_SET.positions.map((entry) => entry.isin).sort());
  });

  test('adds further positions through the CSV upload', async ({ page }) => {
    await loginAsFixtureUser(page, 'admin');
    const head = (await readHeads(page)).find((entry) => entry.name === IMPORT_SET.name);
    expect(head, 'the import set created by the first test').toBeTruthy();
    const before = await readPositions(page, head!.idGtNetSecurityImpHead);

    const container = await openImportView(page);
    await selectImportSet(page, head!.idGtNetSecurityImpHead);
    const menu = await openImportMenu(page, container);
    await menu.getByRole('menuitem', { name: RX.uploadCsv }).first().click();

    const dialog = page.locator('.p-dialog:visible').first();
    await dialog.waitFor({ state: 'visible', timeout: 10_000 });
    // CSVImportHelper.CSV_FIELD_SEPARATOR is ';' — a comma-separated header parses as a single column and the
    // upload is refused with "the field name currency is missing in the header row". The header names are the
    // entity's own field names, matched case-insensitively.
    const csv = ['isin;tickerSymbol;currency', ...CSV_POSITIONS.map((p) => `${p.isin};${p.tickerSymbol};${p.currency}`)]
      .join('\n')
      .concat('\n');
    await dialog.locator('input[type="file"]#fileToUpload').setInputFiles({
      name: 'gtnet-import-positions.csv',
      mimeType: 'text/csv',
      buffer: Buffer.from(csv, 'utf-8')
    });

    const uploadPromise = page.waitForResponse((response) => response.url().includes('/api/gtnetsecurityimppos'));
    await dialog.getByRole('button', { name: RX.upload }).click();
    const uploadResponse = await uploadPromise;
    expect(uploadResponse.ok(), `${uploadResponse.status()} ${await uploadResponse.text()}`).toBeTruthy();
    await dialog.waitFor({ state: 'hidden', timeout: 15_000 });

    const after = await readPositions(page, head!.idGtNetSecurityImpHead);
    expect(after.length, 'the uploaded rows were added to the existing ones').toBe(
      before.length + CSV_POSITIONS.length
    );
    for (const position of CSV_POSITIONS) {
      expect(
        after.some((entry) => entry.isin === position.isin),
        `uploaded ${position.isin}`
      ).toBe(true);
    }
  });

  test('removes the import set again', async ({ page }) => {
    await loginAsFixtureUser(page, 'admin');
    await deleteOwnImportSet(page);
    expect((await readHeads(page)).some((head) => head.name === IMPORT_SET.name)).toBe(false);
  });
});
