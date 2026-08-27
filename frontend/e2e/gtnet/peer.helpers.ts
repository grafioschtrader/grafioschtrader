import { expect, Page } from '@playwright/test';

export const ADMIN = { email: 'admin@test.local', password: 'A123abcd' };
export const PEER_A_FRONTEND = process.env.GTNET_PEER_A_FRONTEND_URL ?? 'http://localhost:4201';
export const PEER_B_FRONTEND = process.env.GTNET_PEER_B_FRONTEND_URL ?? 'http://localhost:4202';
export const PEER_A_OWN = process.env.GTNET_PEER_A_OWN_URL;
export const PEER_B_OWN = process.env.GTNET_PEER_B_OWN_URL;

export async function login(page: Page, baseURL: string, user = ADMIN): Promise<void> {
  await page.goto(`${baseURL}/login`);
  await page.locator('#email').fill(user.email);
  await page.locator('#password').fill(user.password);
  await page.locator('button[type="submit"]').click();
  await page.waitForURL(/\/mainview/, { timeout: 15_000 });
}

export async function authHeaders(page: Page): Promise<Record<string, string>> {
  const token = await page.evaluate(() => sessionStorage.getItem('jwt'));
  expect(token).toBeTruthy();
  return { 'x-auth-token': token! };
}

export async function readGTNet(page: Page): Promise<any> {
  const response = await page.request.get('/api/gtnet/gtnetwithmessage', { headers: await authHeaders(page) });
  expect(response.ok(), await response.text()).toBeTruthy();
  return response.json();
}

export async function openSetup(page: Page, baseURL: string): Promise<void> {
  await page.goto(`${baseURL}/mainview/gtnettabmenu/gtnetsetup`);
  await page.locator('.data-container-full').first().waitFor({ state: 'visible' });
}

export async function openCreate(page: Page): Promise<void> {
  const table = page.locator('.data-container-full').first();
  await table.click();
  await table.click({ button: 'right' });
  await page
    .locator('[role="menu"]:visible')
    .getByRole('menuitem', { name: /Create|Erstellen|CREATE/ })
    .first()
    .click();
  await page.locator('.p-dialog:visible').waitFor({ state: 'visible' });
}

export async function ensureOwnPeer(page: Page, baseURL: string, ownURL: string | undefined): Promise<void> {
  expect(ownURL, 'runner-resolved non-loopback peer URL').toBeTruthy();
  const before = await readGTNet(page);
  if (before.gtNetMyEntryId) return;
  await openSetup(page, baseURL);
  await openCreate(page);
  const dialog = page.locator('.p-dialog:visible');
  const timezone = dialog.locator('#timeZone');
  await expect.poll(() => timezone.locator('option').count()).toBeGreaterThan(1);
  await dialog.locator('#domainRemoteName').fill(ownURL!);
  await timezone.selectOption('UTC').catch(async () => {
    const value = await timezone.locator('option').nth(1).getAttribute('value');
    await timezone.selectOption(value!);
  });
  await dialog.locator('#dailyRequestLimit').fill('1500');
  await dialog.locator('#allowServerCreation').setChecked(true);
  for (const row of await dialog.locator('editable-table tbody tr').all()) {
    await row.locator('select').nth(0).selectOption('AC_OPEN');
    await row.locator('select').nth(1).selectOption('SS_OPEN');
    await row.locator('input[type="number"]').fill('250');
  }
  await dialog
    .locator('p-button button')
    .filter({ hasText: /Save|Speichern|SAVE/ })
    .click();
  await dialog.waitFor({ state: 'hidden' });
  expect((await readGTNet(page)).gtNetMyEntryId).toBeTruthy();
}

export async function ensureRemotePeer(page: Page, baseURL: string, remoteURL: string | undefined): Promise<any> {
  expect(remoteURL).toBeTruthy();
  let data = await readGTNet(page);
  let remote = data.gtNetList.find((entry: any) => entry.domainRemoteName === remoteURL);
  if (remote) return remote;
  await openSetup(page, baseURL);
  await openCreate(page);
  const dialog = page.locator('.p-dialog:visible');
  await dialog.locator('#domainRemoteName').fill(remoteURL!);
  await dialog.locator('button[type="submit"]').click();
  await dialog.waitFor({ state: 'hidden' });
  data = await readGTNet(page);
  remote = data.gtNetList.find((entry: any) => entry.domainRemoteName === remoteURL);
  expect(remote).toBeTruthy();
  return remote;
}

export async function resetRemotePeer(page: Page, remoteURL: string | undefined): Promise<void> {
  expect(remoteURL).toBeTruthy();
  const data = await readGTNet(page);
  const remote = data.gtNetList.find((entry: any) => entry.domainRemoteName === remoteURL);
  if (!remote) return;
  const response = await page.request.delete(`/api/gtnet/${remote.idGtNet}`, { headers: await authHeaders(page) });
  expect(response.ok(), `resetting remote peer ${remoteURL}: ${await response.text()}`).toBeTruthy();
}

export async function sendMessageFromRow(page: Page, baseURL: string, domain: string, code: string): Promise<void> {
  await openSetup(page, baseURL);
  const table = page.locator('.data-container-full').first();
  const row = table.locator('tbody tr', { hasText: domain }).first();
  await row.click();
  await row.click({ button: 'right' });
  const menu = page.locator('[role="menu"]:visible');
  await menu.getByRole('menuitem', { name: /Send message|Nachricht senden|GT_NET_MESSAGE_SEND/ }).click();
  const dialog = page.locator('.p-dialog:visible');
  await dialog.locator('#messageCode').selectOption(code);
  const response = page.waitForResponse((candidate) => candidate.url().endsWith('/api/gtnet/submitmsg'));
  await dialog.locator('button[type="submit"]').click();
  expect((await response).ok()).toBeTruthy();
  await dialog.waitFor({ state: 'hidden' });
}
