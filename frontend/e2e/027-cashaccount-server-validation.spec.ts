import { test, expect } from '@playwright/test';
import { loginAsFixtureUser } from './helpers';
import { loadPortfolios, portfolioNode, RX } from './portfolio.helpers';

/**
 * A validation error of the server appears at the field it names in the signal form cash account dialog. The field
 * stays invalid, and the submit button disabled, until the user changes the rejected value; then the message
 * disappears and the dialog can be saved again. The save is answered by an intercepted 400 response, so the spec
 * changes no data and needs no cleanup. It needs the portfolio and cash accounts of 025-create-portfolio.spec.ts.
 */
const PORTFOLIO = loadPortfolios()[0];
const ACCOUNT = PORTFOLIO.cashAccounts[0];
const SERVER_MESSAGE = 'The server rejects this name';

test('shows a server validation error at its field and withdraws it when the value changes', async ({ page }) => {
  page.setDefaultTimeout(15_000);
  await loginAsFixtureUser(page, PORTFOLIO.loginNickname);
  await portfolioNode(page, PORTFOLIO).click();
  const row = page
    .locator('.datatable tbody tr')
    .filter({ has: page.getByText(ACCOUNT.name, { exact: true }) })
    .first();
  await row.click();
  await row.click({ button: 'right' });
  await page.locator('[role="menu"]:visible').getByText(RX.editCashAccount).click();
  const dialog = page.locator('.p-dialog:visible');
  await expect(dialog.locator('#name')).toHaveValue(ACCOUNT.name);

  await page.route(
    '**/api/cashaccount',
    (route) =>
      route.request().method() === 'PUT'
        ? route.fulfill({
            status: 400,
            json: { className: 'ValidationError', error: { fieldErrors: [{ field: 'name', message: SERVER_MESSAGE }] } }
          })
        : route.continue(),
    { times: 1 }
  );
  const submit = dialog.locator('button[type="submit"]');
  await submit.click();

  const fieldError = dialog.locator('.alert-danger', { hasText: SERVER_MESSAGE });
  await expect(fieldError).toBeVisible();
  await expect(submit).toBeDisabled();

  await dialog.locator('#name').fill(`${ACCOUNT.name} changed`);
  await expect(fieldError).toHaveCount(0);
  await expect(submit).toBeEnabled();

  await dialog.locator('.p-dialog-close-button').click();
  await expect(dialog).toHaveCount(0);
  await expect(row).toContainText(ACCOUNT.name);
});
