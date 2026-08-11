import {test, expect, Page} from '@playwright/test';
import {loginAsFixtureUser} from './helpers';
import {
  addEditableTableRow,
  fillNumber,
  fillText,
  GenericConnectorDefData,
  GenericConnectorEndpointData,
  loadGenericConnectors,
  openContextMenu,
  openGenericConnectorView,
  pickGeoInclusions,
  pickMultiSelect,
  selectByValue,
  setCheckbox,
} from './generic-connector.helpers';

/**
 * Creates the generic connectors exported from the developer database
 * (testdata/generated/generic-connectors.json, produced by backend/nv.bat via
 * scripts/export-generic-connectors.mjs) through the UI as user 'alledit', then activates each of
 * them as the admin user (hg@hugograf.com).
 *
 * Note on V2 seed data: V2__testdata.sql contains CH/US securities referencing
 * gt.datafeed.bxswiss/otcx/fuw although generic_connector_def is NOT seeded. Unregistered connector
 * IDs are tolerated by the backend (ConnectorHelper.getConnectorByConnectorId returns null and all
 * callers skip), and this spec recreates + activates the connectors under the same shortIds, making
 * those references live again. The defs must NOT be seeded in V2 — that would defeat this test.
 */

const CREATOR = 'alledit';
const ADMIN = 'admin'; // hg@hugograf.com in users.json

// No toast assertions: ngx-toastr runs with preventDuplicates=true and a 10s timeOut, and every
// save in this view emits the identical "... was saved" toast — consecutive saves suppress each
// other. saveAndWait's network-level response wait is the authoritative save signal instead.
const CREATE_CONNECTOR_RX = /(Create|Erstellen).*(Generic Connector|Generischer Connector|GENERIC_CONNECTOR_DEF)/i;
const CREATE_ENDPOINT_RX = /(Create|Erstellen)\s*(Endpoint|Endpunkt|ENDPOINT)/i;
const ACTIVATE_RX = /^\s*(Activate|Aktivieren|ACTIVATE)(\s*\.\.\.)?\s*$/i;
const DEACTIVATE_RX = /^\s*(Deactivate|Deaktivieren|DEACTIVATE)(\s*\.\.\.)?\s*$/i;

/** Waits for the next successful /genericconnector save round-trip triggered by `click`. */
async function saveAndWait(page: Page, method: 'POST' | 'PUT', click: () => Promise<void>): Promise<void> {
  const response = page.waitForResponse(r =>
    r.url().includes('/genericconnector') && r.request().method() === method && r.ok(), {timeout: 20_000});
  await click();
  await response;
  // saveConnector()/handleCloseEditDialog re-read all connectors and re-render the accordion.
  await page.waitForTimeout(800);
}

/**
 * Selects the connector in the master dropdown by its readable name and waits for the detail.
 * Always deselects first (empty option): the component re-initializes the expanded accordion
 * panels only when the selected connector ID CHANGES (setChildData/initExpandedPanels), and after
 * every save it re-reads data while keeping the same ID — without the deselect round-trip the
 * endpoint panels holding the field-mapping tables would stay collapsed.
 */
async function selectConnector(page: Page, readableName: string): Promise<void> {
  const select = page.locator('select#idGenericConnector');
  const option = select.locator('option', {hasText: readableName}).first();
  await option.waitFor({state: 'attached', timeout: 10_000});
  await select.selectOption({index: 0});
  await select.dispatchEvent('change');
  await page.waitForTimeout(300);
  await select.selectOption({value: await option.getAttribute('value')});
  await select.dispatchEvent('change');
  await page.locator('generic-connector-def-detail').waitFor({state: 'visible', timeout: 10_000});
  await page.waitForTimeout(400);
}

