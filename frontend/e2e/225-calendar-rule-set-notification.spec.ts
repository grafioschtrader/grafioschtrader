import { expect, Locator, Page, Route, test } from '@playwright/test';
import { getUser, loginAsFixtureUser } from './helpers';
import { toShortDate } from './portfolio.helpers';

const api = '**/api/tradingcalendarruleset';
const yaml = `rules:
  - name: Notification closure
    type: FIXED
    month: 1
    day: 2
    observance: NEXT_WEEKDAY
`;
test.use({ viewport: { width: 1400, height: 1800 } });
test.beforeEach(async ({ page }) => {
  page.setDefaultTimeout(15_000);
  await loginAsFixtureUser(page, 'alledit');
});

async function openView(page: Page): Promise<Locator> {
  const node = page
    .getByRole('treeitem', { name: /^\s*(Stock\s*exchange|Handelsplatz)\s*$/i })
    .locator(':scope > .p-tree-node-content')
    .first();
  if (!(await node.isVisible())) {
    await page
      .locator('.p-tree-node-content')
      .filter({ hasText: /Base Data|Basisdaten/ })
      .first()
      .dblclick();
  }
  await node.click();
  await page
    .locator('p-tab')
    .filter({ hasText: /Trading calendar rules|Handelskalender-Regeln/i })
    .click();
  await page.waitForURL(/\/rulesets$/);
  const table = page.locator('configurable-table');
  await expect(table.locator('tbody tr').first()).toBeVisible();
  return table;
}

async function openDialog(page: Page, table: Locator, row?: Locator): Promise<Locator> {
  await table.locator('h4').click();
  if (row && !(await row.getAttribute('class'))?.includes('p-datatable-row-selected')) await row.click();
  await (row ?? table.locator('h4')).click({ button: 'right' });
  await page
    .locator('[role="menu"]:visible')
    .getByRole('menuitem', { name: row ? /^(Edit|Bearbeiten)\s/ : /^(Create|Erstellen)\s/ })
    .click();
  const dialog = page.locator('.p-dialog:visible');
  await expect(dialog.locator('.monaco-editor')).toBeVisible();
  // Blurring an empty required name changes the centered dialog's height before any header-button click.
  await dialog.locator('#name').blur();
  return dialog;
}

async function closeDialog(dialog: Locator): Promise<void> {
  await dialog.locator('.p-dialog-close-button').click();
  await expect(dialog).toHaveCount(0);
}

async function setYaml(page: Page, text: string): Promise<void> {
  await page.evaluate((value) => {
    const models = (window as any).monaco.editor.getModels();
    models[models.length - 1].setValue(value);
  }, text);
}

