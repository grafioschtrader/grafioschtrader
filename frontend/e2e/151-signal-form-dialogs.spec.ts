import { expect, Locator, Page, test } from '@playwright/test';
import { loginAsFixtureUser } from './helpers';
import { openGenericConnectorView, openContextMenu } from './generic-connector.helpers';
import { openSecurityAccount, openTransactionImport } from './import-transaction.helpers';

// All writes in this file are intercepted. The real routes, dialogs, translations and form definitions are used;
// no test creates a database row or needs data left by an earlier run.
const hiddenBindingWarnings = new WeakMap<Page, string[]>();
test.beforeEach(({ page }) => {
  page.setDefaultTimeout(15_000);
  const warnings: string[] = [];
  hiddenBindingWarnings.set(page, warnings);
  page.on('console', (message) => {
    if (message.text().includes('NG01916')) warnings.push(message.text());
  });
});
test.afterEach(({ page }) => {
  expect(hiddenBindingWarnings.get(page), 'hidden fields must have no Signal Forms binding').toEqual([]);
});

async function menuAction(page: Page, target: Locator, action: RegExp): Promise<void> {
  await target.click();
  await target.click({ button: 'right' });
  await page.locator('[role="menu"]:visible').getByRole('menuitem', { name: action }).first().click();
}

async function close(dialog: Locator): Promise<void> {
  await dialog.locator('.p-dialog-close-button').click();
  await expect(dialog).toHaveCount(0);
}

test('general UDF group validation blocks invalid sizes and clears a fixed size on transfer', async ({ page }) => {
  await loginAsFixtureUser(page, 'admin');
  await page.goto('/mainview/udfmetadatageneral');
  const table = page.locator('udf-metadata-general-table configurable-table');
  await menuAction(page, table.locator('h4'), /^(Create|Erstellen)\b/);
  const dialog = page.locator('udf-metadata-general-edit .p-dialog');
  await expect(dialog.locator('signal-dynamic-form')).toBeVisible();
  await dialog.locator('#description').fill('Signal group validation');
  await dialog.locator('#uiOrder').selectOption({ index: 0 });
  const entity = dialog.locator('#entity');
  if (await entity.isEnabled()) {
    const key = await entity
      .locator('option')
      .evaluateAll((options) => (options as HTMLOptionElement[]).find((option) => option.value)?.value);
    expect(key).toBeTruthy();
    await entity.selectOption(key!);
  }
  await dialog.locator('#udfDataType').selectOption('UDF_String');
  await dialog.locator('#fieldSize').fill('invalid');
  await dialog.locator('#fieldSize').blur();
  const groupError = dialog.locator('signal-error-message .alert-danger');
  await expect(groupError).toBeVisible();
  await expect(dialog.locator('button[type="submit"]')).toBeDisabled();
  const limits = await page.evaluate(
    () =>
      JSON.parse(sessionStorage.getItem('udfConfig')!).uDFPrefixSuffixMap.UDF_String as {
        prefix: number;
        suffix: number;
      }
  );
  const size = `${Math.max(limits.prefix, 1)},${limits.suffix}`;
  await dialog.locator('#fieldSize').fill(size);
  await expect(groupError).toHaveCount(0);
  await expect(dialog.locator('button[type="submit"]')).toBeEnabled();
  await dialog.locator('#udfDataType').selectOption('UDF_Boolean');
  await expect(dialog.locator('#fieldSize')).toBeDisabled();
  await expect(dialog.locator('#fieldSize')).toHaveValue(size);
  await dialog.locator('#udfDataType').selectOption('UDF_String');
  await expect(dialog.locator('#fieldSize')).toHaveValue(size);
  await dialog.locator('#udfDataType').selectOption('UDF_Boolean');
  let saved: Record<string, unknown> | undefined;
  await page.route('**/api/udfmetadatageneral', async (route) => {
    if (route.request().method() !== 'POST') return route.fallback();
    saved = route.request().postDataJSON();
    await route.fulfill({ json: { ...saved, idUDFMetadata: 990151 } });
  });
  await dialog.locator('button[type="submit"]').click();
  await expect(dialog).toHaveCount(0);
  expect(saved).toMatchObject({ description: 'Signal group validation', udfDataType: 'UDF_Boolean', fieldSize: null });
});

