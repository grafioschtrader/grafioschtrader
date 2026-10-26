import { expect, Locator, Page, test } from '@playwright/test';
import * as fs from 'fs';
import * as path from 'path';
import { loginAsFixtureUser } from './helpers';
import { holdResponse } from './response.helpers';
import { exchangeRateRow, nodeRow, openExchangeRateTable, openTaxDataView, RX } from './taxdata.helpers';

const yaml = fs.readFileSync(
  path.resolve(
    __dirname,
    '../../backend/grafioschtrader-server/src/test/resources/testdata/tax-model-notification.yaml'
  ),
  'utf8'
);
test.use({ viewport: { width: 1500, height: 1600 }, actionTimeout: 15_000 });
test.beforeEach(async ({ page }) => {
  await loginAsFixtureUser(page, 'admin');
});

async function modelDialog(page: Page, container: Locator) {
  await container.locator('.p-treetable-tbody > tr').first().click({ button: 'right' });
  await page
    .locator('[role="menu"]:visible')
    .getByRole('menuitem', { name: /Tax model|Steuermodell/i })
    .click();
  const dialog = page.locator('tax-model-edit .p-dialog');
  await expect(dialog.locator('.monaco-editor')).toBeVisible();
  return dialog;
}

async function setYaml(page: Page, value: string) {
  await page.evaluate((text) => {
    const models = (window as any).monaco.editor.getModels();
    models[models.length - 1].setValue(text);
  }, value);
}

async function expectYaml(page: Page, value: string) {
  await expect
    .poll(() =>
      page.evaluate(() => {
        const models = (window as any).monaco.editor.getModels();
        return models[models.length - 1]?.getValue();
      })
    )
    .toBe(value);
}

test('delayed tree and country options render, including a closed and reopened create dialog', async ({ page }) => {
  const tree = await holdResponse(page, '**/api/taxdata/tree');
  try {
    const container = await openTaxDataView(page);
    await tree.arrived;
    tree.release();
    await expect(container.locator('.p-treetable-tbody > tr').first()).toBeVisible();
    const countries = await holdResponse(page, '**/api/globalparameters/countries');
    try {
      // Clicking the blank tree area clears no row selection; this fresh view has none.
      await container.locator('configurable-tree-table thead').click({ button: 'right' });
      await page.locator('[role="menu"]:visible').getByRole('menuitem', { name: RX.createCountry }).click();
      const abandoned = page.locator('p-dynamicdialog [role="dialog"]');
      await countries.arrived;
      await page.keyboard.press('Escape');
      await expect(abandoned).toHaveCount(0);
      await container.locator('configurable-tree-table thead').click({ button: 'right' });
      await page.locator('[role="menu"]:visible').getByRole('menuitem', { name: RX.createCountry }).click();
      const reopened = page.locator('p-dynamicdialog [role="dialog"]');
      await reopened.locator('p-select#countryCode').click();
      await expect(page.getByRole('option').first()).toBeVisible();
      countries.release();
      await countries.done;
      await page.getByRole('option').first().click();
      await expect(page.getByRole('listbox')).toHaveCount(0);
      await reopened.locator('.p-dialog-close-button').click();
      await expect(reopened).toHaveCount(0);
    } finally {
      countries.release();
    }
  } finally {
    tree.release();
  }
});

test('tax-model drafts survive delayed loads and real previews refresh the OnPush detail tables', async ({ page }) => {
  const container = await openTaxDataView(page);
  const model = await holdResponse(page, '**/api/taxdata/country/*/taxmodelyaml');
  const schema = await holdResponse(page, '**/assets/schemas/tax-model-schema.json');
  const options = await holdResponse(page, '**/api/taxdata/eventkinds');
  try {
    const dialog = await modelDialog(page, container);
    await model.arrived;
    await schema.arrived;
    await options.arrived;
    await setYaml(page, yaml);
    model.release();
    schema.release();
    options.release();
    await model.done;
    await expectYaml(page, yaml);
    await dialog.locator('summary').click();
    await expect(dialog.locator('select#eventKind option[value="BUY"]')).toHaveCount(1);
    await dialog.locator('select#eventKind').selectOption('BUY');
    await dialog.locator('select#currency').selectOption('CHF');
    const date = dialog.locator('#eventDate input, input#eventDate').first();
    await date.click();
    await date.press('Control+a');
    await date.pressSequentially('01.06.25');
    await date.press('Tab');
    await expect(date).toHaveValue('01.06.25');
    const cleanValue = dialog.locator('#cleanValue input, input#cleanValue').first();
    await cleanValue.fill('1000');
    await cleanValue.blur();
    const preview = await holdResponse(page, '**/api/taxdata/estimatetaxyaml', 'POST');
    try {
      await dialog.locator('button[type="submit"]').click();
      await preview.arrived;
      preview.release();
      await expect(dialog.locator('tax-details-table[mode="estimate"] tbody tr')).toContainText('10.00');
      await expect(dialog.locator('tax-details-table[mode="matches"]')).toContainText('Notification purchase');
    } finally {
      preview.release();
    }
    await page.route(
      '**/api/taxdata/estimatetaxyaml',
      (route) => route.fulfill({ status: 503, contentType: 'text/plain', body: 'Tax preview temporarily unavailable' }),
      { times: 1 }
    );
    await dialog.locator('button[type="submit"]').click();
    await expect(page.getByText('Tax preview temporarily unavailable', { exact: true })).toBeVisible();
    await expect(dialog.locator('button[type="submit"]')).toBeEnabled();
    const pending = await holdResponse(page, '**/api/taxdata/estimatetaxyaml', 'POST');
    try {
      await dialog.locator('button[type="submit"]').click();
      await pending.arrived;
      await dialog.locator('.p-dialog-close-button').click();
      await expect(dialog).toHaveCount(0);
      const reopened = await modelDialog(page, container);
      pending.release();
      await pending.done;
      await expect(reopened.locator('tax-details-table')).toHaveCount(0);
      await reopened.locator('.p-dialog-close-button').click();
      await expect(reopened).toHaveCount(0);
    } finally {
      pending.release();
    }
  } finally {
    model.release();
    schema.release();
    options.release();
  }
});

test('exchange-rate overrides render after a delayed read and cancel without changing the published rate', async ({
  page
}) => {
  const token = await page.evaluate(() => sessionStorage.getItem('jwt'));
  const response = await page.request.get('/api/taxdata/tree', { headers: { 'x-auth-token': token! } });
  expect(response.ok()).toBeTruthy();
  const countries = await response.json();
  test.skip(
    !countries.some(
      (country: any) =>
        country.countryCode === 'CH' &&
        country.taxYears?.some((year: any) => year.taxYear === 2025 && year.exchangeRateCount > 0)
    ),
    'Requires the optional Kursliste import from 115'
  );
  const container = await openTaxDataView(page);
  const pending = await holdResponse(page, '**/api/taxdata/year/*/exchangerate');
  try {
    const table = await openExchangeRateTable(container, nodeRow(container, '2025'));
    await pending.arrived;
    pending.release();
    const usd = exchangeRateRow(table, 'USD');
    await expect(usd).toContainText('0.79225');
    await usd.locator('button:has(i.pi-pencil)').click();
    await expect(usd.locator('p-inputnumber input').first()).toBeVisible();
    // Cancel exercises the editable child without changing a shared official rate.
    await usd.locator('button:has(i.pi-times)').click();
    await expect(usd.locator('p-inputnumber input')).toHaveCount(0);
    await expect(usd).toContainText('0.79225');
  } finally {
    pending.release();
  }
});
