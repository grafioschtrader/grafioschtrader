import {expect, Locator, Page, test} from '@playwright/test';
import * as fs from 'fs';
import * as path from 'path';
import {TaskTypeExtended} from '../src/app/shared/types/task.type.extended';
import {loginAsFixtureUser, parseCsvRow} from './helpers';
import {enumLabelRx, openContextMenu} from './generic-connector.helpers';

/**
 * Schedules the batch jobs declared in testdata/taskdatachange.csv as the admin user, through the
 * create dialog of the batch processing monitor.
 *
 * Until now this job was queued by `ExecuteStartupTask` when the backend started, for `now + 5 min`
 * and with PRIO_HIGH. In an e2e run that is the wrong moment: the roundtrip first boots the backend,
 * then runs two backend resource-suite phases around the early Playwright specs, so the five minutes
 * were long over before the first spec even logged in. The price update therefore ran without the
 * connector API keys of `005-connector-api-key.spec.ts` and competed with every spec for database,
 * CPU and connectors. `application-e2e.properties` now sets `gt.startup.price.update.task=false`,
 * which removes the startup job, and this spec creates it explicitly.
 *
 * Numbered 100 so that everything the jobs work on already exists: the API keys (005), the currency
 * pairs (045), the instruments (050, 065) and the generic connectors (095). The price update is
 * scheduled for `now + 10 min`, late enough for the remaining specs to create their data first. The
 * rule-set calendar rebuild follows one minute later, so the single BackgroundWorker executes it
 * only after the price update. The spec does not wait for execution — a full price update takes far
 * longer than the whole suite.
 *
 * The admin user runs a German UI (locale de-CH in users.json), so every text selector matches DE, EN
 * and the raw NLS key — same convention as generic-connector.helpers.ts.
 */

const ADMIN = 'admin';

interface TaskFixtureRow {
  idTask: number;
  taskName: string;
  startDelayMinutes: number;
  e2e: string;
}

const CSV_PATH = path.resolve(__dirname,
  '../../backend/grafioschtrader-server/src/test/resources/testdata/taskdatachange.csv');

/** Loads and validates the editable, headered task fixture while preserving its execution order. */
function loadE2ERows(): TaskFixtureRow[] {
  const lines = fs.readFileSync(CSV_PATH, 'utf-8').split(/\r?\n/)
    .filter(line => line.trim().length > 0);
  const expectedHeader = ['idTask', 'startDelayMinutes', 'e2e'];
  const header = parseCsvRow(lines.shift() ?? '');
  if (header.join('|') !== expectedHeader.join('|')) {
    throw new Error(`Unexpected header in ${CSV_PATH}: ${header.join('|')}`);
  }

  const rows = lines.map(line => {
    const [idTaskValue, startDelayValue, e2e] = parseCsvRow(line);
    const idTask = Number(idTaskValue);
    const startDelayMinutes = Number(startDelayValue);
    const taskName = TaskTypeExtended[idTask];
    if (!Number.isInteger(idTask) || !taskName || !Number.isInteger(startDelayMinutes)
      || startDelayMinutes < 1) {
      throw new Error(`Invalid task fixture row in ${CSV_PATH}: ${line}`);
    }
    return {idTask, taskName, startDelayMinutes, e2e};
  }).filter(row => row.e2e === 'e');

  if (new Set(rows.map(row => row.idTask)).size !== rows.length) {
    throw new Error(`Duplicate idTask in ${CSV_PATH}`);
  }
  const priceUpdateIndex = rows.findIndex(row =>
    row.idTask === TaskTypeExtended.PRICE_AND_SPLIT_DIV_CALENDAR_UPDATE_THRU);
  const ruleSetIndex = rows.findIndex(row =>
    row.idTask === TaskTypeExtended.CREATE_STOCK_EXCHANGE_CALENDAR_BY_RULE_SET);
  if (priceUpdateIndex < 0 || ruleSetIndex <= priceUpdateIndex
    || rows[ruleSetIndex].startDelayMinutes <= rows[priceUpdateIndex].startDelayMinutes) {
    throw new Error(`Task 53 must follow task 30 with a later start delay in ${CSV_PATH}`);
  }
  return rows;
}

const TASKS = loadE2ERows();

const ADMIN_DATA_NODE_RX = /(Administrative data|Administrative Daten|ADMIN_DATA)/i;
const MONITOR_NODE_RX = /(Batch processing monitor|Stapelverarbeitungs Monitor|TASK_DATA_MONITOR)/i;
const CREATE_RX = /(Create|Erstellen)/i;
const DELETE_RX = /(Delete|Löschen)/i;
const CONFIRM_YES_RX = /^\s*(yes|ja)\s*$/i;

