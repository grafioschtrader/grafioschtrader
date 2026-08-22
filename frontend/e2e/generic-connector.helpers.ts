import { expect, Locator, Page } from '@playwright/test';
import * as fs from 'fs';
import * as path from 'path';

/**
 * Shared helpers and typings for 095-create-generic-connector.spec.ts.
 *
 * The generic connector testdata is a nested JSON file produced from the developer database by
 * scripts/export-generic-connectors.mjs (invoked by backend/nv.bat). Property names match the
 * Jackson/REST serialization of the backend entities, so the same file could be POSTed to
 * /api/genericconnector or deserialized by a JUnit test with Jackson.
 *
 * The creating user 'alledit' and the activating user 'admin' run a German UI (locale de-CH in
 * users.json) while other e2e users run English — every text selector therefore matches both
 * languages plus the raw NLS key (same convention as manage-client.helpers.ts).
 */

export interface FieldMappingData {
  targetField: string;
  sourceExpression: string;
  csvColumnIndex: number | null;
  dividerExpression: string | null;
  required: boolean;
}

export interface GenericConnectorEndpointData {
  feedSupport: string;
  instrumentType: string;
  urlTemplate: string;
  httpMethod: string;
  responseFormat: string;
  numberFormat: string;
  dateFormatType: string;
  dateFormatPattern: string | null;
  jsonDataStructure: string | null;
  jsonDataPath: string | null;
  jsonColumnNamesPath: string | null;
  jsonStatusPath: string | null;
  jsonStatusOkValue: string | null;
  csvDelimiter: string | null;
  csvSkipHeaderLines: number | null;
  htmlCssSelector: string | null;
  htmlExtractMode: string | null;
  htmlTextCleanup: string | null;
  htmlExtractRegex: string | null;
  htmlSplitDelimiter: string | null;
  tickerBuildStrategy: string;
  currencyPairSeparator: string | null;
  currencyPairSuffix: string | null;
  tickerUppercase: boolean;
  maxDataPoints: number | null;
  paginationEnabled: boolean;
  endpointOptions: string[];
  fieldMappings: FieldMappingData[];
}

export interface HttpHeaderData {
  headerName: string;
  headerValue: string;
}

export interface GenericConnectorDefData {
  e2e: string;
  shortId: string;
  readableName: string;
  descriptionNLS: { map: { [lang: string]: string } } | null;
  domainUrl: string;
  needsApiKey: boolean;
  rateLimitType: string;
  rateLimitRequests: number | null;
  rateLimitPeriodSec: number | null;
  rateLimitConcurrent: number | null;
  intradayDelaySeconds: number;
  regexUrlPattern: string | null;
  supportsSecurity: boolean;
  supportsCurrency: boolean;
  needHistoryGapFiller: boolean;
  gbxDividerEnabled: boolean;
  supportedCategories: string | null;
  geoRestrictions: string | null;
  tokenConfigYaml: string | null;
  httpHeaders: HttpHeaderData[];
  endpoints: GenericConnectorEndpointData[];
}

const JSON_PATH = path.resolve(
  __dirname,
  '../../backend/grafioschtrader-server/src/test/resources/testdata/generic-connectors.json'
);

/** Loads the e2e-tagged connectors from the generated JSON; empty when the file was not generated. */
export function loadGenericConnectors(): GenericConnectorDefData[] {
  if (!fs.existsSync(JSON_PATH)) {
    return [];
  }
  return (JSON.parse(fs.readFileSync(JSON_PATH, 'utf-8')) as GenericConnectorDefData[]).filter((c) => c.e2e === 'e');
}

// ---------------------------------------------------------------------------
// i18n label lookup for Optimus UI multiselect items whose labels are TRANSLATED
// enum names (supportedCategories → AssetclassCategory, endpointOptions →
// EndpointOption). Select/option elements use raw enum names as values and
// need no lookup. Enum translations live in the BACKEND messages*.properties
// (served to the frontend at runtime, per the "Enum translations" NLS rule),
// so those files are parsed in addition to the frontend i18n JSONs.
// ---------------------------------------------------------------------------

