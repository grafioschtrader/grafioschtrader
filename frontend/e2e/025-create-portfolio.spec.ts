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
  RX,
  SecurityAccountData,
  selectWhenLoaded
} from './portfolio.helpers';

/**
 * Creates every portfolio of testdata/portfolios.json with its cash accounts, securities accounts and
 * trading periods. The teardown lives in 888-delete-portfolio.spec.ts, which reads the same fixture —
 * this spec is create-only, so it expects the fixture portfolios not to exist yet.
 */
const PORTFOLIOS = loadPortfolios();

for (const p of PORTFOLIOS) {
  test.describe.serial(`Create portfolio ${p.name} of '${p.loginNickname}'`, () => {
    test(`creates the portfolio ${p.name}`, async ({ page }) => {
      await loginAsFixtureUser(page, p.loginNickname);

      // The tenant root node is the first tree node; its context menu offers "Create Portfolio...".
      await openTreeContextMenu(page, page.locator('.p-tree-node-content').first(), RX.createPortfolio);

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
        await openSecurityAccountsFolder(page, p, RX.createSecurityAccount);

        const dialog = page.locator('.p-dialog');
        await dialog.waitFor({ state: 'visible', timeout: 10_000 });

        await fillFormInput(dialog, '#name', sa.name);
        await selectTradingPlatformPlan(page, dialog, sa);

        // Optimus UI's p-inputnumber only commits its model on blur, so dispatch input and press Tab —
        // otherwise the reactive form stays invalid and Save remains disabled.
        const costInput = dialog.locator('#lowestTransactionCost input');
        await costInput.click();
        await costInput.fill(String(sa.lowestTransactionCost));
        await costInput.dispatchEvent('input');
        await costInput.press('Tab');

        // The dialog pre-fills Equities/Direct investment and Equities/ETF; the fixture rows are added.
        for (const tp of sa.tradingPeriods) {
          await addTradingPeriod(dialog, tp, creds.locale);
        }

        await dialog.locator('button[type="submit"]').click();
        // Waiting for the dialog to close confirms that the creation succeeded.
        await dialog.waitFor({ state: 'hidden', timeout: 10_000 });
      });
    }

    test(`creates ${p.cashAccounts.length} cash accounts in ${p.name}`, async ({ page }) => {
      await loginAsFixtureUser(page, p.loginNickname);

      // Clicking the portfolio node opens its cash account summary view.
      const node = portfolioNode(page, p);
      await node.waitFor({ state: 'visible', timeout: 15_000 });
      await node.click();
      await page.waitForTimeout(3000);

      for (const account of p.cashAccounts) {
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
  });
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
