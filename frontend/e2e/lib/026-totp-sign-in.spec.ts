import { createHmac } from 'node:crypto';
import { expect, Locator, Page, test } from '@playwright/test';
import { libHelpers, UserFixture } from './helpers';

/**
 * Authenticator-app second factor (TOTP) in the standalone host: enrolment through Settings → Sign-in security, the
 * code prompt of the login page, a wrong code, a recovery code, and disabling. The codes are computed here from the
 * secret the setup returned, exactly as an authenticator app computes them (RFC 6238, HMAC-SHA1, six digits, 30 s).
 *
 * The spec owns the account `totp-e2e` and deletes it at the start and at the end. A run that stopped with the factor
 * enabled leaves an account no password alone can enter; the cleanup then lets the fixture administrator remove the
 * second factor first.
 */

const STEP_SECONDS = 30;
const ADMIN = 'admin';
test.use({ extraHTTPHeaders: { 'X-Forwarded-For': '198.51.100.231' } });

const RX = {
  settings: /^(Settings|Einstellungen)$/,
  signInSecurity: /^(Sign-in security|Anmeldesicherheit)\.\.\.$/,
  setup: /^(Set up authenticator app|Authenticator-App einrichten)$/,
  enable: /^(Enable|Aktivieren)$/,
  ok: /^OK$/,
  disable: /^(Disable authenticator app|Authenticator-App deaktivieren)$/,
  enabled: /(Enabled|Aktiviert)/,
  notEnabled: /(Not enabled|Nicht aktiviert)/
};

/** Decodes RFC 4648 Base32 without padding. */
function base32Decode(text: string): Buffer {
  const alphabet = 'ABCDEFGHIJKLMNOPQRSTUVWXYZ234567';
  const bytes: number[] = [];
  let buffer = 0;
  let bits = 0;
  for (const char of text.replace(/=+$/, '').toUpperCase()) {
    buffer = (buffer << 5) | alphabet.indexOf(char);
    bits += 5;
    if (bits >= 8) {
      bytes.push((buffer >>> (bits - 8)) & 0xff);
      bits -= 8;
    }
  }
  return Buffer.from(bytes);
}

/** The six-digit code of one time step. */
function totp(secretBase32: string, step: number): string {
  const counter = Buffer.alloc(8);
  counter.writeBigUInt64BE(BigInt(step));
  const hash = createHmac('sha1', base32Decode(secretBase32)).update(counter).digest();
  const offset = hash[hash.length - 1] & 0x0f;
  return String((hash.readUInt32BE(offset) % 2 ** 31) % 1_000_000).padStart(6, '0');
}

function currentStep(): number {
  return Math.floor(Date.now() / 1000 / STEP_SECONDS);
}

/** Leaves enough of the current step that the computed codes stay inside the window the server accepts. */
async function awaitFreshStep(): Promise<void> {
  const intoStep = Math.floor(Date.now() / 1000) % STEP_SECONDS;
  if (intoStep > STEP_SECONDS - 12) {
    await new Promise((resolve) => setTimeout(resolve, (STEP_SECONDS - intoStep + 1) * 1000));
  }
}

async function signInRequest(page: Page, email: string, password: string, secondFactorCode?: string) {
  return page.request.post('/api/login', {
    data: { email, password, timezoneOffset: 0, secondFactorCode },
    failOnStatusCode: false
  });
}

/** Opens Settings → Sign-in security and waits for the state of the factor. */
async function openSignInSecurity(page: Page): Promise<Locator> {
  await page.getByRole('menuitem', { name: RX.settings }).click();
  await page.getByRole('menuitem', { name: RX.signInSecurity }).click();
  const dialog = page.locator('sign-in-security-dialog .p-dialog');
  await dialog.waitFor({ state: 'visible' });
  // The state arrives with its own request; the action buttons appear with it
  await expect(dialog.getByRole('button', { name: RX.setup })).toBeVisible();
  return dialog;
}

/** Signs in through the login page; the code field must appear after the password was accepted. */
async function signInWithCode(page: Page, user: UserFixture, code: string): Promise<void> {
  await page.evaluate(() => sessionStorage.clear());
  await page.goto('/login');
  await page.locator('#email').fill(user.email);
  await page.locator('#password').fill(user.password);
  await expect(page.locator('#secondFactorCode')).toHaveCount(0);
  const asked = page.waitForResponse((response) => response.url().endsWith('/api/login'));
  await page.locator('button[type="submit"]').click();
  expect((await asked).status()).toBe(401);
  await expect(page.locator('#secondFactorCode')).toBeVisible();
  // The first prompt for the code is no error message
  await expect(page.locator('.p-toast-message-error')).toHaveCount(0);
  await page.locator('#secondFactorCode').fill(code);
  await page.locator('button[type="submit"]').click();
  await expect(page).toHaveURL(/\/mainview/);
}