/**
 * Formats a date the way the datepicker of the dialog parses it. `earliestStartTime` is a
 * DataType.DateTimeNumeric field, so form-pcalendar sets `showTime` and the inner input carries
 * `formConfig.dateFormat` ('dd.mm.y' for the de-CH admin) plus the 24-hour time: '28.07.26 14:35'.
 *
 * The value is a local wall-clock time. The dialog converts it to UTC before the POST and the
 * backend runs in UTC as well (GTStartUp sets the JVM default to BaseConstants.TIME_ZONE), so the
 * delay arrives at BackgroundWorker unchanged.
 */
function toDeChShortDateTime(date: Date): string {
  const pad = (value: number): string => String(value).padStart(2, '0');
  return `${pad(date.getDate())}.${pad(date.getMonth() + 1)}.${pad(date.getFullYear() % 100)} `
    + `${pad(date.getHours())}:${pad(date.getMinutes())}`;
}

/** Navigates to the batch processing monitor under the 'Administrative data' root and waits for the table. */
async function openTaskDataMonitorView(page: Page): Promise<void> {
  const node = page.locator('.p-tree-node-content', {hasText: MONITOR_NODE_RX}).first();
  if (!await node.isVisible()) {
    // The root is created expanded, but expand it explicitly when a previous state collapsed it.
    const root = page.locator('.p-tree-node-content', {hasText: ADMIN_DATA_NODE_RX}).first();
    await root.waitFor({state: 'visible', timeout: 15_000});
    await root.dblclick();
  }
  await node.waitFor({state: 'visible', timeout: 15_000});
  await node.click();

  await page.locator('.data-container').first().waitFor({state: 'visible', timeout: 15_000});
  // Let readData() populate the table before the caller counts rows.
  await page.waitForTimeout(800);
}

/**
 * Picks a task type in the create dialog. `idTask` is a filterable PrimeNG p-select
 * (DynamicFieldHelper.createFieldDropdownStringHeqF), not a native <select>, so the value is chosen
 * from an overlay list instead of through selectOption(). The item text is the translated task
 * description followed by ' - ' and the task number; that number is its only language-independent
 * part, so the option is matched on the trailing number.
 */
async function selectTaskType(page: Page, dialog: Locator, taskNumber: number): Promise<void> {
  // config.field becomes the id of the p-select host element (its [attr.id] host binding).
  const dropdown = dialog.locator('#idTask');
  await expect(dropdown).toBeVisible({timeout: 10_000});
  await dropdown.click();

  const overlay = page.locator('.p-select-overlay').first();
  await overlay.waitFor({state: 'visible', timeout: 10_000});
  const option = overlay.locator('[role="option"]')
    .filter({hasText: new RegExp(`-\\s*${taskNumber}\\s*$`)});
  await expect(option, `task ${taskNumber} is not offered — is it above maxUserCreateTask?`)
    .toHaveCount(1, {timeout: 10_000});
  await option.first().click();
  await overlay.waitFor({state: 'hidden', timeout: 10_000});
  await expect(dropdown, 'the picked task is not shown in the closed dropdown')
    .toContainText(new RegExp(`-\\s*${taskNumber}\\s*$`));
}

/** All data rows of the task table. */
function taskRows(page: Page): Locator {
  return page.locator('.data-container tbody tr');
}

/**
 * Rows whose cell contains the translated label of the given NLS key. The task description and the
 * progress state are rendered translated (TranslateValue.NORMAL), so they are matched through
 * enumLabelRx, which resolves the DE and EN text from the backend properties files and falls back to
 * the raw key. Column positions are not usable here: the expander column shifts every index.
 */
function rowsWithLabel(page: Page, rows: Locator, key: string): Locator {
  return rows.filter({has: page.locator('td', {hasText: enumLabelRx(key)})});
}

/** Opens the row context menu for the given row (pContextMenuRow selects the row on right-click). */
async function openRowMenu(page: Page, row: Locator): Promise<Locator> {
  await row.click();
  await page.waitForTimeout(200);
  await row.click({button: 'right'});
  const menu = page.locator('[role="menu"]:visible');
  await menu.waitFor({state: 'visible', timeout: 5_000});
  return menu;
}

/**
 * Confirms the PrimeNG confirm dialog. Several p-dialog hosts with role="alertdialog" are present in
 * the DOM at all times (the tree's and the table's), so the visible one must be selected explicitly —
 * an unfiltered .first() resolves to a permanently hidden host.
 */
