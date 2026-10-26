import { expect, Locator, Page, Route, test } from '@playwright/test';
import { loginAsFixtureUser } from './helpers';
import { pickMultiSelect } from './generic-connector.helpers';

/** Real options, translations and CRUD, with selected HTTP answers held back. */
const views = {
  plan: /^\s*(Trading\s*platform\s*plan|Handelsplattform\s*Plan)\s*$/i,
  asset: /^\s*(Asset\s*class|Anlageklasse)\s*$/i,
  exchange: /^\s*(Stock\s*exchange|Handelsplatz)\s*$/i,
  udf: /^\s*(Custom field Instrument|Zusatzfeld Instrument)\s*$/i
};

test.beforeEach(async ({ page }) => {
  page.setDefaultTimeout(15_000);
  await loginAsFixtureUser(page, 'alledit');
});

async function openView(page: Page, label: RegExp): Promise<Locator> {
  const node = page.getByRole('treeitem', { name: label }).locator(':scope > .p-tree-node-content').first();
  if (!(await node.isVisible())) {
    await page
      .locator('.p-tree-node-content')
      .filter({ hasText: /Base Data|Basisdaten/ })
      .first()
      .dblclick();
  }
  if (!(await node.isVisible())) {
    await page
      .locator('.p-tree-node-content')
      .filter({ hasText: /General definition of additional field|Definition Zusatzfeld allgemein/ })
      .first()
      .dblclick();
  }
  await node.click();
  const table = page.locator('configurable-table').first();
  await expect(table.locator('tbody tr').first()).toBeVisible();
  return table;
}

async function openDialog(page: Page, table: Locator, row?: Locator): Promise<Locator> {
  const target = row ?? table.locator('h4').first();
  await target.click();
  await target.click({ button: 'right' });
  await page
    .locator('[role="menu"]:visible')
    .getByRole('menuitem', { name: row ? /^(Edit|Bearbeiten)\s/ : /^(Create|Erstellen)\s/ })
    .first()
    .click();
  const dialog = page.locator('.p-dialog:visible');
  await expect(dialog).toBeVisible();
  return dialog;
}

async function closeDialog(dialog: Locator): Promise<void> {
  await dialog.locator('.p-dialog-close-button').click();
  await expect(dialog).toHaveCount(0);
}

/** A held real response also proves that closing a dialog cancels its outstanding subscription. */
async function holdResponse(
  page: Page,
  url: string
): Promise<{ release: () => void; arrived: Promise<void>; done: Promise<void> }> {
  let release!: () => void;
  let arrived!: () => void;
  let done!: () => void;
  const gate = new Promise<void>((resolve) => (release = resolve));
  const requestArrived = new Promise<void>((resolve) => (arrived = resolve));
  const completed = new Promise<void>((resolve) => (done = resolve));
  let holding = false;
  const handler = async (route: Route): Promise<void> => {
    if (holding || route.request().method() !== 'GET') {
      await route.fallback();
      return;
    }
    holding = true;
    const response = await route.fetch();
    expect(response.ok()).toBeTruthy();
    arrived();
    await gate;
    try {
      await route.fulfill({ response });
    } catch (error) {
      // switchMap and closing the dialog abort the browser request while the held answer is in flight.
      if (route.request().failure()?.errorText !== 'net::ERR_ABORTED') {
        throw error;
      }
    } finally {
      await page.unroute(url, handler);
      done();
    }
  };
  await page.route(url, handler);
  return { release, arrived: requestArrived, done: completed };
}

test('platform-plan options arrive without resetting a draft and the existing plan reopens', async ({ page }) => {
  const table = await openView(page, views.plan);
  for (let opening = 0; opening < 2; opening++) {
    const options = await holdResponse(page, '**/api/importtransactionplatform');
    try {
      const dialog = await openDialog(page, table);
      await options.arrived;
      await dialog.locator('input#en').fill('Unsaved platform name');
      await dialog.locator('input#de').fill('Nicht gespeicherter Name');
      await expect(dialog.locator('button[type="submit"]')).toBeDisabled();
      options.release();
      await expect(dialog.locator('select#idTransactionImportPlatform option')).not.toHaveCount(0);
      await expect(dialog.locator('select#transactionFeePlan option[value="FP_FLAT"]')).not.toHaveText('FP_FLAT');
      await expect(dialog.locator('input#en')).toHaveValue('Unsaved platform name');
      await expect(dialog.locator('input#de')).toHaveValue('Nicht gespeicherter Name');
      await closeDialog(dialog);
    } finally {
      options.release();
    }
  }
  const row = table.locator('tbody tr').filter({ hasText: 'Saxo Trader' }).first();
  const dialog = await openDialog(page, table, row);
  await expect(dialog.locator('input#en')).toHaveValue(/Saxo Trader/);
  await expect(dialog.locator('select#idTransactionImportPlatform option:checked')).not.toHaveAttribute('value', '');
  await closeDialog(dialog);
});

