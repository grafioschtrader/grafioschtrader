import { expect, Locator, Page, Response, test } from '@playwright/test';
import { watchlistTable } from './watchlist.helpers';

import { loginAsFixtureUser } from './helpers';

/**
 * Browser coverage of the color schemes in Grafioschtrader: switching through Settings → Theme, a mounted price chart
 * that is restyled in place (zoom, hidden legend entry and drawings survive, no shape save is sent), the undo history
 * of the drawings, the chart PDF export while the dark scheme is active, and the gain colors of the watchlist.
 *
 * The instrument is HP (HPQ) in alledit's `_USA` watchlist, which spec 090 populated; this spec never changes the
 * watchlist. It deletes the drawings of HPQ at its start and end and restores the theme mode `system`.
 */

interface PlotSnapshot {
  range: string[];
  hidden: Array<boolean | string | undefined>;
  shapes: number;
  gridcolor: string;
  borderToken: string;
}

const LOGIN_NICKNAME = 'alledit';
const WATCHLIST = '_USA';
const TICKER = 'HPQ';
const INSTRUMENT_NAME = 'HP';

const RX = {
  settings: /^(Einstellungen|Settings)$/,
  theme: /^(Erscheinungsbild|Theme)$/,
  system: /^System$/,
  light: /^(Hell|Light)$/,
  dark: /^(Dunkel|Dark)$/,
  chart: /(Tagesendkurse\s+als\s+Linengrafik|EOD\s+as\s+line\s+chart)/i,
  chartPdf: /(Diagramm\s+als\s+PDF|Chart\s+as\s+PDF)/i,
  createPdf: /(PDF\s+erstellen|Create\s+PDF)/i
};

function exactText(value: string): RegExp {
  return new RegExp(`^\\s*${value.replace(/[.*+?^${}()|[\]\\]/g, '\\$&')}\\s*$`);
}

/** Chooses an entry of Settings → Theme in the menu bar. */
async function selectTheme(page: Page, entry: RegExp): Promise<void> {
  await page.locator('menubar').getByRole('menuitem', { name: RX.settings }).first().click();
  const themeItem = page.getByRole('menuitem', { name: RX.theme }).first();
  await themeItem.hover();
  const target = page.getByRole('menuitem', { name: entry }).first();
  await expect(target).toBeVisible({ timeout: 5_000 });
  await target.click();
  await page.keyboard.press('Escape');
}

async function currentTheme(page: Page): Promise<string | null> {
  return page.evaluate(() => document.documentElement.getAttribute('data-bs-theme'));
}

async function openWatchlist(page: Page): Promise<void> {
  const node = page.getByRole('treeitem', { name: WATCHLIST, exact: true }).first();
  await node.waitFor({ state: 'visible', timeout: 15_000 });
  await node.locator(':scope > .p-tree-node-content').click();
  const visible = await watchlistTable(page)
    .waitFor({ state: 'visible', timeout: 2_000 })
    .then(() => true)
    .catch(() => false);
  if (!visible) {
    // The tree emits no new selection while the requested watchlist is still selected and a chart occupies the
    // content area, so select another fixture watchlist once and return to the requested one.
    const alternate = page.getByRole('treeitem', { name: '_Switzerland', exact: true }).first();
    await alternate.locator(':scope > .p-tree-node-content').click();
    await node.locator(':scope > .p-tree-node-content').click();
  }
  await watchlistTable(page).waitFor({ state: 'visible', timeout: 15_000 });
  await page.waitForTimeout(1200);
}