async function confirmDialogAccept(page: Page): Promise<void> {
  const confirmDialog = page.locator('[role="alertdialog"]:visible').first();
  await confirmDialog.waitFor({state: 'visible', timeout: 10_000});
  await confirmDialog.getByRole('button', {name: CONFIRM_YES_RX}).first().click();
}

test.describe.serial('schedule CSV-defined batch jobs as admin', () => {

  test('removes previously scheduled fixture jobs', async ({page}) => {
    await loginAsFixtureUser(page, ADMIN);
    await openTaskDataMonitorView(page);

    for (const task of TASKS) {
      // A rerun against the same grafioschtrader_t must not pile up another fixture job. A row that
      // is already executing cannot be deleted — hasRightsForDeleteEntity() drops the menu entry
      // for PROG_RUNNING — so it is skipped here and tolerated by the create tests below.
      const fixtureRows = rowsWithLabel(page, taskRows(page), task.taskName);
      const deletable = fixtureRows.filter({
        hasNot: page.locator('td', {hasText: enumLabelRx('PROG_RUNNING')})
      });
      for (let guard = 0; guard < 20; guard++) {
        // Count only this task type: preceding specs enqueue other jobs and BackgroundWorker may
        // start or finish one while the table reloads, so the total row count is a moving target.
        const before = await deletable.count();
        if (before === 0) {
          break;
        }
        const menu = await openRowMenu(page, deletable.first());
        const deleted = page.waitForResponse(response =>
          response.url().includes('/taskdatachange') && response.request().method() === 'DELETE'
          && response.ok(), {timeout: 20_000});
        await menu.getByText(DELETE_RX).first().click();
        await confirmDialogAccept(page);
        await deleted;
        await expect(deletable).toHaveCount(before - 1, {timeout: 10_000});
      }
      await expect(deletable, `task ${task.idTask} jobs left behind`).toHaveCount(0);
    }
  });

  for (const task of TASKS) {
    test(`creates task ${task.idTask} with a delayed start`, async ({page}) => {
      await loginAsFixtureUser(page, ADMIN);
      await openTaskDataMonitorView(page);

      const startTimeInput = toDeChShortDateTime(
        new Date(Date.now() + task.startDelayMinutes * 60_000));

      const menu = await openContextMenu(page);
      await menu.getByText(CREATE_RX).first().click();

      const dialog = page.locator('.p-dialog');
      await dialog.waitFor({state: 'visible', timeout: 10_000});

      await selectTaskType(page, dialog, task.idTask);
      await page.waitForTimeout(300);

      // Task 30 has no entity field. Task 53 offers optional entity scopes; leaving it empty requests
      // the full rebuild of every rule-based exchange. Either way no idEntity may be submitted.
      const entity = dialog.locator('select#entity');
      if (await entity.isVisible()) {
        await expect(entity).toHaveValue('');
      }
      await expect(dialog.locator('select#idEntity, input#idEntityNum')).toBeHidden();

      // Real key events are mandatory: PrimeNG's onUserInput ignores input events that were not
      // preceded by a keydown, so fill() never reaches the model.
      const dateInput = dialog.locator('#earliestStartTime input, input#earliestStartTime').first();
      await dateInput.click();
      await dateInput.press('Control+a');
      await dateInput.press('Backspace');
      await dateInput.pressSequentially(startTimeInput, {delay: 20});
      await dateInput.blur();
      await expect(dateInput, 'earliestStartTime did not keep the typed value')
        .toHaveValue(startTimeInput);

      const saved = page.waitForResponse(response =>
        response.url().includes('/taskdatachange') && response.request().method() === 'POST'
        && response.ok(), {timeout: 20_000});
      await dialog.locator('button[type="submit"]').click();
      const response = await saved;
      await dialog.waitFor({state: 'hidden', timeout: 15_000});

      // Assert on the persisted entity rather than on formatted table cells.
      const created = await response.json();
      expect(created.idTask, `created job is not task ${task.idTask}`).toBe(task.taskName);
      expect(created.progressStateType, `task ${task.idTask} is not waiting`).toBe('PROG_WAITING');
      expect(created.taskAsId, `created job does not carry id_task ${task.idTask}`).toBe(task.idTask);
      expect(created.entity, `task ${task.idTask} unexpectedly carries an entity`).toBeNull();
      expect(created.idEntity, `task ${task.idTask} unexpectedly carries an entity id`).toBeNull();

      // The cleanup test removed prior waiting rows. Execution itself happens minutes later and is
      // deliberately not awaited here.
      const waiting = rowsWithLabel(page,
        rowsWithLabel(page, taskRows(page), task.taskName), 'PROG_WAITING');
      await expect(waiting, `no waiting table row for task ${task.idTask}`)
        .toHaveCount(1, {timeout: 15_000});
    });
  }
});
