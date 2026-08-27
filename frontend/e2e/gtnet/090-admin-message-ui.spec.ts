import { expect, test } from '@playwright/test';
import { authHeaders, login, PEER_A_FRONTEND, PEER_B_OWN, readGTNet } from './peer.helpers';

const MESSAGE = 'GTNet two-peer browser admin message';

test('sends an admin-only message from the multi-target UI', async ({ page }) => {
  await login(page, PEER_A_FRONTEND);
  const headers = await authHeaders(page);
  const initial = await readGTNet(page);
  const ownedMessageIds: number[] = [];
  for (const peer of initial.gtNetList) {
    const response = await page.request.get(`/api/gtnet/messages/${peer.idGtNet}`, { headers });
    if (response.ok()) {
      const messages = (await response.json()) as any[];
      ownedMessageIds.push(
        ...messages.filter((message) => message.message === MESSAGE).map((message) => message.idGtNetMessage)
      );
    }
  }
  if (ownedMessageIds.length) {
    const cleanup = await page.request.post('/api/gtnet/deletemessagebatch', { headers, data: ownedMessageIds });
    expect(cleanup.ok(), await cleanup.text()).toBeTruthy();
  }
  await page.goto(`${PEER_A_FRONTEND}/mainview/gtnettabmenu/gtnetadminmessages`);
  const table = page.locator('.data-container-full, .data-container').first();
  await table.waitFor({ state: 'visible' });
  const peerRow = table.locator('tbody tr', { hasText: PEER_B_OWN! }).first();
  await peerRow.click();
  await peerRow.click({ button: 'right' });
  await page
    .locator('[role="menu"]:visible')
    .getByRole('menuitem', { name: /Send admin message|Admin-Nachricht senden|GT_NET_ADMIN_MESSAGE_SEND/ })
    .click();
  const dialog = page.locator('.p-dialog:visible');
  await dialog.locator('#message').fill(MESSAGE);
  const visibility = dialog.locator('#visibility');
  if (await visibility.isVisible()) await visibility.selectOption('ADMIN_ONLY');
  const response = page.waitForResponse((candidate) => candidate.url().endsWith('/api/gtnet/submitmsgmulti'));
  await dialog.locator('button[type="submit"]').click();
  expect((await response).ok()).toBeTruthy();
  expect(JSON.stringify(await readGTNet(page))).not.toContain('tokenRemote');
});
