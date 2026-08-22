import { expect, Locator, Page, Response, test } from '@playwright/test';

import { loginAsFixtureUser } from './helpers';

/**
 * Browser coverage for TimeSeriesChartComponent. The assertions deliberately concentrate on how
 * Plotly and the available controls react to input; prices themselves are seeded market data and
 * are not treated as expected values.
 *
 * The three instruments are shared securities with deep history in V2__testdata.sql. Spec 040
 * already populated alledit's `_USA` and `_Switzerland` watchlists, so this spec only selects them
 * by natural keys and never changes either watchlist.
 */

interface ChartResponse {
  dateCloseList?: Array<{ date: string; close: number }>;
  ohlcList?: Array<{ date: string; close: number }>;
  ohlcAvailable: boolean;
  volumeAvailable?: boolean;
}

interface OpenChartResult {
  idSecuritycurrency: number;
  response: ChartResponse;
}

interface PlotState {
  names: string[];
  types: string[];
  connectGaps: Array<boolean | undefined>;
  xStart: string;
  tickSuffix: string;
  shapes: Array<Record<string, unknown>>;
  yAxes: Array<string | undefined>;
}

interface Instrument {
  watchlist: '_USA' | '_Switzerland';
  ticker: string;
  name: string;
}

const LOGIN_NICKNAME = 'alledit';
const HPQ: Instrument = { watchlist: '_USA', ticker: 'HPQ', name: 'HP' };
const DIS: Instrument = { watchlist: '_USA', ticker: 'DIS', name: 'Walt Disney Company' };
const NESN: Instrument = { watchlist: '_Switzerland', ticker: 'NESN', name: 'Nestlé AG' };

const RX = {
  addChart: /(Hinzuf.gen\s+als\s+Liniengrafik|Add\s+to\s+line\s+chart)/i,
  chart: /(Tagesendkurse\s+als\s+Linengrafik|EOD\s+as\s+line\s+chart)/i,
  confirmYes: /^(Ja|Yes)$/i,
  ema: /(Exponentieller\s+gleitender\s+Durchschnitt|Exponential\s+Moving\s+Average)/i,
  percentage: /^(Prozent|Percentage)$/i,
  connectGaps: /^(Verbinde\s+L.cken|Fill\s+gaps)$/i,
  holding: /^(Bestand|Holding)$/i,
  volume: /^(Menge|Volume)$/i,
  sma: /(Einfacher\s+gleitender\s+Mittelwert|Simple\s+Moving\s+Average)/i,
  rsi: /^RSI$/i
};

function exactText(value: string): RegExp {
  return new RegExp(`^\\s*${value.replace(/[.*+?^${}()|[\]\\]/g, '\\$&')}\\s*$`);
}

async function openWatchlist(page: Page, name: Instrument['watchlist']): Promise<void> {
  const node = page.getByRole('treeitem', { name, exact: true }).first();
  await node.waitFor({ state: 'visible', timeout: 15_000 });
  await node.click();
  await page.locator('.data-container').first().waitFor({ state: 'visible', timeout: 15_000 });
  await page.waitForTimeout(1200);
}

function instrumentRow(page: Page, instrument: Instrument): Locator {
  return page
    .locator('.data-container p-table tbody tr')
    .filter({ has: page.locator('td').filter({ hasText: exactText(instrument.ticker) }) })
    .first();
}

async function openInstrumentMenu(page: Page, instrument: Instrument): Promise<Locator> {
  const row = instrumentRow(page, instrument);
  await expect(row, `${instrument.ticker} in ${instrument.watchlist}`).toBeVisible({ timeout: 20_000 });

  // The watchlist uses multi-select. A retained selection can make the first click deselect the
  // row, so retry once when the instrument-specific chart commands are absent.
  for (let attempt = 0; attempt < 2; attempt++) {
    await row.click();
    await page.waitForTimeout(300);
    await row.click({ button: 'right' });
    const menu = page.locator('[role="menu"]:visible');
    await menu.first().waitFor({ state: 'visible', timeout: 5_000 });
    if (
      await menu
        .getByRole('menuitem', { name: RX.chart })
        .first()
        .isVisible()
        .catch(() => false)
    ) {
      return menu;
    }
    await page.keyboard.press('Escape');
  }
  throw new Error(`No chart command for ${instrument.ticker} in ${instrument.watchlist}`);
}

