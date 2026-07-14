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
 * Reads the credentials of any users.csv row by nickname — regardless of the e2e flag ('i', 'e',
 * 'ec', 'er'). Used to obtain the email/password of users that are created by a feature under test
 * (e.g. managed clients / read-only viewers) rather than registered by auth.setup.ts.
 */
export function getCsvUser(nickname: string): TestCredentials {
  const csv = fs.readFileSync(USERS_CSV, 'utf-8');
  const row = csv.split(/\r?\n/)
    .filter(l => l.trim().length > 0)
    .map(l => parseCsvRow(l))
    .find(cols => cols[2] === nickname);
  if (!row) {
    throw new Error(`User '${nickname}' not found in ${USERS_CSV}`);
  }
  return {
    email: row[0],
    password: row[1],
    nickname: row[2],
    role: row[6],
    tenantName: `Tenant ${row[2]}`,
    tenantCurrency: row[5],
  };
}

/**
 * Logs in as any users.csv user by nickname — including integration-test users (e2e='i') that were
 * seeded by ResoureTestSuite and are not registered via the UI by auth.setup.ts. The user row must
 * exist in users.csv and the user must already be active in the target DB.
 */
export async function loginAsCsvUser(page: Page, nickname: string): Promise<TestCredentials> {
  const creds = getCsvUser(nickname);
  await performLogin(page, creds);
  return creds;
}

const MAILHOG_BASE = 'http://localhost:8025';

export interface MailhogMessage {
  subject: string;
  body: string;
}

/**
 * Decodes a quoted-printable byte sequence ("=C3=BC" style escapes) into a Buffer. Characters that
 * are not escapes are passed through as latin-1 bytes, which is correct for MIME content where all
 * non-ASCII bytes must be escaped anyway.
 */
function qpToBuffer(text: string): Buffer {
  const bytes: number[] = [];
  for (let i = 0; i < text.length; i++) {
    if (text.charAt(i) === '=' && /^[0-9A-Fa-f]{2}/.test(text.substring(i + 1, i + 3))) {
      bytes.push(parseInt(text.substring(i + 1, i + 3), 16));
      i += 2;
    } else {
      bytes.push(text.charCodeAt(i) & 0xff);
    }
  }
  return Buffer.from(bytes);
}

/** Decodes RFC 2047 encoded words ("=?UTF-8?Q?...?=" / "=?UTF-8?B?...?=") in a mail header. */
function decodeMimeHeader(value: string): string {
  return value.replace(/=\?[^?]+\?([BbQq])\?([^?]*)\?=/g, (_match, encoding, text) =>
    encoding.toUpperCase() === 'B'
      ? Buffer.from(text, 'base64').toString('utf-8')
      : qpToBuffer(text.replace(/_/g, ' ')).toString('utf-8'));
}

/** Decodes a quoted-printable mail body (removes soft line breaks, resolves =XX escapes). */
function decodeMimeBody(body: string): string {
  return qpToBuffer(body.replace(/=\r?\n/g, '')).toString('utf-8');
}

/**
 * Polls the MailHog API for a message sent to `recipient` whose decoded subject (or body) matches
 * `subjectRegex`. Mail sending in the backend is asynchronous, hence the polling. Returns null when
 * no matching message shows up — callers assert on the result.
 */
export async function findMailhogMessage(recipient: string, subjectRegex: RegExp,
    maxAttempts = 10): Promise<MailhogMessage | null> {
  for (let attempt = 0; attempt < maxAttempts; attempt++) {
    const response = await fetch(
      `${MAILHOG_BASE}/api/v2/search?kind=to&query=${encodeURIComponent(recipient)}`);
    if (response.ok) {
      const result = await response.json() as any;
      for (const item of result.items ?? []) {
        const subject = decodeMimeHeader(item.Content?.Headers?.Subject?.[0] ?? '');
        const body = decodeMimeBody(item.Content?.Body ?? '');
        if (subjectRegex.test(subject) || subjectRegex.test(body)) {
          return {subject, body};
        }
      }
    }
    await new Promise(resolve => setTimeout(resolve, 2000));
  }
  return null;
}
