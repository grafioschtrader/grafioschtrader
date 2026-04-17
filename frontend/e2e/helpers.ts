import {Page} from '@playwright/test';
import * as fs from 'fs';
import * as path from 'path';

const AUTH_DIR = path.join(__dirname, '.auth');
const CREDENTIALS_FILE = path.join(AUTH_DIR, 'credentials.json');
const USERS_CSV = path.resolve(__dirname,
  '../../backend/grafioschtrader-server/src/test/resources/testdata/users.csv');

/**
 * Splits one pipe-separated CSV line into fields, respecting double-quoted sections so that a `|`
 * inside quotes is preserved. Used by every E2E spec that consumes the generated/manually-authored
 * testdata CSVs (users.csv, tradingplatformplan.csv, currencypair.csv, …).
 */
export function parseCsvRow(line: string): string[] {
  const out: string[] = [];
  let cur = '';
  let inQuotes = false;
  for (let i = 0; i < line.length; i++) {
    const c = line.charAt(i);
    if (c === '"') {
      inQuotes = !inQuotes;
      continue;
    }
    if (c === '|' && !inQuotes) {
      out.push(cur);
      cur = '';
    } else {
      cur += c;
    }
  }
  out.push(cur);
  return out;
}

export interface TestCredentials {
  email: string;
  password: string;
  nickname?: string;
  role?: string;
  tenantName: string;
  tenantCurrency: string;
}

/**
 * Reads the primary credentials file written by the auth setup (the first e2e user from users.csv).
 */
export function loadCredentials(): TestCredentials {
  const data = fs.readFileSync(CREDENTIALS_FILE, 'utf-8');
  return JSON.parse(data);
}

/**
 * Reads credentials for a specific nickname (works for any e2e user registered by auth.setup.ts).
 */
export function loadCredentialsByNickname(nickname: string): TestCredentials {
  const file = path.join(AUTH_DIR, `credentials.${nickname}.json`);
  const data = fs.readFileSync(file, 'utf-8');
  return JSON.parse(data);
}

async function performLogin(page: Page, creds: TestCredentials): Promise<void> {
  await page.goto('/login');
  await page.locator('#email').waitFor({state: 'visible', timeout: 15_000});
  await page.locator('#email').fill(creds.email);
  await page.locator('#password').fill(creds.password);
  await page.locator('button[type="submit"]').click();
  await page.waitForURL(/\/mainview/, {timeout: 15_000});
  await page.locator('p-tree').waitFor({state: 'visible', timeout: 15_000});
}

/**
 * Logs in with the primary credentials saved by auth setup (first e2e user).
 */
export async function login(page: Page): Promise<TestCredentials> {
  const creds = loadCredentials();
  await performLogin(page, creds);
  return creds;
}

/**
 * Logs in as a specific nickname. The user must have been registered by auth setup
 * (i.e. tagged e2e='e' in users.csv).
 */
export async function loginAs(page: Page, nickname: string): Promise<TestCredentials> {
  const creds = loadCredentialsByNickname(nickname);
  await performLogin(page, creds);
  return creds;
}

/**
 * Logs in as any users.csv user by nickname — including integration-test users (e2e='i') that were
 * seeded by ResoureTestSuite and are not registered via the UI by auth.setup.ts. The user row must
 * exist in users.csv and the user must already be active in the target DB.
 */
export async function loginAsCsvUser(page: Page, nickname: string): Promise<TestCredentials> {
  const csv = fs.readFileSync(USERS_CSV, 'utf-8');
  const row = csv.split(/\r?\n/)
    .filter(l => l.trim().length > 0)
    .map(l => l.split('|'))
    .find(cols => cols[2] === nickname);
  if (!row) {
    throw new Error(`User '${nickname}' not found in ${USERS_CSV}`);
  }
  const creds: TestCredentials = {
    email: row[0],
    password: row[1],
    nickname: row[2],
    role: row[6],
    tenantName: `Tenant ${row[2]}`,
    tenantCurrency: row[5],
  };
  await performLogin(page, creds);
  return creds;
}
