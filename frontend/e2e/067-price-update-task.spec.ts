import {expect, Locator, Page, test} from '@playwright/test';
import {loginAsCsvUser} from './helpers';
import {enumLabelRx, openContextMenu} from './generic-connector.helpers';

/**
 * Schedules the batch job PRICE_AND_SPLIT_DIV_CALENDAR_UPDATE_THRU (id_task = 30) as the admin user,
 * through the create dialog of the batch processing monitor.
 *
 * Until now this job was queued by `ExecuteStartupTask` when the backend started, for `now + 5 min`
 * and with PRIO_HIGH. In an e2e run that is the wrong moment: the roundtrip first boots the backend,
 * then runs the backend suite `ResoureTestSuite` and only afterwards Playwright, so the five minutes
 * were long over before the first spec even logged in. The price update therefore ran without the
 * connector API keys of `005-connector-api-key.spec.ts` and competed with every spec for database,
 * CPU and connectors. `application-e2e.properties` now sets `gt.startup.price.update.task=false`,
 * which removes the startup job, and this spec creates it explicitly.
 *
 * Numbered 067 so that everything the update works on already exists: the API keys (005), the
 * currency pairs (035), the instruments (040, 047) and the generic connectors (065). The job is
 * scheduled for `now + 10 min`, late enough for the remaining specs to create their data first;
 * `BackgroundWorker` polls every 15 seconds and picks it up on its own. The spec does not wait for
 * the execution — a full price update takes far longer than the whole suite.
 *
 * The admin user runs a German UI (locale de-CH in users.csv), so every text selector matches DE, EN
 * and the raw NLS key — same convention as generic-connector.helpers.ts.
 */

const ADMIN = 'admin';

/** Enum name; it is also the option key of the idTask dropdown and what the POST body carries. */
const PRICE_UPDATE_TASK = 'PRICE_AND_SPLIT_DIV_CALENDAR_UPDATE_THRU';
/** Numeric task type of that enum constant — the visible, language-independent part of the option label. */
const PRICE_UPDATE_TASK_ID = 30;
/** Minutes between the scheduling of the job and its earliest execution. */
const START_DELAY_MINUTES = 10;

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

test.describe.serial('schedule the price update batch job as admin', () => {

  test('removes previously scheduled price update jobs', async ({page}) => {
    await loginAsCsvUser(page, ADMIN);
    await openTaskDataMonitorView(page);

    // A rerun against the same grafioschtrader_t must not pile up a second job, so every leftover row
    // of this task type goes first. A row that is already executing cannot be deleted —
    // hasRightsForDeleteEntity() drops the menu entry for PROG_RUNNING — so it is skipped here and
    // tolerated by the create test below.
    const priceUpdateRows = rowsWithLabel(page, taskRows(page), PRICE_UPDATE_TASK);
    const deletable = priceUpdateRows.filter({
      hasNot: page.locator('td', {hasText: enumLabelRx('PROG_RUNNING')})
    });
    for (let guard = 0; guard < 20; guard++) {
      // Count only the rows of this task type, never the whole table: the specs before this one
      // enqueue jobs of their own, and BackgroundWorker may start or finish one while the table
      // reloads, so a total row count would be a moving target.
      const before = await deletable.count();
      if (before === 0) {
        break;
      }
      const menu = await openRowMenu(page, deletable.first());
      const deleted = page.waitForResponse(r =>
        r.url().includes('/taskdatachange') && r.request().method() === 'DELETE' && r.ok(),
        {timeout: 20_000});
      await menu.getByText(DELETE_RX).first().click();
      await confirmDialogAccept(page);
      await deleted;
      await expect(deletable).toHaveCount(before - 1, {timeout: 10_000});
    }
    await expect(deletable, 'price update jobs left behind').toHaveCount(0);
  });

  test('creates the price update job with a delayed start', async ({page}) => {
    await loginAsCsvUser(page, ADMIN);
    await openTaskDataMonitorView(page);

    const startTimeInput = toDeChShortDateTime(new Date(Date.now() + START_DELAY_MINUTES * 60_000));

    const menu = await openContextMenu(page);
    await menu.getByText(CREATE_RX).first().click();

    const dialog = page.locator('.p-dialog');
    await dialog.waitFor({state: 'visible', timeout: 10_000});

    // The job is offered because its id (30) stays below TaskDataChangeFormConstraints.maxUserCreateTask.
    await selectTaskType(page, dialog, PRICE_UPDATE_TASK_ID);
    await page.waitForTimeout(300);

    // The task declares no allowed entities, so valueChangedOnIdTask() hides entity and idEntity and
    // the earliest start time stays as the only remaining input.
    await expect(dialog.locator('select#entity')).toBeHidden();

    // The dialog prefills now + 1 minute. Real key events are mandatory: PrimeNG's onUserInput
    // ignores input events that were not preceded by a keydown, so fill() never reaches the model.
    const dateInput = dialog.locator('#earliestStartTime input, input#earliestStartTime').first();
    await dateInput.click();
    await dateInput.press('Control+a');
    await dateInput.press('Backspace');
    await dateInput.pressSequentially(startTimeInput, {delay: 20});
    await dateInput.blur();
    await expect(dateInput, 'earliestStartTime did not keep the typed value')
      .toHaveValue(startTimeInput);

    const saved = page.waitForResponse(r =>
      r.url().includes('/taskdatachange') && r.request().method() === 'POST' && r.ok(),
      {timeout: 20_000});
    await dialog.locator('button[type="submit"]').click();
    const response = await saved;
    await dialog.waitFor({state: 'hidden', timeout: 15_000});

    // Assert on the persisted entity rather than on the cells: the table renders the timestamp
    // through the locale formatter, while the response carries what BackgroundWorker will read.
    const created = await response.json();
    expect(created.idTask, 'created job is not the price update').toBe(PRICE_UPDATE_TASK);
    expect(created.progressStateType, 'created job is not waiting').toBe('PROG_WAITING');
    expect(created.taskAsId, 'created job does not carry id_task 30').toBe(PRICE_UPDATE_TASK_ID);

    // The new row must be in the reloaded table, and it must be the only one of its type — the
    // previous test emptied them. Whether BackgroundWorker later picks it up is deliberately not
    // asserted; that happens minutes later, while the remaining specs run.
    const waiting = rowsWithLabel(page,
      rowsWithLabel(page, taskRows(page), PRICE_UPDATE_TASK), 'PROG_WAITING');
    await expect(waiting, 'no waiting table row for the price update job')
      .toHaveCount(1, {timeout: 15_000});
  });
});