test('platform-plan CRUD refreshes rows and selected menus after delayed responses', async ({ page }) => {
  const name = 'E2E OnPush platform plan';
  const renamed = `${name} updated`;
  const token = await page.evaluate(() => sessionStorage.getItem('jwt'));
  expect(token, 'JWT after login').toBeTruthy();
  const headers = { 'x-auth-token': token!, Accept: 'application/json' };
  // Own both names, including a row left behind by an interrupted create or edit. Shared plans stay intact.
  const existing = await page.request.get('/api/tradingplatformplan', { headers });
  expect(existing.ok()).toBeTruthy();
  for (const plan of await existing.json()) {
    if ([name, renamed].includes(plan.platformPlanNameNLS.map.en)) {
      const deleted = await page.request.delete(`/api/tradingplatformplan/${plan.idTradingPlatformPlan}`, {
        headers
      });
      expect(deleted.ok()).toBeTruthy();
    }
  }

  const table = await openView(page, views.plan);
  const ownRow = () => table.locator('tbody tr').filter({ hasText: name });
  const mainEdit = () =>
    page
      .locator('p-menubar')
      .getByRole('menuitem', { name: /^(Edit|Bearbeiten)$/ })
      .first();
  const editItem = /^(Edit|Bearbeiten)\s+(Trading\s*platform\s*plan|Handelsplattform\s*Plan)/i;
  const feeItem = /Edit fee model|Gebührenmodell bearbeiten/i;

  const dialog = await openDialog(page, table);
  await expect(dialog.locator('select#transactionFeePlan option[value="FP_FLAT"]')).toHaveCount(1);
  await dialog.locator('input#en').fill(name);
  await dialog.locator('input#de').fill(name);
  await dialog.locator('select#transactionFeePlan').selectOption('FP_FLAT');
  const created = await holdResponse(page, '**/api/tradingplatformplan');
  try {
    await dialog.locator('button[type="submit"]').click();
    await created.arrived;
    await expect(dialog).toHaveCount(0);
    await expect(ownRow()).toHaveCount(0);
    created.release();
    await expect(ownRow()).toBeVisible();
  } finally {
    created.release();
  }

  await ownRow().click();
  await expect(ownRow()).toHaveClass(/p-datatable-row-selected/);
  await mainEdit().click();
  await page.locator('p-menubar').getByRole('menuitem', { name: editItem }).click();
  const edit = page.locator('.p-dialog:visible');
  await expect(edit.locator('input#en')).toHaveValue(name);
  await edit.locator('input#en').fill(renamed);
  await edit.locator('input#de').fill(renamed);
  const updated = await holdResponse(page, '**/api/tradingplatformplan');
  try {
    await edit.locator('button[type="submit"]').click();
    await updated.arrived;
    await expect(edit).toHaveCount(0);
    await expect(ownRow()).not.toContainText(renamed);
    updated.release();
    await expect(ownRow()).toContainText(renamed);
    await expect(ownRow()).toHaveClass(/p-datatable-row-selected/);
  } finally {
    updated.release();
  }

  // No row click after refresh: the main menu must reference the reloaded entity.
  await mainEdit().click();
  await page.locator('p-menubar').getByRole('menuitem', { name: editItem }).click();
  await expect(page.locator('.p-dialog:visible input#en')).toHaveValue(renamed);
  await closeDialog(page.locator('.p-dialog:visible'));
  for (let opening = 0; opening < 2; opening++) {
    await ownRow().click({ button: 'right' });
    await page.locator('[role="menu"]:visible').getByRole('menuitem', { name: feeItem }).click();
    const feeDialog = page.locator('.p-dialog:visible');
    await expect(feeDialog.locator('yaml-editor .monaco-editor')).toBeVisible();
    await closeDialog(feeDialog);
  }

  await mainEdit().click();
  await page
    .locator('p-menubar')
    .getByRole('menuitem', { name: /^(Delete|Löschen)\s/ })
    .click();
  const removed = await holdResponse(page, '**/api/tradingplatformplan');
  try {
    await page
      .locator('[role="alertdialog"]:visible')
      .getByRole('button', { name: /^(Yes|Ja)$/ })
      .click();
    await removed.arrived;
    removed.release();
    await expect(ownRow()).toHaveCount(0);
  } finally {
    removed.release();
  }
  await mainEdit().click();
  await expect(page.locator('p-menubar').getByRole('menuitem', { name: feeItem })).toHaveCount(0);
});

