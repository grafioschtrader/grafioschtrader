import { Page, test } from '@playwright/test';
import * as fs from 'fs';
import * as path from 'path';

/**
 * Suite helpers shared by every application built on `src/app/lib`.
 *
 * The library owns them, not the Grafioschtrader suite, so they move with `src/app/lib` when the library is
 * extracted: the application suite imports from here, never the other way round. Everything that differs between two
 * applications — where users.json lives, where the credential files go, which URL a successful login lands on — is
 * passed in through {@link SuiteConfig}; the flows themselves are identical because they only drive library
 * components.
 */

/**
 * Splits one pipe-separated CSV line into fields, respecting double-quoted sections so that a `|`
 * inside quotes is preserved. Used by every E2E spec that consumes the generated/manually-authored
 * testdata CSVs (tradingplatformplan.csv, currencypair.csv, …).
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
  /** users.json locale, e.g. 'de-CH' or 'en-US'. Only set by getUser/loginAsFixtureUser. */
  locale?: string;
  tenantName: string;
  tenantCurrency: string;
}

export interface NicknameLangEditTarget {
  localeStr: string;
  /** Checkbox target as stored by the database: 0 = false, 1 = true. */
  uiShowMyProperty: 0 | 1;
}

export interface PasswordEditTarget {
  passwordNew: string;
}

/** One object of users.json, before it is reduced to credentials. */
export interface UserFixture extends TestCredentials {
  nickname: string;
  localeStr: string;
  timezoneOffset: number;
  currency: string;
  role: string;
  /** Consumer tag: 'i' for the JUnit resource suite, 'e' and its variants for the browser suite. */
  e2e: string;
  nicknameLangEdit?: NicknameLangEditTarget;
  passwordEdit?: PasswordEditTarget;
}

/** What a concrete application has to tell the shared helpers about itself. */
export interface SuiteConfig {
  /** Absolute path of the users.json that belongs to this application's backend module. */
  usersJson: string;
  /** Directory the credential files of registered users are written to. */
  authDir: string;
  /** Base URL of the MailHog/Mailpit HTTP API. */
  mailApiBase: string;
  /** URL the application lands on after a successful login of a user that already has a tenant. */
  afterLoginUrl: RegExp;
}

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
      : qpToBuffer(text.replace(/_/g, ' ')).toString('utf-8')
  );
}

/** Decodes a quoted-printable mail body (removes soft line breaks, resolves =XX escapes). */
function decodeMimeBody(body: string): string {
  return qpToBuffer(body.replace(/=\r?\n/g, '')).toString('utf-8');
}

/**
 * The helper set of one application. Create it once per suite with {@link createSuiteHelpers} and re-export the
 * members the specs use.
 */
export interface SuiteHelpers {
  config: SuiteConfig;
  loadCredentials(): TestCredentials;
  loadCredentialsByNickname(nickname: string): TestCredentials;
  saveCredentials(user: UserFixture, isPrimary: boolean): void;
  loadUsers(): UserFixture[];
  loadE2EUsers(): UserFixture[];
  getUser(nickname: string): UserFixture;
  login(page: Page): Promise<TestCredentials>;
  loginAs(page: Page, nickname: string): Promise<TestCredentials>;
  loginAsFixtureUser(page: Page, nickname: string): Promise<TestCredentials>;
  findMailhogMessage(recipient: string, subjectRegex: RegExp, maxAttempts?: number): Promise<MailhogMessage | null>;
  clearMailhog(): Promise<void>;
  getVerificationToken(recipientEmail: string, maxAttempts?: number): Promise<string>;
  registerAndSetupTenant(page: Page, user: UserFixture, tenantSetup: TenantSetup): Promise<void>;
}

/**
 * Fills the application's tenant page, which is the one screen of the registration flow the library cannot supply:
 * `TenantBase` is extended per application, so Grafioschtrader asks for a currency where the standalone host asks for
 * nothing but the name.
 */
export type TenantSetup = (page: Page, user: UserFixture) => Promise<void>;