/** Fills and submits the connector definition dialog for a new connector. */
async function createConnectorDef(page: Page, c: GenericConnectorDefData): Promise<void> {
  const menu = await openContextMenu(page);
  await menu.getByText(CREATE_CONNECTOR_RX).first().click();

  const dialog = page.locator('.p-dialog');
  await dialog.waitFor({state: 'visible', timeout: 10_000});

  // initialize() resets the form after loading stock-exchange base data, then focuses shortId.
  // Wait for that final initialization step before entering values so a slow response cannot
  // clear required fields while the full suite is under load.
  await expect(dialog.locator('input#shortId')).toBeFocused({timeout: 15_000});

  await fillText(dialog, '#readableName', c.readableName);
  // The DE/EN descriptions are required; fxubc_gen has none in the source DB, so use a marked
  // placeholder that keeps the data intent recognizable.
  await fillText(dialog, '#en', c.descriptionNLS?.map?.en ?? `${c.readableName} (e2e placeholder)`);
  await fillText(dialog, '#de', c.descriptionNLS?.map?.de ?? `${c.readableName} (E2E-Platzhalter)`);
  await fillText(dialog, '#domainUrl', c.domainUrl);

  await setCheckbox(dialog, 'needsApiKey', c.needsApiKey);

  // rateLimitType enables/disables the dependent rate fields — set it before them.
  await selectByValue(dialog, 'rateLimitType', c.rateLimitType);
  if (c.rateLimitType === 'TOKEN_BUCKET') {
    await fillNumber(dialog, 'rateLimitRequests', c.rateLimitRequests!);
    await fillNumber(dialog, 'rateLimitPeriodSec', c.rateLimitPeriodSec!);
  } else if (c.rateLimitType === 'SEMAPHORE') {
    await fillNumber(dialog, 'rateLimitConcurrent', c.rateLimitConcurrent!);
  }

  await fillNumber(dialog, 'intradayDelaySeconds', c.intradayDelaySeconds);
  if (c.regexUrlPattern) {
    await fillText(dialog, '#regexUrlPattern', c.regexUrlPattern);
  }

  await setCheckbox(dialog, 'supportsSecurity', c.supportsSecurity);
  await setCheckbox(dialog, 'supportsCurrency', c.supportsCurrency);
  await setCheckbox(dialog, 'needHistoryGapFiller', c.needHistoryGapFiller);
  await setCheckbox(dialog, 'gbxDividerEnabled', c.gbxDividerEnabled);

  if (c.supportedCategories) {
    await pickMultiSelect(page, dialog, 'supportedCategories', c.supportedCategories.split(','));
  }
  if (c.geoRestrictions) {
    // Only plain inclusion tokens exist in the exported data (no GEO:-CATEGORY exclusions).
    await pickGeoInclusions(page, dialog, c.geoRestrictions.trim().split(/\s+/));
  }

  if (c.tokenConfigYaml) {
    // Set the Monaco editor content. The editor publishes changes through onDidChangeModelContent
    // (which feeds the [(value)] binding read by getNewOrExistingInstanceBeforeSave) even while
    // the surrounding token-config fieldset is collapsed — the editor instance and its model are
    // created regardless of visibility, so wait only for DOM attachment, not visibility.
    await dialog.locator('.p-fieldset-legend, .p-fieldset legend').first().click().catch(() => {});
    await dialog.locator('.monaco-editor').first().waitFor({state: 'attached', timeout: 30_000});
    await page.evaluate(yaml => {
      const monaco = (window as any).monaco;
      const models = monaco.editor.getModels();
      models[models.length - 1].setValue(yaml);
    }, c.tokenConfigYaml);
    await page.waitForTimeout(300);
  }

  await fillText(dialog, 'input#shortId', c.shortId);
  await expect(dialog.locator('button[type="submit"]')).toBeEnabled();

  await saveAndWait(page, 'POST', async () => {
    await dialog.locator('button[type="submit"]').click();
    await dialog.waitFor({state: 'hidden', timeout: 15_000});
  });
}

