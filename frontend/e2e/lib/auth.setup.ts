import {expect, Page, test as setup} from '@playwright/test';

import {CsvUser, libHelpers} from './helpers';

/**
 * Registers every users.csv row of `grafiosch-test-integration` tagged `e2e='e'` through the browser, verifies it via
 * MailHog/Mailpit and creates its tenant. The rows tagged `'i'` are created earlier and at REST level by the backend
 * `ResourceTestSuite`, so a spec can log in as either kind.
 *
 * This replaces the former hard-coded sign-in of `admin@test.local`: since the JDBC seeder
 * `IntegrationE2EDataInitializer` was removed, users exist only because the registration flow created them — which is
 * exactly what this setup covers.
 */

/** Fills the tenant page of the standalone host, which asks for nothing but the tenant name. */
async function setupGrafioschTenant(page: Page, user: CsvUser): Promise<void> {
  const tenantNameInput = page.locator('#tenantName');
  await tenantNameInput.waitFor({state: 'visible', timeout: 15_000});
  await tenantNameInput.fill(user.tenantName);
  await page.locator('button[type="submit"]').click();
}

setup('register the lib e2e users from users.csv, verify, and setup their tenants', async ({page}) => {
  const e2eUsers = libHelpers.loadE2EUsers();
  expect(e2eUsers, `No rows with e2e='e' found in ${libHelpers.config.usersCsv}`).not.toHaveLength(0);
  await libHelpers.clearMailhog();
  for (let i = 0; i < e2eUsers.length; i++) {
    await libHelpers.registerAndSetupTenant(page, e2eUsers[i], setupGrafioschTenant);
    libHelpers.saveCredentials(e2eUsers[i], i === 0);
  }
});
