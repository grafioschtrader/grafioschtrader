import { expect, Page, test } from '@playwright/test';
import { libHelpers } from './helpers';

// A second login in the same tab must render from that login's features. The administrator switches GTNet on through
// g.gnet.use while logged in without it, logs out and logs in again without reloading the page: the application
// instance, and with it GlobalparameterService, stays the same, so a feature set cached by the first login would hide
// the GTNet branch of the main tree. The parameter is restored to its original value in finally.

const GTNET_USE = 'g.gnet.use';
const GTNET_NODE = /^\s*(GTNet and messages|GTNet und Nachrichten|GT_NET_NET_AND_MESSAGE)\s*$/;

interface Globalparameter {
  propertyName: string;
  propertyInt: number | null;
  [key: string]: unknown;
}

async function headers(page: Page): Promise<Record<string, string>> {
  const token = await page.evaluate(() => sessionStorage.getItem('jwt'));
  expect(token, 'JWT after login').toBeTruthy();
  return { 'x-auth-token': token! };
}

async function readGtNetUse(page: Page): Promise<Globalparameter | undefined> {
  const response = await page.request.get('/api/globalparameters', { headers: await headers(page) });
  expect(response.ok(), await response.text()).toBeTruthy();
  return ((await response.json()) as Globalparameter[]).find((gp) => gp.propertyName === GTNET_USE);
}

async function writeGtNetUse(page: Page, original: Globalparameter | undefined, value: number): Promise<void> {
  const response = await page.request.put('/api/globalparameters', {
    headers: await headers(page),
    data: { ...(original ?? { propertyName: GTNET_USE }), propertyInt: value }
  });
  expect(response.ok(), `setting ${GTNET_USE}=${value}: ${await response.text()}`).toBeTruthy();
}

function gtNetNode(page: Page) {
  return page.locator('.p-tree-node-content', { hasText: GTNET_NODE });
}

test('a login after logout in the same tab shows the features of that login', async ({ page }) => {
  const credentials = await libHelpers.loginAsFixtureUser(page, 'admin');
  const original = await readGtNetUse(page);
  try {
    await writeGtNetUse(page, original, 0);
    // A fresh page load, so the first session starts without GTNet
    await libHelpers.loginAsFixtureUser(page, 'admin');
    await expect(page.locator('p-tree')).toBeVisible();
    await expect(gtNetNode(page)).toHaveCount(0);

    await writeGtNetUse(page, original, 1);
    await page.getByRole('menuitem', { name: /^(Logout|Abmelden)$/ }).click();
    await page.locator('#email').waitFor({ state: 'visible', timeout: 15_000 });
    await page.locator('#email').fill(credentials.email);
    await page.locator('#password').fill(credentials.password);
    await page.locator('button[type="submit"]').click();
    await page.locator('p-tree').waitFor({ state: 'visible', timeout: 15_000 });
    await expect(gtNetNode(page)).toBeVisible();
  } finally {
    if (original) {
      await writeGtNetUse(page, original, original.propertyInt ?? 0);
    }
  }
});