/** Creates one endpoint via the endpoint dialog; the parent PUTs the whole connector on close. */
async function createEndpoint(page: Page, ep: GenericConnectorEndpointData): Promise<void> {
  const menu = await openContextMenu(page);
  await menu.getByText(CREATE_ENDPOINT_RX).first().click();

  const dialog = page.locator('.p-dialog');
  await dialog.waitFor({state: 'visible', timeout: 10_000});

  await selectByValue(dialog, 'feedSupportInstrumentType', `${ep.feedSupport}|${ep.instrumentType}`);
  await fillText(dialog, '#urlTemplate', ep.urlTemplate);
  await selectByValue(dialog, 'httpMethod', ep.httpMethod);

  // responseFormat enables/disables the JSON/CSV/HTML field groups — set it before them.
  await selectByValue(dialog, 'responseFormat', ep.responseFormat);
  await selectByValue(dialog, 'numberFormat', ep.numberFormat);
  await selectByValue(dialog, 'dateFormatType', ep.dateFormatType);
  if (ep.dateFormatType === 'PATTERN') {
    await fillText(dialog, 'input#dateFormatPattern', ep.dateFormatPattern!);
  }

  if (ep.responseFormat === 'JSON') {
    if (ep.jsonDataStructure) {
      await selectByValue(dialog, 'jsonDataStructure', ep.jsonDataStructure);
    }
    if (ep.jsonDataPath) {
      await fillText(dialog, '#jsonDataPath', ep.jsonDataPath);
    }
    if (ep.jsonColumnNamesPath) {
      await fillText(dialog, '#jsonColumnNamesPath', ep.jsonColumnNamesPath);
    }
    if (ep.jsonStatusPath) {
      await fillText(dialog, 'input#jsonStatusPath', ep.jsonStatusPath);
    }
    if (ep.jsonStatusOkValue) {
      await fillText(dialog, 'input#jsonStatusOkValue', ep.jsonStatusOkValue);
    }
  } else if (ep.responseFormat === 'CSV') {
    if (ep.csvDelimiter) {
      await fillText(dialog, 'input#csvDelimiter', ep.csvDelimiter);
    }
    if (ep.csvSkipHeaderLines != null) {
      await fillNumber(dialog, 'csvSkipHeaderLines', ep.csvSkipHeaderLines);
    }
  } else if (ep.responseFormat === 'HTML') {
    if (ep.htmlCssSelector) {
      await fillText(dialog, '#htmlCssSelector', ep.htmlCssSelector);
    }
    if (ep.htmlExtractMode) {
      await selectByValue(dialog, 'htmlExtractMode', ep.htmlExtractMode);
    }
    if (ep.htmlTextCleanup) {
      await fillText(dialog, '#htmlTextCleanup', ep.htmlTextCleanup);
    }
    if (ep.htmlExtractRegex) {
      await fillText(dialog, '#htmlExtractRegex', ep.htmlExtractRegex);
    }
    if (ep.htmlSplitDelimiter) {
      await fillText(dialog, 'input#htmlSplitDelimiter', ep.htmlSplitDelimiter);
    }
  }

  await selectByValue(dialog, 'tickerBuildStrategy', ep.tickerBuildStrategy);
  if (ep.tickerBuildStrategy === 'CURRENCY_PAIR') {
    if (ep.currencyPairSeparator) {
      await fillText(dialog, 'input#currencyPairSeparator', ep.currencyPairSeparator);
    }
    if (ep.currencyPairSuffix) {
      await fillText(dialog, 'input#currencyPairSuffix', ep.currencyPairSuffix);
    }
  }

  await setCheckbox(dialog, 'tickerUppercase', ep.tickerUppercase);
  if (ep.maxDataPoints != null) {
    await fillNumber(dialog, 'maxDataPoints', ep.maxDataPoints);
  }
  await setCheckbox(dialog, 'paginationEnabled', ep.paginationEnabled);
  if (ep.endpointOptions.length > 0) {
    await pickMultiSelect(page, dialog, 'endpointOptions', ep.endpointOptions);
  }

  await saveAndWait(page, 'PUT', async () => {
    await dialog.locator('button[type="submit"]').click();
    await dialog.waitFor({state: 'hidden', timeout: 15_000});
  });
}