test('enable an authenticator app, sign in with app and recovery code, then disable it', async ({ page }) => {
  const user = libHelpers.getUser('totp-e2e');
  const setupTenant = async () => {
    await page.locator('#tenantName').fill(user.tenantName);
    await page.locator('button[type="submit"]').click();
  };

  /** Removes the account of this spec, including a second factor a failed run left enabled. */
  const cleanup = async () => {
    let response = await signInRequest(page, user.email, user.password);
    if (response.status() === 401 && (await response.text()).includes('SecondFactorRequiredError')) {
      const admin = libHelpers.getUser(ADMIN);
      const adminLogin = await signInRequest(page, admin.email, admin.password);
      expect(adminLogin.ok(), 'fixture administrator signs in').toBe(true);
      const headers = { 'x-auth-token': adminLogin.headers()['x-auth-token'] };
      const users = (await (await page.request.get('/api/useradmin', { headers })).json()) as {
        idUser: number;
        email: string;
      }[];
      const own = users.find((candidate) => candidate.email === user.email);
      expect(own).toBeTruthy();
      const removed = await page.request.delete(`/api/useradmin/${own?.idUser}/secondfactor`, { headers });
      expect(removed.ok(), await removed.text()).toBe(true);
      response = await signInRequest(page, user.email, user.password);
    }
    if (!response.ok()) return;
    const deleted = await page.request.delete('/api/tenant', {
      headers: { 'x-auth-token': response.headers()['x-auth-token'] }
    });
    expect(deleted.ok(), await deleted.text()).toBe(true);
  };

  await cleanup();
  try {
    await libHelpers.registerAndSetupTenant(page, user, setupTenant);
    await libHelpers.loginAsFixtureUser(page, user.nickname);

    // Enrolment: password proof, QR code and secret, confirming code, recovery codes shown once
    let dialog = await openSignInSecurity(page);
    await expect(dialog).toContainText(RX.notEnabled);
    await dialog.getByRole('button', { name: RX.setup }).click();
    await dialog.locator('#password').fill(user.password);
    const setupAnswer = page.waitForResponse((response) => response.url().endsWith('/api/user/totp/setup'));
    await dialog.locator('button[type="submit"]').click();
    const setupResponse = await setupAnswer;
    expect(setupResponse.ok(), await setupResponse.text()).toBe(true);
    expect(setupResponse.headers()['cache-control']).toContain('no-store');
    const setup = (await setupResponse.json()) as { secretBase32: string; otpauthUri: string };
    expect(setup.otpauthUri).toContain('otpauth://totp/Grafiosch:');
    await expect(dialog.locator('img[alt="otpauth QR code"]')).toBeVisible();
    await expect(dialog.locator('.totp-secret')).toHaveText(setup.secretBase32);

    const jwtBefore = await page.evaluate(() => sessionStorage.getItem('jwt'));
    await awaitFreshStep();
    const enrolStep = currentStep() - 1;
    await dialog.locator('#totpCode').fill(totp(setup.secretBase32, enrolStep));
    const enableAnswer = page.waitForResponse((response) => response.url().endsWith('/api/user/totp/enable'));
    await dialog.locator('button[type="submit"]').click();
    const enableResponse = await enableAnswer;
    expect(enableResponse.ok(), await enableResponse.text()).toBe(true);
    const recoveryCodes = ((await enableResponse.json()) as { codes: string[] }).codes;
    expect(recoveryCodes).toHaveLength(10);
    const codeList = dialog.locator('.recovery-codes');
    for (const code of recoveryCodes) {
      await expect(codeList).toContainText(code);
    }
    // The replacement token keeps this session; the previous one has ended
    await expect.poll(() => page.evaluate(() => sessionStorage.getItem('jwt'))).not.toBe(jwtBefore);
    expect((await page.request.get('/api/user/own', { headers: { 'x-auth-token': jwtBefore ?? '' } })).ok()).toBe(
      false
    );
    await page.screenshot({ path: test.info().outputPath('totp-recovery-codes.png') });
    await dialog.getByRole('button', { name: RX.ok }).click();
    await expect(dialog).toContainText(RX.enabled);
    await expect(dialog).toContainText(/10/);
    await dialog.locator('.p-dialog-close-button').click();
    await expect(dialog).toHaveCount(0);

    // Password alone is no longer enough; a wrong code is refused with a message and the field is emptied
    await page.evaluate(() => sessionStorage.clear());
    await page.goto('/login');
    await page.locator('#email').fill(user.email);
    await page.locator('#password').fill(user.password);
    await page.locator('button[type="submit"]').click();
    await expect(page.locator('#secondFactorCode')).toBeVisible();
    await page.locator('#secondFactorCode').fill('12345');
    const refused = page.waitForResponse((response) => response.url().endsWith('/api/login'));
    await page.locator('button[type="submit"]').click();
    const refusal = await refused;
    expect(refusal.status()).toBe(401);
    expect(await refusal.text()).toContain('"invalidCode":true');
    await expect(page.locator('#secondFactorCode')).toHaveValue('');
    await expect(page).toHaveURL(/\/login/);

    // A code of the app signs in
    await page.locator('#secondFactorCode').fill(totp(setup.secretBase32, enrolStep + 1));
    await page.locator('button[type="submit"]').click();
    await expect(page).toHaveURL(/\/mainview/);

    // A recovery code signs in once, typed in lower case without the hyphen
    const recovery = recoveryCodes[0];
    await signInWithCode(page, user, recovery.toLowerCase().replace('-', ''));
    const notice = await libHelpers.findMailhogMessage(user.email, /9 recovery codes left/);
    expect(notice, 'notice of the spent recovery code').toBeTruthy();
    expect((await signInRequest(page, user.email, user.password, recovery)).status()).toBe(401);

    // Disabling needs password and a code; afterwards the password alone signs in again
    dialog = await openSignInSecurity(page);
    await expect(dialog).toContainText(RX.enabled);
    await dialog.getByRole('button', { name: RX.disable }).click();
    await dialog.locator('#password').fill(user.password);
    await dialog.locator('#secondFactorCode').fill(recoveryCodes[1]);
    const disableAnswer = page.waitForResponse((response) => response.url().endsWith('/api/user/totp/disable'));
    await dialog.locator('button[type="submit"]').click();
    expect((await disableAnswer).ok()).toBe(true);
    await expect(dialog).toContainText(RX.notEnabled);
    expect((await signInRequest(page, user.email, user.password)).ok()).toBe(true);
  } finally {
    await page.evaluate(() => sessionStorage.clear()).catch(() => undefined);
    await cleanup();
  }
});