async function openPriceChart(page: Page): Promise<number> {
  await openWatchlist(page);
  const row = watchlistTable(page)
    .locator('p-table tbody tr')
    .filter({ has: page.locator('td').filter({ hasText: exactText(TICKER) }) })
    .first();
  await expect(row, `${TICKER} in ${WATCHLIST}`).toBeVisible({ timeout: 20_000 });
  let menu: Locator | null = null;
  // The watchlist uses multi-select; a retained selection can make the first click deselect the row.
  for (let attempt = 0; attempt < 2 && !menu; attempt++) {
    await row.click();
    await page.waitForTimeout(300);
    await row.click({ button: 'right' });
    const candidate = page.locator('[role="menu"]:visible');
    await candidate.first().waitFor({ state: 'visible', timeout: 5_000 });
    if (
      await candidate
        .getByRole('menuitem', { name: RX.chart })
        .first()
        .isVisible()
        .catch(() => false)
    ) {
      menu = candidate;
    } else {
      await page.keyboard.press('Escape');
    }
  }
  expect(menu, `chart command for ${TICKER}`).not.toBeNull();
  const responsePromise = page.waitForResponse(
    (response) =>
      response.request().method() === 'GET' &&
      /\/api\/historyquote\/securitycurrency\/\d+\/forchart$/.test(new URL(response.url()).pathname),
    { timeout: 30_000 }
  );
  await menu!.getByRole('menuitem', { name: RX.chart }).first().click();
  const response = await responsePromise;
  expect(response.ok(), `loading chart data: ${response.status()}`).toBeTruthy();
  const id = Number(new URL(response.url()).pathname.match(/\/securitycurrency\/(\d+)\/forchart$/)![1]);
  const plot = page.locator('.fullChart .js-plotly-plot').first();
  await plot.waitFor({ state: 'visible', timeout: 30_000 });
  await expect
    .poll(() => plot.evaluate((element: any) => (element.data ?? []).some((t: any) => String(t.name).includes('HP'))), {
      timeout: 30_000,
      message: `${INSTRUMENT_NAME} trace was not opened`
    })
    .toBe(true);
  return id;
}

async function deleteShapesThroughApi(page: Page, idSecuritycurrency: number): Promise<void> {
  const token = await page.evaluate(() => sessionStorage.getItem('jwt'));
  expect(token, 'JWT in sessionStorage after login').toBeTruthy();
  const response = await page.request.delete(`/api/userchartshape/${idSecuritycurrency}`, {
    headers: { 'x-auth-token': token! }
  });
  expect(response.ok(), `shape cleanup returned ${response.status()}`).toBeTruthy();
}

function plot(page: Page): Locator {
  return page.locator('.fullChart .js-plotly-plot').first();
}

async function snapshot(page: Page): Promise<PlotSnapshot> {
  return plot(page).evaluate((element: any) => ({
    range: (element.layout?.xaxis?.range ?? []).map((value: unknown) => String(value)),
    hidden: (element.data ?? []).map((trace: any) => trace.visible),
    shapes: (element.layout?.shapes ?? []).length,
    gridcolor: String(element._fullLayout?.xaxis?.gridcolor ?? ''),
    borderToken: getComputedStyle(document.documentElement).getPropertyValue('--p-content-border-color').trim()
  }));
}

async function clickModeBarButton(page: Page, selector: string): Promise<void> {
  await plot(page).hover();
  const button = plot(page).locator(`.modebar-btn${selector}`).first();
  await expect(button, `Plotly mode-bar button ${selector}`).toBeVisible({ timeout: 5_000 });
  await button.click();
}

function waitForShapeWrite(page: Page, expectedCount: number): Promise<Response> {
  return page.waitForResponse(
    (response) => {
      if (response.request().method() !== 'PUT' || !/\/api\/userchartshape$/.test(new URL(response.url()).pathname)) {
        return false;
      }
      const body = response.request().postDataJSON() as { shapeData?: unknown[] } | null;
      return body?.shapeData?.length === expectedCount;
    },
    { timeout: 20_000 }
  );
}

async function dragOnPlot(page: Page, start: { x: number; y: number }, end: { x: number; y: number }): Promise<void> {
  const box = await plot(page).locator('.draglayer .nsewdrag').first().boundingBox();
  expect(box, 'Plotly drag layer bounding box').not.toBeNull();
  await page.mouse.move(box!.x + box!.width * start.x, box!.y + box!.height * start.y);
  await page.mouse.down();
  await page.mouse.move(box!.x + box!.width * end.x, box!.y + box!.height * end.y, { steps: 8 });
  await page.mouse.up();
}

