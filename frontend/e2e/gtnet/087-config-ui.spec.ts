import { expect, test } from '@playwright/test';
import { login, openSetup, PEER_A_FRONTEND, PEER_B_OWN, readGTNet } from './peer.helpers';

test('edits the handshake-created peer configuration through the setup table', async ({ page }) => {
  await login(page, PEER_A_FRONTEND);
  await openSetup(page, PEER_A_FRONTEND);
  const table = page.locator('.data-container-full').first();
  const row = table.locator('tbody tr', { hasText: PEER_B_OWN! }).first();
  await row.click();
  await row.click({ button: 'right' });
  const menu = page.locator('[role="menu"]:visible');
  await menu
    .getByRole('menuitem', { name: /Configuration|Konfiguration|GT_NET_CONFIG_EDIT/ })
    .first()
    .click();
  const dialog = page.locator('.p-dialog:visible');
  await dialog.locator('#connectionTimeout').fill('12');
  const saved = page.waitForResponse(
    (response) => response.url().includes('/api/gtnetconfig') && response.request().method() === 'PUT'
  );
  await dialog.locator('button[type="submit"]').click();
  expect((await saved).ok()).toBeTruthy();
  await dialog.waitFor({ state: 'hidden' });
  const remote = (await readGTNet(page)).gtNetList.find((entry: any) => entry.domainRemoteName === PEER_B_OWN);
  expect(remote.gtNetConfig.connectionTimeout).toBe(12);
});
