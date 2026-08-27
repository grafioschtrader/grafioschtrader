import { chromium, Page } from '@playwright/test';

const MAIL_API = process.env.LIB_E2E_MAIL_API_URL ?? 'http://localhost:8025';
const USERS = [
  { nickname: 'admin', email: 'admin@test.local', role: 'ROLE_ADMIN' },
  { nickname: 'alledit', email: 'alledit@test.local', role: 'ROLE_ALLEDIT' },
  { nickname: 'limited', email: 'limited@test.local', role: 'ROLE_LIMITEDIT' }
];
const PASSWORD = 'A123abcd';

async function assertPeer(url: string, databaseName: string, peerProfile: boolean): Promise<void> {
  const response = await fetch(`${url}/api/integration-info`);
  if (!response.ok) throw new Error(`${url}/api/integration-info returned HTTP ${response.status}`);
  const info = (await response.json()) as { databaseName: string; activeProfiles: string[] };
  if (info.databaseName !== databaseName || !info.activeProfiles.includes('e2e')) {
    throw new Error(`Wrong GTNet peer at ${url}: ${JSON.stringify(info)}`);
  }
  if (peerProfile && !info.activeProfiles.includes('e2e-peer')) {
    throw new Error(`Peer B at ${url} is missing the e2e-peer profile`);
  }
}

async function verificationToken(email: string): Promise<string> {
  for (let attempt = 0; attempt < 15; attempt++) {
    const response = await fetch(`${MAIL_API}/api/v2/search?kind=to&query=${encodeURIComponent(email)}`);
    const body = response.ok ? ((await response.json()) as any) : null;
    for (const item of body?.items ?? []) {
      const match = String(item.Content?.Body ?? '').match(/tokenverify\?token=([0-9a-f-]{36})/i);
      if (match) return match[1];
    }
    await new Promise((resolve) => setTimeout(resolve, 1000));
  }
  throw new Error(`No verification mail received for ${email}`);
}

async function login(page: Page, email: string): Promise<void> {
  await page.goto('/login');
  await page.locator('#email').fill(email);
  await page.locator('#password').fill(PASSWORD);
  await page.locator('button[type="submit"]').click();
  await page.waitForURL(/\/tenant|\/mainview/, { timeout: 15_000 });
}

async function bootstrapPeer(frontendUrl: string): Promise<void> {
  const browser = await chromium.launch();
  const context = await browser.newContext({ baseURL: frontendUrl });
  const page = await context.newPage();
  try {
    for (const user of USERS) {
      await fetch(`${MAIL_API}/api/v1/messages`, { method: 'DELETE' });
      await page.goto('/register');
      await page.locator('#nickname').fill(user.nickname);
      await page.locator('#email').fill(user.email);
      await page.locator('#password').fill(PASSWORD);
      await page.locator('#passwordConfirm').fill(PASSWORD);
      await page
        .locator('#localeStr')
        .selectOption('en-US')
        .catch(() => page.locator('#localeStr').selectOption('en'));
      await page.locator('button[type="submit"]').click();
      await page.locator('.alert-info').waitFor({ state: 'visible', timeout: 30_000 });
      await page.goto(`/tokenverify?token=${await verificationToken(user.email)}`);
      await page.locator('.alert-success').waitFor({ state: 'visible', timeout: 10_000 });
      await login(page, user.email);
      if (page.url().includes('/tenant')) {
        await page.locator('#tenantName').fill(`Tenant ${user.nickname}`);
        await page.locator('button[type="submit"]').click();
        await page.locator('#email').waitFor({ state: 'visible', timeout: 15_000 });
      }
    }

    await login(page, USERS[0].email);
    const token = await page.evaluate(() => sessionStorage.getItem('jwt'));
    const usersResponse = await context.request.get('/api/useradmin', { headers: { 'x-auth-token': token! } });
    if (!usersResponse.ok()) throw new Error(`Reading users for role setup failed: ${await usersResponse.text()}`);
    const users = (await usersResponse.json()) as any[];
    for (const fixture of USERS.slice(1)) {
      const user = users.find((candidate) => candidate.email === fixture.email);
      if (!user) throw new Error(`Registered user ${fixture.email} is missing`);
      user.mostPrivilegedRole = fixture.role;
      const update = await context.request.put('/api/useradmin', {
        headers: { 'x-auth-token': token! },
        data: user
      });
      if (!update.ok()) throw new Error(`Promoting ${fixture.email} failed: ${await update.text()}`);
    }
  } finally {
    await browser.close();
  }
}

export default async function globalSetup(): Promise<void> {
  const backendA = process.env.GTNET_PEER_A_BACKEND_URL ?? 'http://localhost:8081';
  const backendB = process.env.GTNET_PEER_B_BACKEND_URL ?? 'http://localhost:8082';
  const frontendA = process.env.GTNET_PEER_A_FRONTEND_URL ?? 'http://localhost:4201';
  const frontendB = process.env.GTNET_PEER_B_FRONTEND_URL ?? 'http://localhost:4202';
  await assertPeer(backendA, 'grafiosch_t', false);
  await assertPeer(backendB, 'grafiosch_t1', true);
  if (process.env.GTNET_SKIP_BOOTSTRAP === 'true') return;
  await bootstrapPeer(frontendA);
  await bootstrapPeer(frontendB);
}