function waitForChartResponse(page: Page): Promise<Response> {
  return page.waitForResponse(
    (response) =>
      response.request().method() === 'GET' &&
      /\/api\/historyquote\/securitycurrency\/\d+\/forchart$/.test(new URL(response.url()).pathname),
    { timeout: 30_000 }
  );
}

async function responseChartResult(response: Response): Promise<OpenChartResult> {
  const body = await response.text();
  expect(response.ok(), `loading chart data: ${response.status()} ${body}`).toBeTruthy();
  const match = new URL(response.url()).pathname.match(/\/securitycurrency\/(\d+)\/forchart$/);
  expect(match, 'security id in chart request URL').not.toBeNull();
  return { idSecuritycurrency: Number(match![1]), response: JSON.parse(body) as ChartResponse };
}

async function waitForPlot(page: Page): Promise<void> {
  const plot = page.locator('.fullChart .js-plotly-plot').first();
  await plot.waitFor({ state: 'visible', timeout: 30_000 });
  await page.waitForFunction(
    () => {
      const element = document.querySelector('.fullChart .js-plotly-plot') as any;
      return Boolean(element?.data?.length && element?.layout?.xaxis?.range?.length === 2);
    },
    undefined,
    { timeout: 30_000 }
  );
}

async function plotState(page: Page): Promise<PlotState> {
  await waitForPlot(page);
  return page
    .locator('.fullChart .js-plotly-plot')
    .first()
    .evaluate((element: any) => ({
      names: (element.data ?? []).map((trace: any) => String(trace.name ?? '')),
      types: (element.data ?? []).map((trace: any) => String(trace.type ?? '')),
      connectGaps: (element.data ?? []).map((trace: any) => trace.connectgaps),
      xStart: String(element.layout?.xaxis?.range?.[0] ?? ''),
      tickSuffix: String(element.layout?.yaxis?.ticksuffix ?? ''),
      shapes: JSON.parse(JSON.stringify(element.layout?.shapes ?? [])),
      yAxes: (element.data ?? []).map((trace: any) => trace.yaxis)
    }));
}

async function openChart(page: Page, instrument: Instrument): Promise<OpenChartResult> {
  await openWatchlist(page, instrument.watchlist);
  const menu = await openInstrumentMenu(page, instrument);
  const responsePromise = waitForChartResponse(page);
  await menu.getByRole('menuitem', { name: RX.chart }).first().click();
  const result = await responseChartResult(await responsePromise);
  await waitForPlot(page);
  await expect
    .poll(async () => (await plotState(page)).names.some((name) => name.includes(instrument.name)), {
      timeout: 30_000,
      message: `${instrument.ticker} trace was not opened`
    })
    .toBe(true);
  return result;
}

async function addToChart(page: Page, instrument: Instrument): Promise<OpenChartResult> {
  await openWatchlist(page, instrument.watchlist);
  const menu = await openInstrumentMenu(page, instrument);
  const add = menu.getByRole('menuitem', { name: RX.addChart }).first();
  await expect(add).not.toHaveAttribute('aria-disabled', 'true');
  const responsePromise = waitForChartResponse(page);
  await add.click();
  const result = await responseChartResult(await responsePromise);
  await expect
    .poll(async () => (await plotState(page)).names.some((name) => name.includes(instrument.name)), {
      timeout: 30_000,
      message: `${instrument.ticker} trace was not added`
    })
    .toBe(true);
  return result;
}

function chartCheckbox(page: Page, label: RegExp): Locator {
  return page
    .locator('.fullChart .input-row label')
    .filter({ hasText: label })
    .first()
    .locator('xpath=following-sibling::input[@type="checkbox"][1]');
}

