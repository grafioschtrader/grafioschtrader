import { expect, Locator, Page, Route, test } from '@playwright/test';
import { loginAsFixtureUser } from './helpers';
import { selectByValue } from './generic-connector.helpers';

// Drives the language-model parts of the security UDF metadata: the proposals dialog of an LLM field, the rule
// proposal dialog of a manual field and the value-suggestion test. Two fabricated own fields are added to the table
// response and every language-model endpoint answers from here after a held delay, so the spec needs no provider key
// and writes nothing to the database.

const LLM_FIELD_ID = 990001;
const MANUAL_FIELD_ID = 990002;
const LLM_FIELD = 'E2E LLM proposals';
const MANUAL_FIELD = 'E2E rule proposal';
const PROPOSED_RULE = '[mic=XSWX] https://example.com/{isin}';

const RX = {
  baseDataNode: /(Base Data|Basisdaten)/i,
  securityNode: /^\s*(Custom field Instrument|Zusatzfeld Instrument|UDF_METADATA_SECURITY)\s*$/i,
  proposals: /^(Proposals|Vorschläge)\b/,
  acceptSelected: /^(Accept selected|Ausgewählte annehmen)$/,
  propose: /^(Propose rule|Regel vorschlagen)$/,
  send: /^(Send|Senden)$/,
  takeOver: /^(Take over rule|Regel übernehmen)$/,
  testDescription: /^(Test description|Beschreibung testen)$/,
  proposalTitle: /(Rule proposal by a language model|Regelvorschlag durch ein Sprachmodell)/
};

/** A response answered from the test after release; arrived resolves once the request reached the route. */
interface Held {
  arrived: Promise<void>;
  release: () => void;
}

async function hold(page: Page, url: string, method: string, body: () => unknown): Promise<Held> {
  let release!: () => void;
  let arrive!: () => void;
  const gate = new Promise<void>((resolve) => (release = resolve));
  const arrived = new Promise<void>((resolve) => (arrive = resolve));
  let holding = false;
  const handler = async (route: Route): Promise<void> => {
    if (holding || route.request().method() !== method) return route.fallback();
    holding = true;
    arrive();
    await gate;
    await page.unroute(url, handler);
    try {
      await route.fulfill({ json: body() });
    } catch (error) {
      if (route.request().failure()?.errorText !== 'net::ERR_ABORTED') throw error;
    }
  };
  await page.route(url, handler);
  return { arrived, release };
}

/** Adds the two fabricated own fields to the table and offers rule proposal and value suggestion. */
async function fabricateFields(page: Page): Promise<void> {
  const idUser = Number(await page.evaluate(() => sessionStorage.getItem('idUser')));
  await page.route('**/api/udfmetadatasecurity', async (route) => {
    if (route.request().method() !== 'GET') return route.fallback();
    const response = await route.fetch();
    const rows = (await response.json()) as any[];
    const template = rows[0];
    const fake = (id: number, description: string, fillSource: string, uiOrder: number) => ({
      ...template,
      idUDFMetadata: id,
      idUser,
      description,
      descriptionHelp: null,
      udfDataType: 'UDF_String',
      fieldSize: '32',
      uiOrder,
      udfSpecialType: null,
      fillSource,
      fillRule: fillSource === 'LLM' ? 'Name of the main competitor' : null,
      fillPeriod: null
    });
    await route.fulfill({
      response,
      json: [...rows, fake(LLM_FIELD_ID, LLM_FIELD, 'LLM', 98), fake(MANUAL_FIELD_ID, MANUAL_FIELD, 'MANUAL', 99)]
    });
  });
  await page.route('**/api/udfmetadatasecurity/fillsources', async (route) => {
    const response = await route.fetch();
    const offer = await response.json();
    const sources = (offer.fillSources ?? []).filter((s: any) => s.fillSource !== 'LLM' && s.fillSource !== 'TEMPLATE');
    await route.fulfill({
      response,
      json: {
        ...offer,
        ruleProposal: true,
        valueSuggestion: true,
        fillSources: [
          ...sources,
          { fillSource: 'TEMPLATE', udfDataTypes: ['UDF_String', 'UDF_URLString'] },
          { fillSource: 'LLM', udfDataTypes: ['UDF_String'] }
        ]
      }
    });
  });
  await page.route(`**/api/udfmetadatasecurity/99000*/fillstatus`, (route) =>
    route.fulfill({ json: { inScope: 2, countByState: {} } })
  );
}

