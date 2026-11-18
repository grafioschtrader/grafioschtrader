import { expect, test } from '@playwright/test';
import { libHelpers, UserFixture } from './helpers';

const passwordNew = 'B456defg';
test.use({ extraHTTPHeaders: { 'X-Forwarded-For': '198.51.100.230' } });

test('reset by mailbox proof and fragment link, then sign in with the new password', async ({ page }) => {
  const user: UserFixture = { ...libHelpers.getUser('reset-e2e'), passwordEdit: { passwordNew } };
  const setupTenant = async () => {
    await page.locator('#tenantName').fill(user.tenantName);
    await page.locator('button[type="submit"]').click();
  };
  const signIn = async (password: string) =>
    page.request.post('/api/login', {
      data: { email: user.email, password, timezoneOffset: 0 }
    });
  const cleanup = async () => {
    // Reconcile this account only, including an interrupted registration or an already committed reset.
    let response = await signIn(passwordNew);
    if (!response.ok()) response = await signIn(user.password);
    if (!response.ok()) {
      const mail = await libHelpers.findMailhogMessage(user.email, /tokenverify/, 1);
      const verification = mail?.body.match(/tokenverify\?token=([0-9a-f-]{36})/i)?.[1];
      if (!verification) return;
      await page.request.get(`/api/user/tokenverify/${verification}`);
      response = await signIn(user.password);
      if (!response.ok()) return;
    }
    await libHelpers.registerAndSetupTenant(page, user, setupTenant);
    response = await signIn(passwordNew);
    if (!response.ok()) response = await signIn(user.password);
    expect(response.ok()).toBe(true);
    const removed = await page.request.delete('/api/tenant', {
      headers: { 'x-auth-token': response.headers()['x-auth-token'] }
    });
    expect(removed.ok()).toBe(true);
    await page.evaluate(() => sessionStorage.clear());
  };
  await cleanup();
  const mailbox = await page.request.get(
    `${libHelpers.config.mailApiBase}/api/v2/search?kind=to&query=${encodeURIComponent(user.email)}`
  );
  const oldMail = (await mailbox.json()) as { items: { ID: string }[] };
  for (const message of oldMail.items ?? []) {
    const removed = await page.request.delete(`${libHelpers.config.mailApiBase}/api/v1/messages/${message.ID}`);
    expect(removed.ok()).toBe(true);
  }
  try {
    await libHelpers.registerAndSetupTenant(page, user, setupTenant);
    await page.goto('/login');
    await page.getByRole('button', { name: /^(Forgot password|Passwort vergessen)$/ }).click();
    await expect(page).toHaveURL(/\/passwordreset$/);
    await page.locator('#email').fill(user.email);
    const requested = page.waitForResponse((response) => response.url().endsWith('/api/user/passwordreset'));
    await page.locator('button[type="submit"]').click();
    const reply = await requested;
    expect(reply.ok()).toBe(true);
    expect(reply.request().headers()['x-auth-token']).toBeUndefined();
    await expect(page.locator('#token')).toBeVisible();
    const mail = await libHelpers.findMailhogMessage(user.email, /Reset your password|Passwort zurücksetzen/);
    const token = mail?.body.match(/\b[A-Za-z0-9_-]{43}\b/)?.[0];
    expect(token).toBeTruthy();
    await page.goto(`/passwordreset#token=${token}`);
    await expect(page).toHaveURL(/\/passwordreset$/);
    await expect(page.locator('#token')).toHaveCount(0);
    await page.screenshot({ path: test.info().outputPath('password-reset-form.png') });
    await page.locator('#passwordNew').fill(passwordNew);
    await page.locator('#passwordConfirm').fill('Mismatch123');
    await expect(page.locator('button[type="submit"]')).toBeDisabled();
    await page.locator('#passwordConfirm').fill(passwordNew);
    const confirmed = page.waitForResponse((response) => response.url().endsWith('/api/user/passwordreset/confirm'));
    await page.locator('button[type="submit"]').click();
    const confirmation = await confirmed;
    expect(confirmation.ok(), await confirmation.text()).toBe(true);
    await expect(page.locator('[role="status"]')).toContainText(/revoked|widerrufen/);
    await expect(page.locator('#passwordNew')).toHaveCount(0);
    expect((await signIn(user.password)).ok()).toBe(false);
    await page.getByRole('button', { name: /^(Sign in|Anmelden)$/ }).click();
    await page.locator('#email').fill(user.email);
    await page.locator('#password').fill(passwordNew);
    await page.locator('button[type="submit"]').click();
    await expect(page).toHaveURL(/\/mainview/);
    const previousJwt = await page.evaluate(() => sessionStorage.getItem('jwt'));
    await page.getByRole('menuitem', { name: /^(Settings|Einstellungen)$/ }).click();
    await page.getByRole('menuitem', { name: /^(Change password|Password ändern|Passwort ändern)\.\.\.$/ }).click();
    const dialog = page.locator('.p-dialog:visible');
    await dialog.locator('#passwordOld').fill(passwordNew);
    await dialog.locator('#password').fill(user.password);
    await dialog.locator('#passwordConfirm').fill(user.password);
    const changed = page.waitForResponse(
      (response) => response.url().endsWith('/api/user/password') && response.request().method() === 'PUT'
    );
    await dialog.getByRole('button', { name: /^(Save|Speichern)$/ }).click();
    const changeResponse = await changed;
    expect(changeResponse.ok()).toBe(true);
    const replacement = changeResponse.headers()['x-auth-token'];
    await expect.poll(() => page.evaluate(() => sessionStorage.getItem('jwt'))).toBe(replacement);
    expect(replacement).not.toBe(previousJwt);
    await expect(page.locator('#email')).toHaveCount(0);
    expect((await page.request.get('/api/user/own', { headers: { 'x-auth-token': replacement } })).ok()).toBe(true);
    expect((await page.request.get('/api/user/own', { headers: { 'x-auth-token': previousJwt ?? '' } })).ok()).toBe(
      false
    );
  } finally {
    await cleanup();
  }
});