const MESSAGE_FILES = [
  '../../backend/grafioschtrader-common/src/main/resources/message/messages.properties',
  '../../backend/grafioschtrader-common/src/main/resources/message/messages_de.properties',
  '../../backend/grafiosch-base/src/main/resources/i18n/messages.properties',
  '../../backend/grafiosch-base/src/main/resources/i18n/messages_de.properties'
].map((p) => path.resolve(__dirname, p));

const MAPPING_DESCRIPTORS = [
  '../../backend/grafiosch-base/src/main/resources/META-INF/grafiosch/nls-mapping.properties',
  '../../backend/grafioschtrader-common/src/main/resources/META-INF/grafiosch/nls-mapping.properties'
].map((p) => path.resolve(__dirname, p));

const mappingListCache: { [property: string]: string[] } = {};

/** Reads one comma-separated list from the module NLS descriptors, unioned like NlsMappingRegistry. */
function mappingList(property: string): string[] {
  if (!mappingListCache[property]) {
    const values = new Set<string>();
    for (const file of MAPPING_DESCRIPTORS) {
      if (fs.existsSync(file)) {
        const m = new RegExp(`^${property}\\s*=\\s*(.+)$`, 'm').exec(fs.readFileSync(file, 'utf-8'));
        m?.[1].split(',').forEach((v) => v.trim() && values.add(v.trim()));
      }
    }
    mappingListCache[property] = [...values];
  }
  return mappingListCache[property];
}

/**
 * Mirrors grafiosch.nls.NlsKeyMapper: the key stored in a properties file is NOT the key the client
 * resolves. Rule 4 (upper-case, dots to underscores) is what makes the lower-case field label
 * 'cryptocurrency' serve the frontend enum key CRYPTOCURRENCY — indexing the stored form alone
 * silently loses every key whose storage shape differs from its client shape.
 */
function toClientKey(rawKey: string): string {
  if (rawKey.startsWith('c.')) {
    return rawKey.slice(2);
  }
  if (mappingList('passthrough.prefixes').some((p) => rawKey.startsWith(p))) {
    return rawKey;
  }
  const firstDot = rawKey.indexOf('.');
  if (firstDot > 0 && mappingList('nested.namespaces').includes(rawKey.slice(0, firstDot))) {
    return rawKey;
  }
  return rawKey.toUpperCase().replace(/\./g, '_');
}

let i18nCache: { [key: string]: string[] } | null = null;

function loadI18n(): { [key: string]: string[] } {
  if (!i18nCache) {
    const cache: { [key: string]: string[] } = (i18nCache = {});
    // The frontend JSON translation files are gone; the backend properties files below are the only source of texts
    // (issue #214). Keys are indexed under their CLIENT form, which is what the UI labels are rendered from.
    for (const file of MESSAGE_FILES) {
      if (fs.existsSync(file)) {
        for (const line of fs.readFileSync(file, 'utf-8').split(/\r?\n/)) {
          // '|' is a legal and used key character (CREATE|STANDING_ORDER).
          const m = /^([A-Za-z0-9_.|]+)\s*=\s*(.+)$/.exec(line);
          if (m) {
            (cache[toClientKey(m[1])] ??= []).push(m[2].trim());
          }
        }
      }
    }
  }
  return i18nCache;
}

function escapeRegex(text: string): string {
  return text.replace(/[.*+?^${}()|[\]\\]/g, '\\$&');
}

/** Regex matching the DE or EN translation of an NLS key — or the raw key itself as fallback. */
export function enumLabelRx(key: string): RegExp {
  const labels = loadI18n()[key] ?? [];
  const candidates = [...new Set([...labels, key])].map(escapeRegex);
  return new RegExp(`^\\s*(${candidates.join('|')})\\s*$`, 'i');
}

/**
 * Regex matching a translated label followed by a translated qualifier in parentheses.
 * Additional relation and scope text is accepted because limit-key labels may include both.
 */
