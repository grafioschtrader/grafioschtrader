import { expect, Page, Request, test } from '@playwright/test';
import { SuiteHelpers, TenantSetup, UserFixture } from './helpers';
import { acceptConfirmDialog, clickShowMenuItem, GTNET_RX, openGTNetSetup, visibleDialog } from './gtnet.helpers';

/** Shared HTTP checks for both hosts. The dedicated registration account is reconciled and removed through REST. */
export function authHttpInterceptorScenarios(helpers: SuiteHelpers, setupTenant: TenantSetup): void {
  test.use({ timezoneId: 'America/Los_Angeles' });

  test('registration, verification and authenticated JSON writes work through the browser', async ({ page }) => {
    const user: UserFixture = {
      ...helpers.getUser('admin'),
      email: 'auth-http-interceptor@test.local',
      nickname: 'AuthHttpInterceptor',
      tenantName: 'Auth HTTP test tenant',
      password: 'A123abcd',
      localeStr: 'en-US',
      role: 'LIMITEDIT',
      e2e: 'e',
      passwordEdit: undefined
    };
    // Reconcile only this scenario's account, including a previously interrupted registration.
    const cleanup = async () => {
      await helpers.loginAsFixtureUser(page, 'admin');
      const token = await page.evaluate(() => sessionStorage.getItem('jwt'));
      const usersResponse = await page.request.get('/api/useradmin', { headers: { 'x-auth-token': token! } });
      expect(usersResponse.ok()).toBe(true);
      const existing = (await usersResponse.json()).find((entry: { email: string }) => entry.email === user.email);
      await page.evaluate(() => sessionStorage.clear());
      if (!existing) return;
      if (!existing.enabled) {
        const verificationToken = await helpers.getVerificationToken(user.email);
        await page.goto(`/tokenverify?token=${verificationToken}`);
        await page.locator('#email').waitFor();
      }
      await helpers.registerAndSetupTenant(page, user, setupTenant);
      const login = await page.request.post('/api/login', {
        data: { email: user.email, password: user.password, timezoneOffset: 0 }
      });
      expect(login.ok()).toBe(true);
      const removed = await page.request.delete('/api/tenant', {
        headers: { 'x-auth-token': login.headers()['x-auth-token'] }
      });
      expect(removed.ok()).toBe(true);
    };
    await cleanup();
    try {
      const registration = page.waitForResponse(
        (response) => new URL(response.url()).pathname === '/api/user' && response.request().method() === 'POST'
      );
      const tenantCreation = page.waitForResponse(
        (response) => new URL(response.url()).pathname === '/api/tenant' && response.request().method() === 'POST'
      );
      await helpers.registerAndSetupTenant(page, user, setupTenant);
      const registered = await registration;
      expect(registered.ok()).toBe(true);
      expect(registered.request().headers()['x-auth-token']).toBeUndefined();
      expect(registered.request().headers()['x-time-zone']).toBeUndefined();
      const created = await tenantCreation;
      expect(created.ok()).toBe(true);
      expect(created.request().headers()['x-auth-token']).toBeTruthy();
      expect(created.request().headers()['x-time-zone']).toBe('America/Los_Angeles');

      await page.locator('#email').fill(user.email);
      await page.locator('#password').fill(user.password);
      await page.locator('button[type="submit"]').click();
      await page.waitForURL(/\/mainview/);
      await page.getByRole('menuitem', { name: /^(Settings|Einstellungen|SETTINGS)$/ }).click();
      await page
        .getByRole('menuitem', {
          name: /Change Nickname and language\/country|Spitzname und Land\/Sprache ändern|NICKNAME_LOCALE_CHANGE/
        })
        .click();
      const dialog = await visibleDialog(page);
      await dialog.locator('#nickname').fill('AuthHttpRenamed');
      const update = page.waitForResponse(
        (response) => response.url().endsWith('/api/user/nicknamelocale') && response.request().method() === 'PUT'
      );
      await dialog.getByRole('button', { name: GTNET_RX.save }).click();
      const updated = await update;
      expect(updated.ok()).toBe(true);
      expect(updated.request().headers()['x-auth-token']).toBeTruthy();
      expect(updated.request().headers()['x-time-zone']).toBe('America/Los_Angeles');
      await page.locator('#email').waitFor();
      await page.locator('#email').fill(user.email);
      await page.locator('#password').fill(user.password);
      await page.locator('button[type="submit"]').click();
      await page.waitForURL(/\/mainview/);
      await page.getByRole('menuitem', { name: /^(Settings|Einstellungen|SETTINGS)$/ }).click();
      await page
        .getByRole('menuitem', {
          name: /^(Deleting my data and user account|Löschen meiner Daten und des Benutzerkontos|DELETE_MY)$/
        })
        .click();
      const deletion = page.waitForResponse(
        (response) => new URL(response.url()).pathname === '/api/tenant' && response.request().method() === 'DELETE'
      );
      await acceptConfirmDialog(page);
      const deleted = await deletion;
      expect(deleted.ok()).toBe(true);
      expect(deleted.request().headers()['x-auth-token']).toBeTruthy();
      expect(deleted.request().headers()['x-time-zone']).toBe('America/Los_Angeles');
    } finally {
      await cleanup();
    }
  });

  test('public reads and login omit a stale session token', async ({ page }) => {
    const publicRequests: Request[] = [];
    page.on('request', (request) => {
      const path = new URL(request.url()).pathname;
      if (
        /^\/api\/(login|releasenote|user\/tokenverify\/.*|globalparameters\/(properties\/.*|locales|passwordrequirements|userformdefinition))$/.test(
          path
        )
      ) {
        publicRequests.push(request);
      }
    });
    await page.goto('/login');
    await page.locator('#email').waitFor();
    await page.evaluate(() => sessionStorage.setItem('jwt', 'expired-session-token'));
    await page.reload();
    await page.locator('#email').waitFor();
    // An invalid verification link returns to registration and loads its public form metadata.
    await page.goto('/tokenverify?token=00000000-0000-0000-0000-000000000000');
    await page.locator('#nickname').waitFor();
    await page.goto('/login');
    await page.locator('#email').waitFor();
    const credentials = helpers.getUser('admin');
    await page.locator('#email').fill(credentials.email);
    await page.locator('#password').fill(credentials.password);
    const loginResponse = page.waitForResponse((response) => new URL(response.url()).pathname === '/api/login');
    await page.locator('button[type="submit"]').click();
    expect((await loginResponse).ok()).toBe(true);
    await page.waitForURL(/\/mainview/);
    expect(publicRequests.some((request) => new URL(request.url()).pathname === '/api/login')).toBe(true);
    for (const request of publicRequests) {
      // A reload can cancel a translation request before Chromium supplies its extra header event.
      const headers = request.headers();
      expect(headers['x-auth-token'], request.url()).toBeUndefined();
      expect(headers['x-time-zone'], request.url()).toBeUndefined();
    }
  });

  test('download and multipart upload authenticate; a rejected upload shows one error', async ({ page }) => {
    await helpers.loginAsFixtureUser(page, 'admin');
    const container = await openGTNetSetup(page);
    const exportResponse = page.waitForResponse(
      (response) => response.url().includes('/api/gtnetdataexport') && response.request().method() === 'GET'
    );
    const downloadPromise = page.waitForEvent('download');
    await clickShowMenuItem(page, container, GTNET_RX.gtNetExport);
    const response = await exportResponse;
    expect(response.ok()).toBe(true);
    await expectSessionHeaders(page, response.request());
    const download = await downloadPromise;
    expect(await download.failure()).toBeNull();
    await download.delete();

    await clickShowMenuItem(page, container, GTNET_RX.gtNetImport);
    const dialog = await visibleDialog(page);
    await dialog.locator('input[type="file"]#fileToUpload').setInputFiles({
      name: 'invalid-header.sql',
      mimeType: 'application/sql',
      buffer: Buffer.from('-- deliberately missing export marker\n')
    });
    const importResponse = page.waitForResponse((reply) => reply.url().endsWith('/api/gtnetdataexport/import'));
    await dialog.getByRole('button', { name: GTNET_RX.upload }).click();
    const rejected = await importResponse;
    expect(rejected.ok()).toBe(false);
    await expectSessionHeaders(page, rejected.request());
    expect((await rejected.request().allHeaders())['content-type']).toMatch(/^multipart\/form-data; boundary=/);
    await expect(page.locator('.p-toast-message-error')).toHaveCount(1);
  });

  for (const className of [null, 'ErrorWithLogout', 'ErrorWithLogoutAdmin']) {
    test(`a ${className ?? '401'} response produces one toast and preserves logout handling`, async ({ page }) => {
      await helpers.loginAsFixtureUser(page, 'admin');
      await page.route('**/api/gtnet/gtnetwithmessage', (route) =>
        route.fulfill({
          status: 401,
          contentType: 'application/json',
          body: JSON.stringify(className ? { className, error: {} } : 'login.failure')
        })
      );
      // Navigate within the existing shell: a hard reload also starts unrelated authenticated tree requests.
      await page
        .getByRole('treeitem', { name: GTNET_RX.netAndMessage })
        .locator('.p-tree-node-label')
        .first()
        .click({ timeout: 15_000 });
      await expect(page.locator('.p-toast-message-error')).toHaveCount(1);
      if (className) {
        await page.waitForURL(/\/login/);
        expect(await page.evaluate(() => sessionStorage.getItem('jwt'))).toBeNull();
      } else {
        expect(await page.evaluate(() => sessionStorage.getItem('jwt'))).toBeTruthy();
      }
    });
  }
}

async function expectSessionHeaders(page: Page, request: Request): Promise<void> {
  const headers = await request.allHeaders();
  expect(headers['x-auth-token']).toBe(await page.evaluate(() => sessionStorage.getItem('jwt')));
  expect(headers['x-time-zone']).toBe('America/Los_Angeles');
}
