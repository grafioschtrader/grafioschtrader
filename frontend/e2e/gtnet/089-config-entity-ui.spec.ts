import { expect, test } from '@playwright/test';
import { login, openSetup, PEER_A_FRONTEND, PEER_B_OWN, readGTNet } from './peer.helpers';

test('renders backend kind names and edits the data-request-created entity configuration', async ({ page }) => {
  await login(page, PEER_A_FRONTEND);
  const state = await readGTNet(page);
  const kindNames = state.exchangeKindTypes.map((kind: any) => kind.name);
  await openSetup(page, PEER_A_FRONTEND);
  const outer = page.locator('.data-container-full').first();
  const peerRow = outer.locator('tbody tr', { hasText: PEER_B_OWN! }).first();
  await peerRow.locator('a[href="#"]').first().click();
  const nested = page.locator('gtnet-config-entity-table configurable-table').first();
  await nested.waitFor({ state: 'visible' });
  for (const kind of kindNames) await expect(nested).toContainText(kind);
  const configRow = nested.locator('tbody tr').first();
  await configRow.click();
  await configRow.click({ button: 'right' });
  await page
    .locator('[role="menu"]:visible')
    .getByRole('menuitem', { name: /Edit|Bearbeiten|EDIT_RECORD/ })
    .click();
  const dialog = page.locator('.p-dialog:visible');
  await dialog.locator('#supplierLog').selectOption('SCL_OVERVIEW');
  await dialog.locator('#consumerLog').selectOption('SCL_OVERVIEW');
  await dialog.locator('#consumerUsage').fill('7');
  const saved = page.waitForResponse(
    (response) => response.url().includes('/api/gtnetconfigentity') && response.request().method() === 'PUT'
  );
  await dialog.locator('button[type="submit"]').click();
  expect((await saved).ok()).toBeTruthy();
});
