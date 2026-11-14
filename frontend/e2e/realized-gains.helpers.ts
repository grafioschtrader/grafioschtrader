import { expect, Locator, Page } from '@playwright/test';

/** The year groups of `GET /api/portfolio/realizedgains` that the specs compare with the screen. */
export interface RealizedGainYear {
  year: number;
  saleCount: number;
  systemBookingCount: number;
  yearNetMC: number;
  systemNetMC: number;
  positions: { systemBooking: boolean; costBasisMethod?: string; security: { isin: string; name: string } }[];
}

export interface RealizedGainReport {
  mainCurrency: string;
  appliedMethod: string;
  tenantCountry?: string;
  years: RealizedGainYear[];
}

export interface GermanTaxReport {
  portfolioAssignmentRequired: boolean;
  portfolios: { idPortfolio: number; name: string; taxWithholding?: string; hasAccounts: boolean }[];
  sums: { complete: boolean }[];
}

/** Backend whose info endpoint is checked; E2E_BACKEND_URL selects an isolated stack like in the other specs. */
const BACKEND_URL = process.env.E2E_BACKEND_URL ?? 'http://localhost:8080';

/** Every direct data access runs only against the E2E database, never against another stack. */
export async function e2eHeaders(page: Page): Promise<Record<string, string>> {
  const info = await page.request.get(`${BACKEND_URL}/api/gtinfo`);
  expect(info.ok()).toBeTruthy();
  expect(await info.json()).toMatchObject({ databaseName: 'grafioschtrader_t', activeProfile: 'e2e' });
  const token = await page.evaluate(() => sessionStorage.getItem('jwt'));
  expect(token).toBeTruthy();
  return { 'x-auth-token': token!, Accept: 'application/json' };
}

/** The report as the server calculates it, for all security accounts of the tenant. */
export async function realizedGainReport(page: Page, method?: string): Promise<RealizedGainReport> {
  const query = `idsSecurityaccount=-1${method ? `&costBasisMethod=${method}` : ''}`;
  const response = await page.request.get(`/api/portfolio/realizedgains?${query}`, {
    headers: await e2eHeaders(page)
  });
  expect(response.ok(), await response.text()).toBeTruthy();
  return response.json();
}

/**
 * Opens the tab REALIZED_GAINS of the tenant. The tab host jumps to its own default tab when it is entered by URL, so
 * the visible tab is clicked afterwards.
 */
export async function openRealizedGainsTab(page: Page): Promise<void> {
  await page.goto('/mainview/tenantTabMenu/portfoliotranscost');
  await page.getByRole('tab', { name: /^(Realized gains|Realisierte Gewinne)$/ }).click();
  await expect(page).toHaveURL(/\/realizedGains$/);
}

/** The row of a year in the year table; the nested sale tables of an expanded year are not part of it. */
export function yearRow(page: Page, year: number): Locator {
  return page
    .locator('tenant-realized-gains-year-table configurable-table')
    .first()
    .locator('tbody > tr')
    .filter({ has: page.getByRole('cell', { name: String(year), exact: true }) })
    .first();
}

/** Columns of the year table after the expand toggle: year, gains, losses, net, sales, system net, system count. */
export const YEAR_COLUMN = { saleCount: 5, systemBookingCount: 7 } as const;

export async function expectCell(row: Locator, column: number, expected: number): Promise<void> {
  // A count of zero is shown as an empty cell.
  const pattern = expected === 0 ? /^\s*0?\s*$/ : new RegExp(`^\\s*${expected}\\s*$`);
  await expect(row.locator('td').nth(column)).toHaveText(pattern);
}
