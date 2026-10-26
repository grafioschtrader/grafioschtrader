import { test, expect, Locator, Page } from '@playwright/test';
import { loginAsFixtureUser } from './helpers';
import {
  addTradingPeriod,
  fillFormInput,
  loadPortfolios,
  openTreeContextMenu,
  PortfolioFixture,
  portfolioLabel,
  portfolioNode,
  portfolioSubtree,
  readFixturePortfolio,
  RX,
  SecurityAccountData,
  selectWhenLoaded
} from './portfolio.helpers';

/**
 * Creates every portfolio of testdata/portfolios.json with its cash accounts, securities accounts and
 * trading periods. Existing fixture rows are retained on a rerun because later specs may have booked transactions.
 * Reopening the portfolio and cash-account dialogs checks that delayed currency options do not erase user input.
 * The teardown lives in 888-delete-portfolio.spec.ts, which reads the same fixture.
 */
const PORTFOLIOS = loadPortfolios();

for (const p of PORTFOLIOS) {
  test.describe.serial(`Create portfolio ${p.name} of '${p.loginNickname}'`, () => {
    test(`creates the portfolio ${p.name}`, async ({ page }) => {
      await loginAsFixtureUser(page, p.loginNickname);
      if (await readFixturePortfolio(page, p)) {
        await expect(portfolioNode(page, p)).toBeVisible();
        return;
      }

      // Dashboard is also a root node; identify the tenant root by its label, but right-click its content element.
      // Optimus attaches the context-menu listener there rather than to the enclosing ARIA treeitem.
      const tenantRoot = page.getByRole('treeitem', { name: /^Portfolios-/ }).first();
      await openTreeContextMenu(page, tenantRoot.locator(':scope > .p-tree-node-content'), RX.createPortfolio);

      const dialog = page.locator('.p-dialog');
      await dialog.waitFor({ state: 'visible', timeout: 10_000 });

      await fillFormInput(dialog, '#name', p.name);
      // The currency defaults to the tenant currency, but the fixture is authoritative.
      await selectWhenLoaded(dialog, 'select#currency', { value: p.currency });
      await dialog.locator('button[type="submit"]').click();
      await dialog.waitFor({ state: 'hidden', timeout: 10_000 });

      await expect(page.locator('.p-tree-node-content', { hasText: portfolioLabel(p) })).toBeVisible({
        timeout: 5_000
      });
    });

    for (const sa of p.securityAccounts) {
      test(`creates the securities account ${sa.name} in ${p.name}`, async ({ page }) => {
        const creds = await loginAsFixtureUser(page, p.loginNickname);
        const existing = await readFixturePortfolio(page, p);
        if (existing?.securityaccountList.some((account) => account.name === sa.name)) {
          return;
        }
        await openSecurityAccountsFolder(page, p, RX.createSecurityAccount);

        const dialog = page.locator('.p-dialog');
        await dialog.waitFor({ state: 'visible', timeout: 10_000 });

        await fillFormInput(dialog, '#name', sa.name);
        await selectTradingPlatformPlan(page, dialog, sa);

        // The dialog pre-fills Equities/Direct investment and Equities/ETF; the fixture rows are added.
        for (const tp of sa.tradingPeriods) {
          await addTradingPeriod(dialog, tp, creds.locale!);
        }

        await dialog.locator('button[type="submit"]').click();
        // Waiting for the dialog to close confirms that the creation succeeded.
        await dialog.waitFor({ state: 'hidden', timeout: 10_000 });
      });
    }

    test(`creates ${p.cashAccounts.length} cash accounts in ${p.name}`, async ({ page }) => {
      await loginAsFixtureUser(page, p.loginNickname);
      const existing = await readFixturePortfolio(page, p);

      // Clicking the portfolio node opens its cash account summary view.
      const node = portfolioNode(page, p);
      await node.waitFor({ state: 'visible', timeout: 15_000 });
      await node.click();
      await page.waitForTimeout(3000);

      for (const account of p.cashAccounts) {
        if (existing?.cashaccountList.some((row) => row.name === account.name)) {
          continue;
        }
        // First left-click to activate the panel, then right-click to open its context menu.
        const contentArea = page.locator('.data-container').first();
        await contentArea.waitFor({ state: 'visible', timeout: 10_000 });
        await contentArea.click();
        await page.waitForTimeout(500);
        await contentArea.click({ button: 'right' });

        const menuList = page.locator('[role="menu"]:visible');
        await menuList.waitFor({ state: 'visible', timeout: 5_000 });
        await menuList.getByText(RX.createCashAccount).first().click();

        const dialog = page.locator('.p-dialog');
        await dialog.waitFor({ state: 'visible', timeout: 10_000 });

        await fillFormInput(dialog, '#name', account.name);
        await selectWhenLoaded(dialog, 'select#currency', { value: account.currency });

        await dialog.locator('button[type="submit"]').click();
        await dialog.waitFor({ state: 'hidden', timeout: 10_000 });
        // Small delay to let the table refresh.
        await page.waitForTimeout(1000);
      }

      for (const account of p.cashAccounts) {
        await expect(page.locator('td', { hasText: account.name }).first()).toBeVisible({ timeout: 5_000 });
      }

      // Dynamically hidden columns must be omitted from the header as well as the body and footer. Otherwise all
      // values following the hidden column are displayed below the preceding header.
      const summaryTable = page.locator('.datatable p-table').first();
      const headerCellCount = await summaryTable.locator('thead tr').first().locator('th').count();
      const accountRow = summaryTable.locator('tbody tr').filter({ hasText: p.cashAccounts[0].name }).first();
      await expect(accountRow).toBeVisible();
      expect(await accountRow.locator(':scope > td').count()).toBe(headerCellCount);
      expect(await summaryTable.locator('tfoot tr').first().locator('td').count()).toBe(headerCellCount);
    });

    test(`reopens ${p.name} and its cash accounts while currency options arrive late`, async ({ page }) => {
      page.setDefaultTimeout(15_000);
      await loginAsFixtureUser(page, p.loginNickname);
      // Navigate before the context menu selects the same node; clicking that selected node would only deselect it.
      await portfolioNode(page, p).click();
      await expect(
        page.locator('.datatable tbody tr').filter({ hasText: p.cashAccounts[0].name }).first()
      ).toBeVisible();
      const currencies = await holdCurrencyOptions(page);
      try {
        await openTreeContextMenu(page, portfolioNode(page, p), RX.editPortfolio);
        const dialog = page.locator('.p-dialog:visible');
        await expect(dialog.locator('#name')).toHaveValue(p.name);
        await dialog.locator('#name').fill('Unsaved portfolio name');
        currencies.release();
        await expect(dialog.locator('select#currency option:checked')).toHaveAttribute('value', p.currency);
        await expect(dialog.locator('#name')).toHaveValue('Unsaved portfolio name');
        await dialog.locator('.p-dialog-close-button').click();
        await expect(dialog).toHaveCount(0);
      } finally {
        currencies.release();
      }

      for (const account of p.cashAccounts) {
        const options = await holdCurrencyOptions(page);
        try {
          const row = page
            .locator('.datatable tbody tr')
            .filter({ has: page.getByText(account.name, { exact: true }) })
            .first();
          await row.click();
          await row.click({ button: 'right' });
          await page.locator('[role="menu"]:visible').getByText(RX.editCashAccount).click();
          const dialog = page.locator('.p-dialog:visible');
          await expect(dialog.locator('#name')).toHaveValue(account.name);
          await dialog.locator('#name').fill('Unsaved cash account');
          options.release();
          await expect(dialog.locator('select#currency option:checked')).toHaveAttribute('value', account.currency);
          await expect(dialog.locator('#name')).toHaveValue('Unsaved cash account');
          await dialog.locator('.p-dialog-close-button').click();
          await expect(dialog).toHaveCount(0);
        } finally {
          options.release();
        }
      }
    });
  });
}

