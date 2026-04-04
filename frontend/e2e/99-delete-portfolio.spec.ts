import {test, expect} from '@playwright/test';
import {login} from './helpers';

test.describe.serial('Delete portfolio with accounts (cleanup)', () => {

  const PORTFOLIO_NAME = 'Test Portfolio';
  const SECURITY_ACCOUNT_NAME = 'Test Securities';
  const CASH_ACCOUNTS = [
    {name: 'CHF Account', currency: 'CHF'},
    {name: 'USD Account', currency: 'USD'},
    {name: 'EUR Account', currency: 'EUR'},
  ];

  test('should delete 3 cash accounts', async ({page}) => {
    await login(page);

    // Navigate to the portfolio cash account view
    const portfolioText = page.getByText(`${PORTFOLIO_NAME} / CHF`);
    await portfolioText.click();
    await page.waitForTimeout(3000);

    for (const account of CASH_ACCOUNTS) {
      // Click the data row with the account name to select it (use selectable row to exclude header)
      const row = page.locator('tr[data-p-selectable-row]', {hasText: account.name});
      await row.waitFor({state: 'visible', timeout: 10_000});
      await row.click();
      await page.waitForTimeout(500);

      // Right-click the selected row to open context menu
      await row.click({button: 'right'});

      // Click the "Delete Cash account" menu item
      const menuList = page.locator('[role="menu"]:visible');
      await menuList.waitFor({state: 'visible', timeout: 5_000});
      await menuList.getByText(/Delete/i).first().click();

      // Confirm the deletion in the PrimeNG confirm dialog
      const confirmDialog = page.getByRole('alertdialog', {name: 'Confirmation'});
      await confirmDialog.waitFor({state: 'visible', timeout: 5_000});
      // Click the "yes" accept button
      await confirmDialog.getByRole('button', {name: /yes/i}).click();

      // Wait for the delete to complete
      await page.waitForTimeout(2000);
    }
  });

  test('should delete the securities account', async ({page}) => {
    await login(page);

    // Expand the portfolio node
    const portfolioText = page.getByText(`${PORTFOLIO_NAME} / CHF`);
    await portfolioText.waitFor({state: 'visible', timeout: 10_000});
    await portfolioText.dblclick();

    // Wait for and expand "Securities accounts" to show the account
    const secAccountsText = page.getByText('Securities accounts');
    await secAccountsText.waitFor({state: 'visible', timeout: 10_000});
    await secAccountsText.dblclick();

    // Right-click on "Test Securities" node
    const secAccountText = page.getByText(SECURITY_ACCOUNT_NAME);
    await secAccountText.waitFor({state: 'visible', timeout: 10_000});
    await secAccountText.click({button: 'right'});

    // Click "Delete" menu item
    await page.locator('p-contextmenu').getByText('Delete', {exact: true}).click();

    // Confirm the deletion
    const confirmDialog = page.getByRole('alertdialog', {name: 'Confirmation'});
    await confirmDialog.waitFor({state: 'visible', timeout: 5_000});
    await confirmDialog.getByRole('button', {name: /yes/i}).click();

    // Wait for the node to disappear
    await secAccountText.waitFor({state: 'hidden', timeout: 10_000});
  });

  test('should delete the portfolio', async ({page}) => {
    await login(page);

    // Right-click on "Test Portfolio / CHF" node
    const portfolioText = page.getByText(`${PORTFOLIO_NAME} / CHF`);
    await portfolioText.waitFor({state: 'visible', timeout: 10_000});
    await portfolioText.click({button: 'right'});

    // Click "Delete" menu item (may say "Delete" or "Delete Portfolio...")
    await page.locator('p-contextmenu').getByText('Delete', {exact: true}).click();

    // Confirm the deletion
    const confirmDialog2 = page.getByRole('alertdialog', {name: 'Confirmation'});
    await confirmDialog2.waitFor({state: 'visible', timeout: 5_000});
    await confirmDialog2.getByRole('button', {name: /yes/i}).click();

    // Wait for the portfolio to disappear from the tree
    await portfolioText.waitFor({state: 'hidden', timeout: 10_000});
  });
});
