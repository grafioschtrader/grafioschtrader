import {test, expect, Locator, Page} from '@playwright/test';
import {loginAsFixtureUser} from './helpers';

/**
 * E2E coverage for the correlation feature (CorrelationComponent + CorrelationTableComponent +
 * the correlation-set-edit / correlation-add-instrument dialogs).
 *
 * The flow is context-menu driven: create a correlation set, search & add instruments, view the
 * auto-computed correlation matrix, and generate a rolling-correlation Plotly chart between two
 * instruments. A second test drives the max-instruments upper limit.
 *
 * Data constraint: correlation needs overlapping historical prices. Only the CH/US securities that
 * nv.bat dumps into V2__testdata.sql carry historyquote rows in grafioschtrader_t (span 2000-2026,
 * near-total overlap). Those are the only instruments used here (searched by asset class, ISIN and
 * approximate name). The generated/securities.json securities have no DB history and are avoided.
 *
 * The user hugo.graf@grafiosch.com = users.json nickname 'alledit' (role ALLEDIT, de-CH locale), so
 * the running UI is German; every text selector below matches both the German and English strings.
 */

const LOGIN_NICKNAME = 'alledit';
// Names deliberately chosen so neither is a substring of the other (option-text exact matching).
const SET_NAME = 'E2E Corr Matrix';
const SET_NAME_LIMIT = 'E2E Corr Limit';
const MAX_INSTRUMENTS = 20; // gt.max.correlation.instruments in grafioschtrader_t

// Blue-chip CH securities with deep, fully overlapping history (verified against the test DB).
const NESTLE_ISIN = 'CH0038863350';
const ABB_ISIN = 'CH0012221716';

// The add-instrument dialog contains both the search form and its result table. The default
// Playwright viewport is too short to expose the result-table action buttons.
test.use({viewport: {width: 1440, height: 1200}});

/** Opens the correlation view via the left-tree root node and waits for it to render. */
async function gotoCorrelationView(page: Page): Promise<void> {
  const root = page.locator('.p-tree-node-content', {
    hasText: /Watchlist\s*-\s*(Korrelationsmatrix|Correlation\s*matrix)/i,
  }).first();
  await root.waitFor({state: 'visible', timeout: 15_000});
  await root.click();
  await page.locator('.data-container').first().waitFor({state: 'visible', timeout: 15_000});
  await expect(page.locator('h4.singleRowTableHeader')).toBeVisible({timeout: 10_000});
}

/** Left-click to activate the panel, then right-click to open the context menu; returns the menu. */
async function openContextMenu(page: Page): Promise<Locator> {
  const container = page.locator('.data-container').first();
  await container.click();
  await page.waitForTimeout(300);
  await container.click({button: 'right'});
  const menu = page.locator('[role="menu"]:visible');
  await menu.waitFor({state: 'visible', timeout: 5_000});
  return menu;
}

/** Rows in the matrix's own table, excluding the nested add-instrument result table. */
function matrixRows(page: Page): Locator {
  return page.locator('correlation-table > p-table tbody tr');
}

/** Number of instrument rows currently shown in the correlation matrix table. */
async function matrixRowCount(page: Page): Promise<number> {
  return matrixRows(page).count();
}

/**
 * Ensures a correlation set with the given name exists and is selected in the top-form dropdown.
 * Idempotent: reuses an existing set (so reruns don't accumulate sets against the tenant limit).
 */
async function ensureSetSelected(page: Page, name: string): Promise<void> {
  const optionForName = page.locator('select#idCorrelationSet option')
    .filter({hasText: name});

  if (await optionForName.count() === 0) {
    const menu = await openContextMenu(page);
    await menu.getByText(/(Erstellen\s+Korrelationsset|Create\s+Correlation\s+set)/i).first().click();

    const dialog = page.locator('correlation-set-edit');
    await dialog.locator('.p-dialog').waitFor({state: 'visible', timeout: 10_000});

    const nameInput = dialog.locator('#name');
    await nameInput.click();
    await nameInput.fill(name);
    await nameInput.dispatchEvent('input');

    // Daily returns → prepareShowMenu enables the chart menu and rolling options auto-populate
    // (the rolling control auto-selects its first value).
    const sampling = dialog.locator('select#samplingPeriod');
    await sampling.selectOption({value: 'DAILY_RETURNS'});
    await sampling.dispatchEvent('change');

    await dialog.locator('button[type="submit"]').click();
    await dialog.locator('.p-dialog').waitFor({state: 'hidden', timeout: 15_000});

    await expect(optionForName).toHaveCount(1, {timeout: 10_000});
  }

  const select = page.locator('select#idCorrelationSet');
  await select.selectOption({label: name});
  await select.dispatchEvent('change');
  await page.waitForTimeout(500);
}

