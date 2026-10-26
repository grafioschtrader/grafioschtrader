import { expect, Locator, Page, Route, test } from '@playwright/test';
import { getUser, loginAsFixtureUser } from './helpers';
import {
  expandTreeNode,
  loadPortfolios,
  openTreeContextMenu,
  portfolioNode,
  portfolioSubtree,
  toShortDate,
  RX
} from './portfolio.helpers';

// Both variants edit only an unsaved document and call real, read-only estimation endpoints.
test.use({ viewport: { width: 1400, height: 1800 } });
test.beforeEach(({ page }) => page.setDefaultTimeout(15_000));
const feeMenu = /Edit fee model|Gebührenmodell bearbeiten/i;
const yaml = `rules:
  - name: Notification commission
    condition: "true"
    expression: "tradeValue * 0.01"
fx:
  periods:
    - validFrom: "1900-01-01"
      status: ASSUMED
      note: "Synthetic unsaved tariff for notification tests"
      source: "https://example.com/test-tariff"
      rules:
        - name: Notification FX
          condition: "true"
          expression: "amount / 10000"
`;

async function openFeeDialog(page: Page, mode: 'plan' | 'account'): Promise<Locator> {
  if (mode === 'plan') {
    const label = /^\s*(Trading\s*platform\s*plan|Handelsplattform\s*Plan)\s*$/i;
    const node = page.getByRole('treeitem', { name: label }).locator(':scope > .p-tree-node-content').first();
    if (!(await node.isVisible())) {
      await page
        .locator('.p-tree-node-content')
        .filter({ hasText: /Base Data|Basisdaten/ })
        .first()
        .dblclick();
    }
    await node.click();
    await page.locator('configurable-table h4').click();
    const row = page.locator('configurable-table tbody tr').filter({ hasText: 'Saxo Trader' }).first();
    if (!(await row.getAttribute('class'))?.includes('p-datatable-row-selected')) await row.click();
    await expect(row).toHaveClass(/p-datatable-row-selected/);
    await row.click({ button: 'right' });
    await page.locator('[role="menu"]:visible').getByRole('menuitem', { name: feeMenu }).click();
  } else {
    const portfolio = loadPortfolios().find((p) => p.loginNickname === 'alledit' && p.securityAccounts.length > 0)!;
    expect(portfolio).toBeTruthy();
    const node = portfolioNode(page, portfolio);
    await expandTreeNode(node.locator('..'));
    const subtree = portfolioSubtree(page, portfolio);
    const folder = subtree.locator('.p-tree-node-content').filter({ hasText: RX.securityAccountsFolder }).first();
    await expandTreeNode(folder.locator('..'));
    await openTreeContextMenu(
      page,
      subtree
        .locator('.p-tree-node-content')
        .filter({ hasText: new RegExp(`^\\s*${portfolio.securityAccounts[0].name}\\s*$`) })
        .first(),
      feeMenu
    );
  }
  const dialog = page.locator('.p-dialog:visible');
  await expect(dialog.locator('fee-model-edit .monaco-editor, .monaco-editor')).toBeVisible();
  return dialog;
}

async function closeDialog(dialog: Locator): Promise<void> {
  await dialog.locator('.p-dialog-close-button').click();
  await expect(dialog).toHaveCount(0);
}

/** Delay a real answer; newer requests pass through so the old one can be released last. */
async function holdResponse(page: Page, url: string) {
  let release!: () => void;
  let arrived!: () => void;
  let done!: () => void;
  const gate = new Promise<void>((resolve) => (release = resolve));
  const pending = new Promise<void>((resolve) => (arrived = resolve));
  const completed = new Promise<void>((resolve) => (done = resolve));
  let holding = false;
  const handler = async (route: Route) => {
    if (holding) return route.fallback();
    holding = true;
    try {
      const response = await route.fetch();
      expect(response.ok(), await response.text()).toBeTruthy();
      arrived();
      await gate;
      try {
        await route.fulfill({ response });
      } catch (error) {
        if (route.request().failure()?.errorText !== 'net::ERR_ABORTED') throw error;
      }
    } finally {
      await page.unroute(url, handler);
      done();
    }
  };
  await page.route(url, handler);
  return { release, arrived: pending, done: completed };
}

async function fillNumber(scope: Locator, name: string, value: string): Promise<void> {
  await scope.locator(`#${name} input, input#${name}`).fill(value);
}

async function setYaml(page: Page): Promise<void> {
  await page.evaluate((text) => {
    const models = (window as any).monaco.editor.getModels();
    models[models.length - 1].setValue(text);
  }, yaml);
}