export function enumLabelWithQualifierRx(labelKey: string, qualifierKey: string): RegExp {
  const labelCandidates = [...new Set([...(loadI18n()[labelKey] ?? []), labelKey])].map(escapeRegex);
  const qualifierCandidates = [...new Set([...(loadI18n()[qualifierKey] ?? []), qualifierKey])].map(escapeRegex);
  return new RegExp(
    `^\\s*(${labelCandidates.join('|')})(?:\\s*/.*?)?\\s*\\((${qualifierCandidates.join('|')})(?:,.*)?\\)\\s*$`,
    'i'
  );
}

/** Regex matching the complete translated label of a limit key, including every relation and scope qualifier. */
export function enumLabelWithQualifiersRx(labelKeys: string[], qualifierKeys: string[]): RegExp {
  const alternatives = (key: string): string => {
    const labels = loadI18n()[key] ?? [];
    return `(${[...new Set([...labels, key])].map(escapeRegex).join('|')})`;
  };
  const labels = labelKeys.map(alternatives).join('\\s*/\\s*');
  const qualifiers = qualifierKeys.map(alternatives).join('\\s*,\\s*');
  return new RegExp(`^\\s*${labels}\\s*\\(\\s*${qualifiers}\\s*\\)\\s*$`, 'i');
}

// ---------------------------------------------------------------------------
// UI helpers
// ---------------------------------------------------------------------------

/** Navigates to the Generic Connector base-data view after login and waits for the master view. */
export async function openGenericConnectorView(page: Page): Promise<void> {
  // The node lives under the collapsed 'Base Data - ...' root — expand it first (fresh login ⇒ collapsed).
  const baseDataNode = page.locator('.p-tree-node-content', { hasText: /(Base Data|Basisdaten)/i }).first();
  await baseDataNode.waitFor({ state: 'visible', timeout: 15_000 });
  await baseDataNode.dblclick();

  const treeNode = page
    .locator('.p-tree-node-content', {
      hasText: /(Generic Connector|Generischer Connector|GENERIC_CONNECTOR_DEF)/i
    })
    .first();
  await treeNode.waitFor({ state: 'visible', timeout: 15_000 });
  await treeNode.click();

  await page.locator('.data-container').first().waitFor({ state: 'visible', timeout: 15_000 });
  // Let readData() populate the connector dropdown before the caller inspects it.
  await page.waitForTimeout(800);
}

/** Activates the master view panel and opens its context menu; returns the visible menu. */
export async function openContextMenu(page: Page): Promise<Locator> {
  const contentArea = page.locator('.data-container').first();
  // Click the heading area, not the container center — the container is filled with child panels
  // (detail/tables) whose own click handlers would consume the panel activation.
  await contentArea.locator('h4').first().click();
  await page.waitForTimeout(300);
  await contentArea.locator('h4').first().click({ button: 'right' });
  const menu = page.locator('[role="menu"]:visible');
  await menu.waitFor({ state: 'visible', timeout: 5_000 });
  return menu;
}

/** Fills a text input/textarea the dynamic-form way: click, fill, dispatch input, blur. */
export async function fillText(scope: Locator, selector: string, value: string): Promise<void> {
  const input = scope.locator(selector).first();
  await input.click();
  await input.fill(value);
  await input.dispatchEvent('input');
  await input.blur();
}

/** Fills a number field; tolerates both plain input and wrapped (p-inputNumber style) markup. */
export async function fillNumber(scope: Locator, fieldId: string, value: number): Promise<void> {
  const input = scope.locator(`input#${fieldId}, #${fieldId} input`).first();
  await input.click();
  await input.fill(String(value));
  await input.dispatchEvent('input');
  await input.blur();
}

/** Selects a native <select> option by value and commits the change to the reactive form. */
export async function selectByValue(scope: Locator, fieldId: string, value: string): Promise<void> {
  const select = scope.locator(`select#${fieldId}`);
  await select.selectOption({ value });
  await select.dispatchEvent('change');
}

/**
 * Sets a dynamic-form checkbox (FormCheckboxComponent renders a plain native
 * <input type="checkbox"> — NOT an Optimus UI p-checkbox) to the desired state. setChecked() performs
 * a real click when the state differs, which fires the change event the reactive form listens to.
 */