async function selectOptimusOption(page: Page, select: Locator, optionName: RegExp): Promise<void> {
  await select.click();
  const overlay = page.locator('.p-select-overlay:visible').first();
  await overlay.waitFor({ state: 'visible', timeout: 10_000 });
  const option = overlay.getByRole('option', { name: optionName }).first();
  await expect(option).toBeVisible({ timeout: 10_000 });
  await option.click();
  await overlay.waitFor({ state: 'hidden', timeout: 10_000 });
}

async function openChartContextMenu(page: Page): Promise<Locator> {
  const chart = page.locator('.fullChart').first();
  await chart.click({ position: { x: 8, y: 8 } });
  await page.waitForTimeout(250);
  await chart.click({ button: 'right', position: { x: 8, y: 8 } });
  const menu = page.locator('[role="menu"]:visible');
  await menu.first().waitFor({ state: 'visible', timeout: 5_000 });
  return menu;
}

async function editIndicator(
  page: Page,
  indicator: 'SMA' | 'EMA' | 'RSI',
  parentName: RegExp,
  values: Record<string, number>
): Promise<void> {
  const menu = await openChartContextMenu(page);
  const parent = menu.getByRole('menuitem', { name: parentName }).first();
  await expect(parent).not.toHaveAttribute('aria-disabled', 'true');
  await parent.hover();

  const edit = page
    .locator('[role="menu"]:visible')
    .getByRole('menuitem', { name: new RegExp(`^(Bearbeiten|Edit)\\s+${indicator}$`, 'i') })
    .first();
  await expect(edit).toBeVisible({ timeout: 5_000 });
  await edit.click();

  const dialog = page.locator('indicator-edit .p-dialog').first();
  await dialog.waitFor({ state: 'visible', timeout: 10_000 });
  for (const [field, value] of Object.entries(values)) {
    const input = dialog.locator(`#${field} input, input#${field}`).first();
    await input.click();
    await input.press('Control+a');
    await input.press('Backspace');
    await input.pressSequentially(String(value), { delay: 20 });
    await input.press('Tab');
    expect(Number((await input.inputValue()).replace(/[^\d.-]/g, '')), field).toBe(value);
  }

  const responsePromise = page.waitForResponse(
    (response) =>
      response.request().method() === 'POST' &&
      new RegExp(`/api/historyquote/\\d+/taindicator/${indicator}$`).test(new URL(response.url()).pathname),
    { timeout: 30_000 }
  );
  await dialog.locator('button[type="submit"]').click();
  const response = await responsePromise;
  const responseText = await response.text();
  expect(response.ok(), `${indicator} calculation: ${response.status()} ${responseText}`).toBeTruthy();
  expect(response.request().postDataJSON()).toEqual(
    Object.fromEntries(Object.entries(values).map(([field, value]) => [field, String(value)]))
  );
  await dialog.waitFor({ state: 'hidden', timeout: 10_000 });
  for (const period of Object.values(values)) {
    await expect
      .poll(async () => (await plotState(page)).names.some((name) => name.includes(`(${period})`)), {
        timeout: 30_000,
        message: `${indicator} period ${period} was not plotted`
      })
      .toBe(true);
  }
}

async function disableIndicator(page: Page, name: RegExp): Promise<void> {
  const menu = await openChartContextMenu(page);
  await menu.getByRole('menuitem', { name }).first().click();
}

function toDeChShortDate(iso: string): string {
  const [year, month, day] = iso.split('-');
  return `${day}.${month}.${year.slice(-2)}`;
}

async function deleteShapesThroughApi(page: Page, idSecuritycurrency: number): Promise<void> {
  const token = await page.evaluate(() => sessionStorage.getItem('jwt'));
  expect(token, 'JWT in sessionStorage after login').toBeTruthy();
  const response = await page.request.delete(`/api/userchartshape/${idSecuritycurrency}`, {
    headers: { 'x-auth-token': token! }
  });
  expect(response.ok(), `shape cleanup returned ${response.status()}: ${await response.text()}`).toBeTruthy();
}