test('asset-class mappings load for each dialog session and category changes replace instrument options', async ({
  page
}) => {
  const table = await openView(page, views.asset);
  // End a session while its mapping is still pending, then deliver that obsolete answer before reopening.
  const pending = await holdResponse(page, '**/api/gt_globalparameters/possibleassetclassspezinstrument');
  try {
    const dialog = await openDialog(page, table);
    await pending.arrived;
    await closeDialog(dialog);
    pending.release();
    await pending.done;
  } finally {
    pending.release();
  }
  for (let opening = 0; opening < 2; opening++) {
    const dialog = await openDialog(page, table);
    const category = dialog.locator('select#categoryType');
    await expect(category.locator('option[value="EQUITIES"]')).toHaveCount(1);
    await expect(category.locator('option[value="CURRENCY_CASH"]')).toHaveCount(0);
    await category.selectOption('EQUITIES');
    const instrument = dialog.locator('select#specialInvestmentInstrument');
    await expect(instrument.locator('option[value="DIRECT_INVESTMENT"]')).toHaveCount(1);
    await category.selectOption('FIXED_INCOME');
    await expect(instrument.locator('option[value="CFD"]')).toHaveCount(0);
    await closeDialog(dialog);
  }
  const dialog = await openDialog(page, table, table.locator('tbody tr').first());
  await expect(dialog.locator('.dynamic-form-row:has(label[for="en"]) input')).not.toHaveValue('');
  await closeDialog(dialog);
});

test('stock-exchange drafts survive delayed options and only the latest country supplies indices', async ({ page }) => {
  const table = await openView(page, views.exchange);
  const errors: string[] = [];
  page.on('pageerror', (error) => errors.push(error.message));
  for (let opening = 0; opening < 2; opening++) {
    const timezones = await holdResponse(page, '**/api/globalparameters/timezones');
    let releaseIndices: (() => void) | undefined;
    try {
      const dialog = await openDialog(page, table);
      await timezones.arrived;
      await dialog.locator('input#name').fill('Unsaved exchange name');
      timezones.release();
      await timezones.done;
      await expect(dialog.locator('select#timeZone option[value="Europe/Zurich"]')).toHaveCount(1);
      await expect(dialog.locator('input#name')).toHaveValue('Unsaved exchange name');
      const oldIndices = await holdResponse(page, '**/api/security/search?*stockexchangeCountryCode=CH*');
      releaseIndices = oldIndices.release;
      const country = dialog.locator('select#countryCode');
      await country.selectOption('CH');
      await oldIndices.arrived;
      const latest = page.waitForResponse(
        (response) =>
          new URL(response.url()).pathname === '/api/security/search' &&
          new URL(response.url()).searchParams.get('stockexchangeCountryCode') === 'US'
      );
      await country.selectOption('US');
      const securities = (await (await latest).json()) as { idSecuritycurrency: number; name: string }[];
      expect(securities.length, 'US reference indices in the test database').toBeGreaterThan(0);
      const indices = dialog.locator('select#idIndexUpdCalendar');
      await expect(indices.locator('option')).toHaveCount(securities.length + 1);
      oldIndices.release();
      await oldIndices.done;
      await expect(indices.locator('option')).toHaveCount(securities.length + 1);
      await expect(indices.locator(`option[value="${securities[0].idSecuritycurrency}"]`)).toHaveText(
        securities[0].name
      );

      const ruleSets = dialog.locator('select#idTradingCalendarRuleSet');
      await indices.selectOption(String(securities[0].idSecuritycurrency));
      await expect(ruleSets).toBeDisabled();
      await indices.selectOption('');
      await expect(ruleSets).toBeEnabled();
      await expect(ruleSets.locator('option')).not.toHaveCount(1);
      await ruleSets.selectOption({ index: 1 });
      await expect(indices).toBeDisabled();
      await expect(indices).toHaveValue('');
      await ruleSets.selectOption('');
      await expect(indices).toBeEnabled();

      // Select then clear an unassigned MIC. Clearing must keep the dialog usable and unlock its country.
      await dialog.locator('p-select#mic').click();
      await page.locator('.p-select-overlay .p-select-option').nth(1).click();
      await expect(country).toBeDisabled();
      await dialog.locator('p-select#mic').click();
      await page.locator('.p-select-overlay .p-select-option').first().click();
      await expect(country).toBeEnabled();
      await closeDialog(dialog);
    } finally {
      timezones.release();
      releaseIndices?.();
    }
  }
  const dialog = await openDialog(page, table, table.locator('tbody tr').first());
  await expect(dialog.locator('input#name')).not.toHaveValue('');
  await expect(dialog.locator('select#timeZone option:checked')).not.toHaveAttribute('value', '');
  await closeDialog(dialog);
  expect(errors).toEqual([]);
});