export function createSuiteHelpers(config: SuiteConfig): SuiteHelpers {
  const legacyCredentialsFile = path.join(config.authDir, 'credentials.json');

  function loadUsers(): UserFixture[] {
    const users = JSON.parse(fs.readFileSync(config.usersJson, 'utf-8')) as UserFixture[];
    if (!Array.isArray(users) || users.length === 0) {
      throw new Error(`Expected a non-empty user array in ${config.usersJson}`);
    }
    return users.map((user) => ({
      ...user,
      tenantName: `Tenant ${user.nickname}`,
      tenantCurrency: user.currency,
      locale: user.localeStr
    }));
  }

  function loadE2EUsers(): UserFixture[] {
    return loadUsers().filter((u) => u.e2e === 'e');
  }

  function loadCredentials(): TestCredentials {
    return JSON.parse(fs.readFileSync(legacyCredentialsFile, 'utf-8'));
  }

  function loadCredentialsByNickname(nickname: string): TestCredentials {
    return JSON.parse(fs.readFileSync(path.join(config.authDir, `credentials.${nickname}.json`), 'utf-8'));
  }

  function saveCredentials(user: UserFixture, isPrimary: boolean): void {
    if (!fs.existsSync(config.authDir)) {
      fs.mkdirSync(config.authDir, { recursive: true });
    }
    const payload: TestCredentials = {
      email: user.email,
      password: user.password,
      nickname: user.nickname,
      role: user.role,
      tenantName: user.tenantName,
      tenantCurrency: user.currency
    };
    fs.writeFileSync(path.join(config.authDir, `credentials.${user.nickname}.json`), JSON.stringify(payload));
    if (isPrimary) {
      fs.writeFileSync(legacyCredentialsFile, JSON.stringify(payload));
    }
  }

  function getUser(nickname: string): UserFixture {
    const user = loadUsers().find((u) => u.nickname === nickname);
    if (!user) {
      throw new Error(`User '${nickname}' not found in ${config.usersJson}`);
    }
    return user;
  }

  /**
   * Wrapped in a named test.step so the timing reporter can report the cost of the UI login as one
   * number over the whole run — every test logs in, so this is one of the larger fixed costs.
   */
  async function performLogin(page: Page, creds: TestCredentials): Promise<void> {
    await test.step('login', async () => {
      await page.goto('/login');
      await page.locator('#email').waitFor({ state: 'visible', timeout: 15_000 });
      await page.locator('#email').fill(creds.email);
      await page.locator('#password').fill(creds.password);
      await page.locator('button[type="submit"]').click();
      await page.waitForURL(config.afterLoginUrl, { timeout: 15_000 });
      await page.locator('p-tree').waitFor({ state: 'visible', timeout: 15_000 });
    });
  }

  /**
   * Returns the fixture password currently accepted by the backend, including a password-edit target from a prior run.
   */
  async function findAcceptedPassword(page: Page, user: UserFixture): Promise<string | null> {
    const candidates = [user.passwordEdit?.passwordNew, user.password].filter(
      (password, index, all): password is string => !!password && all.indexOf(password) === index
    );
    for (const password of candidates) {
      const response = await page.request.post('/api/login', {
        data: { email: user.email, password, timezoneOffset: Number(user.timezoneOffset) },
        failOnStatusCode: false
      });
      if (response.ok()) {
        return password;
      }
    }
    return null;
  }

  async function credentialsWithAcceptedPassword(page: Page, user: UserFixture): Promise<TestCredentials> {
    const password = await findAcceptedPassword(page, user);
    if (!password) {
      throw new Error(`Neither the original nor target password is accepted for ${user.email}`);
    }
    return { ...user, password };
  }

  async function findMailhogMessage(
    recipient: string,
    subjectRegex: RegExp,
    maxAttempts = 10
  ): Promise<MailhogMessage | null> {
    for (let attempt = 0; attempt < maxAttempts; attempt++) {
      const response = await fetch(
        `${config.mailApiBase}/api/v2/search?kind=to&query=${encodeURIComponent(recipient)}`
      );
      if (response.ok) {
        const result = (await response.json()) as any;
        for (const item of result.items ?? []) {
          const subject = decodeMimeHeader(item.Content?.Headers?.Subject?.[0] ?? '');
          const body = decodeMimeBody(item.Content?.Body ?? '');
          if (subjectRegex.test(subject) || subjectRegex.test(body)) {
            return { subject, body };
          }
        }
      }
      await new Promise((resolve) => setTimeout(resolve, 2000));
    }
    return null;
  }

  async function clearMailhog(): Promise<void> {
    await fetch(`${config.mailApiBase}/api/v1/messages`, { method: 'DELETE' });
  }

  async function getVerificationToken(recipientEmail: string, maxAttempts = 10): Promise<string> {
    for (let attempt = 0; attempt < maxAttempts; attempt++) {
      const response = await fetch(
        `${config.mailApiBase}/api/v2/search?kind=to&query=${encodeURIComponent(recipientEmail)}`
      );
      if (response.ok) {
        const body = (await response.json()) as any;
        for (const item of body.items ?? []) {
          const match = (item.Content?.Body ?? '').match(/tokenverify\?token=([0-9a-f-]{36})/i);
          if (match) {
            return match[1];
          }
        }
      }
      await new Promise((resolve) => setTimeout(resolve, 2000));
    }
    throw new Error(`No verification email found for ${recipientEmail} after ${maxAttempts} attempts`);
  }

  /**
   * The full registration flow through the browser: register, pull the token out of the verification mail, verify,
   * log in for the first time and create the tenant. Afterwards the application signs the user out, so the caller is
   * back at the login form.
   *
   * A user that can already sign in is left alone, and one that signs in but has no tenant yet only gets the tenant
   * step. That makes the setup re-runnable against a database that still holds the users of an earlier run — the same
   * property every spec in this repository is expected to have — instead of failing on "email already used".
   */
  async function registerAndSetupTenant(page: Page, user: UserFixture, tenantSetup: TenantSetup): Promise<void> {
    const acceptedPassword = await findAcceptedPassword(page, user);
    if (acceptedPassword) {
      await completeFirstLogin(page, { ...user, password: acceptedPassword }, tenantSetup);
      return;
    }
    await page.goto('/register');
    const nicknameInput = page.locator('#nickname');
    await nicknameInput.waitFor({ state: 'visible', timeout: 15_000 });
    await nicknameInput.fill(user.nickname);
    await page.locator('#email').fill(user.email);
    await page.locator('#password').fill(user.password);
    await page.locator('#passwordConfirm').fill(user.password);
    const localeSelect = page.locator('#localeStr');
    await localeSelect.waitFor({ state: 'visible' });
    // Frontend locale options use the language prefix (e.g. 'en' for 'en-US'). Try the full string first,
    // fall back to the language code.
    const langCode = user.localeStr.split('-')[0];
    await localeSelect
      .selectOption({ value: user.localeStr })
      .catch(() => localeSelect.selectOption({ value: langCode }));
    await page.locator('button[type="submit"]').click();
    await page.locator('.alert-info').waitFor({ state: 'visible', timeout: 30_000 });

    const token = await getVerificationToken(user.email);
    await page.goto(`/tokenverify?token=${token}`);

    await page.locator('#email').waitFor({ state: 'visible', timeout: 15_000 });
    await page.locator('.alert-success').waitFor({ state: 'visible', timeout: 5_000 });
    await completeFirstLogin(page, user, tenantSetup);
  }

  /**
   * Signs in and, if the user has no tenant yet, walks the tenant page. The application routes a tenant-less user to
   * /tenant and signs them out again after the tenant was created, so both outcomes end at a defined place.
   */
  async function completeFirstLogin(page: Page, user: UserFixture, tenantSetup: TenantSetup): Promise<void> {
    await page.goto('/login');
    await page.locator('#email').waitFor({ state: 'visible', timeout: 15_000 });
    await page.locator('#email').fill(user.email);
    await page.locator('#password').fill(user.password);
    await page.locator('button[type="submit"]').click();
    await page.waitForURL(/\/tenant|\/mainview/, { timeout: 15_000 });

    if (page.url().includes('/tenant')) {
      await tenantSetup(page, user);
      // After tenant setup the user is logged out; wait for the login form.
      await page.locator('#email').waitFor({ state: 'visible', timeout: 15_000 });
    }
  }

  return {
    config,
    loadCredentials,
    loadCredentialsByNickname,
    saveCredentials,
    loadUsers,
    loadE2EUsers,
    getUser,
    login: async (page: Page) => {
      const creds = loadCredentials();
      await performLogin(page, creds);
      return creds;
    },
    loginAs: async (page: Page, nickname: string) => {
      const creds = loadCredentialsByNickname(nickname);
      await performLogin(page, creds);
      return creds;
    },
    loginAsFixtureUser: async (page: Page, nickname: string) => {
      const creds = await credentialsWithAcceptedPassword(page, getUser(nickname));
      await performLogin(page, creds);
      return creds;
    },
    findMailhogMessage,
    clearMailhog,
    getVerificationToken,
    registerAndSetupTenant
  };
}

/** Helpers of the portable library suite: the grafiosch-test-integration backend and the grafiosch-host frontend. */
export const libHelpers = createSuiteHelpers({
  usersJson: path.resolve(
    __dirname,
    '../../../backend/grafiosch-test-integration/src/test/resources/testdata/users.json'
  ),
  authDir: path.join(__dirname, '.auth'),
  mailApiBase: process.env.LIB_E2E_MAIL_API_URL ?? 'http://localhost:8025',
  afterLoginUrl: /\/mainview/
});

export const {
  clearMailhog,
  findMailhogMessage,
  getUser,
  loadCredentials,
  loadCredentialsByNickname,
  loadUsers,
  loadE2EUsers,
  login,
  loginAs,
  loginAsFixtureUser,
  registerAndSetupTenant,
  saveCredentials
} = libHelpers;