/** Opens the "Add existing instrument" search dialog via the context menu; returns its root element. */
async function openAddInstrumentDialog(page: Page): Promise<Locator> {
  const menu = await openContextMenu(page);
  await menu.getByText(/(Bestehendes\s+Instrument\s+hinzuf.gen|Add\s+existing\s+instrument)/i)
    .first().click();
  const addDialog = page.locator('correlation-add-instrument');
  await addDialog.locator('.p-dialog').waitFor({state: 'visible', timeout: 10_000});
  // Nothing may be typed before the search master data has arrived: the subscribe in
  // SecuritycurrencySearchBase.initialize() assigns the dropdown options and then calls
  // setDefaultValuesAndEnableSubmit(), which is a form.reset() — anything entered earlier is
  // silently wiped, leaving the criteria empty and the Search button disabled by
  // atLeastOneFieldValidator. idStockexchange is filled one line before that reset, so its options
  // prove the reset already happened.
  await expect(addDialog.locator('select#idStockexchange option')).not.toHaveCount(0, {timeout: 15_000});
  return addDialog;
}

/** Clicks Search and waits for at least one result row to appear. */
async function runSearch(page: Page, addDialog: Locator): Promise<void> {
  await addDialog.locator('button[type="submit"]').click();
  await expect(addDialog.locator('correlation-set-add-instrument-table tbody tr').first())
    .toBeVisible({timeout: 15_000});
}

/** Ticks the first `count` result-row checkboxes. */
async function selectResultRows(addDialog: Locator, count: number): Promise<void> {
  const rows = addDialog.locator('correlation-set-add-instrument-table tbody tr');
  for (let i = 0; i < count; i++) {
    await rows.nth(i).locator('p-tablecheckbox').click();
  }
}

/** The results-table Add button. */
function addButton(addDialog: Locator): Locator {
  return addDialog.getByRole('button', {name: /^(Hinzuf.gen|Add)$/});
}

/** Clicks Add and waits for the operation to complete (selection cleared → button disabled again). */
async function clickAddAndWait(addDialog: Locator): Promise<void> {
  const btn = addButton(addDialog);
  await expect(btn).toBeEnabled({timeout: 5_000});
  await btn.click();
  await expect(btn).toBeDisabled({timeout: 10_000});
}

/** Closes the add-instrument dialog via its Close ("Beenden") button. */
async function closeAddInstrumentDialog(page: Page, addDialog: Locator): Promise<void> {
  await addDialog.getByRole('button', {name: /^(Beenden|Close)$/}).click();
  await addDialog.locator('.p-dialog').waitFor({state: 'hidden', timeout: 10_000});
}

/** Opens a fresh add dialog, runs one search, selects rows, adds them, and closes the dialog. */
async function addBySearch(
  page: Page,
  fillSearch: (addDialog: Locator) => Promise<void>,
  rowsToSelect: number,
): Promise<void> {
  const addDialog = await openAddInstrumentDialog(page);
  await fillSearch(addDialog);
  await runSearch(page, addDialog);
  await selectResultRows(addDialog, rowsToSelect);
  await clickAddAndWait(addDialog);
  await closeAddInstrumentDialog(page, addDialog);
}