/** Holds one real response, leaving subsequent requests free to finish first. */
async function holdResponse(page: Page, url: string) {
  let release!: () => void, arrived!: () => void, done!: () => void;
  const gate = new Promise<void>((resolve) => (release = resolve));
  const pending = new Promise<void>((resolve) => (arrived = resolve));
  const completed = new Promise<void>((resolve) => (done = resolve));
  let holding = false;
  const handler = async (route: Route) => {
    if (holding || (url === api && route.request().method() !== 'GET')) return route.fallback();
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

test('drafts and YAML diagnostics survive delayed options and schema on repeated opening', async ({ page }) => {
  const table = await openView(page);
  const abandoned = await holdResponse(page, `${api}/options`);
  try {
    const dialog = await openDialog(page, table);
    await abandoned.arrived;
    await closeDialog(dialog);
    abandoned.release();
    await abandoned.done;
  } finally {
    abandoned.release();
  }
  for (let opening = 0; opening < 2; opening++) {
    const options = await holdResponse(page, `${api}/options`);
    const schema = await holdResponse(page, '**/assets/schemas/trading-calendar-rule-schema.json');
    try {
      const dialog = await openDialog(page, table);
      await options.arrived;
      await schema.arrived;
      await dialog.locator('#name').fill('Unsaved notification draft');
      await setYaml(page, 'rules: [');
      await expect(dialog.locator('yaml-editor [role="alert"]')).not.toHaveCount(0);
      schema.release();
      await schema.done;
      await expect(dialog.locator('yaml-editor [role="alert"]')).not.toHaveCount(0);
      options.release();
      await expect(dialog.locator('select#idExtendsRuleSet option')).not.toHaveCount(0);
      await expect(dialog.locator('#name')).toHaveValue('Unsaved notification draft');
      await expect(dialog.locator('button[type="submit"]')).toBeDisabled();
      await setYaml(page, yaml);
      await expect(dialog.locator('button[type="submit"]')).toBeEnabled();
      await closeDialog(dialog);
    } finally {
      options.release();
      schema.release();
    }
  }
});

test('real YAML CRUD refreshes selection and the main menu after delayed table answers', async ({ page }) => {
  const name = 'E2E OnPush calendar rules';
  const renamed = `${name} updated`;
  const token = await page.evaluate(() => sessionStorage.getItem('jwt'));
  const headers = { 'x-auth-token': token!, Accept: 'application/json' };
  const existing = await page.request.get('/api/tradingcalendarruleset', { headers });
  expect(existing.ok()).toBeTruthy();
  for (const row of await existing.json()) {
    if ([name, renamed].includes(row.name)) {
      const deleted = await page.request.delete(`/api/tradingcalendarruleset/${row.idTradingCalendarRuleSet}`, {
        headers
      });
      expect(deleted.ok()).toBeTruthy();
    }
  }
  const table = await openView(page);
  const ownRow = () => table.locator('tbody tr').filter({ hasText: name });
  const dialog = await openDialog(page, table);
  await expect(dialog.locator('select#idExtendsRuleSet option')).not.toHaveCount(0);
  await dialog.locator('#name').fill(name);
  await setYaml(page, yaml);
  const created = await holdResponse(page, api);
  try {
    await dialog.locator('button[type="submit"]').click();
    await created.arrived;
    await expect(dialog).toHaveCount(0);
    await expect(ownRow()).toHaveCount(0);
    created.release();
    await expect(ownRow()).toBeVisible();
  } finally {
    created.release();
  }
  const edit = await openDialog(page, table, ownRow());
  await expect(edit.locator('#name')).toHaveValue(name);
  await edit.locator('#name').fill(renamed);
  await setYaml(page, yaml.replace('Notification closure', 'Updated closure'));
  const updated = await holdResponse(page, api);
  try {
    await edit.locator('button[type="submit"]').click();
    await updated.arrived;
    await expect(ownRow()).not.toContainText(renamed);
    updated.release();
    await expect(ownRow()).toContainText(renamed);
    await expect(ownRow()).toHaveClass(/p-datatable-row-selected/);
  } finally {
    updated.release();
  }
  const mainEdit = page
    .locator('p-menubar')
    .getByRole('menuitem', { name: /^(Edit|Bearbeiten)$/ })
    .first();
  await mainEdit.click();
  await page
    .locator('p-menubar')
    .getByRole('menuitem', { name: /^(Edit|Bearbeiten)\s/ })
    .click();
  const reopened = page.locator('.p-dialog:visible');
  await expect(reopened.locator('#name')).toHaveValue(renamed);
  await expect
    .poll(() => page.evaluate(() => (window as any).monaco.editor.getModels().at(-1).getValue()))
    .toContain('Updated closure');
  await expect(reopened.locator('select#idExtendsRuleSet option').filter({ hasText: renamed })).toHaveCount(0);
  await closeDialog(reopened);
  await mainEdit.click();
  await page
    .locator('p-menubar')
    .getByRole('menuitem', { name: /^(Delete|Löschen)\s/ })
    .click();
  await page
    .locator('[role="alertdialog"]:visible')
    .getByRole('button', { name: /^(Yes|Ja)$/ })
    .click();
  await expect(ownRow()).toHaveCount(0);
});

test('preview replaces obsolete year and YAML responses, recovers from failure and cancels on close', async ({
  page
}) => {
  const table = await openView(page);
  const row = table.locator('tbody tr').filter({ has: page.getByRole('cell', { name: 'XSWX', exact: true }) });
  const dialog = await openDialog(page, table, row);
  await expect(dialog.locator('#name')).toHaveValue('SIX Swiss Exchange');
  await setYaml(page, yaml);
  const endpoint = `${api}/*/preview?year=*`;
  const obsolete = await holdResponse(page, endpoint);
  const preview = dialog.locator('trading-calendar-rule-set-preview');
  const next = dialog.getByRole('button', { name: /(Next year|Nächstes Jahr)$/i });
  const previous = dialog.getByRole('button', { name: /(Previous year|Vorheriges Jahr)$/i });
  try {
    await dialog.locator('p-fieldset .p-fieldset-legend button').click();
    await obsolete.arrived;
    await expect(preview.getByRole('columnheader', { name: /Closure date|Schliessungstag/i })).toBeVisible();
    await setYaml(page, yaml.replace('Notification closure', 'Current closure'));
    await next.click();
    await expect(preview.locator('tbody tr')).toHaveCount(1);
    await expect(preview).toContainText('Current closure');
    const year = new Date().getFullYear() + 1;
    const date = new Date(Date.UTC(year, 0, 2));
    while ([0, 6].includes(date.getUTCDay())) date.setUTCDate(date.getUTCDate() + 1);
    const formatted = toShortDate(date.toISOString().slice(0, 10), getUser('alledit').localeStr);
    await expect(preview).toContainText(formatted);
    obsolete.release();
    await obsolete.done;
    await expect(preview).toContainText(formatted);
    await expect(preview).not.toContainText('Notification closure');
    await page.route(endpoint, (route) => route.abort('failed'), { times: 1 });
    await previous.click();
    await expect(dialog.locator('p-fieldset .text-red-600')).toBeVisible();
    await next.click();
    await expect(dialog.locator('p-fieldset .text-red-600')).toHaveCount(0);
    await expect(preview).toContainText('Current closure');
    const abandoned = await holdResponse(page, endpoint);
    try {
      await next.click();
      await abandoned.arrived;
      await closeDialog(dialog);
      abandoned.release();
      await abandoned.done;
      const reopened = await openDialog(page, table, row);
      await expect(reopened.locator('#name')).toHaveValue('SIX Swiss Exchange');
      await expect(reopened).not.toContainText('Current closure');
      await closeDialog(reopened);
    } finally {
      abandoned.release();
    }
  } finally {
    obsolete.release();
  }
});

test('tab selection follows browser history below the OnPush tab host', async ({ page }) => {
  await openView(page);
  const rules = page.getByRole('tab', { name: /Trading calendar rules|Handelskalender-Regeln/i });
  const exchanges = page.getByRole('tab', { name: /^(Stock exchange|Handelsplatz)$/i });
  await expect(rules).toHaveAttribute('aria-selected', 'true');
  await exchanges.click();
  await expect(exchanges).toHaveAttribute('aria-selected', 'true');
  await page.goBack();
  await page.waitForURL(/\/rulesets$/);
  await expect(rules).toHaveAttribute('aria-selected', 'true');
  await page.goForward();
  await expect(exchanges).toHaveAttribute('aria-selected', 'true');
});
