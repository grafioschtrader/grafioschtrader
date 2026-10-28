import { expect, test } from '@playwright/test';
import { login, PEER_A_FRONTEND, readGTNet } from './peer.helpers';

test('builds exchange-log tabs from the backend registry and renders the traffic tree', async ({ page }) => {
  await login(page, PEER_A_FRONTEND);
  const kinds = (await readGTNet(page)).exchangeKindTypes;
  await page.goto(`${PEER_A_FRONTEND}/mainview/gtnetexchangelog`);
  // One tab per registered kind, labelled with the translated kind name rather than the constant
  const tabs = page.getByRole('tab');
  await expect(tabs).toHaveCount(kinds.length);
  for (const kind of kinds) await expect(tabs.filter({ hasText: kind.name })).toHaveCount(0);
  await tabs.first().click();
  await expect(page.locator('configurable-table').first()).toBeVisible();
});