const asset = {
  idAssetClass: 990151,
  categoryType: 'EQUITIES',
  specialInvestmentInstrument: 'DIRECT_INVESTMENT',
  subCategoryNLS: { map: { en: 'Signal asset', de: 'Signal Anlage' } },
  createdBy: 999999,
  creationTime: '2026-01-01T00:00:00',
  lastModifiedTime: '2026-01-01T00:00:00'
};

async function mockAssets(page: Page): Promise<void> {
  await page.route('**/api/assetclass', (route) =>
    route.request().method() === 'GET' ? route.fulfill({ json: [asset] }) : route.fallback()
  );
  await page.route('**/api/assetclass/hassecurity', (route) => route.fulfill({ json: [] }));
  await page.route('**/api/assetclass/990151/hassecurity', (route) => route.fulfill({ json: false }));
}

test('asset suggestions refresh through FIELD_DISPLAY and direct editing hides the audit notes', async ({ page }) => {
  await loginAsFixtureUser(page, 'alledit');
  await mockAssets(page);
  await page.goto('/mainview/assetclass');
  const table = page.locator('configurable-table').first();
  await menuAction(page, table.locator('h4'), /^(Create|Erstellen)\b/);
  const dialog = page.locator('assetclass-edit .p-dialog');
  await expect(dialog.locator('signal-dynamic-form')).toBeVisible();
  await expect(dialog.locator('#noteRequest')).toHaveCount(0);
  await expect(dialog.locator('#noteAcceptReject')).toHaveCount(0);
  const input = dialog.locator('.dynamic-form-row:has(label[for="en"]) input');
  await input.fill('Sig');
  const suggestion = page.getByRole('option', { name: 'Signal asset', exact: true });
  await expect(suggestion).toBeVisible();
  await suggestion.click();
  await expect(input).toHaveValue('Signal asset');
  await close(dialog);
});

test('a change request sends its editable note and hides the review note', async ({ page }) => {
  await loginAsFixtureUser(page, 'limit1');
  await mockAssets(page);
  await page.goto('/mainview/assetclass');
  const table = page.locator('configurable-table').first();
  await menuAction(page, table.locator('tbody tr').filter({ hasText: 'Signal' }).first(), /^(Edit|Bearbeiten)\b/);
  const dialog = page.locator('assetclass-edit .p-dialog');
  await expect(dialog.locator('#noteRequest')).toBeEnabled();
  await expect(dialog.locator('#noteAcceptReject')).toHaveCount(0);
  await dialog.locator('#noteRequest').fill('Signal request note');
  let saved: Record<string, unknown> | undefined;
  await page.route('**/api/assetclass', async (route) => {
    if (route.request().method() !== 'PUT') return route.fallback();
    saved = route.request().postDataJSON();
    await route.fulfill({ json: saved });
  });
  await dialog.getByRole('button', { name: /Save data change request|Daten.nderungsantrag speichern/ }).click();
  await expect(dialog).toHaveCount(0);
  expect(saved).toMatchObject({ noteRequestOrReject: 'Signal request note' });
});