async function clickModeBarButton(page: Page, selector: string): Promise<void> {
  const plot = page.locator('.fullChart .js-plotly-plot').first();
  await plot.hover();
  const button = plot.locator(`.modebar-btn${selector}`).first();
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

async function drawShape(
  page: Page,
  mode: 'drawline' | 'drawrect' | 'drawcircle',
  start: { x: number; y: number },
  end: { x: number; y: number },
  expectedCount: number
): Promise<void> {
  await clickModeBarButton(page, `[data-val="${mode}"]`);
  await expect
    .poll(
      () =>
        page
          .locator('.fullChart .js-plotly-plot')
          .first()
          .evaluate((element: any) => element.layout?.dragmode),
      { timeout: 5_000 }
    )
    .toBe(mode);
  const dragLayer = page.locator('.fullChart .js-plotly-plot .draglayer .nsewdrag').first();
  const box = await dragLayer.boundingBox();
  expect(box, 'Plotly drawing layer bounding box').not.toBeNull();

  const responsePromise = waitForShapeWrite(page, expectedCount);
  await page.mouse.move(box!.x + box!.width * start.x, box!.y + box!.height * start.y);
  await page.mouse.down();
  await page.mouse.move(box!.x + box!.width * end.x, box!.y + box!.height * end.y, { steps: 8 });
  await page.mouse.up();

  const response = await responsePromise;
  const body = response.request().postDataJSON() as { shapeData: Array<{ type: string }> };
  expect(response.ok(), `saving ${mode}: ${response.status()} ${await response.text()}`).toBeTruthy();
  expect(body.shapeData).toHaveLength(expectedCount);
  expect(body.shapeData[expectedCount - 1].type).toBe(mode.replace('draw', ''));
  await expect.poll(async () => (await plotState(page)).shapes.length, { timeout: 10_000 }).toBe(expectedCount);
}

test.describe.serial('time-series chart interaction', () => {
  test.use({ viewport: { width: 1600, height: 1200 } });

  test('reacts to date ranges, checkboxes, chart types, and all technical indicators', async ({ page }) => {
    test.setTimeout(240_000);
    await loginAsFixtureUser(page, LOGIN_NICKNAME);
    const hpq = await openChart(page, HPQ);
    expect(hpq.response.ohlcAvailable, 'HPQ must expose OHLC data for chart-type coverage').toBe(true);
    expect(hpq.response.volumeAvailable, 'HPQ must expose volume data for volume coverage').toBe(true);

    const fromDate = page.locator('.fullChart #fromDate input, .fullChart input#fromDate').first();
    await expect(fromDate).toBeVisible();
    const originalDate = await fromDate.inputValue();
    const dates = hpq.response.ohlcList ?? hpq.response.dateCloseList ?? [];
    expect(dates.length, 'HPQ chart history').toBeGreaterThan(500);
    const manualIso = dates[Math.floor(dates.length / 2)].date;
    const manualDate = toDeChShortDate(manualIso);
    await fromDate.click();
    await fromDate.press('Control+a');
    await fromDate.press('Backspace');
    await fromDate.pressSequentially(manualDate, { delay: 20 });
    await fromDate.press('Tab');
    await expect(fromDate).toHaveValue(manualDate);
    await expect.poll(async () => (await plotState(page)).xStart, { timeout: 15_000 }).toContain(manualIso);

    await page.locator('.fullChart .input-row .fa-undo').first().click();
    await expect(fromDate).toHaveValue(originalDate);

    const presetButtons = page.locator('.fullChart .input-row').first().locator('button');
    await expect(presetButtons).toHaveCount(9);
    for (let index = 0; index < 8; index++) {
      const previousInput = await fromDate.inputValue();
      const previousRange = (await plotState(page)).xStart;
      await presetButtons.nth(index).click();
      await expect(fromDate).not.toHaveValue(previousInput);
      await expect.poll(async () => (await plotState(page)).xStart, { timeout: 15_000 }).not.toBe(previousRange);
    }

    const percentage = chartCheckbox(page, RX.percentage);
    await percentage.setChecked(true);
    await expect.poll(async () => (await plotState(page)).tickSuffix).toBe('%');
    await percentage.setChecked(false);
    await expect.poll(async () => (await plotState(page)).tickSuffix).toBe('');

    const gaps = chartCheckbox(page, RX.connectGaps);
    await gaps.setChecked(false);
    await expect.poll(async () => (await plotState(page)).connectGaps[0]).toBe(false);
    await gaps.setChecked(true);
    await expect.poll(async () => (await plotState(page)).connectGaps[0]).toBe(true);

    const holding = chartCheckbox(page, RX.holding);
    await holding.setChecked(false);
    await expect(holding).not.toBeChecked();
    await holding.setChecked(true);
    await expect(holding).toBeChecked();

    const volume = chartCheckbox(page, RX.volume);
    await volume.setChecked(true);
    await expect.poll(async () => (await plotState(page)).types.includes('bar'), { timeout: 15_000 }).toBe(true);
    await volume.setChecked(false);
    await expect.poll(async () => (await plotState(page)).types.includes('bar'), { timeout: 15_000 }).toBe(false);

    const chartType = page.locator('.fullChart .input-row').nth(1).locator('p-select').nth(1);
    await selectOptimusOption(page, chartType, /^(Kerze|Candlestick)$/i);
    await expect.poll(async () => (await plotState(page)).types[0], { timeout: 15_000 }).toBe('candlestick');
    await selectOptimusOption(page, chartType, /^OHLC$/i);
    await expect.poll(async () => (await plotState(page)).types[0], { timeout: 15_000 }).toBe('ohlc');
    await selectOptimusOption(page, chartType, /(Tagesendkurse\s+als\s+Linengrafik|EOD\s+as\s+line\s+chart)/i);
    await expect.poll(async () => (await plotState(page)).types[0], { timeout: 15_000 }).toBe('scatter');

    await editIndicator(page, 'SMA', RX.sma, { taShortPeriod: 10, taMediumPeriod: 30, taLongPeriod: 100 });
    await editIndicator(page, 'EMA', RX.ema, { taShortPeriod: 12, taMediumPeriod: 26, taLongPeriod: 60 });
    await editIndicator(page, 'RSI', RX.rsi, { taShortPeriod: 7, taMediumPeriod: 14 });
    await expect.poll(async () => (await plotState(page)).yAxes.includes('y3'), { timeout: 15_000 }).toBe(true);

    await disableIndicator(page, RX.rsi);
    await expect.poll(async () => (await plotState(page)).yAxes.includes('y3'), { timeout: 15_000 }).toBe(false);
    await disableIndicator(page, RX.ema);
    await disableIndicator(page, RX.sma);
  });

  test('adds DIS and NESN and normalizes the comparison to USD and CHF', async ({ page }) => {
    test.setTimeout(180_000);
    await loginAsFixtureUser(page, LOGIN_NICKNAME);
    await openChart(page, HPQ);
    await addToChart(page, DIS);
    await addToChart(page, NESN);

    await expect
      .poll(
        async () =>
          (await plotState(page)).names.filter((name) =>
            [HPQ.name, DIS.name, NESN.name].some((expected) => name.includes(expected))
          ).length,
        { timeout: 30_000 }
      )
      .toBe(3);
    await expect(chartCheckbox(page, RX.percentage)).toBeChecked();
    await expect(page.locator('.fullChart .input-row').nth(1).locator('p-select')).toHaveCount(1);

    const menu = await openChartContextMenu(page);
    await expect(menu.getByRole('menuitem', { name: RX.sma }).first()).toHaveAttribute('aria-disabled', 'true');
    await page.keyboard.press('Escape');

    const currency = page.locator('.fullChart .input-row').nth(1).locator('p-select').first();
    for (const code of ['CHF', 'USD']) {
      const before = (await plotState(page)).xStart;
      await selectOptimusOption(page, currency, new RegExp(`^${code}$`));
      await expect(currency).toContainText(code);
      await expect
        .poll(async () => (await plotState(page)).names.length, { timeout: 15_000 })
        .toBeGreaterThanOrEqual(3);
      expect((await plotState(page)).xStart).toBe(before);
    }
  });

  test('persists drawings and supports erase, undo, redo, reload, and delete all', async ({ page }) => {
    test.setTimeout(180_000);
    await loginAsFixtureUser(page, LOGIN_NICKNAME);
    const hpq = await openChart(page, HPQ);

    // Startup cleanup makes a retry converge even when an earlier attempt stopped after drawing.
    await deleteShapesThroughApi(page, hpq.idSecuritycurrency);
    await openChart(page, DIS);
    await openChart(page, HPQ);
    await expect.poll(async () => (await plotState(page)).shapes.length, { timeout: 20_000 }).toBe(0);

    await drawShape(page, 'drawline', { x: 0.12, y: 0.2 }, { x: 0.27, y: 0.72 }, 1);
    await drawShape(page, 'drawrect', { x: 0.36, y: 0.18 }, { x: 0.49, y: 0.75 }, 2);
    await drawShape(page, 'drawcircle', { x: 0.6, y: 0.2 }, { x: 0.73, y: 0.72 }, 3);

    await clickModeBarButton(page, '[data-val="zoom"]');
    await expect
      .poll(
        () =>
          page
            .locator('.fullChart .js-plotly-plot')
            .first()
            .evaluate((element: any) => element.layout?.dragmode),
        { timeout: 5_000 }
      )
      .toBe('zoom');
    const circlePath = page
      .locator('.fullChart .js-plotly-plot .shapelayer .shape-group[data-index="2"] > path')
      .first();
    await expect(circlePath).toBeVisible();
    await circlePath.dispatchEvent('click');
    await expect
      .poll(
        () =>
          page
            .locator('.fullChart .js-plotly-plot')
            .first()
            .evaluate((element: any) => element._fullLayout?._activeShapeIndex),
        { timeout: 5_000 }
      )
      .toBe(2);
    const eraseResponsePromise = waitForShapeWrite(page, 2);
    await clickModeBarButton(page, '[data-title="Erase active shape"]');
    const eraseResponse = await eraseResponsePromise;
    expect(eraseResponse.ok(), `erasing shape: ${await eraseResponse.text()}`).toBeTruthy();
    expect((eraseResponse.request().postDataJSON() as { shapeData: unknown[] }).shapeData).toHaveLength(2);

    const undoResponsePromise = waitForShapeWrite(page, 3);
    await clickModeBarButton(page, '[data-title="Undo"]');
    await undoResponsePromise;
    await expect.poll(async () => (await plotState(page)).shapes.length, { timeout: 10_000 }).toBe(3);

    const redoResponsePromise = waitForShapeWrite(page, 2);
    await clickModeBarButton(page, '[data-title="Redo"]');
    await redoResponsePromise;
    await expect.poll(async () => (await plotState(page)).shapes.length, { timeout: 10_000 }).toBe(2);

    const loadPromise = page.waitForResponse(
      (response) =>
        response.request().method() === 'GET' &&
        new RegExp(`/api/userchartshape/${hpq.idSecuritycurrency}$`).test(new URL(response.url()).pathname),
      { timeout: 20_000 }
    );
    await openChart(page, DIS);
    await openChart(page, HPQ);
    const loadResponse = await loadPromise;
    expect(loadResponse.ok(), `reloading shapes: ${await loadResponse.text()}`).toBeTruthy();
    await expect.poll(async () => (await plotState(page)).shapes.length, { timeout: 20_000 }).toBe(2);

    await clickModeBarButton(page, '[data-title="Delete all shapes"]');
    const confirmation = page.locator('[role="alertdialog"]:visible').first();
    await confirmation.waitFor({ state: 'visible', timeout: 10_000 });
    const deletePromise = page.waitForResponse(
      (response) =>
        response.request().method() === 'DELETE' &&
        new RegExp(`/api/userchartshape/${hpq.idSecuritycurrency}$`).test(new URL(response.url()).pathname),
      { timeout: 20_000 }
    );
    await confirmation.getByRole('button', { name: RX.confirmYes }).first().click();
    const deleteResponse = await deletePromise;
    expect(deleteResponse.ok(), `deleting shapes: ${await deleteResponse.text()}`).toBeTruthy();
    await expect.poll(async () => (await plotState(page)).shapes.length, { timeout: 10_000 }).toBe(0);
  });
});
