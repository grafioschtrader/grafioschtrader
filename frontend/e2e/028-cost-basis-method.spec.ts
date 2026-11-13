import { expect, Locator, Page, test } from '@playwright/test';

import { loginAsFixtureUser } from './helpers';
import {
  loadPortfolios,
  openTreeContextMenu,
  PortfolioFixture,
  portfolioNode,
  readFixturePortfolio,
  RX
} from './portfolio.helpers';

/**
 * The cost basis method as a setting of the tenant and as an override of a portfolio. The spec owns nothing but these
 * two values: it sets both back to their defaults at the start, so a run that failed halfway does not break the next
 * one, and again at the end, because every later spec calculates with average cost.
 */
const METHODS = ['AVERAGE_COST', 'FIFO', 'LIFO'];
const EDIT_TENANT_RX = /^(Edit Client|Bearbeiten Klient|EDIT_RECORD\|TENANT)(\.\.\.)?$/i;
const SELECT = 'select#costBasisMethod';

// A portfolio that 888 keeps, so the spec also runs alone against a database a roundtrip has already torn down.
const portfolios = loadPortfolios();
const portfolio: PortfolioFixture | undefined = portfolios.find((p) => !p.delete) ?? portfolios[0];

async function authHeaders(page: Page): Promise<{ 'x-auth-token': string }> {
  const token = await page.evaluate(() => sessionStorage.getItem('jwt'));
  expect(token).toBeTruthy();
  return { 'x-auth-token': token! };
}

/** Sets the tenant to average cost and removes the override of the fixture portfolio through REST. */
async function resetMethods(page: Page, p: PortfolioFixture): Promise<void> {
  const headers = await authHeaders(page);
  const tenantResponse = await page.request.get('/api/tenant', { headers });
  expect(tenantResponse.ok(), await tenantResponse.text()).toBeTruthy();
  const tenant = await tenantResponse.json();
  if (tenant.costBasisMethod !== 'AVERAGE_COST') {
    const saved = await page.request.put('/api/tenant', {
      headers,
      data: { ...tenant, costBasisMethod: 'AVERAGE_COST' }
    });
    expect(saved.ok(), await saved.text()).toBeTruthy();
  }
  const stored = await readFixturePortfolio(page, p);
  expect(stored, `fixture portfolio ${p.name}`).toBeTruthy();
  const portfolioResponse = await page.request.get(`/api/portfolio/${stored!.idPortfolio}`, { headers });
  expect(portfolioResponse.ok(), await portfolioResponse.text()).toBeTruthy();
  const full = await portfolioResponse.json();
  if (full.costBasisMethod != null) {
    const saved = await page.request.put('/api/portfolio', { headers, data: { ...full, costBasisMethod: null } });
    expect(saved.ok(), await saved.text()).toBeTruthy();
  }
}

async function openTenantDialog(page: Page): Promise<Locator> {
  const tenantRoot = page.getByRole('treeitem', { name: /^Portfolios-/ }).first();
  await openTreeContextMenu(page, tenantRoot.locator(':scope > .p-tree-node-content'), EDIT_TENANT_RX);
  const dialog = page.locator('.p-dialog:visible');
  await dialog.waitFor({ state: 'visible', timeout: 10_000 });
  await expect(dialog.locator(`${SELECT} option`)).toHaveCount(METHODS.length, { timeout: 10_000 });
  return dialog;
}

async function openPortfolioDialog(page: Page, p: PortfolioFixture): Promise<Locator> {
  await openTreeContextMenu(page, portfolioNode(page, p), RX.editPortfolio);
  const dialog = page.locator('.p-dialog:visible');
  await dialog.waitFor({ state: 'visible', timeout: 10_000 });
  // The empty option for the method of the tenant precedes the three methods.
  await expect(dialog.locator(`${SELECT} option`)).toHaveCount(METHODS.length + 1, { timeout: 10_000 });
  return dialog;
}

/** Selects a method, saves the dialog and returns the method the server stored. */
async function saveMethod(page: Page, dialog: Locator, url: string, method: string): Promise<string | null> {
  const select = dialog.locator(SELECT);
  await select.selectOption({ value: method });
  await select.dispatchEvent('change');
  const responsePromise = page.waitForResponse(
    (response) => response.url().endsWith(url) && response.request().method() === 'PUT',
    { timeout: 20_000 }
  );
  await dialog.locator('button[type="submit"]').click();
  const response = await responsePromise;
  expect(response.ok(), `${response.status()} ${await response.text()}`).toBeTruthy();
  await dialog.waitFor({ state: 'hidden', timeout: 10_000 });
  return ((await response.json()) as { costBasisMethod: string | null }).costBasisMethod;
}

test.describe.serial('cost basis method setting', () => {
  test.skip(!portfolio, 'no e2e portfolio in portfolios.json');
  const p = portfolio as PortfolioFixture;

  test('the tenant offers the three methods and stores the chosen one', async ({ page }) => {
    await loginAsFixtureUser(page, p.loginNickname);
    await resetMethods(page, p);
    await page.reload();

    let dialog = await openTenantDialog(page);
    const values = await dialog
      .locator(`${SELECT} option`)
      .evaluateAll((options) => options.map((option) => (option as HTMLOptionElement).value));
    expect(values).toEqual(METHODS);
    await expect(dialog.locator(SELECT)).toHaveValue('AVERAGE_COST');
    expect(await saveMethod(page, dialog, '/api/tenant', 'FIFO')).toBe('FIFO');

    dialog = await openTenantDialog(page);
    await expect(dialog.locator(SELECT)).toHaveValue('FIFO');
    expect(await saveMethod(page, dialog, '/api/tenant', 'AVERAGE_COST')).toBe('AVERAGE_COST');
  });

  test('a portfolio overrides the tenant and returns to it with the empty option', async ({ page }) => {
    await loginAsFixtureUser(page, p.loginNickname);
    await resetMethods(page, p);
    await page.reload();

    let dialog = await openPortfolioDialog(page, p);
    await expect(dialog.locator(SELECT)).toHaveValue('');
    expect(await saveMethod(page, dialog, '/api/portfolio', 'LIFO')).toBe('LIFO');

    dialog = await openPortfolioDialog(page, p);
    await expect(dialog.locator(SELECT)).toHaveValue('LIFO');
    expect(await saveMethod(page, dialog, '/api/portfolio', '')).toBeNull();

    dialog = await openPortfolioDialog(page, p);
    await expect(dialog.locator(SELECT)).toHaveValue('');
  });
});