test('reviewing a proposal locks the request note and transfers an acceptance note', async ({ page }) => {
  await loginAsFixtureUser(page, 'alledit');
  await mockAssets(page);
  const proposal = {
    entity: asset,
    proposedEntity: {
      ...asset,
      idProposeRequest: 990151,
      subCategoryNLS: { map: { en: 'Proposed signal asset', de: 'Signal Anlage' } }
    },
    proposeChangeEntity: {
      idProposeRequest: 990151,
      entity: 'Assetclass',
      idEntity: asset.idAssetClass,
      idOwnerEntity: asset.createdBy,
      dataChangeState: 'OPEN',
      noteRequest: 'Signal proposal note',
      createdBy: 999998,
      creationTime: '2026-01-01T00:00:00',
      proposeChangeFieldList: []
    }
  };
  await page.route('**/api/proposechangeentity/withentity', (route) => route.fulfill({ json: [proposal] }));
  await page.goto('/mainview/proposeChangeTabMenu/proposerequestforyou');
  const row = page.locator('configurable-table tbody tr').filter({ hasText: 'Signal proposal note' });
  await menuAction(page, row, /^(Edit|Bearbeiten)\b/);
  const dialog = page.locator('assetclass-edit .p-dialog');
  await expect(dialog.locator('#noteRequest')).toHaveValue('Signal proposal note');
  await expect(dialog.locator('#noteRequest')).toBeDisabled();
  await expect(dialog.locator('#noteAcceptReject')).toBeEnabled();
  await expect(dialog.locator('button[type="submit"]')).toBeEnabled();
  await expect(dialog.getByRole('button', { name: /Reject|ablehnen/i })).toBeVisible();
  await dialog.locator('#noteAcceptReject').fill('Signal acceptance note');
  await expect(dialog.locator('button[type="submit"]')).toBeEnabled();
  let saved: Record<string, unknown> | undefined;
  await page.route('**/api/assetclass', async (route) => {
    if (route.request().method() !== 'PUT') return route.fallback();
    saved = route.request().postDataJSON();
    await route.fulfill({ json: saved });
  });
  await dialog.locator('button[type="submit"]').click();
  await expect(dialog).toHaveCount(0);
  expect(saved).toMatchObject({ idProposeRequest: 990151, noteRequestOrReject: 'Signal acceptance note' });
});

test('GTNet fieldsets enforce the FieldConfig minimum, maximum and maximum length', async ({ page }) => {
  await loginAsFixtureUser(page, 'admin');
  await page.route('**/api/gtnet/gtnetwithmessage', async (route) => {
    const response = await route.fetch();
    await route.fulfill({ json: { ...(await response.json()), gtNetList: [], gtNetMyEntryId: null } });
  });
  await page.goto('/mainview/gtnettabmenu/gtnetsetup');
  const table = page.locator('configurable-table').first();
  await menuAction(page, table.locator('h4').first(), /^(Create|Erstellen)\b/);
  const dialog = page.locator('gtnet-edit .p-dialog');
  const fieldset = dialog.locator('signal-dynamic-form fieldset');
  await expect(fieldset.locator('legend')).not.toBeEmpty();
  const domain = fieldset.locator('#domainRemoteName');
  await expect(domain).toHaveAttribute('maxlength', '128');
  await domain.fill('https://example.com');
  await expect(dialog.locator('#timeZone option[value="UTC"]')).toHaveCount(1);
  await dialog.locator('#timeZone').selectOption('UTC');
  const limit = fieldset.locator('#dailyRequestLimit');
  const save = dialog.getByRole('button', { name: /Save|Speichern/ });
  await limit.fill('-1');
  await expect(save).toBeDisabled();
  await limit.fill('10000');
  await expect(save).toBeDisabled();
  await limit.fill('1234');
  await expect(save).toBeEnabled();
  await close(dialog);
});

