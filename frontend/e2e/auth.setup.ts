import {test as setup, expect, Page, request} from '@playwright/test';
import * as fs from 'fs';
import * as path from 'path';
import {parseCsvRow} from './helpers';

const MAILHOG_API = 'http://localhost:8025/api/v2';
const AUTH_DIR = path.join(__dirname, '.auth');
const LEGACY_CREDENTIALS_FILE = path.join(AUTH_DIR, 'credentials.json');
const USERS_CSV = path.resolve(__dirname,
  '../../backend/grafioschtrader-server/src/test/resources/testdata/users.csv');

interface CsvUser {
  email: string;
  password: string;
  nickname: string;
  localeStr: string;
  timezoneOffset: string;
  currency: string;
  role: string;
  e2e: string;
}

function loadE2EUsers(): CsvUser[] {
  const csv = fs.readFileSync(USERS_CSV, 'utf-8');
  return csv.split(/\r?\n/)
    .filter(l => l.trim().length > 0)
    .map(line => {
      const [email, password, nickname, localeStr, timezoneOffset, currency, role, e2e] = parseCsvRow(line);
      return {email, password, nickname, localeStr, timezoneOffset, currency, role, e2e};
    })
    .filter(u => u.e2e === 'e');
}

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

async function registerAndSetupTenant(page: Page, user: CsvUser): Promise<void> {
  // === Register ===
  await page.goto('/register');
  const nicknameInput = page.locator('#nickname');
  await nicknameInput.waitFor({state: 'visible', timeout: 15_000});
  await nicknameInput.fill(user.nickname);
  await page.locator('#email').fill(user.email);
  await page.locator('#password').fill(user.password);
  await page.locator('#passwordConfirm').fill(user.password);
  const localeSelect = page.locator('#localeStr');
  await localeSelect.waitFor({state: 'visible'});
  // Frontend locale options use the language prefix (e.g. 'en' for 'en-US'). Try the full string first,
  // fall back to the language code.
  const langCode = user.localeStr.split('-')[0];
  await localeSelect.selectOption({value: user.localeStr}).catch(() => localeSelect.selectOption({value: langCode}));
  await page.locator('button[type="submit"]').click();
  await expect(page.locator('.alert-info')).toBeVisible({timeout: 30_000});

  // === Verify ===
  const token = await getVerificationTokenFromMailHog(user.email);
  expect(token).toBeTruthy();
  await page.goto(`/tokenverify?token=${token}`);

  // === First login ===
  await page.locator('#email').waitFor({state: 'visible', timeout: 15_000});
  await expect(page.locator('.alert-success')).toBeVisible({timeout: 5_000});
  await page.locator('#email').fill(user.email);
  await page.locator('#password').fill(user.password);
  await page.locator('button[type="submit"]').click();
  await page.waitForURL(/\/tenant/, {timeout: 15_000});

  // === Tenant setup — name follows UserResourceTest convention: "Tenant " + nickname ===
  const tenantName = `Tenant ${user.nickname}`;
  const tenantNameInput = page.locator('#tenantName');
  await tenantNameInput.waitFor({state: 'visible', timeout: 15_000});
  await page.waitForTimeout(2000);
  await tenantNameInput.fill(tenantName);

  const currencySelect = page.locator('select#currency');
  await currencySelect.waitFor({state: 'visible', timeout: 10_000});
  await page.waitForFunction(
    () => {
      const sel = document.querySelector('select#currency') as HTMLSelectElement;
      return sel && sel.options.length > 1;
    },
    {timeout: 10_000}
  );
  await currencySelect.selectOption({label: user.currency});

  await page.locator('button[type="submit"]').click();

  // After tenant setup the user is logged out; wait for the login form
  await page.locator('#email').waitFor({state: 'visible', timeout: 15_000});
}

function saveCredentials(user: CsvUser, isPrimary: boolean): void {
  if (!fs.existsSync(AUTH_DIR)) {
    fs.mkdirSync(AUTH_DIR, {recursive: true});
  }
  const payload = {
    email: user.email,
    password: user.password,
    nickname: user.nickname,
    role: user.role,
    tenantName: `Tenant ${user.nickname}`,
    tenantCurrency: user.currency,
  };
  fs.writeFileSync(
    path.join(AUTH_DIR, `credentials.${user.nickname}.json`),
    JSON.stringify(payload),
  );
  if (isPrimary) {
    fs.writeFileSync(LEGACY_CREDENTIALS_FILE, JSON.stringify(payload));
  }
}

setup('register all e2e users from users.csv, verify, and setup their tenants', async ({page}) => {
  const e2eUsers = loadE2EUsers();
  if (e2eUsers.length === 0) {
    throw new Error(`No rows with e2e='e' found in ${USERS_CSV}`);
  }
  await clearMailHog();
  for (let i = 0; i < e2eUsers.length; i++) {
    const u = e2eUsers[i];
    await registerAndSetupTenant(page, u);
    saveCredentials(u, i === 0);
  }
});