test.describe.serial('generic connectors — create as alledit, activate as admin', () => {
  // The def/endpoint dialogs hold 20+ stacked form rows and do not scroll internally — with the
  // default 720px-high viewport the submit button lands outside the viewport and is unclickable.
  test.use({viewport: {width: 1400, height: 1800}});

  const connectors = loadGenericConnectors();

  for (const c of connectors) {
    test(`creates generic connector '${c.shortId}' (${c.readableName})`, async ({page}) => {
      test.setTimeout(240_000); // up to 4 endpoints × up to 7 field mappings, one PUT per row
      await loginAsFixtureUser(page, CREATOR);
      await openGenericConnectorView(page);

      // Idempotency: skip when the connector already exists (dropdown option label = readableName).
      const existing = page.locator('select#idGenericConnector option', {hasText: c.readableName});
      if (await existing.count() > 0) {
        return;
      }

      await createConnectorDef(page, c);
      await selectConnector(page, c.readableName);

      // Endpoints — the accordion orders panels by (feedSupport, instrumentType), which matches the
      // JSON export order, so endpoint i corresponds to panel i.
      for (const ep of c.endpoints) {
        await createEndpoint(page, ep);
        await selectConnector(page, c.readableName);
      }

      // Field mappings — every row save PUTs the whole connector and re-renders the accordion, so
      // the panel/table locators are re-resolved on each iteration.
      for (let i = 0; i < c.endpoints.length; i++) {
        for (const m of c.endpoints[i].fieldMappings) {
          const table = page.locator('generic-connector-endpoint-panel').nth(i)
            .locator('generic-connector-field-mapping-table');
          await table.waitFor({state: 'visible', timeout: 10_000});
          await saveAndWait(page, 'PUT', () => addEditableTableRow(page, table, async row => {
            await row.locator('select').first().selectOption({value: m.targetField});
            const textInputs = row.locator('input[type="text"]');
            await textInputs.nth(0).fill(m.sourceExpression);
            if (m.csvColumnIndex != null) {
              await row.locator('input[type="number"]').first().fill(String(m.csvColumnIndex));
            }
            if (m.dividerExpression) {
              await textInputs.nth(1).fill(m.dividerExpression);
            }
            if (m.required) {
              await row.locator('p-checkbox').first().click();
            }
          }));
        }
      }

      // HTTP headers (currently only gettex) — same editable-table pattern on the header table.
      for (const h of c.httpHeaders) {
        const table = page.locator('generic-connector-http-header-table');
        await table.waitFor({state: 'visible', timeout: 10_000});
        await saveAndWait(page, 'PUT', () => addEditableTableRow(page, table, async row => {
          const textInputs = row.locator('input[type="text"]');
          await textInputs.nth(0).fill(h.headerName);
          await textInputs.nth(1).fill(h.headerValue);
        }));
      }

      // Final assertions: connector selected, detail shows core fields, one accordion panel per endpoint.
      await selectConnector(page, c.readableName);
      const detail = page.locator('generic-connector-def-detail');
      await expect(detail.getByText(c.shortId, {exact: false}).first()).toBeVisible({timeout: 10_000});
      await expect(page.locator('generic-connector-endpoint-panel')).toHaveCount(c.endpoints.length);
      for (let i = 0; i < c.endpoints.length; i++) {
        await expect(page.locator('generic-connector-endpoint-panel').nth(i)
          .locator('generic-connector-field-mapping-table tbody tr'))
          .toHaveCount(c.endpoints[i].fieldMappings.length, {timeout: 10_000});
      }
    });
  }

  test('admin activates all generic connectors', async ({page}) => {
    test.skip(connectors.length === 0, 'no generic connector testdata generated');
    test.setTimeout(180_000);
    await loginAsFixtureUser(page, ADMIN);
    await openGenericConnectorView(page);

    for (const c of connectors) {
      await selectConnector(page, c.readableName);
      let menu = await openContextMenu(page);

      // Idempotency: an already-activated connector offers Deactivate instead of Activate.
      if (await menu.getByText(DEACTIVATE_RX).count() > 0) {
        await page.keyboard.press('Escape');
        await page.waitForTimeout(300);
        continue;
      }

      const activateResponse = page.waitForResponse(r =>
        r.url().includes('/genericconnector/activate/') && r.ok(), {timeout: 20_000});
      await menu.getByText(ACTIVATE_RX).first().click();
      await activateResponse;
      await page.waitForTimeout(800);

      // Re-open the menu: Deactivate proves activated=true round-tripped (and the connector
      // factory was reloaded by the backend activate endpoint).
      await selectConnector(page, c.readableName);
      menu = await openContextMenu(page);
      await expect(menu.getByText(DEACTIVATE_RX).first()).toBeVisible({timeout: 5_000});
      await page.keyboard.press('Escape');
      await page.waitForTimeout(300);
    }
  });

  test('activated connectors are offered in the security dialog connector selects', async ({page}) => {
    // Restrict to connectors the dialog offers regardless of context: geo-restricted connectors
    // (gettex/bxswiss/otcx) may be filtered for a Spanish security, so assert only unrestricted ones.
    const unrestricted = connectors.filter(c => !c.geoRestrictions && c.supportsSecurity);
    test.skip(unrestricted.length === 0, 'no unrestricted security connectors in testdata');

    await loginAsFixtureUser(page, CREATOR);
    // Let the navigation tree finish rendering before probing for the watchlist node.
    await page.waitForTimeout(1500);
    const watchlistNode = page.getByRole('treeitem', {name: 'Spain', exact: true}).first();
    test.skip(await watchlistNode.count() === 0, 'Spain watchlist not present (spec 040-create-watchlist not run)');
    await watchlistNode.click();
    await page.waitForTimeout(1500);

    const contentArea = page.locator('.data-container').first();
    await contentArea.click();
    await page.waitForTimeout(300);
    await contentArea.click({button: 'right'});
    const menu = page.locator('[role="menu"]:visible');
    await menu.waitFor({state: 'visible', timeout: 5_000});
    await menu.getByText(/^(Create\s*and\s*add\s*security|Hinzuf.*neues\s*Wertpapier)\b/i).first().click();

    const dialog = page.locator('.p-dialog');
    await dialog.waitFor({state: 'visible', timeout: 10_000});
    const histSelect = dialog.locator('select#idConnectorHistory');
    await expect(histSelect.locator('option')).not.toHaveCount(0, {timeout: 10_000});

    for (const c of unrestricted) {
      const connectorId = `gt.datafeed.${c.shortId}`;
      if (c.endpoints.some(ep => ep.feedSupport === 'FS_HISTORY' && ep.instrumentType === 'SECURITY')) {
        await expect(histSelect.locator(`option[value="${connectorId}"]`)).toHaveCount(1);
      }
      if (c.endpoints.some(ep => ep.feedSupport === 'FS_INTRA' && ep.instrumentType === 'SECURITY')) {
        await expect(dialog.locator(`select#idConnectorIntra option[value="${connectorId}"]`)).toHaveCount(1);
      }
    }
  });
});
