import { expect, test } from '@playwright/test';
import { loginAsFixtureUser } from './helpers';
import { mockTenantAnalysis } from './tenant-analysis.helpers';
import { dividendReport, holdTenantResponse, openTenantTab } from './tenant-notification.helpers';

test.describe('tenant views notify after delayed responses', () => {
  test.use({ viewport: { width: 1800, height: 1100 } });
  test.beforeEach(async ({ page }) => {
    await loginAsFixtureUser(page, 'alledit');
  });

  test('dividends render nested accounts and reload after applying the account dialog', async ({ page }) => {
    const held = await holdTenantResponse(page, '**/api/portfolio/dividends?*', dividendReport());
    try {
      await openTenantTab(page, /^(Dividends\/Interest|Dividende\/Zins)$/);
      await held.arrived;
      await expect(page.locator('p-table').first()).toBeVisible();
      held.release();
      const row = page.locator('p-table').first().locator('tbody > tr').filter({ hasText: '2025' });
      await expect(row).toBeVisible();
      await row.locator('a').click();
      await expect(page.locator('tenant-dividends-security-extended')).toContainText('Notification equity');
      await expect(page.locator('tenant-dividends-cashaccount-extended')).toContainText('Notification cash');
      await page.getByRole('menuitem', { name: /^(Show|Ansicht)$/ }).click();
      await page.getByRole('menuitem', { name: /Evaluation of depots|Auswertung über Depots/i }).click();
      const dialog = page.locator('tenant-dividend-security-account-selection-dialog .p-dialog');
      await expect(dialog).toContainText('Notification securities');
      await expect(dialog).toContainText('Notification cash');
      const reload = await holdTenantResponse(page, '**/api/portfolio/dividends?*', dividendReport(2024));
      try {
        await dialog.getByRole('button', { name: /Apply|Übernehmen|Anwenden/ }).click();
        await reload.arrived;
        await expect(dialog).toHaveCount(0);
        reload.release();
        await expect(page.locator('p-table').first()).toContainText('2024');
        await expect(page.locator('p-table').first()).not.toContainText('2025');
      } finally {
        reload.release();
      }
    } finally {
      held.release();
    }
  });

  test('fee host publishes delayed comparison summaries and replacement rows', async ({ page }) => {
    await mockTenantAnalysis(page);
    await page.goto('/mainview/tenantTabMenu/portfoliotranscost');
    await page.getByRole('tab', { name: /^(Fee model comparison|Gebührenmodellvergleich)$/ }).click();
    const held = await holdTenantResponse(page, '**/api/securityaccount/71001/feemodelcomparison?*', {
      planName: 'Delayed comparison plan',
      totalTransactions: 1,
      comparedCount: 1,
      skippedCount: 0,
      errorCount: 0,
      details: [],
      meanAbsoluteError: 12,
      meanRelativeError: 0,
      rmse: 12
    });
    try {
      await page.locator('select#idSecuritycashAccount').selectOption('71001');
      await held.arrived;
      await expect(page.getByText('Delayed comparison plan', { exact: true })).toHaveCount(0);
      await expect(page.locator('fx-observation-table').first()).toContainText('EUR/CHF');
      // Let the select's animation and independent FX/form notifications finish before releasing the answer.
      await page.waitForTimeout(500);
      held.release();
      await expect(page.getByText('Delayed comparison plan', { exact: true })).toBeVisible();
      await page.locator('select#idSecuritycashAccount').selectOption('71002');
      await expect(page.locator('fee-model-comparison-table')).toContainText('Replacement income');
      await expect(page.getByText('Delayed comparison plan', { exact: true })).toHaveCount(0);
    } finally {
      held.release();
    }
  });

  test('cash summary refreshes after changing the grouping', async ({ page }) => {
    const summary = (name: string) => ({
      mainCurrency: 'CHF',
      missingExchangeRates: [],
      accountPositionGroupSummaryList: [
        {
          groupName: name,
          accountPositionSummaryList: [
            { cashaccount: { name, currency: 'CHF' }, cashBalance: 123, cashBalanceMC: 123, valueMC: 123 }
          ]
        }
      ]
    });
    const held = await holdTenantResponse(
      page,
      '**/api/portfolio/securitycashaccountsummary/*?*',
      summary('Delayed cash summary')
    );
    try {
      await page.goto('/mainview/tenantTabMenu');
      await held.arrived;
      await expect(page.locator('p-table').first()).toBeVisible();
      held.release();
      await expect(page.getByRole('cell', { name: 'Delayed cash summary', exact: true })).toBeVisible();
      const next = await holdTenantResponse(
        page,
        '**/api/portfolio/securitycashaccountsummary/*?*',
        summary('New portfolio grouping')
      );
      try {
        await page.locator('p-select').first().click();
        await page.getByRole('option', { name: /Grouped by portfolio|Portfolio gruppiert/ }).click();
        await next.arrived;
        next.release();
        await expect(page.getByRole('cell', { name: 'New portfolio grouping', exact: true })).toBeVisible();
      } finally {
        next.release();
      }
    } finally {
      held.release();
    }
  });

  test('transaction cost summary renders a delayed account', async ({ page }) => {
    const held = await holdTenantResponse(page, '**/api/portfolio/transactioncost', {
      mainCurrency: 'CHF',
      transactionCostGroupSummaries: [
        {
          securityaccount: { idSecuritycashAccount: 71001, name: 'Delayed cost account' },
          groupTotalTransactionCostMC: 123,
          transactionCostPositions: []
        }
      ]
    });
    try {
      await page.goto('/mainview/tenantTabMenu/portfoliotranscost');
      await held.arrived;
      await expect(page.locator('configurable-table').first()).toBeVisible();
      held.release();
      await expect(page.getByRole('cell', { name: 'Delayed cost account', exact: true })).toBeVisible();
    } finally {
      held.release();
    }
  });

  test('EOD gaps replace instruments when the year changes', async ({ page }) => {
    const year = new Date().getFullYear();
    const missing = (name: string) => ({
      year,
      firstEverTradingDay: `${year - 1}-01-01`,
      securities: [{ idSecuritycurrency: 71003, name, currency: 'CHF' }],
      currencypairs: [],
      dateSecurityMissingMap: { [`${year}-01-05`]: [71003] },
      countIdSecurityMissingsMap: { 71003: 2 }
    });
    const held = await holdTenantResponse(page, '**/api/holding/missingquotes/*', missing('Delayed missing equity'));
    try {
      await page.goto('/mainview/tenantTabMenu/performancetabmenu');
      await page.getByRole('tab', { name: /Missing EOD|Fehlende Tagesendkurse/ }).click();
      await held.arrived;
      await expect(page.locator('#idYearSelect')).toBeVisible();
      held.release();
      await expect(page.locator('tenant-performance-eod-missing-table')).toContainText('Delayed missing equity');
      const next = await holdTenantResponse(
        page,
        '**/api/holding/missingquotes/*',
        missing('Replacement missing equity')
      );
      try {
        await page.locator('#idYearSelect').click();
        await page.getByRole('option', { name: String(year - 1), exact: true }).click();
        await next.arrived;
        next.release();
        await expect(page.locator('tenant-performance-eod-missing-table')).toContainText('Replacement missing equity');
      } finally {
        next.release();
      }
    } finally {
      held.release();
    }
  });
});