async function drawShape(page: Page, mode: 'drawline' | 'drawrect', expectedCount: number, offset: number) {
  await clickModeBarButton(page, `[data-val="${mode}"]`);
  await expect.poll(() => plot(page).evaluate((element: any) => element.layout?.dragmode)).toBe(mode);
  const write = waitForShapeWrite(page, expectedCount);
  await dragOnPlot(page, { x: 0.3 + offset, y: 0.35 }, { x: 0.45 + offset, y: 0.6 });
  expect((await write).ok(), `saving ${mode}`).toBeTruthy();
  await expect.poll(async () => (await snapshot(page)).shapes, { timeout: 10_000 }).toBe(expectedCount);
}

/** Computed text color of a probe element colored with a CSS custom property of the active scheme. */
async function tokenColor(page: Page, token: string): Promise<string> {
  return page.evaluate((name) => {
    const probe = document.createElement('span');
    probe.style.color = `var(${name})`;
    document.body.appendChild(probe);
    const color = getComputedStyle(probe).color;
    probe.remove();
    return color;
  }, token);
}

test.describe.serial('dark mode', () => {
  test.use({ viewport: { width: 1600, height: 1200 } });

  test('switches the scheme through the menu, keeps the choice over a reload and marks the stored mode', async ({
    page
  }) => {
    await loginAsFixtureUser(page, LOGIN_NICKNAME);
    await page.evaluate(() => localStorage.removeItem('gt.theme'));

    await selectTheme(page, RX.dark);
    await expect.poll(() => currentTheme(page)).toBe('dark');
    expect(await page.evaluate(() => localStorage.getItem('gt.theme'))).toBe('dark');
    const darkBody = await page.evaluate(() => getComputedStyle(document.body).backgroundColor);
    expect(darkBody, 'page background in the dark scheme').not.toBe('rgb(255, 255, 255)');

    await page.reload();
    await expect.poll(() => currentTheme(page)).toBe('dark');

    await page.locator('menubar').getByRole('menuitem', { name: RX.settings }).first().click();
    await page.getByRole('menuitem', { name: RX.theme }).first().hover();
    const darkEntry = page.getByRole('menuitem', { name: RX.dark }).first();
    await expect(darkEntry.locator('.pi-check')).toHaveCount(1);
    await expect(page.getByRole('menuitem', { name: RX.light }).first().locator('.pi-check')).toHaveCount(0);
    await page.keyboard.press('Escape');

    await selectTheme(page, RX.system);
    await expect.poll(() => currentTheme(page)).toBe('light');
    expect(await page.evaluate(() => localStorage.getItem('gt.theme'))).toBe('system');
  });

  test('restyles a zoomed price chart with drawings in place and keeps its undo history', async ({ page }) => {
    test.setTimeout(180_000);
    await loginAsFixtureUser(page, LOGIN_NICKNAME);
    await page.evaluate(() => localStorage.setItem('gt.theme', 'light'));
    const id = await openPriceChart(page);
    // Drawings left behind by an aborted earlier run are counted instead of removed; the finally block deletes them.
    const initial = (await snapshot(page)).shapes;

    try {
      await drawShape(page, 'drawline', initial + 1, 0);
      await drawShape(page, 'drawrect', initial + 2, 0.15);

      // Zoom with the drag mode zoom, then hide the first legend entry
      await clickModeBarButton(page, '[data-val="zoom"]');
      await dragOnPlot(page, { x: 0.2, y: 0.5 }, { x: 0.8, y: 0.5 });
      await plot(page).locator('g.legend g.traces').first().click();
      await expect.poll(async () => (await snapshot(page)).hidden[0]).toBe('legendonly');
      const before = await snapshot(page);
      expect(before.gridcolor).toBe(before.borderToken);

      const shapeRequests: string[] = [];
      page.on('request', (request) => {
        if (/\/api\/userchartshape/.test(request.url())) shapeRequests.push(`${request.method()} ${request.url()}`);
      });

      await selectTheme(page, RX.dark);
      await expect.poll(() => currentTheme(page)).toBe('dark');
      await expect
        .poll(async () => {
          const s = await snapshot(page);
          return s.gridcolor === s.borderToken && s.gridcolor !== before.gridcolor;
        })
        .toBe(true);
      const dark = await snapshot(page);
      expect(dark.range).toEqual(before.range);
      expect(dark.hidden).toEqual(before.hidden);
      expect(dark.shapes).toBe(initial + 2);

      await selectTheme(page, RX.light);
      await expect.poll(async () => (await snapshot(page)).gridcolor).toBe(before.gridcolor);
      const light = await snapshot(page);
      expect(light.range).toEqual(before.range);
      expect(light.hidden).toEqual(before.hidden);
      expect(light.shapes).toBe(initial + 2);

      await page.waitForTimeout(1000); // longer than the 500 ms debounce of the shape save
      expect(shapeRequests, 'a scheme change must not save the drawings').toEqual([]);

      const undoWrite = waitForShapeWrite(page, initial + 1);
      await clickModeBarButton(page, '[data-title="Undo"]');
      expect((await undoWrite).ok()).toBeTruthy();
      await expect.poll(async () => (await snapshot(page)).shapes).toBe(initial + 1);
      const remaining = await plot(page).evaluate(
        (element: any) => element.layout.shapes[element.layout.shapes.length - 1].type
      );
      expect(remaining, 'undo removes the second drawing').toBe('line');

      // Chart PDF export while the dark scheme is active
      await selectTheme(page, RX.dark);
      await expect.poll(() => currentTheme(page)).toBe('dark');
      const chart = page.locator('.fullChart').first();
      await chart.click({ position: { x: 8, y: 8 } });
      await chart.click({ button: 'right', position: { x: 8, y: 8 } });
      const menu = page.locator('[role="menu"]:visible');
      await menu.getByRole('menuitem', { name: RX.chartPdf }).first().click();
      const dialog = page.locator('.p-dialog:visible').last();
      const create = dialog.getByRole('button', { name: RX.createPdf });
      await expect(create).toBeEnabled({ timeout: 15_000 });
      const pdfResponse = page.waitForResponse(
        (response) =>
          response.request().method() === 'POST' &&
          /\/api\/security\/chart\/pdf$/.test(new URL(response.url()).pathname),
        { timeout: 60_000 }
      );
      await create.click();
      const pdf = await pdfResponse;
      expect(pdf.ok(), `chart PDF: ${pdf.status()}`).toBeTruthy();
      expect(pdf.headers()['content-type'] ?? '').toContain('pdf');
    } finally {
      await deleteShapesThroughApi(page, id);
      await page.evaluate(() => localStorage.setItem('gt.theme', 'system'));
    }
  });

  test('colors positive results in the watchlist with the gain color of the active scheme', async ({ page }) => {
    await loginAsFixtureUser(page, LOGIN_NICKNAME);
    await page.evaluate(() => localStorage.setItem('gt.theme', 'light'));
    await page.reload();
    await openWatchlist(page);

    try {
      const gainCells = watchlistTable(page).locator('p-table tbody .gt-gain');
      await expect.poll(() => gainCells.count(), { timeout: 20_000 }).toBeGreaterThan(0);
      const lightGain = await tokenColor(page, '--gt-gain-color');
      expect(await gainCells.first().evaluate((el) => getComputedStyle(el).color)).toBe(lightGain);
      // Every green cell shows a value above zero: no minus sign and a non-zero digit.
      for (const text of await gainCells.allInnerTexts()) {
        expect(text.trim(), `green cell "${text}"`).toMatch(/^(?![-−]).*[1-9]/);
      }

      await selectTheme(page, RX.dark);
      await expect.poll(() => currentTheme(page)).toBe('dark');
      const darkGain = await tokenColor(page, '--gt-gain-color');
      expect(darkGain).not.toBe(lightGain);
      await expect.poll(() => gainCells.first().evaluate((el) => getComputedStyle(el).color)).toBe(darkGain);
    } finally {
      await page.evaluate(() => localStorage.setItem('gt.theme', 'system'));
    }
  });
});
