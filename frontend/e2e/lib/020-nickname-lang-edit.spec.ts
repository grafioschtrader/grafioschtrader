import { expect, Locator, Page, test } from '@playwright/test';

import { libHelpers, NicknameLangEditTarget } from './helpers';

const RX = {
  settings: /^(Settings|Einstellungen|SETTINGS)$/,
  nicknameLocale: /Change Nickname and language\/country|Spitzname und Land\/Sprache ändern|NICKNAME_LOCALE_CHANGE/,
  save: /^(Save|Speichern|SAVE)$/
};

async function openNicknameLangDialog(page: Page): Promise<Locator> {
  await page.getByRole('menuitem', { name: RX.settings }).click({ timeout: 10_000 });
  await page.getByRole('menuitem', { name: RX.nicknameLocale }).click({ timeout: 10_000 });
  const dialog = page.locator('.p-dialog:visible');
  await dialog.waitFor({ state: 'visible', timeout: 10_000 });
  return dialog;
}

async function applyNicknameLangTarget(page: Page, target: NicknameLangEditTarget): Promise<void> {
  const dialog = await openNicknameLangDialog(page);
  await dialog.locator('#localeStr').selectOption(target.localeStr);
  await dialog.locator('#uiShowMyProperty').setChecked(target.uiShowMyProperty === 1);

  const updateResponsePromise = page.waitForResponse(
    (response) => response.url().endsWith('/api/user/nicknamelocale') && response.request().method() === 'PUT'
  );
  await dialog.getByRole('button', { name: RX.save }).click();
  const updateResponse = await updateResponsePromise;
  expect(updateResponse.ok(), `${updateResponse.status()} ${await updateResponse.text()}`).toBeTruthy();
  await page.locator('#email').waitFor({ state: 'visible', timeout: 10_000 });
}

async function expectNicknameLangTarget(page: Page, target: NicknameLangEditTarget): Promise<void> {
  const dialog = await openNicknameLangDialog(page);
  await expect(dialog.locator('#localeStr')).toHaveValue(target.localeStr);
  if (target.uiShowMyProperty === 1) {
    await expect(dialog.locator('#uiShowMyProperty')).toBeChecked();
  } else {
    await expect(dialog.locator('#uiShowMyProperty')).not.toBeChecked();
  }
}

const users = libHelpers.loadUsers().filter((user) => user.nicknameLangEdit);
if (users.length === 0) {
  throw new Error(`No nicknameLangEdit targets found in ${libHelpers.config.usersJson}`);
}

test.describe.serial('nickname, locale, and ownership settings', () => {
  for (const user of users) {
    test(`updates settings for ${user.email}`, async ({ page }) => {
      const target = user.nicknameLangEdit as NicknameLangEditTarget;
      await libHelpers.loginAsFixtureUser(page, user.nickname);
      await applyNicknameLangTarget(page, target);

      await libHelpers.loginAsFixtureUser(page, user.nickname);
      await page.reload();
      await page.locator('p-tree').waitFor({ state: 'visible', timeout: 15_000 });
      await expectNicknameLangTarget(page, target);
    });
  }
});