test('the signal file input enables upload and permits retry after a failed request', async ({ page }) => {
  await loginAsFixtureUser(page, 'admin');
  await page.goto('/mainview/gtnettabmenu/gtnetsetup');
  const table = page.locator('configurable-table').first();
  await table.locator('h4').first().click();
  await page
    .getByRole('menuitem', { name: /^(View|Ansicht)$/ })
    .first()
    .click();
  await page
    .getByRole('menuitem', { name: /Import GTNet data|GTNet-Daten importieren/ })
    .first()
    .click();
  const dialog = page.locator('upload-file-dialog .p-dialog');
  const submit = dialog.locator('button[type="submit"]');
  await expect(submit).toBeDisabled();
  await dialog
    .locator('input[type="file"]')
    .setInputFiles({ name: 'signal-form.sql', mimeType: 'text/plain', buffer: Buffer.from('-- signal form') });
  await expect(submit).toBeEnabled();
  let attempts = 0;
  await page.route('**/api/gtnetdataexport/import', async (route) => {
    attempts++;
    expect(route.request().headers()['content-type']).toContain('multipart/form-data');
    await route.fulfill(attempts === 1 ? { status: 500, json: { message: 'Signal upload retry' } } : { json: {} });
  });
  await submit.click();
  await expect(submit).toBeEnabled();
  await submit.click();
  await expect(dialog).toHaveCount(0);
  expect(attempts).toBe(2);
});

test('the instrument button opens its search and a calendar parse error disappears after correction', async ({
  page
}) => {
  await loginAsFixtureUser(page, 'alledit');
  await page.goto('/mainview/bankruptsecurity');
  const table = page.locator('configurable-table').first();
  await menuAction(page, table.locator('h4').first(), /^(Create|Erstellen)\b/);
  const dialog = page.locator('bankrupt-security-edit .p-dialog');
  const input = dialog.locator('#noDataSince input');
  await input.pressSequentially('invalid');
  await input.blur();
  await expect(dialog.locator('signal-error-message .alert-danger')).toBeVisible();
  await input.press('Control+a');
  await input.press('Backspace');
  await input.pressSequentially('20.03.23');
  await input.blur();
  await expect(dialog.locator('signal-error-message .alert-danger')).toHaveCount(0);
  await dialog.locator('p-inputgroup button').click();
  const search = page.locator('.p-dialog').filter({ has: page.locator('securitycurrency-search-and-set') });
  await expect(search).toBeVisible();
  await close(search);
  await close(dialog);
});

test('connector inclusion and exclusion trees follow the selected geography', async ({ page }) => {
  await loginAsFixtureUser(page, 'admin');
  await openGenericConnectorView(page);
  const menu = await openContextMenu(page);
  await menu.getByRole('menuitem', { name: /^(Create|Erstellen).*Connector/i }).click();
  const dialog = page.locator('generic-connector-def-edit .p-dialog');
  const inclusions = dialog.locator('#geoInclusions');
  await expect(inclusions).toBeVisible();
  await inclusions.click();
  const overlay = page.locator('.p-treeselect-overlay:visible');
  const country = overlay.locator('.p-tree-node-content').first();
  const label = await country.locator('.p-tree-node-label').innerText();
  await country.locator('.p-checkbox').click();
  await inclusions.click();
  await expect(inclusions).toContainText(label);
  const exclusions = dialog.locator('#geoExclusions');
  await exclusions.click();
  const excludedCountry = page.locator('.p-treeselect-overlay:visible .p-tree-node-content').first();
  await expect(excludedCountry).toContainText(label);
  await excludedCountry.locator('.p-checkbox').click();
  await exclusions.click();
  await expect(exclusions).toContainText(label);
  // Removing an inclusion removes its dependent exclusions as well.
  await inclusions.locator('.p-treeselect-clear-icon').click();
  await expect(inclusions.locator('.p-treeselect-label')).not.toContainText(label);
  await expect(exclusions.locator('.p-treeselect-label')).not.toContainText(label);
  if (await page.locator('.p-treeselect-overlay:visible').count()) await inclusions.click();
  await close(dialog);
});

