import { expect, test } from '@playwright/test';
import { login, openSetup, PEER_A_FRONTEND, PEER_B_OWN, typeNumber } from './peer.helpers';

test('renders backend kind names and edits the data-request-created entity configuration', async ({ page }) => {
  await login(page, PEER_A_FRONTEND);
  await openSetup(page, PEER_A_FRONTEND);
  const outer = page.locator('.data-container-full').first();
  const peerRow = outer.locator('tbody tr', { hasText: PEER_B_OWN! }).first();
  await peerRow.locator('a[href="#"]').first().click();
  // The expanded row is an accordion that renders the entity table only while its panel is open.
  await page
    .locator('gtnet-expanded p-accordion-header')
    .filter({ hasText: /Entity Configuration|Entity-Konfiguration|GT_NET_CONFIG_ENTITY_TABLE/ })
    .first()
    .click();
  const nested = page.locator('gtnet-config-entity-table configurable-table').first();
  await nested.waitFor({ state: 'visible' });
  // The kind of the data request accepted in 086 is rendered with its translated name, not as a number or a constant
  await expect(nested).toContainText(/Integration stream|Integrations-Datenstrom/);
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
  await typeNumber(dialog.locator('#consumerUsage'), '7');
  const saved = page.waitForResponse(
    (response) => response.url().includes('/api/gtnetconfigentity') && response.request().method() === 'PUT'
  );
  await dialog.locator('button[type="submit"]').click();
  expect((await saved).ok()).toBeTruthy();
});
