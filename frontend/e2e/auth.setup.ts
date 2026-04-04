import {test as setup, expect, request} from '@playwright/test';
import * as fs from 'fs';
import * as path from 'path';

const MAILHOG_API = 'http://localhost:8025/api/v2';
const AUTH_DIR = path.join(__dirname, '.auth');
const CREDENTIALS_FILE = path.join(AUTH_DIR, 'credentials.json');

// Test user credentials — unique per run to avoid conflicts
const TEST_USER = {
  nickname: `testuser_${Date.now()}`,
  email: `testuser_${Date.now()}@test.local`,
  password: 'Testuser1234',
  locale: 'en',
  tenantName: `TestTenant_${Date.now()}`,
  tenantCurrency: 'CHF',
};

/**
 * Fetches the latest email from MailHog and extracts the verification token.
 */
async function getVerificationTokenFromMailHog(recipientEmail: string, maxAttempts = 10): Promise<string> {
  const apiContext = await request.newContext();
  for (let attempt = 0; attempt < maxAttempts; attempt++) {
    const response = await apiContext.get(`${MAILHOG_API}/search?kind=to&query=${recipientEmail}`);
    const body = await response.json();
    if (body.count > 0) {
      const message = body.items[0];
      const emailBody: string = message.Content?.Body ?? '';
      const tokenMatch = emailBody.match(/tokenverify\?token=([0-9a-f-]{36})/i);
      if (tokenMatch) {
        await apiContext.dispose();
        return tokenMatch[1];
      }
    }
    await new Promise(resolve => setTimeout(resolve, 2000));
  }
  await apiContext.dispose();
  throw new Error(`No verification email found for ${recipientEmail} after ${maxAttempts} attempts`);
}

async function clearMailHog(): Promise<void> {
  const apiContext = await request.newContext();
  await apiContext.delete('http://localhost:8025/api/v1/messages');
  await apiContext.dispose();
}

setup('register user, verify email, setup tenant, and save credentials', async ({page}) => {
  await clearMailHog();

  // === Step 1: Register ===
  await page.goto('/register');
  const nicknameInput = page.locator('#nickname');
  await nicknameInput.waitFor({state: 'visible', timeout: 15_000});
  await nicknameInput.fill(TEST_USER.nickname);
  await page.locator('#email').fill(TEST_USER.email);
  await page.locator('#password').fill(TEST_USER.password);
  await page.locator('#passwordConfirm').fill(TEST_USER.password);
  const localeSelect = page.locator('#localeStr');
  await localeSelect.waitFor({state: 'visible'});
  await localeSelect.selectOption({value: TEST_USER.locale});
  await page.locator('button[type="submit"]').click();
  await expect(page.locator('.alert-info')).toBeVisible({timeout: 30_000});

  // === Step 2: Verify email token via MailHog ===
  const token = await getVerificationTokenFromMailHog(TEST_USER.email);
  expect(token).toBeTruthy();
  await page.goto(`/tokenverify?token=${token}`);

  // === Step 3: First login ===
  await page.locator('#email').waitFor({state: 'visible', timeout: 15_000});
  await expect(page.locator('.alert-success')).toBeVisible({timeout: 5_000});
  await page.locator('#email').fill(TEST_USER.email);
  await page.locator('#password').fill(TEST_USER.password);
  await page.locator('button[type="submit"]').click();
  await page.waitForURL(/\/tenant/, {timeout: 15_000});

  // === Step 4: Tenant setup ===
  const tenantNameInput = page.locator('#tenantName');
  await tenantNameInput.waitFor({state: 'visible', timeout: 15_000});
  // Wait for the form to be fully initialized (dropdown options loaded from backend)
  await page.waitForTimeout(2000);
  await tenantNameInput.fill(TEST_USER.tenantName);

  // Select currency — native <select> rendered by FormInputSelectComponent
  // Wait for options to be loaded from the backend before selecting
  const currencySelect = page.locator('select#currency');
  await currencySelect.waitFor({state: 'visible', timeout: 10_000});
  // Wait until the select has more than 1 option (options loaded from backend)
  await page.waitForFunction(
    () => {
      const sel = document.querySelector('select#currency') as HTMLSelectElement;
      return sel && sel.options.length > 1;
    },
    {timeout: 10_000}
  );
  await currencySelect.selectOption({label: TEST_USER.tenantCurrency});

  await page.locator('button[type="submit"]').click();

  // After tenant setup, user is logged out
  await page.locator('#email').waitFor({state: 'visible', timeout: 15_000});

  // === Step 5: Save credentials for subsequent tests ===
  if (!fs.existsSync(AUTH_DIR)) {
    fs.mkdirSync(AUTH_DIR, {recursive: true});
  }
  fs.writeFileSync(CREDENTIALS_FILE, JSON.stringify({
    email: TEST_USER.email,
    password: TEST_USER.password,
    tenantName: TEST_USER.tenantName,
    tenantCurrency: TEST_USER.tenantCurrency,
  }));
});