async function openSecurityMetadata(page: Page): Promise<Locator> {
  const baseDataNode = page.locator('.p-tree-node-content', { hasText: RX.baseDataNode }).first();
  await baseDataNode.waitFor({ state: 'visible', timeout: 15_000 });
  const treeNode = page.locator('.p-tree-node-content', { hasText: RX.securityNode }).first();
  if (!(await treeNode.isVisible())) await baseDataNode.dblclick();
  await treeNode.click();
  await page.waitForURL(/\/mainview\/udfmetadatasecurity/, { timeout: 10_000 });
  const table = page.locator('configurable-table').first();
  await expect(table.getByRole('cell', { name: MANUAL_FIELD, exact: true })).toBeVisible();
  return table;
}

async function rowMenu(page: Page, table: Locator, description: string): Promise<Locator> {
  const row = table.locator('tbody tr').filter({ has: page.getByRole('cell', { name: description, exact: true }) });
  if (!(await row.getAttribute('class'))?.includes('p-datatable-row-selected')) await row.click();
  await row.click({ button: 'right' });
  const menu = page.locator('[role="menu"]:visible');
  await expect(menu).toBeVisible();
  return menu;
}

async function openEdit(page: Page, table: Locator, description: string): Promise<Locator> {
  await (await rowMenu(page, table, description)).getByRole('menuitem', { name: /^(Edit|Bearbeiten)\b/ }).click();
  const dialog = page.locator('udf-metadata-security-edit .p-dialog').first();
  await expect(dialog.locator('#description')).toHaveValue(description);
  return dialog;
}

async function close(dialog: Locator): Promise<void> {
  await dialog.locator('.p-dialog-close-button').first().click();
  await expect(dialog).toHaveCount(0);
}

