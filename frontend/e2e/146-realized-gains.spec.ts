import { expect, test } from '@playwright/test';

import { loginAsFixtureUser } from './helpers';
import { scenario } from './cash-security-transaction.helpers';
import { expectCell, openRealizedGainsTab, realizedGainReport, YEAR_COLUMN, yearRow } from './realized-gains.helpers';

/**
 * The tab REALIZED_GAINS. The year row of the report and the cost basis method of the selection are checked against
 * the report the backend calculates for the same method, so the spec follows the data whatever other specs added.
 *
 * `alledit` owns the market sale of 122 (Banco Santander, 4 of 10 units); a partial sale from one purchase consumes the
 * same cost under every method, so the year row must not move when the method changes while the sale table names the
 * method. `limit1` owns the security transfer of 145, which the report marks as system booking and counts apart from
 * the sales. The spec creates and changes no data; it needs 122 and 145 and so runs after them.
 */
const SALE_YEAR = Number(scenario.transactions[1].date.slice(0, 4));

test.describe('realized gains tab', () => {
  test.use({ viewport: { width: 1800, height: 1100 }, actionTimeout: 15_000, timezoneId: 'Europe/Zurich' });

  test('the selected method is applied to the sale of the year and leaves its gain unchanged', async ({ page }) => {
    await loginAsFixtureUser(page, scenario.loginNickname);
    const average = await realizedGainReport(page, 'AVERAGE_COST');
    const year = average.years.find((group) => group.year === SALE_YEAR);
    expect(year, '122 partial sale is a prerequisite').toBeDefined();
    expect(year!.positions.some((position) => position.security.isin === scenario.securityIsin)).toBeTruthy();

    for (const method of ['FIFO', 'LIFO']) {
      const other = (await realizedGainReport(page, method)).years.find((group) => group.year === SALE_YEAR)!;
      expect(other.saleCount).toBe(year!.saleCount);
      expect(other.yearNetMC).toBeCloseTo(year!.yearNetMC, 2);
    }

    await openRealizedGainsTab(page);
    const row = yearRow(page, SALE_YEAR);
    await expect(row).toBeVisible();
    await expectCell(row, YEAR_COLUMN.saleCount, year!.saleCount);

    for (const [method, label] of [
      ['FIFO', /^FIFO/],
      ['LIFO', /^LIFO/]
    ] as const) {
      const response = page.waitForResponse(
        (candidate) =>
          candidate.url().includes('/api/portfolio/realizedgains?') &&
          candidate.url().includes(`costBasisMethod=${method}`)
      );
      await page.locator('select#costBasisMethod').selectOption(method);
      await page.getByRole('button', { name: /^(Calculate|Berechnen)$/ }).click();
      expect((await response).ok()).toBeTruthy();
      // The caption names the applied method after the colon, the table cell starts with the label.
      await expect(page.locator('h4').filter({ hasText: new RegExp(`(method|Methode): ${method}`) })).toBeVisible();
      await expectCell(yearRow(page, SALE_YEAR), YEAR_COLUMN.saleCount, year!.saleCount);

      await yearRow(page, SALE_YEAR).locator('td').first().locator('a').click();
      const sales = page.locator('tenant-realized-gains-sale-table');
      const sale = sales.locator('tbody > tr').filter({ hasText: scenario.securityName });
      await expect(sale).toHaveCount(1);
      await expect(sale.getByRole('cell', { name: label })).toBeVisible();
      await yearRow(page, SALE_YEAR).locator('td').first().locator('a').click();
      await expect(sales).toHaveCount(0);
    }
  });

  test('a security transfer is counted as system booking and not as sale', async ({ page }) => {
    await loginAsFixtureUser(page, 'limit1');
    const report = await realizedGainReport(page);
    const booked = report.years.filter((group) => group.systemBookingCount > 0);
    expect(booked.length, '145 security transfer is a prerequisite').toBeGreaterThan(0);
    const year = booked[0];
    const system = year.positions.filter((position) => position.systemBooking);
    expect(system).toHaveLength(year.systemBookingCount);
    expect(year.saleCount).toBe(year.positions.length - system.length);

    await openRealizedGainsTab(page);
    const row = yearRow(page, year.year);
    await expect(row).toBeVisible();
    await expectCell(row, YEAR_COLUMN.saleCount, year.saleCount);
    await expectCell(row, YEAR_COLUMN.systemBookingCount, year.systemBookingCount);

    await row.locator('td').first().locator('a').click();
    await expect(
      page.locator('tenant-realized-gains-sale-table tbody > tr').filter({ hasText: system[0].security.name })
    ).not.toHaveCount(0);
  });
});
