import { expect, Locator, Page, test as base } from '@playwright/test';
import { loginAsFixtureUser } from './helpers';

/** REST contract used by the browser tests, independent of Angular's form and rendering classes. */
export interface DashboardWidget {
  instanceId: string;
  type: string;
  width: 'THIRD' | 'HALF' | 'TWO_THIRDS' | 'FULL';
  config: Record<string, number | string | boolean | null>;
}

export interface DashboardDocument {
  revision: number | null;
  widgets: DashboardWidget[];
}

export interface DashboardCatalogue {
  descriptors: { type: string }[];
  defaults: DashboardWidget[];
}

export type DashboardUser = 'admin' | 'alledit' | 'user' | 'limit1';

export const RX = {
  edit: /^(Edit dashboard|Dashboard bearbeiten)$/,
  save: /^(Save dashboard|Dashboard speichern)$/,
  cancel: /^(Cancel editing|Bearbeitung abbrechen)$/,
  reset: /^(Reset to defaults|Auf Standard zurücksetzen)$/,
  configure: /^(Configure|Konfigurieren)$/,
  submit: /^(Save|Speichern)$/,
  remove: /^(Remove|Entfernen)$/,
  add: /^(Add widget|Widget hinzufügen)$/,
  earlier: /^(Move earlier|Nach vorne verschieben)$/,
  later: /^(Move later|Nach hinten verschieben)$/,
  reload: /^(Reload saved layout|Gespeicherte Anordnung neu laden)$/,
  conflict: /Another session saved|Eine andere Sitzung hat/,
  previous: /Previous unsaved draft|Vorheriger, nicht gespeicherter Entwurf/
};

export const COMMON_TYPES = [
  'UNREAD_MAIL',
  'PROPOSE_CHANGE_OPEN',
  'HOLDING_WINNERS',
  'HOLDING_LOSERS',
  'PERFORMANCE_LAST_SESSIONS'
];

export const SETTINGS: Record<string, { field: string; min: number; max: number; edited: number }> = {
  UNREAD_MAIL: { field: 'maxRows', min: 1, max: 20, edited: 3 },
  PROPOSE_CHANGE_OPEN: { field: 'maxRows', min: 1, max: 20, edited: 3 },
  USER_LIMIT_REQUESTS: { field: 'maxRows', min: 1, max: 20, edited: 3 },
  HOLDING_WINNERS: { field: 'topN', min: 1, max: 5, edited: 5 },
  HOLDING_LOSERS: { field: 'topN', min: 1, max: 5, edited: 5 },
  PERFORMANCE_LAST_SESSIONS: { field: 'days', min: 2, max: 10, edited: 7 }
};

/** Both the configured backend and the browser's proxy must lead to the isolated test database before login. */
export async function verifyDashboardTestTarget(page: Page): Promise<void> {
  for (const url of [`${process.env.E2E_BACKEND_URL ?? 'http://localhost:8080'}/api/gtinfo`, '/api/gtinfo']) {
    const response = await page.request.get(url);
    expect(response.ok(), `dashboard test target ${url}`).toBeTruthy();
    const info = await response.json();
    expect(info.databaseName, `Never mutate a non-test dashboard through ${url}`).toBe('grafioschtrader_t');
    expect(info.activeProfile.split(','), `Expected the e2e profile at ${url}`).toContain('e2e');
  }
}

export async function dashboardHeaders(page: Page): Promise<Record<string, string>> {
  const token = await page.evaluate(() => sessionStorage.getItem('jwt'));
  if (!token) throw new Error('Fixture user must be authenticated');
  return { 'x-auth-token': token, Accept: 'application/json' };
}

export async function readDashboard(page: Page): Promise<DashboardDocument> {
  const response = await page.request.get('/api/dashboard', { headers: await dashboardHeaders(page) });
  expect(response.ok(), await response.text()).toBeTruthy();
  return response.json();
}

export async function resetDashboard(page: Page): Promise<DashboardDocument> {
  const previous = await readDashboard(page);
  const response = await page.request.put('/api/userdashboard', {
    headers: await dashboardHeaders(page),
    data: { expectedRevision: previous.revision, schemaVersion: 1, widgets: [], resetToDefault: true }
  });
  expect(response.ok(), `reset personal dashboard: ${await response.text()}`).toBeTruthy();
  return response.json();
}