test('the shared asset-class base still filters the security-UDF multi-select on repeated opening', async ({
  page
}) => {
  const table = await openView(page, views.udf);
  for (let opening = 0; opening < 2; opening++) {
    const dialog = await openDialog(page, table);
    await pickMultiSelect(page, dialog, 'categoryTypeEnums', ['EQUITIES']);
    await pickMultiSelect(page, dialog, 'specialInvestmentInstrumentEnums', ['DIRECT_INVESTMENT']);
    await expect(dialog.locator('#specialInvestmentInstrumentEnums')).toContainText(/Direct investment|Direktanlage/);
    await closeDialog(dialog);
  }
});

for (const kind of ['asset', 'exchange'] as const) {
  test(`${kind} table refreshes CRUD, selection and reference-based delete permissions`, async ({ page }) => {
    const name = `E2E OnPush ${kind}`;
    const renamed = `${name} updated`;
    const endpoint = kind === 'asset' ? 'assetclass' : 'stockexchange';
    const idKey = kind === 'asset' ? 'idAssetClass' : 'idStockexchange';
    const label = (row: any): string => (kind === 'asset' ? row.subCategoryNLS.map.en : row.name);
    const token = await page.evaluate(() => sessionStorage.getItem('jwt'));
    const headers = { 'x-auth-token': token!, Accept: 'application/json' };
    const getRows = () =>
      page.request.get(`/api/${endpoint}${kind === 'exchange' ? '?includeNameOfCalendarIndex=false' : ''}`, {
        headers
      });
    const before = await getRows();
    expect(before.ok()).toBeTruthy();
    for (const row of await before.json()) {
      if ([name, renamed].includes(label(row))) {
        const deleted = await page.request.delete(`/api/${endpoint}/${row[idKey]}`, { headers });
        expect(deleted.ok()).toBeTruthy();
      }
    }

    const table = await openView(page, views[kind]);
    const ownRow = () => table.locator('tbody tr').filter({ hasText: name });
    const editItem = /^(Edit|Bearbeiten)\s/;
    const deleteItem = /^(Delete|Löschen)\s/;
    const mainEdit = page
      .locator('p-menubar')
      .getByRole('menuitem', { name: /^(Edit|Bearbeiten)$/ })
      .first();
    const nameInput = (dialog: Locator, language = 'en') =>
      kind === 'asset'
        ? dialog.locator(`.dynamic-form-row:has(label[for="${language}"]) input`).first()
        : dialog.locator('input#name');
    const fillName = async (dialog: Locator, value: string) => {
      for (const language of kind === 'asset' ? ['en', 'de'] : ['en']) {
        const input = nameInput(dialog, language);
        await input.fill(value);
        await input.press('Tab');
      }
    };

    const dialog = await openDialog(page, table);
    if (kind === 'asset') {
      await dialog.locator('select#categoryType').selectOption('EQUITIES');
      await dialog.locator('select#specialInvestmentInstrument').selectOption('DIRECT_INVESTMENT');
    } else {
      await dialog.locator('select#countryCode').selectOption('CH');
      await dialog.locator('input#timeOpen').fill('09:00:00');
      await dialog.locator('input#timeClose').fill('17:00:00');
      await dialog.locator('select#timeZone').selectOption('Europe/Zurich');
    }
    await fillName(dialog, name);
    const created = await holdResponse(
      page,
      `**/api/${endpoint}${kind === 'exchange' ? '?includeNameOfCalendarIndex=true' : ''}`
    );
    try {
      await dialog.locator('button[type="submit"]').click();
      await created.arrived;
      await expect(dialog).toHaveCount(0);
      await expect(ownRow()).toHaveCount(0);
      created.release();
      await expect(ownRow()).toBeVisible();
    } finally {
      created.release();
    }

    const edit = await openDialog(page, table, ownRow());
    await expect(nameInput(edit)).toHaveValue(name);
    await fillName(edit, renamed);
    if (kind === 'exchange') await edit.locator('input#website').fill('https://example.com/onpush');
    const refreshed = await holdResponse(page, `**/api/${endpoint}/hassecurity`);
    try {
      await edit.locator('button[type="submit"]').click();
      await refreshed.arrived;
      await expect(edit).toHaveCount(0);
      await expect(ownRow()).not.toContainText(renamed);
      refreshed.release();
      await expect(ownRow()).toContainText(renamed);
      await expect(ownRow()).toHaveClass(/p-datatable-row-selected/);
    } finally {
      refreshed.release();
    }

    // The retained menu must use the reloaded entity without another row click.
    await mainEdit.click();
    if (kind === 'exchange') {
      await expect(
        page.locator('p-menubar').getByRole('menuitem', { name: /^(Website|Webseite|WEBSITE)$/i })
      ).toBeEnabled();
    }
    await page.locator('p-menubar').getByRole('menuitem', { name: editItem }).click();
    const reopened = page.locator('.p-dialog:visible');
    await expect(nameInput(reopened)).toHaveValue(renamed);
    if (kind === 'asset') await expect(reopened.locator('select#categoryType')).toBeEnabled();
    else await expect(reopened.locator('input#website')).toHaveValue('https://example.com/onpush');
    await closeDialog(reopened);
    await mainEdit.click();
    const deletion = page.locator('p-menubar').getByRole('menuitem', { name: deleteItem });
    await expect(deletion).toBeEnabled();
    await deletion.click();
    await page
      .locator('[role="alertdialog"]:visible')
      .getByRole('button', { name: /^(Yes|Ja)$/ })
      .click();
    await expect(ownRow()).toHaveCount(0);

    const recordsResponse = await getRows();
    const referencesResponse = await page.request.get(`/api/${endpoint}/hassecurity`, { headers });
    expect(recordsResponse.ok()).toBeTruthy();
    expect(referencesResponse.ok()).toBeTruthy();
    const records: any[] = await recordsResponse.json();
    const references = new Map<number, number>(
      (await referencesResponse.json()).map((r: any) => (kind === 'asset' ? r : [r.id, r.s]))
    );
    const referenced = records.find(
      (r) => references.get(r[idKey])! > 0 && records.filter((x) => label(x) === label(r)).length === 1
    );
    expect(referenced, 'a uniquely named reference fixture').toBeTruthy();
    const row = table
      .locator('tbody tr')
      .filter({ has: page.getByRole('cell', { name: label(referenced), exact: true }) });
    await row.click();
    await mainEdit.click();
    await expect(page.locator('p-menubar').getByRole('menuitem', { name: deleteItem })).toHaveAttribute(
      'aria-disabled',
      'true'
    );
    await page.locator('p-menubar').getByRole('menuitem', { name: editItem }).click();
    const locked = page.locator('.p-dialog:visible');
    await expect(locked.locator(kind === 'asset' ? 'select#categoryType' : 'select#timeZone')).toBeDisabled();
    await closeDialog(locked);
  });
}
