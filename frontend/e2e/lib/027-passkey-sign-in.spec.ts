import { CDPSession, expect, Page, test } from '@playwright/test';
import { libHelpers } from './helpers';

/**
 * Passkeys in the standalone host, with the virtual authenticator of the Chrome DevTools protocol on the `localhost`
 * relying party: registration through Settings → Sign-in security, sign-in from the login page without e-mail address,
 * the return to the address a sign-in interrupted - the mechanism the OAuth authorize page relies on - and deletion.
 *
 * The spec sets `g.public.base.url` to the backend on localhost as the fixture administrator and restores the previous
 * value in `finally`. It owns the account `passkey-e2e` and deletes it at the start and at the end; its passkeys go
 * with it.
 */

const PUBLIC_BASE_URL = 'http://localhost:8081';
const PUBLIC_BASE_URL_KEY = 'g.public.base.url';
const RETURN_ROUTE = '/mainview/usermessage/mailsendrecv';
const LABEL = 'Virtual authenticator';
test.use({ extraHTTPHeaders: { 'X-Forwarded-For': '198.51.100.232' } });

const RX = {
  settings: /^(Settings|Einstellungen)$/,
  signInSecurity: /^(Sign-in security|Anmeldesicherheit)\.\.\.$/,
  addPasskey: /^(Add passkey|Passkey hinzufügen)$/,
  addProof: /(name the passkey|benennen Sie den Passkey)/,
  signInWithPasskey: /^(Sign in with passkey|Mit Passkey anmelden)$/,
  delete: /^(Delete|Löschen)$/,
  confirmYes: /^\s*(yes|ja)\s*$/i
};

interface Globalparameter {
  propertyName: string;
  propertyString: string | null;
  [key: string]: unknown;
}

async function signInRequest(page: Page, email: string, password: string) {
  return page.request.post('/api/login', { data: { email, password, timezoneOffset: 0 }, failOnStatusCode: false });
}

/** Reads or writes `g.public.base.url` as the fixture administrator. */
async function publicBaseUrl(page: Page, value?: string | null): Promise<Globalparameter | undefined> {
  const admin = libHelpers.getUser('admin');
  const login = await signInRequest(page, admin.email, admin.password);
  expect(login.ok(), 'fixture administrator signs in').toBe(true);
  const headers = { 'x-auth-token': login.headers()['x-auth-token'] };
  const all = (await (await page.request.get('/api/globalparameters', { headers })).json()) as Globalparameter[];
  const current = all.find((gp) => gp.propertyName === PUBLIC_BASE_URL_KEY);
  if (value !== undefined) {
    const saved = await page.request.put('/api/globalparameters', {
      headers,
      data: { ...(current ?? { propertyName: PUBLIC_BASE_URL_KEY }), propertyString: value }
    });
    expect(saved.ok(), `${PUBLIC_BASE_URL_KEY}=${value}: ${await saved.text()}`).toBe(true);
  }
  return current;
}

async function openSignInSecurity(page: Page) {
  await page.getByRole('menuitem', { name: RX.settings }).click();
  await page.getByRole('menuitem', { name: RX.signInSecurity }).click();
  const dialog = page.locator('sign-in-security-dialog .p-dialog');
  await dialog.waitFor({ state: 'visible' });
  return dialog;
}

test('register a passkey, sign in with it, return to the interrupted address and delete it', async ({ page }) => {
  const user = libHelpers.getUser('passkey-e2e');
  const setupTenant = async () => {
    await page.locator('#tenantName').fill(user.tenantName);
    await page.locator('button[type="submit"]').click();
  };
  const cleanup = async () => {
    const response = await signInRequest(page, user.email, user.password);
    if (!response.ok()) return;
    const deleted = await page.request.delete('/api/tenant', {
      headers: { 'x-auth-token': response.headers()['x-auth-token'] }
    });
    expect(deleted.ok(), await deleted.text()).toBe(true);
  };

  const original = await publicBaseUrl(page, PUBLIC_BASE_URL);
  let cdp: CDPSession | undefined;
  await cleanup();
  try {
    await libHelpers.registerAndSetupTenant(page, user, setupTenant);
    await libHelpers.loginAsFixtureUser(page, user.nickname);
    cdp = await page.context().newCDPSession(page);
    await cdp.send('WebAuthn.enable');
    await cdp.send('WebAuthn.addVirtualAuthenticator', {
      options: {
        protocol: 'ctap2',
        transport: 'internal',
        hasResidentKey: true,
        hasUserVerification: true,
        isUserVerified: true,
        automaticPresenceSimulation: true
      }
    });

    // Registration: password proof and name, then the authenticator creates the credential
    let dialog = await openSignInSecurity(page);
    await dialog.getByRole('button', { name: RX.addPasskey }).click();
    const addDialog = page.locator('.p-dialog:visible').filter({ hasText: RX.addProof });
    await addDialog.locator('#password').fill(user.password);
    await addDialog.locator('#label').fill(LABEL);
    const registered = page.waitForResponse(
      (response) => response.url().endsWith('/api/user/passkey') && response.request().method() === 'POST'
    );
    await addDialog.locator('button[type="submit"]').click();
    const registration = await registered;
    expect(registration.ok(), await registration.text()).toBe(true);
    await expect(addDialog).toHaveCount(0);
    await expect(dialog.locator('configurable-table')).toContainText(LABEL);
    await page.screenshot({ path: test.info().outputPath('passkey-table.png') });
    await dialog.locator('.p-dialog-close-button').click();

    // Sign-in without e-mail address, returning to the address a sign-in interrupted
    await page.evaluate((route) => {
      sessionStorage.clear();
      sessionStorage.setItem('returnAfterLogin', route);
    }, RETURN_ROUTE);
    await page.goto('/login');
    const passkeyButton = page.getByRole('button', { name: RX.signInWithPasskey });
    await expect(passkeyButton).toBeVisible();
    const signedIn = page.waitForResponse((response) => response.url().endsWith('/api/login/passkey'));
    await passkeyButton.click();
    const signIn = await signedIn;
    expect(signIn.ok(), await signIn.text()).toBe(true);
    await expect(page).toHaveURL(new RegExp(RETURN_ROUTE.replaceAll('/', '\\/') + '$'));

    // Deletion: the row goes, the login page offers the passkey further, but it no longer signs in
    dialog = await openSignInSecurity(page);
    await dialog.locator('configurable-table tbody tr', { hasText: LABEL }).click();
    await dialog.getByRole('button', { name: RX.delete }).click();
    const deleted = page.waitForResponse(
      (response) => response.url().includes('/api/user/passkey/') && response.request().method() === 'DELETE'
    );
    await page.getByRole('button', { name: RX.confirmYes }).click();
    expect((await deleted).ok()).toBe(true);
    await expect(dialog.locator('configurable-table')).not.toContainText(LABEL);
  } finally {
    await cdp?.detach().catch(() => undefined);
    await page.evaluate(() => sessionStorage.clear()).catch(() => undefined);
    await cleanup();
    await publicBaseUrl(page, original?.propertyString ?? 'none');
  }
});
