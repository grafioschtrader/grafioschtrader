import {expect, Page, test} from '@playwright/test';

async function login(page: Page, email: string): Promise<void> {
  await page.goto('/login');
  await page.locator('#email').fill(email);
  await page.locator('#password').fill('Test1234');
  await page.locator('button[type="submit"]').click();
  await expect(page).toHaveURL(/\/mail\/mailsendrecv/);
}

test.describe.serial('reusable mail system', () => {
  const subject = `Library E2E ${Date.now()}`;

  test('admin sends an internal message to a user', async ({page}) => {
    await login(page, 'admin@test.local');
    await page.getByRole('button', {name: /send to user/i}).click();
    const dialog = page.locator('.p-dialog');
    await dialog.locator('select#idUserTo').selectOption({label: '2 - user'});
    await dialog.locator('#subject').fill(subject);
    await dialog.locator('#message').fill('Portable grafiosch mail body');
    const sendResponsePromise = page.waitForResponse(response =>
      response.url().endsWith('/api/mailsendrecv') && response.request().method() === 'POST');
    await dialog.getByRole('button', {name: 'Send', exact: true}).click();
    const sendResponse = await sendResponsePromise;
    expect(sendResponse.ok(), `${sendResponse.status()} ${await sendResponse.text()}`).toBeTruthy();
    const inboxResponsePromise = page.waitForResponse(response =>
      response.url().endsWith('/api/mailsendrecv') && response.request().method() === 'GET');
    await page.reload();
    const inboxResponse = await inboxResponsePromise;
    const inboxBody = await inboxResponse.text();
    expect(inboxResponse.ok(), `${inboxResponse.status()} ${inboxBody}`).toBeTruthy();
    expect(inboxBody).toContain(subject);
    await expect(page.getByText(subject)).toBeVisible();
  });

  test('recipient reads the message and can open forwarding settings', async ({page}) => {
    await login(page, 'user@test.local');
    await expect(page.getByText(subject)).toBeVisible();
    await page.getByText(subject).click();
    await expect(page.locator('textarea')).toContainText('Portable grafiosch mail body');
    await page.getByRole('link', {name: /mail setting forward/i}).click();
    await expect(page.locator('editable-table')).toBeVisible();
  });

  test('registration email is delivered through the external SMTP adapter', async ({request}) => {
    const backend = process.env.LIB_E2E_BACKEND_URL ?? 'http://localhost:8081';
    const mailApi = process.env.LIB_E2E_MAIL_API_URL ?? 'http://localhost:8025';
    const unique = Date.now();
    const email = `registration-${unique}@test.local`;
    const response = await request.post(`${backend}/api/user`, {
      headers: {referer: 'http://localhost:4201/'},
      data: {
        email,
        password: 'Test1234',
        nickname: `registration-${unique}`.slice(0, 30),
        localeStr: 'en-US',
        timezoneOffset: 0,
      },
    });
    expect(response.ok(), await response.text()).toBeTruthy();

    let body = '';
    for (let attempt = 0; attempt < 10 && !body; attempt++) {
      const result = await request.get(`${mailApi}/api/v2/search?kind=to&query=${encodeURIComponent(email)}`);
      if (result.ok()) {
        const messages = await result.json() as any;
        body = messages.items?.[0]?.Content?.Body ?? '';
      }
      if (!body) {
        await new Promise(resolve => setTimeout(resolve, 1000));
      }
    }
    expect(body).toContain('tokenverify?token=');
  });
});
