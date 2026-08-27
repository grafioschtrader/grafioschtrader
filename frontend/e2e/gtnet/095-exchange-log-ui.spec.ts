import { expect, test } from '@playwright/test';
import { login, PEER_A_FRONTEND, readGTNet } from './peer.helpers';

test('builds exchange-log tabs from the backend registry and renders the traffic tree', async ({ page }) => {
  await login(page, PEER_A_FRONTEND);
  const kinds = (await readGTNet(page)).exchangeKindTypes;
  await page.goto(`${PEER_A_FRONTEND}/mainview/gtnetexchangelog`);
  for (const kind of kinds) await expect(page.getByText(kind.name, { exact: true }).first()).toBeVisible();
  await page.getByText(kinds[0].name, { exact: true }).first().click();
  await expect(page.locator('configurable-table').first()).toBeVisible();
});