export async function setCheckbox(scope: Locator, fieldId: string, desired: boolean): Promise<void> {
  await scope.locator(`input#${fieldId}`).first().setChecked(desired);
}

/**
 * Selects entries in an Optimus UI p-multiSelect whose item labels are translated enum names.
 * Opens the overlay, clicks each item by its DE/EN label (or raw key), then closes with Escape.
 */
export async function pickMultiSelect(page: Page, scope: Locator, fieldId: string, enumNames: string[]): Promise<void> {
  if (enumNames.length === 0) {
    return;
  }
  await scope.locator(`#${fieldId}`).first().click();
  const overlay = page.locator('.p-multiselect-overlay, .p-multiselect-panel').first();
  await overlay.waitFor({ state: 'visible', timeout: 5_000 });
  for (const name of enumNames) {
    const item = overlay.locator('[role="option"]', { hasText: enumLabelRx(name) }).first();
    await item.waitFor({ state: 'visible', timeout: 5_000 });
    await item.click();
  }
  await page.keyboard.press('Escape');
  await overlay.waitFor({ state: 'hidden', timeout: 5_000 });
}

/**
 * Checks nodes in the geoInclusions p-treeselect (checkbox mode, no propagation). Geo tokens are
 * matched against the MIC in the node label "name (MIC)" or a country node whose key is the 2-char
 * code. Tokens whose MIC has no configured stockexchange (e.g. EQWB, MUNC, MUND in the seed data)
 * are silently skipped — the live UI cannot select them either, so the persisted geoRestrictions
 * may be a subset of the exported value. Do not assert geoRestrictions strictly.
 */
export async function pickGeoInclusions(page: Page, scope: Locator, geoTokens: string[]): Promise<void> {
  if (geoTokens.length === 0) {
    return;
  }
  const trigger = scope.locator('#geoInclusions').first();
  await trigger.click();
  const overlay = page.locator('.p-treeselect-overlay, .p-treeselect-panel').first();
  await overlay.waitFor({ state: 'visible', timeout: 5_000 });
  const filter = overlay.locator('input').first();
  for (const token of geoTokens) {
    // MIC child nodes ("Exchange name (MIC)") are hidden under collapsed country nodes — use the
    // tree filter (filterBy=label) to surface them with their ancestors expanded.
    await filter.fill(token);
    await filter.dispatchEvent('input');
    await page.waitForTimeout(400);
    const rx =
      token.length === 4 ? new RegExp(`\\(${escapeRegex(token)}\\)`) : new RegExp(`^\\s*${escapeRegex(token)}\\b`, 'i');
    const node = overlay.locator('.p-tree-node-content', { hasText: rx }).first();
    if ((await node.count()) > 0) {
      await node.locator('.p-checkbox, input[type="checkbox"]').first().click();
      await page.waitForTimeout(150);
    }
    await filter.fill('');
    await filter.dispatchEvent('input');
    await page.waitForTimeout(200);
  }
  // Escape is consumed by the focused filter searchbox — close by toggling the trigger instead.
  await trigger.click();
  await overlay.waitFor({ state: 'hidden', timeout: 5_000 }).catch(async () => {
    await page.keyboard.press('Escape');
    await overlay.waitFor({ state: 'hidden', timeout: 5_000 });
  });
}

/**
 * Adds one row to an EditableTableComponent-based child table (field mappings / HTTP headers),
 * fills it via the provided callback, and saves it with the row's check button. Each save PUTs the
 * whole connector definition, so the caller must wait for the toast/re-render afterwards.
 */
export async function addEditableTableRow(
  page: Page,
  table: Locator,
  fill: (row: Locator) => Promise<void>
): Promise<void> {
  // The '+' p-button sits next to the caption heading inside the table header.
  await table.locator('.p-datatable-header button').first().click();
  // addNewRow() enters edit mode after a ~50ms setTimeout.
  const row = table.locator('tbody tr').last();
  await row.locator('button:has(i.pi-check)').waitFor({ state: 'visible', timeout: 5_000 });
  await fill(row);
  await row.locator('button:has(i.pi-check)').click();
}