for (const mode of ['plan', 'account'] as const) {
  test(`${mode}: delayed options preserve drafts and estimation results render on repeated opening`, async ({
    page
  }) => {
    await loginAsFixtureUser(page, 'alledit');
    for (let opening = 0; opening < 2; opening++) {
      const options = await holdResponse(page, '**/api/stockexchange?includeNameOfCalendarIndex=false');
      try {
        const dialog = await openFeeDialog(page, mode);
        await options.arrived;
        const fee = dialog.locator('p-fieldset').first();
        await fee.locator('.p-fieldset-legend button').click();
        await fillNumber(fee, 'tradeValue', '1200');
        options.release();
        await options.done;
        await expect(fee.locator('select#specInvestInstrument option[value="0"]')).not.toHaveText('DIRECT_INVESTMENT');
        await expect(fee.locator('#tradeValue input, input#tradeValue')).toHaveValue(/1.?200/);
        await setYaml(page);
        const estimate = await holdResponse(
          page,
          mode === 'plan' ? '**/api/tradingplatformplan/estimatecost' : '**/api/securityaccount/estimatecostyaml'
        );
        try {
          await fee
            .locator('dynamic-form')
            .getByRole('button', { name: /Test fee estimation|Gebühren.*testen/i })
            .click();
          await estimate.arrived;
          await expect(fee).not.toContainText('Notification commission');
          estimate.release();
          await expect(fee).toContainText('Notification commission');
          await expect(fee.locator('.text-green-600')).toContainText('12');
        } finally {
          estimate.release();
        }
        await closeDialog(dialog);
      } finally {
        options.release();
      }
    }
  });

  test(`${mode}: FX renders the newest response, errors and recovery and cancels on close`, async ({ page }) => {
    await loginAsFixtureUser(page, 'alledit');
    const kinds = await holdResponse(page, '**/api/tradingplatformplan/fxkinds');
    const dialog = await openFeeDialog(page, mode);
    const fx = dialog.locator('fx-markup-preview');
    const fieldset = dialog.locator('p-fieldset').last();
    try {
      await fieldset.locator('.p-fieldset-legend button').click();
      await kinds.arrived;
      await fillNumber(fx, 'amount', '1000');
      kinds.release();
      await kinds.done;
      await expect(fx.locator('#amount input, input#amount')).toHaveValue(/1.?000/);
      await fx.locator('select#payCurrency').selectOption('CHF');
      await fx.locator('select#receiveCurrency').selectOption('USD');
      await fx.locator('select#kind').selectOption('TRANSFER');
      await expect(fx.locator('select#kind option:checked')).not.toHaveText('TRANSFER');
      await fx.locator('#date input').pressSequentially(toShortDate('2026-10-01', getUser('alledit').localeStr));
      await fx.locator('#date input').press('Tab');
      await setYaml(page);
      const button = fx.getByRole('button', {
        name: /Test FX markup|FX.*testen|Devisen.*testen|Währungsaufschlag testen/i
      });
      const endpoint =
        mode === 'plan' ? '**/api/tradingplatformplan/estimatefxmarkup' : '**/api/securityaccount/estimatefxmarkupyaml';
      const obsolete = await holdResponse(page, endpoint);
      try {
        await button.click();
        await obsolete.arrived;
        await fillNumber(fx, 'amount', '2000');
        await button.click();
        await expect(fx.locator('[aria-live]')).toContainText(/0[.,]2/);
        await expect(fx.locator('[aria-live]')).toContainText('Notification FX');
        obsolete.release();
        await obsolete.done;
        await expect(fx.locator('[aria-live]')).toContainText(/0[.,]2/);
      } finally {
        obsolete.release();
      }
      await page.route(endpoint, (route) => route.abort('failed'), { times: 1 });
      await button.click();
      await expect(fx.locator('.text-red-600')).toBeVisible();
      await button.click();
      await expect(fx.locator('.text-red-600')).toHaveCount(0);
      await expect(fx.locator('[aria-live]')).toContainText('Notification FX');
      const abandoned = await holdResponse(page, endpoint);
      try {
        await button.click();
        await abandoned.arrived;
        await closeDialog(dialog);
        abandoned.release();
        await abandoned.done;
        const reopened = await openFeeDialog(page, mode);
        await expect(reopened.locator('fx-markup-preview [aria-live]')).toHaveCount(0);
        await closeDialog(reopened);
      } finally {
        abandoned.release();
      }
    } finally {
      kinds.release();
    }
  });
}
