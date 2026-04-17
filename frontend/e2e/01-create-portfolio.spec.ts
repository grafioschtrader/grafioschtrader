import {test, expect, Page} from '@playwright/test';
import {login} from './helpers';

test.describe.serial('Create portfolio with accounts', () => {

  const PORTFOLIO_NAME = 'Test Portfolio';
  const SECURITY_ACCOUNT_NAME = 'Test Securities';
  const CASH_ACCOUNTS = [
    {name: 'CHF Account', currency: 'CHF'},
    {name: 'USD Account', currency: 'USD'},
    {name: 'EUR Account', currency: 'EUR'},
  ];

  test('should create a portfolio', async ({page}) => {
    await login(page);

    // Right-click on the portfolio root node (first tree node)
    const treeNodes = page.locator('.p-tree-node-content');
    await treeNodes.first().click({button: 'right'});

    // Click the "Create Portfolio..." context menu item
    await page.locator('p-contextmenu').getByText(/Create Portfolio/i).click();

    // Wait for the portfolio dialog to appear (PrimeNG dialog inside dynamic dialog)
    const dialog = page.locator('.p-dialog');
    await dialog.waitFor({state: 'visible', timeout: 10_000});

    // Fill the portfolio name
    await dialog.locator('#name').fill(PORTFOLIO_NAME);
    // Currency defaults to tenant currency (CHF) — no change needed
    await dialog.locator('button[type="submit"]').click();

    // Wait for dialog to close
    await dialog.waitFor({state: 'hidden', timeout: 10_000});

    // Verify the portfolio appears in the tree
    await expect(page.locator('.p-tree-node-content', {hasText: PORTFOLIO_NAME})).toBeVisible({timeout: 5_000});
  });

  test('should create a securities account', async ({page}) => {
    await login(page);

    // Find and expand the portfolio node by clicking on its text
    const portfolioText = page.getByText(`${PORTFOLIO_NAME} / CHF`);
    await portfolioText.waitFor({state: 'visible', timeout: 10_000});
    // Double-click to expand the node (or use the toggle)
    await portfolioText.dblclick();

    // Wait for "Securities accounts" child to appear
    const secAccountsText = page.getByText('Securities accounts');
    await secAccountsText.waitFor({state: 'visible', timeout: 10_000});

    // Right-click on it to open context menu
    await secAccountsText.click({button: 'right'});

    // Click "Create Securities account..." context menu item
    await page.locator('p-contextmenu').getByText(/Create.*Securities\s*account/i).click();

    // Wait for the securities account dialog
    const dialog = page.locator('.p-dialog');
    await dialog.waitFor({state: 'visible', timeout: 10_000});

    const nameInput = dialog.locator('#name');
    await nameInput.click();
    await nameInput.fill(SECURITY_ACCOUNT_NAME);
    await nameInput.dispatchEvent('input');
    await nameInput.blur();

    // Select a trading platform plan — could be native select or PrimeNG p-select
    const tradingPlanNativeSelect = dialog.locator('select#tradingPlatformPlan');
    const isNativeSelect = await tradingPlanNativeSelect.isVisible().catch(() => false);
    if (isNativeSelect) {
      const options = tradingPlanNativeSelect.locator('option');
      const optionCount = await options.count();
      if (optionCount > 1) {
        await tradingPlanNativeSelect.selectOption({index: 1});
        await tradingPlanNativeSelect.dispatchEvent('change');
      }
    } else {
      // PrimeNG p-select: click to open, then select the first option
      const pSelect = dialog.locator('#tradingPlatformPlan');
      await pSelect.click();
      // Wait for the overlay panel with options to appear and click first non-empty option
      const optionItem = page.locator('.p-select-overlay .p-select-option').first();
      await optionItem.waitFor({state: 'visible', timeout: 5_000});
      await optionItem.click();
    }

    // Set lowest transaction cost (PrimeNG p-inputnumber wraps an <input>). PrimeNG only
    // commits the model on blur, so dispatch input then press Tab to trigger Angular's
    // change detection — otherwise the reactive form stays invalid and Save stays disabled.
    const costInput = dialog.locator('#lowestTransactionCost input');
    await costInput.click();
    await costInput.fill('5');
    await costInput.dispatchEvent('input');
    await costInput.press('Tab');

    await dialog.locator('button[type="submit"]').click();

    // Wait for dialog to close — confirms the creation succeeded
    await dialog.waitFor({state: 'hidden', timeout: 10_000});
  });

  test('should create 3 cash accounts (CHF, USD, EUR)', async ({page}) => {
    await login(page);

    // Click on the portfolio node text to navigate to its cash account summary view
    const portfolioText = page.getByText(`${PORTFOLIO_NAME} / CHF`);
    await portfolioText.click();

    // Wait for the portfolio cash account table to load
    await page.waitForTimeout(3000);

    for (const account of CASH_ACCOUNTS) {
      // First left-click to activate the panel, then right-click to open context menu
      const contentArea = page.locator('.data-container').first();
      await contentArea.waitFor({state: 'visible', timeout: 10_000});
      await contentArea.click();
      await page.waitForTimeout(500);
      await contentArea.click({button: 'right'});

      // The context menu appears — find visible menu items
      // PrimeNG context menu renders a <ul> with role="menu" at body level
      const menuList = page.locator('[role="menu"]:visible');
      await menuList.waitFor({state: 'visible', timeout: 5_000});
      await menuList.locator('[role="menuitem"]').first().click();

      // Fill the cash account form
      const dialog = page.locator('.p-dialog');
      await dialog.waitFor({state: 'visible', timeout: 10_000});

      const nameInput = dialog.locator('#name');
      await nameInput.click();
      await nameInput.fill(account.name);
      // Trigger Angular change detection
      await nameInput.dispatchEvent('input');

      // Select currency — native <select> but options load async from backend
      const currencySelect = dialog.locator('select#currency');
      await currencySelect.waitFor({state: 'visible'});
      // Wait for options to load
      await expect(currencySelect.locator('option')).not.toHaveCount(0, {timeout: 10_000});
      await currencySelect.selectOption(account.currency);
      // Trigger Angular change detection
      await currencySelect.dispatchEvent('change');

      await dialog.locator('button[type="submit"]').click();

      // Wait for dialog to close
      await dialog.waitFor({state: 'hidden', timeout: 10_000});

      // Small delay to let the table refresh
      await page.waitForTimeout(1000);
    }

    // Verify all 3 accounts are visible in the table
    for (const account of CASH_ACCOUNTS) {
      await expect(page.locator('td', {hasText: account.name})).toBeVisible({timeout: 5_000});
    }
  });
});
