import { expect, Locator, Page, Route, test } from '@playwright/test';
import { loginAsFixtureUser } from './helpers';
import {
  addEditableTableRow,
  loadGenericConnectors,
  openContextMenu,
  openGenericConnectorView
} from './generic-connector.helpers';

// Shared fixtures are read-only. Persistence scenarios own and clean up their dedicated connector definitions.
test.use({ viewport: { width: 1400, height: 1800 } });
test.beforeEach(({ page }) => page.setDefaultTimeout(15_000));

async function openDefinition(page: Page): Promise<Locator> {
  const menu = await openContextMenu(page);
  await menu.getByRole('menuitem', { name: /^(Create|Erstellen).*Connector/i }).click();
  const dialog = page.locator('generic-connector-def-edit .p-dialog');
  await expect(dialog.locator('#shortId')).toBeFocused();
  // The first blur shows the required-field error and changes the centered dialog's position. Settle that layout
  // before clicking a header control, otherwise the button can move between pointer-down and pointer-up.
  await dialog.locator('#shortId').blur();
  return dialog;
}

async function closeDialog(dialog: Locator): Promise<void> {
  await dialog.locator('.p-dialog-close-button').click();
  await expect(dialog).toHaveCount(0);
}

/** Holds one real HTTP response so assertions observe the notification caused by its eventual arrival. */
async function holdResponse(page: Page, url: string) {
  let release!: () => void;
  let arrived!: () => void;
  let done!: () => void;
  const gate = new Promise<void>((resolve) => (release = resolve));
  const requestArrived = new Promise<void>((resolve) => (arrived = resolve));
  const completed = new Promise<void>((resolve) => (done = resolve));
  let holding = false;
  const handler = async (route: Route): Promise<void> => {
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
  return { release, arrived: requestArrived, done: completed };
}

async function setYaml(page: Page, dialog: Locator, value: string): Promise<void> {
  await expect(dialog.locator('.monaco-editor')).toHaveCount(1);
  await page.evaluate((text) => {
    const models = (window as any).monaco.editor.getModels();
    models[models.length - 1].setValue(text);
  }, value);
}

test('connector drafts survive delayed options, closing and reopening', async ({ page }) => {
  await loginAsFixtureUser(page, 'alledit');
  await openGenericConnectorView(page);
  const abandoned = await holdResponse(page, '**/api/stockexchange/basedata');
  try {
    const dialog = await openDefinition(page);
    await abandoned.arrived;
    await closeDialog(dialog);
    abandoned.release();
    await abandoned.done;
  } finally {
    abandoned.release();
  }
  for (let opening = 0; opening < 2; opening++) {
    const pending = await holdResponse(page, '**/api/stockexchange/basedata');
    try {
      const dialog = await openDefinition(page);
      await pending.arrived;
      await dialog.locator('#shortId').fill('unsaved_notification');
      await dialog.locator('#readableName').fill('Unsaved connector draft');
      await dialog.locator('#en').fill('English description');
      await dialog.locator('#de').fill('Deutsche Beschreibung');
      await dialog.locator('#domainUrl').fill('https://example.com');
      await expect(dialog.locator('#geoInclusions')).toBeHidden();
      pending.release();
      await pending.done;
      await expect(dialog.locator('#geoInclusions')).toBeVisible();
      await expect(dialog.locator('#readableName')).toHaveValue('Unsaved connector draft');
      await expect(dialog.locator('#shortId')).toHaveValue('unsaved_notification');
      const rate = dialog.locator('select#rateLimitType');
      await expect(rate.locator('option[value="TOKEN_BUCKET"]')).toHaveCount(1);
      await rate.selectOption('TOKEN_BUCKET');
      await expect(dialog.locator('#rateLimitRequests input')).toBeEnabled();
      await expect(dialog.locator('#rateLimitConcurrent input')).toBeDisabled();
      await rate.selectOption('SEMAPHORE');
      await expect(dialog.locator('#rateLimitRequests input')).toBeDisabled();
      await expect(dialog.locator('#rateLimitConcurrent input')).toBeEnabled();
      await rate.selectOption('NONE');
      await expect(dialog.locator('#rateLimitConcurrent input')).toBeDisabled();
      await closeDialog(dialog);
    } finally {
      pending.release();
    }
  }
});

test('YAML diagnostics arrive asynchronously and an obsolete validation cannot replace the current text', async ({
  page
}) => {
  await loginAsFixtureUser(page, 'alledit');
  await openGenericConnectorView(page);
  const schema = await holdResponse(page, '**/assets/schemas/token-config-schema.json');
  try {
    const dialog = await openDefinition(page);
    await schema.arrived;
    await dialog.locator('.p-fieldset-legend button').click();
    const editor = dialog.locator('yaml-editor');
    await setYaml(page, dialog, 'seed: [');
    await expect(editor.getByRole('alert')).not.toHaveCount(0);
    // Wait for the debounced Monaco markers as well: js-yaml's EOF position can be beyond the last model line.
    await expect
      .poll(() => page.evaluate(() => (window as any).monaco.editor.getModelMarkers({}).length))
      .toBeGreaterThan(0);
    schema.release();
    await schema.done;
    await expect(editor.getByRole('alert')).not.toHaveCount(0);
    await setYaml(page, dialog, 'seed: {}');
    await expect(editor.getByRole('alert')).toHaveCount(0);
    const validation = await holdResponse(page, '**/api/yaml/validate');
    try {
      await editor.getByRole('button').click();
      await validation.arrived;
      await expect(editor.getByRole('button')).toBeDisabled();
      validation.release();
      await validation.done;
      await expect(editor.getByRole('alert')).not.toHaveCount(0);
      await expect(editor.getByRole('button')).toBeEnabled();
    } finally {
      validation.release();
    }
    const obsolete = await holdResponse(page, '**/api/yaml/validate');
    try {
      await editor.getByRole('button').click();
      await obsolete.arrived;
      await setYaml(page, dialog, '');
      obsolete.release();
      await obsolete.done;
      await expect(editor.getByRole('alert')).toHaveCount(0);
      await expect(editor.getByRole('button')).toBeEnabled();
    } finally {
      obsolete.release();
    }
    await editor.getByRole('textbox', { name: 'Editor content', exact: true }).focus();
    await page.keyboard.press('Control+Space');
    await expect(editor.locator('.suggest-widget').getByText('ttlSeconds', { exact: true })).toBeVisible();
    await page.keyboard.press('Escape');
    await setYaml(
      page,
      dialog,
      'seed:\n  url: https://example.com\n  regex: (.*)\nlogin:\n  url: https://example.com/login\n  body: "{}"\n  jwtPath: access_token'
    );
    await editor.getByRole('button').click();
    await expect(editor.locator('[aria-live]')).toContainText(/Configuration is valid|Konfiguration ist gültig/);
    await expect(editor.getByRole('alert')).toHaveCount(0);
    await closeDialog(dialog);
  } finally {
    schema.release();
  }
});

test('endpoint format dependencies and original values survive repeated opening', async ({ page }) => {
  await loginAsFixtureUser(page, 'admin');
  await openGenericConnectorView(page);
  const headers = { 'x-auth-token': (await page.evaluate(() => sessionStorage.getItem('jwt')))! };
  const response = await page.request.get('/api/genericconnector', { headers });
  expect(response.ok()).toBeTruthy();
  const connector = (await response.json()).find((c: any) => c.endpoints?.length > 0);
  expect(connector, '095 has created at least one connector with an endpoint').toBeTruthy();
  await page.locator('select#idGenericConnector').selectOption(String(connector.idGenericConnector));
  const endpoint = connector.endpoints[0];
  for (let opening = 0; opening < 2; opening++) {
    // Clicking the header selects the endpoint and toggles expansion, which is irrelevant to its edit dialog.
    const panel = page.locator('p-accordion-panel').first().locator('..');
    if (!(await panel.getAttribute('class'))?.includes('active-border')) {
      await panel.locator('p-accordion-header').click();
    }
    const menu = await openContextMenu(page);
    await menu.getByRole('menuitem', { name: /^(Edit|Bearbeiten).*?(Endpoint|Endpunkt)/i }).click();
    const dialog = page.locator('generic-connector-endpoint-edit .p-dialog');
    await expect(dialog.locator('#urlTemplate')).toHaveValue(endpoint.urlTemplate);
    await expect(dialog.locator('#feedSupportInstrumentType')).toBeDisabled();
    await expect(dialog.locator('#responseFormat')).toHaveValue(endpoint.responseFormat);
    await dialog.locator('#responseFormat').selectOption('CSV');
    await expect(dialog.locator('#csvDelimiter')).toBeEnabled();
    await expect(dialog.locator('#jsonDataStructure')).toBeDisabled();
    await dialog.locator('#responseFormat').selectOption('HTML');
    await dialog.locator('#htmlExtractMode').selectOption('REGEX_GROUPS');
    await expect(dialog.locator('#htmlExtractRegex')).toBeEnabled();
    await expect(dialog.locator('#htmlSplitDelimiter')).toBeDisabled();
    await dialog.locator('#htmlExtractMode').selectOption('SPLIT_POSITIONS');
    await expect(dialog.locator('#htmlExtractRegex')).toBeDisabled();
    await expect(dialog.locator('#htmlSplitDelimiter')).toBeEnabled();
    await dialog.locator('#responseFormat').selectOption('JSON');
    await dialog.locator('#jsonDataStructure').selectOption('COLUMN_ROW_ARRAYS');
    await expect(dialog.locator('#jsonColumnNamesPath')).toHaveClass(/required-input/);
    await expect(dialog.locator('#csvDelimiter')).toBeDisabled();
    await dialog.locator('#urlTemplate').fill('https://example.com/unsaved');
    await closeDialog(dialog);
  }
});

async function ownConnectors(page: Page) {
  const headers = { 'x-auth-token': (await page.evaluate(() => sessionStorage.getItem('jwt')))! };
  const shortIds = ['e2e_notify_a', 'e2e_notify_b'];
  const cleanup = async () => {
    const response = await page.request.get('/api/genericconnector', { headers });
    expect(response.ok()).toBeTruthy();
    for (const connector of await response.json()) {
      if (shortIds.includes(connector.shortId)) {
        const deleted = await page.request.delete(`/api/genericconnector/${connector.idGenericConnector}`, { headers });
        expect(deleted.ok(), await deleted.text()).toBeTruthy();
      }
    }
  };
  await cleanup();
  const { e2e, ...fixture } = loadGenericConnectors().find((connector) => connector.endpoints.length > 0)!;
  const connectors: any[] = [];
  for (const shortId of shortIds) {
    const response = await page.request.post('/api/genericconnector', {
      headers,
      data: {
        ...fixture,
        shortId,
        readableName: `E2E notification ${shortId}`,
        httpHeaders: [],
        descriptionNLS: { map: { en: 'Notification fixture', de: 'Benachrichtigungstest' } },
        endpoints: fixture.endpoints.slice(0, 1)
      }
    });
    expect(response.ok(), await response.text()).toBeTruthy();
    connectors.push(await response.json());
  }
  return { connectors, headers, cleanup };
}

async function selectConnectorId(page: Page, id: number): Promise<void> {
  await page.locator('select#idGenericConnector').selectOption(String(id));
  await expect(page.locator('generic-connector-def-detail')).toBeVisible();
}

async function selectFirstEndpoint(page: Page): Promise<void> {
  const panel = page.locator('p-accordion-panel').first().locator('..');
  if (!(await panel.getAttribute('class'))?.includes('active-border')) {
    await panel.locator('p-accordion-header').click();
  }
}

test('master view publishes delayed data, retains a newer selection and refreshes nested edits', async ({ page }) => {
  await loginAsFixtureUser(page, 'alledit');
  const own = await ownConnectors(page);
  const [first, second] = own.connectors;
  const pending = await holdResponse(page, '**/api/genericconnector');
  try {
    await openGenericConnectorView(page);
    await pending.arrived;
    await expect(page.locator(`select#idGenericConnector option[value="${first.idGenericConnector}"]`)).toHaveCount(0);
    pending.release();
    await pending.done;
    await expect(page.locator(`select#idGenericConnector option[value="${first.idGenericConnector}"]`)).toHaveCount(1);
    await selectConnectorId(page, first.idGenericConnector);
    await expect(page.locator('generic-connector-def-detail')).toContainText(first.shortId);
    await expect(page.locator('generic-connector-endpoint-panel')).toHaveCount(1);
    const saved = await holdResponse(page, '**/api/genericconnector');
    try {
      await addEditableTableRow(page, page.locator('generic-connector-http-header-table'), async (row) => {
        await row.locator('input').nth(0).fill('X-Notification');
        await row.locator('input').nth(1).fill('signal-value');
      });
      await saved.arrived;
      await selectConnectorId(page, second.idGenericConnector);
      await expect(page.locator('generic-connector-def-detail')).toContainText(second.shortId);
      const refreshed = page.waitForResponse(
        (response) =>
          new URL(response.url()).pathname === '/api/genericconnector' && response.request().method() === 'GET'
      );
      saved.release();
      await saved.done;
      expect((await refreshed).ok()).toBeTruthy();
      await expect(page.locator('select#idGenericConnector')).toHaveValue(String(second.idGenericConnector));
      await expect(page.locator('generic-connector-def-detail')).toContainText(second.shortId);
    } finally {
      saved.release();
    }
    await selectConnectorId(page, first.idGenericConnector);
    await expect(page.locator('generic-connector-http-header-table')).toContainText('signal-value');
    // Repeated edits refresh the endpoint input reference without losing the selected endpoint's main menu.
    for (let edit = 0; edit < 2; edit++) {
      await selectFirstEndpoint(page);
      const menu = await openContextMenu(page);
      await menu.getByRole('menuitem', { name: /^(Edit|Bearbeiten).*?(Endpoint|Endpunkt)/i }).click();
      const dialog = page.locator('generic-connector-endpoint-edit .p-dialog');
      await expect(dialog.locator('#urlTemplate')).toHaveValue(
        edit === 0
          ? first.endpoints[0].urlTemplate
          : `${first.endpoints[0].urlTemplate}${first.endpoints[0].urlTemplate.includes('?') ? '&' : '?'}notification=0`
      );
      const url = `${first.endpoints[0].urlTemplate}${first.endpoints[0].urlTemplate.includes('?') ? '&' : '?'}notification=${edit}`;
      await dialog.locator('#urlTemplate').fill(url);
      await dialog.locator('#urlTemplate').blur();
      await expect(dialog.locator('#urlTemplate')).toHaveValue(url);
      const refreshed = page.waitForResponse(
        (response) =>
          new URL(response.url()).pathname === '/api/genericconnector' && response.request().method() === 'GET'
      );
      await dialog.locator('button[type="submit"]').click();
      expect((await refreshed).ok()).toBeTruthy();
      await expect(dialog).toHaveCount(0);
      await expect(page.locator('generic-connector-endpoint-panel')).toContainText(url);
      await page
        .locator('p-menubar')
        .getByRole('menuitem', { name: /^(Edit|Bearbeiten)$/ })
        .click();
      await expect(
        page.locator('p-menubar').getByRole('menuitem', { name: /^(Edit|Bearbeiten).*?(Endpoint|Endpunkt)/i })
      ).not.toHaveAttribute('aria-disabled', 'true');
      await page.keyboard.press('Escape');
    }
    const headerRow = page
      .locator('generic-connector-http-header-table tbody tr')
      .filter({ hasText: 'X-Notification' });
    const deleted = page.waitForResponse(
      (response) =>
        new URL(response.url()).pathname === '/api/genericconnector' && response.request().method() === 'GET'
    );
    await headerRow.locator('button:has(i.pi-trash)').click();
    expect((await deleted).ok()).toBeTruthy();
    await expect(headerRow).toHaveCount(0);
  } finally {
    pending.release();
    await own.cleanup();
  }
});

/** A deterministic connector response isolates UI notification from external price-provider availability. */
async function endpointAnswer(
  page: Page,
  marker: string,
  parsedRows: Record<string, string>[] = [{ close: '123.45' }]
) {
  let release!: () => void;
  let arrived!: () => void;
  let done!: () => void;
  const gate = new Promise<void>((resolve) => (release = resolve));
  const request = new Promise<void>((resolve) => (arrived = resolve));
  const completed = new Promise<void>((resolve) => (done = resolve));
  const handler = async (route: Route) => {
    arrived();
    await gate;
    try {
      await route.fulfill({
        json: {
          success: parsedRows.length > 0,
          httpStatus: 200,
          requestUrl: `https://example.invalid/${marker}`,
          executionTimeMs: 42,
          errorMessage: parsedRows.length ? null : marker,
          rawResponseSnippet: marker,
          parsedRows
        }
      });
    } catch (error) {
      if (route.request().failure()?.errorText !== 'net::ERR_ABORTED') throw error;
    } finally {
      await page.unroute('**/api/genericconnector/test', handler);
      done();
    }
  };
  await page.route('**/api/genericconnector/test', handler);
  return { release, arrived: request, done: completed };
}

test('endpoint test results render after HTTP, reset their columns and ignore a closed dialog response', async ({
  page
}) => {
  await loginAsFixtureUser(page, 'admin');
  await openGenericConnectorView(page);
  const headers = { 'x-auth-token': (await page.evaluate(() => sessionStorage.getItem('jwt')))! };
  const response = await page.request.get('/api/genericconnector', { headers });
  const connector = (await response.json()).find((item: any) => item.endpoints?.length > 0);
  await selectConnectorId(page, connector.idGenericConnector);
  const openTest = async () => {
    await selectFirstEndpoint(page);
    const menu = await openContextMenu(page);
    await menu.getByRole('menuitem', { name: /Test endpoint|Endpunkt testen/i }).click();
    const dialog = page.locator('generic-connector-test-dialog .p-dialog');
    await expect(dialog.locator('button[type="submit"]')).toBeEnabled();
    return dialog;
  };
  let dialog = await openTest();
  for (const [marker, rows] of [
    ['first-result', [{ close: '123.45' }]],
    ['second-result', [{ date: '2026-10-20', volume: '700' }]],
    ['empty-result', []]
  ] as [string, Record<string, string>[]][]) {
    const answer = await endpointAnswer(page, marker, rows);
    try {
      await dialog.locator('button[type="submit"]').click();
      await answer.arrived;
      await expect(dialog.locator('button[type="submit"]')).toBeDisabled();
      answer.release();
      await answer.done;
      await expect(dialog.locator('.mb-1').filter({ hasText: `https://example.invalid/${marker}` })).toBeVisible();
      await expect(dialog.locator('button[type="submit"]')).toBeEnabled();
      await expect(dialog.locator('p-table th')).toHaveCount(rows.length ? Object.keys(rows[0]).length : 0);
      for (const value of Object.values(rows[0] ?? {})) {
        await expect(dialog.getByRole('cell', { name: value, exact: true })).toBeVisible();
      }
    } finally {
      answer.release();
    }
  }
  await page.route(
    '**/api/genericconnector/test',
    (route) =>
      route.fulfill({
        status: 503,
        contentType: 'text/plain',
        body: 'Endpoint temporarily unavailable'
      }),
    { times: 1 }
  );
  await dialog.locator('button[type="submit"]').click();
  await expect(page.getByText('Endpoint temporarily unavailable', { exact: true })).toBeVisible();
  await expect(dialog.locator('button[type="submit"]')).toBeEnabled();
  const obsolete = await endpointAnswer(page, 'obsolete-result');
  try {
    await dialog.locator('button[type="submit"]').click();
    await obsolete.arrived;
    await closeDialog(dialog);
    dialog = await openTest();
    const current = await endpointAnswer(page, 'current-result');
    try {
      await dialog.locator('button[type="submit"]').click();
      await current.arrived;
      current.release();
      await current.done;
      await expect(dialog.locator('.mb-1').filter({ hasText: 'https://example.invalid/current-result' })).toBeVisible();
      obsolete.release();
      await obsolete.done;
      await expect(dialog.locator('.mb-1').filter({ hasText: 'https://example.invalid/obsolete-result' })).toHaveCount(
        0
      );
      await expect(dialog.locator('.mb-1').filter({ hasText: 'https://example.invalid/current-result' })).toBeVisible();
      await closeDialog(dialog);
    } finally {
      current.release();
    }
  } finally {
    obsolete.release();
  }
});