test('the securities-account tri-state cycles through false, true and unset', async ({ page }) => {
  await loginAsFixtureUser(page, 'admin');
  const token = await page.evaluate(() => sessionStorage.getItem('jwt'));
  const response = await page.request.get('/api/portfolio/tenant', { headers: { 'x-auth-token': token! } });
  expect(response.ok()).toBeTruthy();
  const portfolios = (await response.json()) as { name: string; currency: string }[];
  expect(portfolios.length).toBeGreaterThan(0);
  const portfolio = portfolios[0];
  const node = page.getByRole('treeitem', { name: `${portfolio.name} / ${portfolio.currency}`, exact: true });
  if ((await node.getAttribute('aria-expanded')) !== 'true')
    await node.locator(':scope > .p-tree-node-content').dblclick();
  const folder = node
    .getByRole('treeitem', { name: /^(Securities\s+accounts|Depots)$/ })
    .locator(':scope > .p-tree-node-content');
  await menuAction(page, folder, /Create\s+Securities\s*account|Erstellen\s+Depot/i);
  const dialog = page.locator('.p-dialog:visible');
  const tri = dialog.locator('#taxExemptInvestor .tri-state-checkbox');
  await expect(tri).toHaveText('-');
  await tri.click();
  await expect(tri).toHaveAttribute('data-state', 'false');
  await tri.press('Space');
  await expect(tri).toHaveAttribute('data-state', 'true');
  await tri.press('Enter');
  await expect(tri).toHaveText('-');
  await dialog.locator('.p-dialog-close-button').click();
  await expect(dialog).toHaveCount(0);
});

test('CSV upload transfers a disabled template choice without a reactive control', async ({ page }) => {
  await loginAsFixtureUser(page, 'limit1');
  const accountId = await openSecurityAccount(page, { portfolioName: 'Migros', securityAccountName: 'Migros' });
  await page.route(`**/api/importtransactionhead/securityaccount/${accountId}`, (route) =>
    route.fulfill({
      json: [
        {
          idTransactionHead: 990151,
          name: 'Signal CSV upload',
          useGtPlatform: true,
          securityaccount: { idSecuritycashAccount: accountId }
        }
      ]
    })
  );
  await page.route('**/api/importtransactionpos/importtransactionhead/990151', (route) => route.fulfill({ json: [] }));
  let templateKey: string | undefined;
  await page.route('**/api/**/csv/*', async (route) => {
    const response = await route.fetch();
    expect(response.ok()).toBeTruthy();
    const options = (await response.json()) as { key: number | string; value: string }[];
    const option = options.find((option) => /deutsch/i.test(option.value));
    expect(option).toBeTruthy();
    templateKey = String(option!.key);
    await route.fulfill({ json: [option] });
  });
  await openTransactionImport(page);
  await page.locator('select#idTransactionHead').selectOption('990151');
  const container = page
    .locator('securityaccount-import-transaction-table')
    .locator('xpath=ancestor::div[contains(@class,"data-container")]')
    .first();
  await container.click();
  await container.click({ button: 'right' });
  await page
    .locator('[role="menu"]:visible')
    .getByRole('menuitem', { name: /Upload CSV file|Hochladen CSV-Datei/i })
    .click();
  const dialog = page.locator('upload-file-dialog .p-dialog');
  await expect(dialog.locator('#idTransactionImportTemplate')).toBeDisabled();
  await expect(dialog.locator('#idTransactionImportTemplate')).toHaveValue(templateKey!);
  await dialog
    .locator('input[type="file"]')
    .setInputFiles({ name: 'signal-form.csv', mimeType: 'text/csv', buffer: Buffer.from('fixture') });
  let uploaded = false;
  await page.route('**/api/importtransactionhead/990151/uploadtransaction', async (route) => {
    const body = route.request().postDataBuffer()!.toString();
    expect(body.match(/name="idTransactionImportTemplate"\r?\n\r?\n([^\r\n]+)/)?.[1]).toBe(templateKey);
    uploaded = true;
    await route.fulfill({ status: 204 });
  });
  await dialog.locator('button[type="submit"]').click();
  await expect(dialog).toHaveCount(0);
  expect(uploaded).toBe(true);
});