test.describe('UDF fill by language model notification', () => {
  test.use({ viewport: { width: 1600, height: 1400 } });
  test.beforeEach(async ({ page }) => {
    page.setDefaultTimeout(15_000);
    await loginAsFixtureUser(page, 'admin');
    await fabricateFields(page);
  });

  test('proposals arrive after a delay and a decision reloads them', async ({ page }) => {
    const table = await openSecurityMetadata(page);
    const proposal = (id: number, name: string, value: string) => ({
      idSecuritycurrency: id,
      name,
      value,
      sourceUrl: `https://example.com/${id}`,
      evidence: `${name} competes with ${value}`,
      asOfDate: '2026-10-01',
      ruleVersion: 1
    });
    const loaded = await hold(page, `**/api/udfmetadatasecurity/${LLM_FIELD_ID}/proposals`, 'GET', () => [
      proposal(1, 'E2E Alpha', 'Beta Corp'),
      proposal(2, 'E2E Gamma', 'Delta Corp')
    ]);
    await (await rowMenu(page, table, LLM_FIELD)).getByRole('menuitem', { name: RX.proposals }).click();
    // Both language-model dialogs are appended to the body, outside their component element.
    const dialog = page.locator('.p-dialog', { hasText: `: ${LLM_FIELD}` });
    await loaded.arrived;
    await expect(dialog.locator('.proposal-row')).toHaveCount(0);
    loaded.release();
    await expect(dialog.locator('.proposal-row')).toHaveCount(2);
    const accept = dialog.getByRole('button', { name: RX.acceptSelected });
    await expect(accept).toBeDisabled();
    await dialog.locator('.proposal-row').first().locator('input[type="checkbox"]').check();
    await expect(accept).toBeEnabled();

    const decided = await hold(page, `**/api/udfmetadatasecurity/${LLM_FIELD_ID}/proposals/accept`, 'POST', () => [
      { idSecuritycurrency: 1, outcome: 'APPLIED' }
    ]);
    const reloaded = await hold(page, `**/api/udfmetadatasecurity/${LLM_FIELD_ID}/proposals`, 'GET', () => [
      proposal(2, 'E2E Gamma', 'Delta Corp')
    ]);
    await accept.click();
    await decided.arrived;
    decided.release();
    await reloaded.arrived;
    await expect(dialog.locator('p.bg-info')).toContainText('1');
    await expect(dialog.locator('.proposal-row')).toHaveCount(2);
    reloaded.release();
    await expect(dialog.locator('.proposal-row')).toHaveCount(1);
    await expect(accept).toBeDisabled();
    await close(dialog);
  });

  test('a rule proposal shows its examples and result after delays and fills the rule', async ({ page }) => {
    const table = await openSecurityMetadata(page);
    const dialog = await openEdit(page, table, MANUAL_FIELD);
    await selectByValue(dialog, 'fillSource', 'TEMPLATE');
    const preview = await hold(
      page,
      `**/api/udfmetadatasecurity/${MANUAL_FIELD_ID}/proposerule/preview`,
      'GET',
      () => ({
        providers: [{ label: 'E2E model', keySource: 'TENANT' }],
        examples: [
          {
            idSecuritycurrency: 1,
            name: 'E2E Alpha',
            isin: 'CH0000000001',
            ticker: 'ALP',
            mic: 'XSWX',
            currency: 'CHF',
            value: 'a'
          },
          {
            idSecuritycurrency: 2,
            name: 'E2E Gamma',
            isin: 'CH0000000002',
            ticker: 'GAM',
            mic: 'XSWX',
            currency: 'CHF',
            value: 'b'
          }
        ]
      })
    );
    await dialog.getByRole('button', { name: RX.propose }).click();
    const proposal = page.locator('.p-dialog', { hasText: RX.proposalTitle });
    await preview.arrived;
    await expect(proposal.locator('p-progressspinner')).toBeVisible();
    preview.release();
    await expect(proposal.locator('.proposal-example')).toHaveCount(2);

    const proposed = await hold(page, `**/api/udfmetadatasecurity/${MANUAL_FIELD_ID}/proposerule`, 'POST', () => ({
      rule: PROPOSED_RULE,
      parseError: null,
      testResult: {
        inScope: 2,
        withValue: 2,
        produced: 2,
        equal: 2,
        differing: 0,
        noLine: 0,
        invalid: 0,
        mismatches: [],
        samples: [{ name: 'E2E Alpha', computed: 'https://example.com/CH0000000001' }]
      }
    }));
    await proposal.getByRole('button', { name: RX.send }).click();
    await proposed.arrived;
    await expect(proposal.locator('p-progressspinner')).toBeVisible();
    proposed.release();
    await expect(proposal.locator('.proposal-rule')).toHaveText(PROPOSED_RULE);
    await expect(proposal.locator('udf-fill-test-result')).toContainText('https://example.com/CH0000000001');
    await proposal.getByRole('button', { name: RX.takeOver }).click();
    await expect(proposal).toHaveCount(0);
    await expect(dialog.locator('#fillRule')).toHaveValue(PROPOSED_RULE);
    await close(dialog);
  });

  test('the value-suggestion test lists each result as it arrives', async ({ page }) => {
    const table = await openSecurityMetadata(page);
    const dialog = await openEdit(page, table, LLM_FIELD);
    const candidates = await hold(page, '**/api/udfmetadatasecurity/testsuggest/candidates', 'POST', () => [
      { idSecuritycurrency: 1, name: 'E2E Alpha', existingValue: null },
      { idSecuritycurrency: 2, name: 'E2E Gamma', existingValue: 'Delta Corp' }
    ]);
    const result = (id: number, value: string, equalsExisting: boolean | null) => ({
      idSecuritycurrency: id,
      outcome: 'PROPOSED',
      value,
      sourceUrl: `https://example.com/${id}`,
      evidence: `evidence ${id}`,
      asOfDate: null,
      note: null,
      equalsExisting,
      fetches: []
    });
    const first = await hold(page, '**/api/udfmetadatasecurity/testsuggest/1', 'POST', () =>
      result(1, 'Beta Corp', null)
    );
    const second = await hold(page, '**/api/udfmetadatasecurity/testsuggest/2', 'POST', () =>
      result(2, 'Delta Corp', true)
    );
    await dialog.getByRole('button', { name: RX.testDescription }).click();
    const test = dialog.locator('udf-fill-suggest-test-result');
    await candidates.arrived;
    candidates.release();
    await first.arrived;
    await expect(test.locator('p-progressspinner')).toBeVisible();
    await expect(test.locator('li')).toHaveCount(0);
    first.release();
    await second.arrived;
    await expect(test.locator('ul > li')).toHaveCount(1);
    await expect(test).toContainText('Beta Corp');
    second.release();
    await expect(test.locator('ul > li')).toHaveCount(2);
    await expect(test.locator('p-progressspinner')).toHaveCount(0);
    await close(dialog);
  });
});
