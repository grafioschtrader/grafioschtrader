import {expect, Page, test} from '@playwright/test';

import {libHelpers} from './helpers';

/**
 * The reusable mail system driven through the library components of the standalone host: the admin sends an internal
 * message from the inbox view, the recipient reads it and reaches the forwarding settings.
 *
 * Both users come from `users.json` and were created by the backend `ResourceTestSuite`, so the recipient is selected
 * by nickname rather than by id — nothing pins the primary keys any more since the JDBC seeder was removed.
 *
 * Menu entries are matched bilingually: the host renders in the language of the signed-in user, and users.json may
 * carry any locale.
 */
const RX = {
  adminData: /^(Administrative data - Messages|Administrative Daten - Nachrichten)$/,
  userMessage: /^(Messages for\/from|Nachrichten für\/von)$/,
  sendToUser: /^(Message to user|Nachricht an Benutzer)$/,
  send: /^(Send|Absenden)$/,
  mailSettingForward: /^(Setting messages|Einstellung Nachrichten)$/,
};

/** Opens Administrative data -> the mail inbox from the navigation tree. */
async function gotoInbox(page: Page): Promise<void> {
  await page.getByRole('treeitem', {name: RX.userMessage}).first().click();
  await page.locator('send-recv-treetable, .data-container').first().waitFor({state: 'visible', timeout: 15_000});
}

test.describe.serial('reusable mail system', () => {
  const subject = `Library E2E ${Date.now()}`;
  const recipient = libHelpers.getUser('user');

  test('admin sends an internal message to a user', async ({page}) => {
    await libHelpers.loginAsFixtureUser(page, 'admin');
    await gotoInbox(page);

    // SEND_TO_USER lives in the context menu of the inbox table, which ConfigurableTreeTableComponent opens on a
    // right click; the same items are mirrored into the main menu bar through ActivePanelService.
    await page.locator('.data-container').first().click({button: 'right'});
    const menu = page.locator('[role="menu"]:visible');
    await menu.waitFor({state: 'visible', timeout: 10_000});
    await menu.getByRole('menuitem', {name: RX.sendToUser}).first().click();

    const dialog = page.locator('.p-dialog');
    await dialog.waitFor({state: 'visible', timeout: 10_000});

    // The options are labelled "<id> - <nickname>", so the recipient is resolved by nickname and selected by the
    // option's value. Nothing may assume a fixed id: users are created by the registration flow, not seeded.
    // The lookup runs in the page rather than through a hasText filter, because the rendered option text carries
    // surrounding whitespace that an anchored locator regex would never match.
    const userSelect = dialog.locator('select#idUserTo');
    const recipientValue = await userSelect.evaluate((select, nickname) =>
      Array.from((select as HTMLSelectElement).options)
        .find(option => option.textContent.trim().endsWith(nickname))?.value ?? null, recipient.nickname);
    expect(recipientValue, `no option for '${recipient.nickname}' in the recipient select`).toBeTruthy();
    await userSelect.selectOption(recipientValue);

    await dialog.locator('#subject').fill(subject);
    await dialog.locator('#message').fill('Portable grafiosch mail body');

    const sendResponsePromise = page.waitForResponse(response =>
      response.url().endsWith('/api/mailsendrecv') && response.request().method() === 'POST');
    await dialog.getByRole('button', {name: RX.send, exact: true}).click();
    const sendResponse = await sendResponsePromise;
    expect(sendResponse.ok(), `${sendResponse.status()} ${await sendResponse.text()}`).toBeTruthy();

    await page.reload();
    await gotoInbox(page);
    await expect(page.getByText(subject)).toBeVisible();
  });

  test('recipient reads the message and can open forwarding settings', async ({page}) => {
    await libHelpers.loginAsFixtureUser(page, recipient.nickname);
    await gotoInbox(page);

    await expect(page.getByText(subject)).toBeVisible();
    await page.getByText(subject).click();
    await expect(page.locator('textarea')).toContainText('Portable grafiosch mail body');

    // The two mail views are the tabs of SendRecvForwardTabMenuComponent, not separate navigation links.
    await page.getByRole('tab', {name: RX.mailSettingForward}).click();
    await expect(page.locator('editable-table')).toBeVisible();
  });

  test('registration email is delivered through the external SMTP adapter', async ({request}) => {
    const backend = process.env.LIB_E2E_BACKEND_URL ?? 'http://localhost:8081';
    const unique = Date.now();
    const email = `registration-${unique}@test.local`;
    const response = await request.post(`${backend}/api/user`, {
      headers: {referer: 'http://localhost:4201/register'},
      data: {
        email,
        password: 'A123abcd',
        nickname: `registration-${unique}`.slice(0, 30),
        localeStr: 'en-US',
        timezoneOffset: 0,
      },
    });
    expect(response.ok(), await response.text()).toBeTruthy();

    const token = await libHelpers.getVerificationToken(email);
    expect(token).toBeTruthy();
  });
});