test.describe.serial('Correlation matrix', () => {

  test('builds a 6-instrument matrix and generates a rolling-correlation chart', async ({page}) => {
    test.setTimeout(180_000);
    await loginAsFixtureUser(page, LOGIN_NICKNAME);
    await gotoCorrelationView(page);
    await ensureSetSelected(page, SET_NAME);

    // Idempotent: only populate when the set is (near) empty. Six instruments = target.
    if (await matrixRowCount(page) < 6) {
      // 1) by ISIN — Nestlé
      await addBySearch(page, async (d) => {
        const isin = d.locator('#isin');
        await isin.click();
        await isin.fill(NESTLE_ISIN);
        await isin.dispatchEvent('input');
      }, 1);

      // 2) by approximate name — Novartis
      await addBySearch(page, async (d) => {
        const name = d.locator('#name');
        await name.click();
        await name.fill('Novartis');
        await name.dispatchEvent('input');
      }, 1);

      // 3) by ISIN — ABB
      await addBySearch(page, async (d) => {
        const isin = d.locator('#isin');
        await isin.click();
        await isin.fill(ABB_ISIN);
        await isin.dispatchEvent('input');
      }, 1);

      // 4) by approximate name — Roche
      await addBySearch(page, async (d) => {
        const name = d.locator('#name');
        await name.click();
        await name.fill('Roche');
        await name.dispatchEvent('input');
      }, 1);

      // 5) by asset class — pick the first two equities (search excludes the four already added)
      await addBySearch(page, async (d) => {
        const ac = d.locator('select#assetclassType');
        await ac.selectOption({value: 'EQUITIES'});
        await ac.dispatchEvent('change');
      }, 2);
    }

    // --- Assert the matrix was computed ---
    await expect(matrixRows(page)).toHaveCount(6, {timeout: 20_000});
    // Color-coded numeric correlation cells prove a real calculation (getBackgroundColor → hsl()).
    await expect(page.locator('correlation-table td[style*="background-color"]').first())
      .toBeVisible({timeout: 20_000});
    // The tomato "no overlapping closing prices" warning must NOT be present.
    await expect(page.locator('correlation-table')).toBeVisible();
    await expect(page.getByText(
      /Berechnung nicht m.glich|no overlapping closing prices/i)).toHaveCount(0);

    // --- Generate the rolling-correlation chart (linked function) ---
    // Click a correlation cell in row 0 under a different instrument's column: this selects the row
    // (instrument A) and sets the column instrument (B), enabling the "A<->B as graph" menu item.
    // td layout per row: [0]=toggler [1]=name [2]=currency [3]=ticker [4]=col A [5]=col B ...
    const firstRow = matrixRows(page).first();
    await firstRow.locator('td').nth(5).click();

    const menu = await openContextMenu(page);
    // CORRELATION_SET_GRAPH = "…als Grafik"/"… as graph". Exclude the CORRELATION_ADD_GRAPH variant
    // ("…hinzufügen als Grafik"/"…add as graph"); on the first chart it isn't present anyway.
    const graphItem = menu.getByText(/(als\s+Grafik|as\s+graph)/i)
      .filter({hasNotText: /hinzuf.gen\s+als\s+Grafik|add\s+as\s+graph/i});
    await expect(graphItem.first()).toBeVisible({timeout: 5_000});
    await graphItem.first().click();

    // The chart opens in the mainbottom outlet as a Plotly chart.
    await expect(page.locator('.js-plotly-plot').first()).toBeVisible({timeout: 25_000});
  });

  test('enforces the max-instruments upper limit', async ({page}) => {
    test.setTimeout(240_000);
    await loginAsFixtureUser(page, LOGIN_NICKNAME);
    await gotoCorrelationView(page);
    await ensureSetSelected(page, SET_NAME_LIMIT);

    // Build up to the limit (idempotent: skip if a previous run already filled it).
    if (await matrixRowCount(page) < MAX_INSTRUMENTS) {
      const addDialog = await openAddInstrumentDialog(page);

      // Broad search by SIX Swiss Exchange (184 securities with history). Options are
      // value=idStockexchange, label=name, so match the option value by its visible text.
      const exchange = addDialog.locator('select#idStockexchange');
      const exchangeValue = await addDialog.locator('select#idStockexchange option')
        .filter({hasText: /SIX\s+Swiss\s+Exchange/i}).first().getAttribute('value');
      await exchange.selectOption(exchangeValue!);
      await exchange.dispatchEvent('change');
      await runSearch(page, addDialog);

      // Add instruments in rounds of up to 10 until the set holds 20.
      let added = await matrixRowCount(page);
      while (added < MAX_INSTRUMENTS) {
        const take = Math.min(10, MAX_INSTRUMENTS - added);
        await selectResultRows(addDialog, take);
        await clickAddAndWait(addDialog);
        added += take;
        // Results reload (already-added instruments are excluded) — wait for fresh rows.
        await expect(addDialog.locator('correlation-set-add-instrument-table tbody tr').first())
          .toBeVisible({timeout: 15_000});
      }

      // In-dialog proof: with 20 instruments in the set, selecting one more disables Add.
      await selectResultRows(addDialog, 1);
      await expect(addButton(addDialog)).toBeDisabled({timeout: 5_000});

      await closeAddInstrumentDialog(page, addDialog);
    }

    // The matrix now holds exactly the maximum number of instruments.
    await expect(matrixRows(page)).toHaveCount(MAX_INSTRUMENTS, {timeout: 20_000});

    // At the limit, Optimus UI prevents interaction with the add command via aria-disabled.
    const menu = await openContextMenu(page);
    const addInstrumentItem = menu.getByRole('menuitem', {
      name: /(Bestehendes\s+Instrument\s+hinzuf.gen|Add\s+existing\s+instrument)/i,
    }).first();
    await expect(addInstrumentItem).toHaveAttribute('aria-disabled', 'true');
    await expect(page.locator('correlation-add-instrument .p-dialog')).toHaveCount(0);
  });
});
