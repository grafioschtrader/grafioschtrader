import { expect, Page, test } from '@playwright/test';
import { loginAsFixtureUser } from './helpers';

test.use({ actionTimeout: 15000 });

const NAME = 'YAML validation e2e';
const FEE = "rules:\n  - name: Flat\n    condition: 'true'\n    expression: '5'";
const TOKEN =
  "seed: {url: 'https://example.test', regex: '(ticket)'}\nlogin: {url: 'https://example.test', body: '{seedValue}', jwtPath: token}";
async function auth(page: Page) {
  return { 'x-auth-token': (await page.evaluate(() => sessionStorage.getItem('jwt')))! };
}
async function cleanup(page: Page) {
  const headers = await auth(page);
  for (const [route, id, owns] of [
    ['tradingplatformplan', 'idTradingPlatformPlan', (row: any) => row.platformPlanNameNLS?.map?.en === NAME],
    ['genericconnector', 'idGenericConnector', (row: any) => row.shortId === 'yaml-validation-e2e']
  ] as const) {
    const response = await page.request.get(`/api/${route}`, { headers });
    expect(response.ok()).toBeTruthy();
    for (const row of await response.json())
      if (owns(row)) {
        const deleted = await page.request.delete(`/api/${route}/${row[id]}`, { headers });
        expect(deleted.ok(), await deleted.text()).toBeTruthy();
      }
  }
}
async function plan(page: Page) {
  const response = await page.request.post('/api/tradingplatformplan', {
    headers: await auth(page),
    data: { platformPlanNameNLS: { map: { en: NAME, de: NAME } }, transactionFeePlan: 0, feeModelYaml: FEE }
  });
  expect(response.ok(), await response.text()).toBeTruthy();
  return response.json();
}
async function readPlan(page: Page, id: number) {
  return (await (await page.request.get('/api/tradingplatformplan', { headers: await auth(page) })).json()).find(
    (row: any) => row.idTradingPlatformPlan === id
  );
}

test.beforeEach(async ({ page, request }) => {
  const info = await request.get(`${process.env.E2E_BACKEND_URL ?? 'http://localhost:8080'}/api/gtinfo`);
  expect((await info.json()).databaseName).toBe('grafioschtrader_t');
  await loginAsFixtureUser(page, 'alledit');
  await cleanup(page);
});
test.afterEach(async ({ page }) => {
  await cleanup(page);
});

test('direct configuration writes reject invalid YAML and leave saved values unchanged', async ({ page }) => {
  const headers = await auth(page);
  const saved = await plan(page);
  for (const yaml of ['rules: [', `${FEE}\n---\nx: 1`, 'rules: []\nrules: []']) {
    const rejected = await page.request.put('/api/tradingplatformplan', {
      headers,
      data: { ...saved, feeModelYaml: yaml }
    });
    expect(rejected.status(), await rejected.text()).toBe(400);
    expect((await readPlan(page, saved.idTradingPlatformPlan)).feeModelYaml).toBe(FEE);
  }
  const invalidCreate = await page.request.post('/api/tradingplatformplan', {
    headers,
    data: { platformPlanNameNLS: { map: { en: NAME, de: NAME } }, transactionFeePlan: 0, feeModelYaml: 'rules: [' }
  });
  expect(invalidCreate.status()).toBe(400);
  const definition = {
    shortId: 'yaml-validation-e2e',
    readableName: NAME,
    domainUrl: 'https://example.test/',
    endpoints: [],
    httpHeaders: [],
    tokenConfigYaml: TOKEN
  };
  const created = await page.request.post('/api/genericconnector', { headers, data: definition });
  expect(created.ok(), await created.text()).toBeTruthy();
  const connector = await created.json();
  const rejected = await page.request.put('/api/genericconnector', {
    headers,
    data: { ...connector, tokenConfigYaml: 'seed: [' }
  });
  expect(rejected.status(), await rejected.text()).toBe(400);
  const read = await page.request.get(`/api/genericconnector/${connector.idGenericConnector}`, { headers });
  expect((await read.json()).tokenConfigYaml).toBe(TOKEN);
  const clear = await page.request.put('/api/tradingplatformplan', { headers, data: { ...saved, feeModelYaml: null } });
  expect(clear.ok(), await clear.text()).toBeTruthy();
});

test('fee editor retains rejected text, offers help and previews without saving', async ({ page }) => {
  const saved = await plan(page);
  await page.goto('/mainview/tradingplatformplan');
  const row = page.locator('tr[data-p-selectable-row]', { hasText: NAME }).first();
  await row.click();
  await row.click({ button: 'right' });
  await page
    .locator('[role="menu"]:visible')
    .getByText(/(Edit fee model|Geb.hrenmodell bearbeiten)/i)
    .click();
  const dialog = page.locator('.p-dialog:visible');
  await expect(dialog.locator('.monaco-editor')).toBeVisible();
  const setText = async (text: string) => {
    // Replace the complete document without Monaco auto-indenting or closing delimiters while typing.
    await page.evaluate((value) => {
      const model = (window as any).monaco.editor
        .getModels()
        .find((candidate: any) => candidate.getLanguageId() === 'yaml');
      model.setValue(value);
    }, text);
    await dialog.locator('.monaco-editor .view-lines').click({ position: { x: 80, y: 10 } });
    await page.keyboard.press('ControlOrMeta+End');
  };
  await setText('custody:\n  periods:\n    - amount: ');
  await page.keyboard.press('Control+Space');
  await expect(dialog.locator('.suggest-widget.visible')).toContainText('assetValue');
  await page.keyboard.press('Escape');
  await setText('rules: ]');
  await expect(dialog.getByRole('button', { name: /(Save|Speichern)$/ })).toBeDisabled();
  await expect(dialog.locator('[role="alert"]')).toBeVisible();
  await setText('rules: []');
  await dialog.getByRole('button', { name: /^(Validate|Pr.fen)$/ }).click();
  await expect(dialog.locator('[role="alert"]').first()).toBeVisible();
  await expect(dialog).toBeVisible();
  expect((await readPlan(page, saved.idTradingPlatformPlan)).feeModelYaml).toBe(FEE);
  await setText(FEE.replace("'5'", "'7'"));
  await dialog.getByRole('button', { name: /^(Validate|Pr.fen)$/ }).click();
  await expect(dialog.getByText(/(Configuration is valid|Konfiguration ist g.ltig)/)).toBeVisible();
  await dialog.getByRole('button', { name: /^(Test fee estimation|Geb.hrensch.tzung testen)$/ }).click();
  const preview = page.waitForResponse((response) => response.url().includes('/estimatecost'));
  await dialog
    .locator('dynamic-form')
    .getByRole('button', { name: /(Test fee estimation|Geb.hrensch.tzung testen)/ })
    .click();
  expect((await (await preview).json()).estimatedCost).toBe(7);
  expect((await readPlan(page, saved.idTradingPlatformPlan)).feeModelYaml).toBe(FEE);
  await dialog.getByRole('button', { name: /(Save|Speichern)$/ }).click();
  await expect(dialog).toHaveCount(0);
  expect((await readPlan(page, saved.idTradingPlatformPlan)).feeModelYaml).toContain("'7'");
});
