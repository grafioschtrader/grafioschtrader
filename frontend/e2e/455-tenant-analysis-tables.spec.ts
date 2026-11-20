import { expect, test } from '@playwright/test';

import { loginAsFixtureUser } from './helpers';
import { mockTenantAnalysis } from './tenant-analysis.helpers';

/**
 * The fee-comparison and FX-observation child tables of the tenant analysis, `FeeModelComparisonTableComponent` and
 * `FxObservationTableComponent`. Both run with the OnPush default and receive their rows through signal inputs, so
 * every assertion waits on what only a new input puts on screen: sorting, translated filter options, a recalculation
 * after toggling zero costs, the replacement of rows and filter options when the account changes.
 *
 * The report responses are mocked by `tenant-analysis.helpers.ts`; the spec uses the real login and NLS bundles but
 * creates no portfolio, account or transaction, so it depends on no other spec.
 */

test.describe('tenant fee and FX reports', () => {
  test.use({ viewport: { width: 1800, height: 1100 }, actionTimeout: 15_000 });

  test.beforeEach(async ({ page }) => {
    await loginAsFixtureUser(page, 'alledit');
    await mockTenantAnalysis(page);
    await page.goto('/mainview/tenantTabMenu/portfoliotranscost');
    // The tab host initially navigates to its summary; open the comparison through the visible tab afterwards.
    await expect(page).toHaveURL(/\/portfoliotranscost\/transcostsummary$/);
    await page.getByRole('tab', { name: /^(Fee model comparison|Gebührenmodellvergleich)$/ }).click();
    await expect(page.locator('select#idSecuritycashAccount option')).toHaveCount(3);
    await page.locator('select#idSecuritycashAccount').selectOption('71001');
  });

  test('fee details sort, filter translated values and refresh after recalculation', async ({ page }) => {
    const table = page.locator('fee-model-comparison-table');
    const rows = table.locator('tbody tr');
    await expect(rows).toHaveCount(2);
    // The initial date sort is bound to the shared table, even though the response arrives in reverse order.
    await expect(rows.first()).toContainText('Earlier buy');
    await expect(rows.first().getByRole('cell', { name: /^(Buy|Kaufen)$/ })).toBeVisible();
    const costHeader = table.getByRole('columnheader', { name: /Actual cost|Istkosten/ });
    await costHeader.click();
    await expect(rows.first()).toContainText('Later sell');
    await costHeader.click();
    await expect(rows.first()).toContainText('Earlier buy');

    const typeFilter = table.locator('p-multiselect').first();
    await typeFilter.click();
    const options = page.getByRole('listbox');
    await options.getByRole('option', { name: /^(Sell|Verkaufen)$/ }).click();
    await page.keyboard.press('Escape');
    await expect(rows).toHaveCount(1);
    await expect(rows.first()).toContainText('Later sell');
    // Clearing the selected option must restore the complete data set.
    await typeFilter.click();
    await options.getByRole('option', { name: /^(Sell|Verkaufen)$/ }).click();
    await page.keyboard.press('Escape');
    await expect(rows).toHaveCount(2);

    const recalculation = page.waitForRequest(
      (request) => request.url().includes('/feemodelcomparison?') && request.url().includes('excludeZeroCost=false')
    );
    await page.locator('input#excludeZeroCost').uncheck();
    await recalculation;
    await expect(rows).toHaveCount(3);
    await expect(table).toContainText('Zero-cost buy');

    await page.locator('input#excludeZeroCost').check();
    await page.locator('select#idSecuritycashAccount').selectOption('71002');
    await expect(rows).toHaveCount(1);
    await expect(table).toContainText('Replacement income');
    await expect(table).not.toContainText('Earlier buy');
    await typeFilter.click();
    await expect(options.getByRole('option', { name: /^(Interest\/Dividends|Zins\/Dividende)$/ })).toBeVisible();
    await expect(options.getByRole('option', { name: /^(Sell|Verkaufen)$/ })).toHaveCount(0);
  });

  test('FX rows derive translated labels and sample hints anew when the account changes', async ({ page }) => {
    const accounts = page.locator('fx-observation-table').nth(0);
    const transfers = page.locator('fx-observation-table').nth(1);
    const rows = accounts.locator('tbody tr');
    await expect(rows).toHaveCount(2);
    await expect(rows.first()).toContainText('EUR/CHF');
    const smallSample = rows.filter({ hasText: 'USD/CHF' });
    await expect(smallSample).toContainText(/Fewer than five observations|Weniger als fünf Beobachtungen/);
    await expect(smallSample.getByRole('cell', { name: /^(Trade|Wertpapierhandel)$/ })).toBeVisible();
    await expect(rows.filter({ hasText: 'EUR/CHF' })).not.toContainText(/Fewer than five|Weniger als fünf/);
    await expect(transfers).toContainText('CAD/CHF');
    await expect(transfers.getByRole('cell', { name: /^(Cash transfer|Kontoübertrag)$/ })).toBeVisible();

    const meanHeader = accounts.getByRole('columnheader', { name: /^(Mean deviation|Mittlere Abweichung)/ });
    await meanHeader.click();
    await expect(rows.first()).toContainText('USD/CHF');
    await meanHeader.click();
    await expect(rows.first()).toContainText('EUR/CHF');

    await page.locator('select#idSecuritycashAccount').selectOption('71002');
    await expect(rows).toHaveCount(1);
    await expect(rows.first()).toContainText('GBP/CHF');
    await expect(accounts).not.toContainText(/USD\/CHF|EUR\/CHF|Fewer than five|Weniger als fünf/);
    await expect(transfers).toContainText('CAD/CHF');

    await page.locator('select#idSecuritycashAccount').selectOption('');
    await expect(accounts.getByRole('cell', { name: 'GBP/CHF', exact: true })).toHaveCount(0);
    await expect(
      page.locator('fee-model-comparison-table').getByRole('cell', { name: 'Replacement income', exact: true })
    ).toHaveCount(0);
    await expect(transfers).toContainText('CAD/CHF');
  });
});
