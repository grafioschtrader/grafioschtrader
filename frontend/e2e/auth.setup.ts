import { expect, Page, test as setup } from '@playwright/test';

import { UserFixture } from './lib/helpers';
import { gtHelpers } from './helpers';

/**
 * Registers every users.json object tagged `e2e='e'` through the browser, verifies it via MailHog and sets its tenant
 * up. The objects tagged `'i'` are created earlier and at REST level by backend `ResourceTestSuite_1`.
 *
 * The flow itself is shared with the portable library suite (`e2e/lib/helpers.ts`); only the tenant screen below is
 * specific to Grafioschtrader, because its tenant needs a currency.
 */

/** Fills the Grafioschtrader tenant page: name by convention plus the currency from users.json. */
async function setupGtTenant(page: Page, user: UserFixture): Promise<void> {
  // Name follows the UserResourceTest convention "Tenant " + nickname.
  const tenantNameInput = page.locator('#tenantName');
  await tenantNameInput.waitFor({ state: 'visible', timeout: 15_000 });
  await page.waitForTimeout(2000);
  await tenantNameInput.fill(user.tenantName);

  const currencySelect = page.locator('select#currency');
  await currencySelect.waitFor({ state: 'visible', timeout: 10_000 });
  await page.waitForFunction(
    () => {
      const sel = document.querySelector('select#currency') as HTMLSelectElement;
      return sel && sel.options.length > 1;
    },
    { timeout: 10_000 }
  );
  await currencySelect.selectOption({ label: user.currency });

  await page.locator('button[type="submit"]').click();
}

setup('register all e2e users from users.json, verify, and setup their tenants', async ({ page }) => {
  const e2eUsers = gtHelpers.loadE2EUsers();
  expect(e2eUsers, `No objects with e2e='e' found in ${gtHelpers.config.usersJson}`).not.toHaveLength(0);
  await gtHelpers.clearMailhog();
  for (let i = 0; i < e2eUsers.length; i++) {
    await gtHelpers.registerAndSetupTenant(page, e2eUsers[i], setupGtTenant);
    gtHelpers.saveCredentials(e2eUsers[i], i === 0);
  }
});