/** The spec owns only the personal layouts of these test fixtures; every retry starts from catalogue defaults. */
export const test = base.extend<{ dashboardUser: DashboardUser; dashboard: DashboardDocument }>({
  dashboardUser: ['admin', { option: true }],
  dashboard: async ({ page, dashboardUser }, use) => {
    await verifyDashboardTestTarget(page);
    await loginAsFixtureUser(page, dashboardUser);
    const defaults = await resetDashboard(page);
    try {
      await page.goto('/mainview/dashboard');
      await expect(page.getByRole('button', { name: RX.edit })).toBeEnabled();
      await use(defaults);
    } finally {
      // An interrupted UI action cannot prevent cleanup through the authenticated API.
      await resetDashboard(page);
    }
  }
});

export function card(page: Page, type: string): Locator {
  return page.locator(`#dashboardGrid [data-widget-type="${type}"]`);
}

export async function cardOrder(page: Page): Promise<string[]> {
  return page
    .locator('#dashboardGrid [data-widget-type]')
    .evaluateAll((cards) => cards.map((element) => element.getAttribute('data-widget-type')!));
}

export async function editDashboard(page: Page): Promise<DashboardCatalogue> {
  const catalogueResponse = page.waitForResponse(
    (response) =>
      new URL(response.url()).pathname === '/api/dashboard/catalogue' && response.request().method() === 'GET'
  );
  await page.getByRole('button', { name: RX.edit }).click();
  const response = await catalogueResponse;
  expect(response.ok(), await response.text()).toBeTruthy();
  await expect(page.locator('#dashboardTray')).toBeVisible();
  return response.json();
}

export async function openConfiguration(page: Page, type: string): Promise<Locator> {
  await card(page, type).getByRole('button', { name: RX.configure }).click();
  const dialog = page.locator('dashboard-config .p-dialog:visible');
  await expect(dialog).toBeVisible();
  // onShow initializes the form asynchronously; filling it earlier can be overwritten by its original value.
  await expect(dialog.locator(`#${SETTINGS[type].field}`)).toHaveValue(/\d+/);
  return dialog;
}

export async function closeConfiguration(page: Page, dialog: Locator): Promise<void> {
  await dialog.locator('.p-dialog-header-actions button').click();
  // Hidden is insufficient: the old two-way visibility binding left a hidden component that could never reopen.
  await expect(page.locator('dashboard-config')).toHaveCount(0);
}

export async function configureWidget(page: Page, type: string, value: number): Promise<void> {
  const dialog = await openConfiguration(page, type);
  await dialog.locator(`#${SETTINGS[type].field}`).fill(String(value));
  await dialog.getByRole('button', { name: RX.submit }).click();
  await expect(page.locator('dashboard-config')).toHaveCount(0);
}

export async function saveDashboard(page: Page, status = 200): Promise<DashboardDocument> {
  const saved = page.waitForResponse(
    (response) => new URL(response.url()).pathname === '/api/userdashboard' && response.request().method() === 'PUT'
  );
  await page.getByRole('button', { name: RX.save }).click();
  const response = await saved;
  expect(response.status(), await response.text()).toBe(status);
  const request = response.request().postDataJSON();
  for (const widget of request.widgets as DashboardWidget[]) {
    const setting = SETTINGS[widget.type];
    expect(typeof widget.config[setting.field], `${widget.type}.${setting.field} must be a JSON number`).toBe('number');
    expect(Number.isInteger(widget.config[setting.field])).toBe(true);
  }
  if (status === 200) await expect(page.getByRole('button', { name: RX.edit })).toBeEnabled();
  return response.json();
}

/** Check the server document fetched by a real page reload as well as the displayed ordering and widths. */
export async function expectReloadedLayout(page: Page, widgets: DashboardWidget[]): Promise<void> {
  const loaded = page.waitForResponse(
    (response) => new URL(response.url()).pathname === '/api/dashboard' && response.request().method() === 'GET'
  );
  await page.reload();
  const response = await loaded;
  expect(response.ok(), await response.text()).toBeTruthy();
  expect((await response.json()).widgets).toEqual(widgets);
  await expect(page.getByRole('button', { name: RX.edit })).toBeEnabled();
  await expect.poll(() => cardOrder(page)).toEqual(widgets.map((widget) => widget.type));
  const spans = { THIRD: 4, HALF: 6, TWO_THIRDS: 8, FULL: 12 };
  for (const widget of widgets) {
    await expect(card(page, widget.type)).toHaveCSS('--card-span', String(spans[widget.width]));
  }
}
