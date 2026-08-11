import {expect, Locator, Page, test} from '@playwright/test';

import {libHelpers, PasswordEditTarget} from './helpers';

const RX = {
  settings: /^(Settings|Einstellungen)$/,
  password: /^(Change password|Password ändern)\.\.\.$/,
  save: /^(Save|Speichern)$/,
};

async function openPasswordDialog(page: Page): Promise<Locator> {
  await page.getByRole('menuitem', {name: RX.settings}).click({timeout: 10_000});
  await page.getByRole('menuitem', {name: RX.password}).click({timeout: 10_000});
  const dialog = page.locator('.p-dialog:visible');
  await dialog.waitFor({state: 'visible', timeout: 10_000});
  return dialog;
}

const users = libHelpers.loadUsers().filter(user => user.passwordEdit);
if (users.length === 0) {
  throw new Error(`No passwordEdit target found in ${libHelpers.config.usersJson}`);
}

test.describe.serial('password settings', () => {
  for (const user of users) {
    test(`changes the password for ${user.email}`, async ({page}) => {
      const target = user.passwordEdit as PasswordEditTarget;
      const currentCredentials = await libHelpers.loginAsFixtureUser(page, user.nickname);
      const dialog = await openPasswordDialog(page);

      await dialog.locator('#passwordOld').fill(currentCredentials.password);
      await dialog.locator('#password').fill(target.passwordNew);
      await dialog.locator('#passwordConfirm').fill(target.passwordNew);

      const updateResponsePromise = page.waitForResponse(response =>
        response.url().endsWith('/api/user/password') && response.request().method() === 'PUT');
      await dialog.getByRole('button', {name: RX.save}).click();
      const updateResponse = await updateResponsePromise;
      expect(updateResponse.ok(), `${updateResponse.status()} ${await updateResponse.text()}`).toBeTruthy();
      await page.locator('#email').waitFor({state: 'visible', timeout: 10_000});

      const changedCredentials = await libHelpers.loginAsFixtureUser(page, user.nickname);
      expect(changedCredentials.password).toBe(target.passwordNew);
    });
  }
});