/** Holds the actual HTTP answer until the test has typed into the already initialized form. */
async function holdCurrencyOptions(page: Page): Promise<{ release: () => void }> {
  let release!: () => void;
  const gate = new Promise<void>((resolve) => (release = resolve));
  await page.route(
    '**/api/gt_globalparameters/currencies',
    async (route) => {
      const response = await route.fetch();
      await gate;
      await route.fulfill({ response });
    },
    { times: 1 }
  );
  return { release };
}

/**
 * Expands the portfolio and opens the context menu of its "Securities accounts" folder. Everything is
 * scoped to the portfolio's own subtree because a user can own several portfolios, each carrying an
 * identically labelled folder.
 */
async function openSecurityAccountsFolder(page: Page, p: PortfolioFixture, itemRx: RegExp): Promise<void> {
  const node = portfolioNode(page, p);
  await node.waitFor({ state: 'visible', timeout: 15_000 });
  await node.dblclick();

  const folder = portfolioSubtree(page, p)
    .locator('.p-tree-node-content')
    .filter({ hasText: RX.securityAccountsFolder })
    .first();
  await folder.waitFor({ state: 'visible', timeout: 10_000 });
  await openTreeContextMenu(page, folder, itemRx);
}

/**
 * Picks the trading platform plan. The fixture holds a case-insensitive substring of the label,
 * because the plan names are translated and the two fixture users run different UI languages. A null
 * value keeps the original behaviour of taking the first non-empty option.
 *
 * The field is rendered as a native <select> by the dynamic form; the p-select branch is kept as a
 * fallback for the case that the form control is switched over.
 */
async function selectTradingPlatformPlan(page: Page, dialog: Locator, sa: SecurityAccountData): Promise<void> {
  const nativeSelect = dialog.locator('select#tradingPlatformPlan');
  if (await nativeSelect.isVisible().catch(() => false)) {
    const options = nativeSelect.locator('option');
    await expect(options).not.toHaveCount(0, { timeout: 10_000 });
    if (sa.tradingPlatformPlan) {
      const labels = await options.allTextContents();
      const wanted = sa.tradingPlatformPlan.toLowerCase();
      const index = labels.findIndex((label) => label.toLowerCase().includes(wanted));
      expect(
        index,
        `no trading platform plan matching '${sa.tradingPlatformPlan}' in [${labels.join(', ')}]`
      ).toBeGreaterThanOrEqual(0);
      await nativeSelect.selectOption({ index });
    } else if ((await options.count()) > 1) {
      await nativeSelect.selectOption({ index: 1 });
    }
    await nativeSelect.dispatchEvent('change');
    return;
  }

  const pSelect = dialog.locator('#tradingPlatformPlan');
  await pSelect.click();
  const overlayOptions = page.locator('.p-select-overlay .p-select-option');
  await overlayOptions.first().waitFor({ state: 'visible', timeout: 5_000 });
  const option = sa.tradingPlatformPlan
    ? overlayOptions.filter({ hasText: sa.tradingPlatformPlan }).first()
    : overlayOptions.first();
  await option.click();
}
